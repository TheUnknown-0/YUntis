package sh.lsx.untisx.ui.timetable

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate

internal data class WeekFetch(
    val entries: List<TimetableEntry>,
    val fresh: AuthData?,
    val allRefused: Boolean,
    val refusedDays: List<Int> = emptyList(),
)

internal class TimetableFetch(
    private val context: Context,
    private val requireAuth: suspend () -> AuthData,
    private val withPassword: suspend () -> String,
    private val onFreshSession: suspend (AuthData) -> Unit,
    private val resolveElementIds: (List<TimetableEntry>) -> List<TimetableEntry>,
) {
    private val routePrefs = context.getSharedPreferences("timetable_api_routes", Context.MODE_PRIVATE)
    private fun routeKey(auth: AuthData) = "${auth.server}/${auth.loginName}/${auth.user}"

    suspend fun fetchWeek(claimed: Int, monday: LocalDate, anchorMonday: LocalDate): WeekFetch {
        val currentAuth = requireAuth()

        val preferred = routePrefs.getString(routeKey(currentAuth), null)
        if (!monday.isBefore(anchorMonday) && preferred != "internal" && preferred != "legacy") {
            val rest = try {
                UntisClient.fetchTimetableEntries(currentAuth, withPassword(), monday, monday.plusDays(6))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UntisException.RateLimited) {
                throw e
            } catch (e: UntisException.Network) {
                throw e
            } catch (e: UntisException.Certificate) {
                throw e
            } catch (e: UntisException.BadCredentials) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "REST week $claimed failed: ${e.javaClass.simpleName}")
                null
            }

            if (rest != null) {
                routePrefs.edit().putString(routeKey(currentAuth), "rest").apply()
                Log.d(TAG, "week $claimed via REST -> ${rest.size} entries")
                return WeekFetch(resolveElementIds(rest), currentAuth, allRefused = false)
            }
        }

        val intern = if (preferred == "legacy") null else try {
            UntisClient.getTimetableIntern(currentAuth, withPassword(), monday, monday.plusDays(6))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.RateLimited) {
            throw e
        } catch (e: UntisException.Network) {
            throw e
        } catch (e: UntisException.Certificate) {
            throw e
        } catch (e: UntisException.BadCredentials) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "intern week $claimed failed: ${e.javaClass.simpleName}")
            null
        }

        if (intern != null) {
            routePrefs.edit().putString(routeKey(currentAuth), "internal").apply()
            Log.d(TAG, "week $claimed via intern -> ${intern.size} entries")
            return WeekFetch(intern, currentAuth, allRefused = false)
        }

        return weekViaLegacy(currentAuth, monday)
    }

    private suspend fun weekViaLegacy(auth: AuthData, monday: LocalDate): WeekFetch {
        return try {
            val (entries, fresh) = SessionManager.withValidSession(context, auth) { a ->
                UntisClient.getTimetable(
                    auth = a,
                    startDate = monday.toUntisDate(),
                    endDate = monday.plusDays(6).toUntisDate(),
                )
            }

            Log.d(TAG, "week ${monday.toUntisDate()} via legacy -> ${entries.size} entries")

            WeekFetch(entries, fresh, allRefused = false)
                .also { routePrefs.edit().putString(routeKey(auth), "legacy").apply() }
        } catch (e: UntisException.Api) {
            if (e.code != UntisClient.RPC_NO_TIMETABLE && e.code != UntisClient.RPC_NO_RIGHTS) throw e

            fetchWeekByDay(auth, monday)
        }
    }

    private suspend fun fetchWeekByDay(
        auth: AuthData,
        monday: LocalDate,
    ): WeekFetch {
        val all = ArrayList<TimetableEntry>()
        val refused = ArrayList<Int>()
        var latest = auth
        var succeeded = 0

        for (i in 0 until 7) {
            val day = monday.plusDays(i.toLong())

            try {
                val (entries, fresh) = SessionManager.withValidSession(context, latest) { a ->
                    UntisClient.getTimetable(
                        auth = a,
                        startDate = day.toUntisDate(),
                        endDate = day.toUntisDate(),
                    )
                }

                if (fresh != null) latest = fresh

                all += entries
                succeeded++
            } catch (e: UntisException.Api) {
                if (e.code == UntisClient.RPC_NO_TIMETABLE || e.code == UntisClient.RPC_NO_RIGHTS) {
                    refused += day.toUntisDate()
                    continue
                }

                throw e
            }
        }

        return WeekFetch(all, latest, allRefused = succeeded == 0, refusedDays = refused)
    }

    suspend fun fetchEvents(
        auth: AuthData,
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry> {
        val internal = try {
            UntisClient.getTimetableIntern(auth, withPassword(), from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.RateLimited) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "events via mobile API failed: ${e.javaClass.simpleName}")
            null
        }

        if (internal != null) return internal

        try {
            val (entries, fresh) = SessionManager.withValidSession(context, auth) { a ->
                UntisClient.getTimetable(
                    auth = a,
                    startDate = from.toUntisDate(),
                    endDate = to.toUntisDate(),
                )
            }

            if (fresh != null) onFreshSession(fresh)

            return entries
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.RateLimited) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "events via public API failed: ${e.javaClass.simpleName}")
        }

        return fetchEventsByMonth(auth, from, to)
    }

    private suspend fun fetchEventsByMonth(
        auth: AuthData,
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry> {
        val all = ArrayList<TimetableEntry>()
        var latest = auth
        var cursor = from

        while (!cursor.isAfter(to)) {
            val chunkEnd = minOf(cursor.plusMonths(1).minusDays(1), to)

            val (entries, fresh) = SessionManager.withValidSession(context, latest) { a ->
                UntisClient.getTimetable(
                    auth = a,
                    startDate = cursor.toUntisDate(),
                    endDate = chunkEnd.toUntisDate(),
                )
            }

            if (fresh != null) latest = fresh

            all += entries
            cursor = chunkEnd.plusDays(1)
        }

        return all
    }

    suspend fun fetchStatsWeek(
        auth: AuthData,
        weekStart: LocalDate,
    ): List<TimetableEntry> {
        try {
            return UntisClient.getTimetableIntern(auth, withPassword(), weekStart, weekStart.plusDays(6))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.RateLimited) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "stats week via mobile API failed: ${e.javaClass.simpleName}")
        }

        val (entries, fresh) = SessionManager.withValidSession(context, auth) { a ->
            UntisClient.getTimetable(
                auth = a,
                startDate = weekStart.toUntisDate(),
                endDate = weekStart.plusDays(6).toUntisDate(),
            )
        }

        if (fresh != null) onFreshSession(fresh)

        return entries
    }

    suspend fun fetchMessages(auth: AuthData, fallback: List<DayMessage>): List<DayMessage> {
        val password = try {
            withPassword()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "messages fetch failed: ${e.javaClass.simpleName}")
            return fallback
        }

        val today = LocalDate.now()
        val found = LinkedHashMap<Int, DayMessage>()
        var anySuccess = false

        for (daysBack in 0..2) {
            val date = today.minusDays(daysBack.toLong())

            try {
                val messages = UntisClient.fetchMessagesOfDay(auth, password, date)
                anySuccess = true

                messages.forEach { message ->
                    val key = if (message.id != 0) message.id else message.body.hashCode()
                    found.putIfAbsent(key, message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UntisException.RateLimited) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "messages fetch failed: ${e.javaClass.simpleName}")
            }
        }

        return if (anySuccess) found.values.toList() else fallback
    }

    private companion object {
        const val TAG = "TimetableFetch"
    }
}
