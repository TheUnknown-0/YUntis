package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.data.CorePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.AuthResult

@Serializable
data class AuthData(
    val server: String,
    val loginName: String,
    val displayName: String,
    val user: String,
    val session: AuthResult,
) {
    val isLoggedIn: Boolean get() = session.sessionId.isNotBlank()
}

object AuthPrefs {

    const val KEY_BLOB = "auth_blob"

    internal val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_BLOB) }
    }
}
