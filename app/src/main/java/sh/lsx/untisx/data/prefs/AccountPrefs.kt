package sh.lsx.untisx.data.prefs

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.MessagesStore
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.security.Crypto
import androidx.core.content.edit

object AccountPrefs {

    const val KEY_ACCOUNTS = "accounts_blob"
    const val KEY_ACTIVE = "active_account"

    private val json = AuthPrefs.json

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun accountId(data: AuthData): String = "${data.server}/${data.loginName}/${data.user}"

    fun loadAccounts(context: Context): List<AuthData> {
        val encoded = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val plain = Crypto.decrypt(encoded) ?: return emptyList()

        return runCatching {
            json.decodeFromString(ListSerializer(AuthData.serializer()), plain)
        }.getOrDefault(emptyList())
    }

    private fun saveAccounts(context: Context, accounts: List<AuthData>) {
        prefs(context).edit {
            putString(
                KEY_ACCOUNTS,
                Crypto.encrypt(json.encodeToString(ListSerializer(AuthData.serializer()), accounts)),
            )
        }
    }

    fun getActiveId(context: Context): String? = prefs(context).getString(KEY_ACTIVE, null)

    fun getActive(context: Context): AuthData? {
        migrate(context)

        val accounts = loadAccounts(context)
        if (accounts.isEmpty()) return null

        val id = getActiveId(context)
        return accounts.firstOrNull { accountId(it) == id } ?: accounts.first()
    }

    fun setActive(context: Context, id: String) {
        prefs(context).edit { putString(KEY_ACTIVE, id) }
    }

    fun addAccount(context: Context, data: AuthData, password: String) {
        val id = accountId(data)
        val accounts = loadAccounts(context).filterNot { accountId(it) == id } + data

        saveAccounts(context, accounts)
        PasswordVault.store(context, id, password)
        setActive(context, id)
    }

    fun removeAccount(context: Context, id: String) {
        val accounts = loadAccounts(context).filterNot { accountId(it) == id }

        saveAccounts(context, accounts)
        PasswordVault.clear(context, id)
        TimetableCache.clear(context, id)
        StatsStore.clear(context, id)
        ExamsStore.clear(context, id)
        MessagesStore.clear(context, id)

        if (getActiveId(context) == id) {
            val next = accounts.firstOrNull()?.let { accountId(it) }

            if (next == null) {
                prefs(context).edit { remove(KEY_ACTIVE) }
            } else {
                setActive(context, next)
            }
        }
    }

    fun updateSession(context: Context, updated: AuthData) {
        val id = accountId(updated)
        val accounts = loadAccounts(context).map { if (accountId(it) == id) updated else it }

        saveAccounts(context, accounts)

        if (getActiveId(context) == null) {
            setActive(context, id)
        }
    }

    private fun migrate(context: Context) {
        val prefs = prefs(context)
        val legacy = AuthPrefs.load(context) ?: return

        val accounts = if (prefs.contains(KEY_ACCOUNTS)) loadAccounts(context) else emptyList()

        if (accounts.none { accountId(it) == accountId(legacy) }) {
            saveAccounts(context, accounts + legacy)
            setActive(context, accountId(legacy))
        }

        prefs.edit { remove(AuthPrefs.KEY_BLOB) }
    }
}
