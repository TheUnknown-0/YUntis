package sh.lsx.untisx.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.CredentialsPrefs
import sh.lsx.untisx.data.prefs.PasswordVault

object SessionManager {

    private const val TAG = "SessionManager"

    private val reauthLock = Mutex()

    @Volatile
    var loginRequiredNotifier: LoginRequiredNotifier? = null

    suspend fun <T> withValidSession(
        context: Context,
        auth: AuthData,
        request: suspend (AuthData) -> T,
    ): Pair<T, AuthData?> {
        return try {
            request(auth) to null
        } catch (e: UntisException.SessionExpired) {
            Log.d(TAG, "session expired -> re-authenticating")

            val fresh = reauthLock.withLock {
                AccountStore.ensureLoaded(context)

                val id = AccountPrefs.accountId(auth)
                val stored = AccountStore.accounts.value
                    .firstOrNull { AccountPrefs.accountId(it) == id }
                    ?: auth

                if (stored.session.sessionId.isNotBlank() &&
                    stored.session.sessionId != auth.session.sessionId
                ) {
                    return@withLock stored.session
                }

                val password = withContext(Dispatchers.IO) {
                    PasswordVault.retrieve(context, id)
                } ?: run {
                    markInvalid(context, id)
                    throw UntisException.BadCredentials
                }

                val result = try {
                    UntisClient.authenticate(
                        server = stored.server,
                        loginName = stored.loginName,
                        user = stored.user,
                        password = password,
                    )
                } catch (e: UntisException.BadCredentials) {
                    markInvalid(context, id)
                    throw e
                }

                AccountStore.updateSession(context, stored.copy(session = result))
                CredentialsPrefs.clear(context, id)

                result
            }

            val updated = auth.copy(session = fresh)

            request(updated) to updated
        }
    }

    private fun markInvalid(context: Context, accountId: String) {
        if (CredentialsPrefs.markInvalid(context, accountId)) {
            loginRequiredNotifier?.onLoginRequired(context)
        }
    }
}