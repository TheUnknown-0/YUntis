package sh.lsx.untisx.data.prefs

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.security.Crypto
import androidx.core.content.edit

@Serializable
data class AuthData(
    val server: String,
    val loginName: String,
    val displayName: String,
    val user: String,
    val password: String,
    val session: AuthResult,
) {
    val isLoggedIn: Boolean get() = session.sessionId.isNotBlank()
}

object AuthPrefs {

    const val KEY_BLOB = "auth_blob"

    internal val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AuthData? {
        val encoded = prefs(context).getString(KEY_BLOB, null) ?: return null
        val plain = Crypto.decrypt(encoded) ?: return null

        return runCatching { json.decodeFromString<AuthData>(plain) }.getOrNull()
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_BLOB) }
    }
}
