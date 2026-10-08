package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.security.DecryptResult

object PasswordVault {

    private const val PREFIX = "pwd_"

    private fun key(accountId: String) = PREFIX + accountId

    private fun prefs(context: Context) =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun store(context: Context, accountId: String, password: String) {
        if (password.isBlank()) return

        prefs(context).edit { putString(key(accountId), AccountStore.cryptor.encrypt(password)) }
    }

    fun retrieve(context: Context, accountId: String): String? {
        val encoded = prefs(context).getString(key(accountId), null) ?: return null

        return when (val decrypted = AccountStore.cryptor.decrypt(encoded)) {
            is DecryptResult.Value -> decrypted.plaintext
            DecryptResult.Missing, DecryptResult.KeyLost, DecryptResult.Transient -> null
        }
    }

    fun clear(context: Context, accountId: String) {
        prefs(context).edit { remove(key(accountId)) }
    }
}
