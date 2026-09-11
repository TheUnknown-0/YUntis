package sh.lsx.untisx.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.PasswordVault

object SessionManager {

    private const val TAG = "SessionManager"

    private val reauthLock = Mutex()

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
                val accounts = AccountPrefs.loadAccounts(context)
                val stored = accounts.firstOrNull { AccountPrefs.accountId(it) == AccountPrefs.accountId(auth) }

                UntisClient.authenticate(
                    server = (stored ?: auth).server,
                    loginName = (stored ?: auth).loginName,
                    user = (stored ?: auth).user,
                    password = retrievePassword(context, stored ?: auth),
                )
            }

            val updated = auth.copy(session = fresh)

            AccountPrefs.updateSession(context, updated)
            request(updated) to updated
        }
    }

    private fun retrievePassword(context: Context, auth: AuthData): String {
        val id = AccountPrefs.accountId(auth)
        return PasswordVault.retrieve(context, id)
            ?: throw UntisException.BadCredentials
    }
}
