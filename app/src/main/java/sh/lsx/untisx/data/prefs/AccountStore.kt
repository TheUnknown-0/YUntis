package sh.lsx.untisx.data.prefs

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.MessagesStore
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.security.Crypto
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult

sealed interface AccountLoadError {
    data object Transient : AccountLoadError

    data object KeyLost : AccountLoadError
}

class AccountUnavailableException : Exception("Accounts are temporarily unavailable.")

object AccountStore {

    private const val TAG = "AccountStore"

    private val json = AuthPrefs.json
    private val serializer = ListSerializer(AuthData.serializer())

    @Volatile
    internal var cryptor: Cryptor = Crypto

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
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

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
        when (val result = withContext(Dispatchers.IO) { readAccounts(context) }) {
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
                    writeAccounts(context, listOf(legacy))
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
        prefs(context).edit { putString(AccountPrefs.KEY_ACTIVE, id) }
    }

    private fun writeAccounts(context: Context, accounts: List<AuthData>) {
        val encoded = cryptor.encrypt(json.encodeToString(serializer, accounts))

        prefs(context).edit { putString(AccountPrefs.KEY_ACCOUNTS, encoded) }
    }

    suspend fun addAccount(context: Context, data: AuthData, password: String) {
        mutex.withLock {
            ensureEditableLocked(context)

            val id = AccountPrefs.accountId(data)

            withContext(Dispatchers.IO) {
                PasswordVault.store(context, id, password)

                val updated = _accounts.value.filterNot { AccountPrefs.accountId(it) == id } + data

                writeAccounts(context, updated)
                setActiveId(context, id)
                CredentialsPrefs.clear(context, id)

                _accounts.value = updated
                _activeId.value = id
                _error.value = null
                _loaded.value = true
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

                writeAccounts(context, list)

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
        mutex.withLock {
            val accounts = _accounts.value.filterNot { AccountPrefs.accountId(it) == id }

            withContext(Dispatchers.IO) {
                writeAccounts(context, accounts)
                PasswordVault.clear(context, id)
                TimetableCache.clear(context, id)
                StatsStore.clear(context, id)
                ExamsStore.clear(context, id)
                MessagesStore.clear(context, id)
                CredentialsPrefs.clear(context, id)
            }

            _accounts.value = accounts

            if (_activeId.value == id || _activeId.value == null) {
                val next = accounts.firstOrNull()?.let { AccountPrefs.accountId(it) }

                if (next == null) {
                    prefs(context).edit { remove(AccountPrefs.KEY_ACTIVE) }
                } else {
                    setActiveId(context, next)
                }

                _activeId.value = next
            }
        }
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

    internal fun resetForTests() {
        cryptor = Crypto
        _accounts.value = emptyList()
        _activeId.value = null
        _loaded.value = false
        _error.value = null
    }
}
