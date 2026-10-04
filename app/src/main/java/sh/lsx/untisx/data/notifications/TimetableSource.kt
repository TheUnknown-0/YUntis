package sh.lsx.untisx.data.notifications

import android.content.Context
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.PasswordVault
import java.time.LocalDate

object TimetableSource {

    data class Fetched(
        val entries: List<TimetableEntry>,
        val fresh: AuthData?,
    )

    suspend fun fetchWeek(context: Context, auth: AuthData, monday: LocalDate): Fetched =
        fetchRange(context, auth, monday, monday.plusDays(6))

    suspend fun fetchRange(
        context: Context,
        auth: AuthData,
        from: LocalDate,
        to: LocalDate,
    ): Fetched {
        return try {
            val (entries, fresh) = SessionManager.withValidSession(context, auth) { current ->
                UntisClient.getTimetable(
                    auth = current,
                    startDate = from.toUntisDate(),
                    endDate = to.toUntisDate(),
                )
            }

            Fetched(entries, fresh)
        } catch (e: UntisException.Api) {
            if (e.code != UntisClient.RPC_NO_TIMETABLE && e.code != UntisClient.RPC_NO_RIGHTS) throw e

            val password = PasswordVault.retrieve(context, AccountPrefs.accountId(auth))
                ?: throw UntisException.BadCredentials

            Fetched(UntisClient.getTimetableIntern(auth, password, from, to), fresh = null)
        }
    }
}
