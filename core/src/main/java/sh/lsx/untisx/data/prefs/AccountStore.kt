package sh.lsx.untisx.data.prefs

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.MessagesStore
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.notebook.NotebookAccountLifecycle
import sh.lsx.untisx.data.notebook.NotebookAccountLocks
import sh.lsx.untisx.data.security.Crypto
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult

sealed interface AccountLoadError {
    data object Transient : AccountLoadError

    data object KeyLost : AccountLoadError
}

class AccountUnavailableException : Exception("Accounts are temporarily unavailable.")

class NotebookCleanupException(
    accountId: String,
    message: String = "Notebook cleanup failed for account $accountId; the account was not removed.",
) : Exception(message)

enum class AccountRemovalFailure {
    ACCOUNTS_UNAVAILABLE,
    DELETION_MARKER_FAILED,
    ACCOUNT_PERSISTENCE_FAILED,
}

sealed interface AccountRemovalResult {
    data object Removed : AccountRemovalResult
    data class RemovedCleanupPending(val accountId: String) : AccountRemovalResult
    data class Retained(val accountId: String, val failure: AccountRemovalFailure) : AccountRemovalResult
    data class Indeterminate(val accountId: String) : AccountRemovalResult
}

object AccountStore {

    private const val TAG = "AccountStore"

    private val json = AuthPrefs.json
    private val serializer = ListSerializer(AuthData.serializer())

    @VisibleForTesting
    @Volatile
    var cryptor: Cryptor = Crypto

    private val mutex = Mutex()

    private val _accounts = MutableStateFlow<List<AuthData>>(emptyList())
    val accounts: StateFlow<List<AuthData>> = _accounts.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _error = MutableStateFlow<AccountLoadError?>(null)
    val error: StateFlow<AccountLoadError?> = _error.asStateFlow()

    val active: AuthData?
        get() {
            val list = _accounts.value
            val id = _activeId.value

            return list.firstOrNull { AccountPrefs.accountId(it) == id } ?: list.firstOrNull()
        }

    val snapshot: List<AuthData> get() = _accounts.value

    private fun prefs(context: Context) =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun refresh(context: Context) {
        mutex.withLock {
            if (_loaded.value && _error.value == null) return@withLock

            loadLocked(context)
        }
    }

    suspend fun ensureLoaded(context: Context) {
        if (_loaded.value && _error.value == null) return

        mutex.withLock {
            if (!_loaded.value || _error.value != null) loadLocked(context)
        }
    }

    private sealed interface ReadResult {
        data class Ok(val accounts: List<AuthData>) : ReadResult

        data object Missing : ReadResult

        data object Transient : ReadResult

        data object KeyLost : ReadResult
    }

    private suspend fun loadLocked(context: Context) {
        val readResult = withContext(Dispatchers.IO) {
            val result = readAccounts(context)

            runCatching {
                when (result) {
                    is ReadResult.Ok -> NotebookAccountLifecycle.reconcile(
                        context,
                        result.accounts.mapTo(mutableSetOf<String>()) { AccountPrefs.accountId(it) },
                        accountsReadable = true,
                    )

                    ReadResult.Missing -> NotebookAccountLifecycle.reconcile(
                        context,
                        emptySet<String>(),
                        accountsReadable = false,
                    )

                    ReadResult.Transient,
                    ReadResult.KeyLost -> Unit
                }
            }

            result
        }

        when (val result = readResult) {
            is ReadResult.Ok -> {
                _accounts.value = result.accounts
                _activeId.value = readActiveId(context)
                _error.value = null
                _loaded.value = true
            }

            ReadResult.Missing -> {
                _accounts.value = emptyList()
                _activeId.value = readActiveId(context)
                _error.value = null
                _loaded.value = true
            }

            ReadResult.Transient -> _error.value = AccountLoadError.Transient

            ReadResult.KeyLost -> _error.value = AccountLoadError.KeyLost
        }
    }

    private fun readAccounts(context: Context): ReadResult {
        val encoded = prefs(context).getString(AccountPrefs.KEY_ACCOUNTS, null)
            ?: return migrateLegacy(context)

        return when (val decrypted = cryptor.decrypt(encoded)) {
            is DecryptResult.Value -> runCatching {
                json.decodeFromString(serializer, decrypted.plaintext)
            }.fold(
                onSuccess = { ReadResult.Ok(it) },
                onFailure = {
                    Log.d(TAG, "accounts blob unparseable")
                    ReadResult.KeyLost
                },
            )

            DecryptResult.Missing -> ReadResult.Missing

            DecryptResult.KeyLost -> ReadResult.KeyLost

            DecryptResult.Transient -> ReadResult.Transient
        }
    }

    private fun migrateLegacy(context: Context): ReadResult {
        val prefs = prefs(context)
        val legacyEncoded = prefs.getString(AuthPrefs.KEY_BLOB, null) ?: return ReadResult.Missing

        return when (val decoded = cryptor.decrypt(legacyEncoded)) {
            is DecryptResult.Value -> {
                val legacy = runCatching {
                    json.decodeFromString<AuthData>(decoded.plaintext)
                }.getOrNull()

                if (legacy == null) {
                    prefs.edit { remove(AuthPrefs.KEY_BLOB) }
                    ReadResult.Missing
                } else {
                    if (!writeAccounts(context, listOf(legacy))) return ReadResult.Transient

                    setActiveId(context, AccountPrefs.accountId(legacy))
                    prefs.edit { remove(AuthPrefs.KEY_BLOB) }
                    ReadResult.Ok(listOf(legacy))
                }
            }

            DecryptResult.Missing -> ReadResult.Missing

            DecryptResult.KeyLost -> {
                prefs.edit { remove(AuthPrefs.KEY_BLOB) }
                ReadResult.Missing
            }

            DecryptResult.Transient -> ReadResult.Transient
        }
    }

    private fun readActiveId(context: Context): String? =
        prefs(context).getString(AccountPrefs.KEY_ACTIVE, null)

    private fun setActiveId(context: Context, id: String) {
        runCatching { prefs(context).edit().putString(AccountPrefs.KEY_ACTIVE, id).commit() }
    }

    private fun clearActiveId(context: Context) {
        runCatching { prefs(context).edit().remove(AccountPrefs.KEY_ACTIVE).commit() }
    }

    private fun encodeAccounts(accounts: List<AuthData>): String? = try {
        cryptor.encrypt(json.encodeToString(serializer, accounts)).takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w(TAG, "account encryption unavailable: ${e.javaClass.simpleName}")
        null
    }

    private fun commitAccounts(context: Context, encoded: String): Boolean = try {
        prefs(context).edit().putString(AccountPrefs.KEY_ACCOUNTS, encoded).commit()
    } catch (e: Exception) {
        Log.w(TAG, "account commit failed: ${e.javaClass.simpleName}")
        false
    }

    private fun writeAccounts(context: Context, accounts: List<AuthData>): Boolean {
        val encoded = encodeAccounts(accounts) ?: return false
        return commitAccounts(context, encoded)
    }

    private enum class PersistOutcome { COMMITTED, RETAINED, INDETERMINATE }

    private fun verifyRemovalOutcome(
        context: Context,
        accountId: String,
        expectedEncoded: String,
    ): PersistOutcome {
        val stored = prefs(context).getString(AccountPrefs.KEY_ACCOUNTS, null)
            ?: return PersistOutcome.INDETERMINATE

        if (stored == expectedEncoded) return PersistOutcome.COMMITTED

        return when (val decrypted = cryptor.decrypt(stored)) {
            is DecryptResult.Value -> {
                val accounts = runCatching {
                    json.decodeFromString(serializer, decrypted.plaintext)
                }.getOrNull() ?: return PersistOutcome.INDETERMINATE

                if (accounts.any { AccountPrefs.accountId(it) == accountId }) {
                    PersistOutcome.RETAINED
                } else {
                    PersistOutcome.COMMITTED
                }
            }

            DecryptResult.Missing,
            DecryptResult.KeyLost,
            DecryptResult.Transient -> PersistOutcome.INDETERMINATE
        }
    }

    suspend fun addAccount(context: Context, data: AuthData, password: String) {
        val id = AccountPrefs.accountId(data)

        NotebookAccountLocks.lock(id).withLock {
            mutex.withLock {
                ensureEditableLocked(context)

                val existing = _accounts.value.any { AccountPrefs.accountId(it) == id }

                withContext(Dispatchers.IO) {
                    if (!existing && !NotebookAccountLifecycle.finalizeForReadd(context, id)) {
                        throw AccountUnavailableException()
                    }

                    PasswordVault.store(context, id, password)

                    val updated = _accounts.value.filterNot { AccountPrefs.accountId(it) == id } + data

                    if (!writeAccounts(context, updated)) throw AccountUnavailableException()

                    NotebookAccountLifecycle.activate(context, id)
                    setActiveId(context, id)
                    CredentialsPrefs.clear(context, id)

                    _accounts.value = updated
                    _activeId.value = id
                    _error.value = null
                    _loaded.value = true
                }
            }
        }
    }

    suspend fun updateSession(context: Context, updated: AuthData) {
        mutex.withLock {
            if (_error.value != null) return@withLock
            if (!_loaded.value) {
                loadLocked(context)
                if (_error.value != null) return@withLock
            }

            val id = AccountPrefs.accountId(updated)
            val target = _accounts.value.firstOrNull { AccountPrefs.accountId(it) == id } ?: return@withLock

            if (target == updated) return@withLock

            withContext(Dispatchers.IO) {
                val list = _accounts.value.map { if (AccountPrefs.accountId(it) == id) updated else it }

                if (!writeAccounts(context, list)) return@withContext

                val active = _activeId.value
                if (active == null) {
                    setActiveId(context, id)
                    _activeId.value = id
                }

                _accounts.value = list
            }
        }
    }

    suspend fun removeAccount(context: Context, id: String) {
        when (val result = removeAccountTransactional(context, id)) {
            AccountRemovalResult.Removed,
            is AccountRemovalResult.RemovedCleanupPending -> Unit

            is AccountRemovalResult.Retained -> throw NotebookCleanupException(id)

            is AccountRemovalResult.Indeterminate -> throw NotebookCleanupException(
                id,
                "Account removal state could not be confirmed for account $id; " +
                    "notebook data was preserved and cleanup will be finalized on restart.",
            )
        }
    }

    suspend fun removeAccountTransactional(context: Context, id: String): AccountRemovalResult {
        return NotebookAccountLocks.lock(id).withLock {
            mutex.withLock inner@ {
                if (!_loaded.value || _error.value != null) loadLocked(context)

                if (_error.value != null) {
                    return@inner AccountRemovalResult.Retained(
                        id,
                        AccountRemovalFailure.ACCOUNTS_UNAVAILABLE,
                    )
                }

                val accounts = _accounts.value
                val accountWasPresent = accounts.any { AccountPrefs.accountId(it) == id }
                val remaining = accounts.filterNot { AccountPrefs.accountId(it) == id }

                val previousBlocked = withContext(Dispatchers.IO) {
                    NotebookAccountLifecycle.isBlocked(context, id)
                }
                val previousPhase = withContext(Dispatchers.IO) {
                    NotebookAccountLifecycle.phase(context, id)
                }
                val markerPrepared = withContext(Dispatchers.IO) {
                    NotebookAccountLifecycle.prepare(context, id)
                }

                if (!markerPrepared) {
                    val restored = withContext(Dispatchers.IO) {
                        NotebookAccountLifecycle.restoreAfterAbortedRemoval(
                            context,
                            id,
                            previousBlocked,
                            previousPhase,
                        )
                    }

                    return@inner if (restored && accountWasPresent) {
                        AccountRemovalResult.Retained(id, AccountRemovalFailure.DELETION_MARKER_FAILED)
                    } else {
                        AccountRemovalResult.Indeterminate(id)
                    }
                }

                val outcome = if (!accountWasPresent) {
                    PersistOutcome.COMMITTED
                } else {
                    val encoded = withContext(Dispatchers.IO) { encodeAccounts(remaining) }

                    if (encoded == null) {
                        val restored = withContext(Dispatchers.IO) {
                            NotebookAccountLifecycle.restoreAfterAbortedRemoval(
                                context,
                                id,
                                previousBlocked,
                                previousPhase,
                            )
                        }

                        return@inner if (restored) {
                            AccountRemovalResult.Retained(id, AccountRemovalFailure.ACCOUNT_PERSISTENCE_FAILED)
                        } else {
                            AccountRemovalResult.Indeterminate(id)
                        }
                    }

                    val committed = withContext(Dispatchers.IO) { commitAccounts(context, encoded) }

                    if (committed) {
                        PersistOutcome.COMMITTED
                    } else {
                        withContext(Dispatchers.IO) { verifyRemovalOutcome(context, id, encoded) }
                    }
                }

                when (outcome) {
                    PersistOutcome.RETAINED -> {
                        val restored = withContext(Dispatchers.IO) {
                            NotebookAccountLifecycle.restoreAfterAbortedRemoval(
                                context,
                                id,
                                previousBlocked,
                                previousPhase,
                            )
                        }

                        return@inner if (restored) {
                            AccountRemovalResult.Retained(id, AccountRemovalFailure.ACCOUNT_PERSISTENCE_FAILED)
                        } else {
                            AccountRemovalResult.Indeterminate(id)
                        }
                    }

                    PersistOutcome.INDETERMINATE -> return@inner AccountRemovalResult.Indeterminate(id)
                    PersistOutcome.COMMITTED -> Unit
                }

                _accounts.value = remaining
                updateActiveAfterRemovalLocked(context, id, remaining)

                val cleanupComplete = withContext(Dispatchers.IO) {
                    val notebookComplete = try {
                        NotebookAccountLifecycle.finalizeCommittedDeletion(context, id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        false
                    }

                    try {
                        PasswordVault.clear(context, id)
                        TimetableCache.clear(context, id)
                        StatsStore.clear(context, id)
                        ExamsStore.clear(context, id)
                        MessagesStore.clear(context, id)
                        CredentialsPrefs.clear(context, id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) { }

                    notebookComplete
                }

                if (cleanupComplete) {
                    AccountRemovalResult.Removed
                } else {
                    AccountRemovalResult.RemovedCleanupPending(id)
                }
            }
        }
    }

    private suspend fun updateActiveAfterRemovalLocked(
        context: Context,
        removedId: String,
        accounts: List<AuthData>,
    ) {
        if (_activeId.value != removedId && _activeId.value != null) return

        val next = accounts.firstOrNull()?.let { AccountPrefs.accountId(it) }

        withContext(Dispatchers.IO) {
            if (next == null) clearActiveId(context) else setActiveId(context, next)
        }

        _activeId.value = next
    }

    suspend fun discardUnreadable(context: Context) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                prefs(context).edit { remove(AccountPrefs.KEY_ACCOUNTS) }
            }

            _accounts.value = emptyList()
            _activeId.value = null
            _loaded.value = true
            _error.value = null
        }
    }

    fun getActiveIdSync(context: Context): String? = _activeId.value ?: readActiveId(context)

    suspend fun setActive(context: Context, id: String) {
        mutex.withLock {
            if (_accounts.value.none { AccountPrefs.accountId(it) == id }) return@withLock

            withContext(Dispatchers.IO) { setActiveId(context, id) }
            _activeId.value = id
        }
    }

    private suspend fun ensureEditableLocked(context: Context) {
        if (_error.value == AccountLoadError.Transient) throw AccountUnavailableException()

        if (!_loaded.value) loadLocked(context)

        when (_error.value) {
            AccountLoadError.Transient -> throw AccountUnavailableException()

            AccountLoadError.KeyLost -> {
                withContext(Dispatchers.IO) {
                    prefs(context).edit { remove(AccountPrefs.KEY_ACCOUNTS) }
                }

                _accounts.value = emptyList()
                _error.value = null
                _loaded.value = true
            }

            null -> Unit
        }
    }

    @VisibleForTesting
    fun resetForTests() {
        cryptor = Crypto
        _accounts.value = emptyList()
        _activeId.value = null
        _loaded.value = false
        _error.value = null
    }
}
