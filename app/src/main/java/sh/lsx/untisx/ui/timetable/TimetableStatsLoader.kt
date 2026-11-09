package sh.lsx.untisx.ui.timetable

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.StatsData
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.StatsPrefs
import sh.lsx.untisx.ui.util.UiText
import java.time.LocalDate

internal class TimetableStatsLoader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val requireAuth: suspend () -> AuthData,
    private val withPassword: suspend () -> String,
    private val fetchStatsWeek: suspend (AuthData, LocalDate) -> List<TimetableEntry>,
    /** Appended to the account id, so a second data set (for example the own class) is stored apart. */
    private val keySuffix: String = "",
) {

    private fun key(auth: AuthData) = AccountPrefs.accountId(auth) + keySuffix

    var data by mutableStateOf<StatsData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var upToDate by mutableStateOf(false)
        private set
    var year: SchoolYear? = null
        private set

    suspend fun load(accountId: String) {
        data = withContext(Dispatchers.IO) { StatsStore.load(context, accountId + keySuffix) }
    }

    fun currentYear(): SchoolYearStats? = StatsStore.currentYear(data, year)

    fun currentYearLessonCount(): Int {
        val statsYear = currentYear() ?: return 0
        val includeEvents = StatsPrefs.includeEvents(context)

        return statsYear.days.values.sumOf { day ->
            if (includeEvents) day.lessons else day.lessons - day.eventLessons
        }
    }

    suspend fun recordWeek(
        monday: LocalDate,
        entries: List<TimetableEntry>,
        refusedDates: List<Int>,
        auth: AuthData,
    ) {
        val statsYear = resolveYear(auth)

        data = withContext(Dispatchers.IO) {
            StatsStore.recordWeek(
                context = context,
                accountId = key(auth),
                year = statsYear,
                weekStart = monday,
                entries = entries,
                today = AppClock.today(),
                skipDates = refusedDates.toSet(),
            )
        }
    }

    fun loadHistorical() {
        if (loading) return

        loading = true
        error = null

        scope.launch {
            try {
                val currentAuth = requireAuth()
                val resolvedYear = resolveYear(currentAuth)
                year = resolvedYear

                val today = AppClock.today()
                val start = resolvedYear.startDate.toUntisLocalDate()
                val end = if (resolvedYear.endDate > 0) {
                    runCatching { resolvedYear.endDate.toUntisLocalDate() }.getOrDefault(start.plusYears(1).minusDays(1))
                } else {
                    start.plusYears(1).minusDays(1)
                }
                val lastDay = minOf(today, end)

                if (lastDay >= start) {
                    val counted = StatsStore.countedDays(data, resolvedYear)
                    val accountId = key(currentAuth)

                    val weeks = generateSequence(start.toUntisWeekStart()) { it.plusWeeks(1) }
                        .takeWhile { it <= lastDay }
                        .filter { weekStart ->
                            (0..6).any { offset ->
                                val date = weekStart.plusDays(offset.toLong())
                                date >= start && date <= lastDay && date.toUntisDate() !in counted
                            }
                        }
                        .toList()

                    upToDate = weeks.isEmpty()
                    progress = 0 to weeks.size

                    for ((index, weekStart) in weeks.withIndex()) {
                        if (index > 0) delay(500)
                        val entries = fetchStatsWeek(currentAuth, weekStart)

                        data = withContext(Dispatchers.IO) {
                            StatsStore.recordWeek(
                                context = context,
                                accountId = accountId,
                                year = resolvedYear,
                                weekStart = weekStart,
                                entries = entries,
                                today = today,
                            )
                        }

                        progress = index + 1 to weeks.size
                    }
                }

                data = withContext(Dispatchers.IO) {
                    StatsStore.load(context, key(currentAuth))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "stats backfill failed: ${e.javaClass.simpleName}")
                error = friendlyError(e)
            } finally {
                loading = false
                progress = null
            }
        }
    }

    private suspend fun resolveYear(auth: AuthData): SchoolYear {
        year?.let { return it }

        val today = AppClock.today()
        val stored = data?.years?.values
            ?.filter { it.startDate > 0 && it.endDate > 0 }
            ?.filter { today.toUntisDate() in it.startDate..it.endDate }
            ?.maxByOrNull { it.startDate }

        if (stored != null) {
            return SchoolYear(
                id = 0,
                name = stored.name,
                startDate = stored.startDate,
                endDate = stored.endDate,
            ).also { year = it }
        }

        val password = runCatching { withPassword() }.getOrNull()

        return (password?.let { UntisClient.fetchCurrentSchoolYear(auth, it) }
            ?: StatsStore.fallbackYear(today))
            .also { year = it }
    }

    private companion object {
        const val TAG = "TimetableStatsLoader"
    }
}
