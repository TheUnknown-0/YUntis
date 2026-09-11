package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.data.security.Crypto
import sh.lsx.untisx.data.prefs.AuthPrefs.json

object PasswordVault {

    private const val PREFIX = "pwd_"

    private fun key(accountId: String) = PREFIX + accountId

    private fun prefs(context: Context) =
        context.getSharedPreferences("untisx_prefs", Context.MODE_PRIVATE)

    fun store(context: Context, accountId: String, password: String) {
        if (password.isBlank()) return

        prefs(context).edit { putString(key(accountId), Crypto.encrypt(password)) }
    }

    fun retrieve(context: Context, accountId: String): String? {
        val encoded = prefs(context).getString(key(accountId), null) ?: return null

        return Crypto.decrypt(encoded)
    }

    fun clear(context: Context, accountId: String) {
        prefs(context).edit { remove(key(accountId)) }
    }
}
