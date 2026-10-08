package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.data.CorePrefs

object CredentialsPrefs {

    private const val PREFIX = "credentials_invalid_"

    private fun key(accountId: String) = PREFIX + accountId

    private fun prefs(context: Context) =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun isInvalid(context: Context, accountId: String): Boolean =
        prefs(context).getBoolean(key(accountId), false)

    fun markInvalid(context: Context, accountId: String): Boolean {
        if (isInvalid(context, accountId)) return false

        prefs(context).edit { putBoolean(key(accountId), true) }
        return true
    }

    fun clear(context: Context, accountId: String) {
        prefs(context).edit { remove(key(accountId)) }
    }
}
