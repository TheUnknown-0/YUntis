package sh.lsx.untisx.ui.timetable

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.plusUntisWeeks
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val WEEK_SPAN = 104
        const val CENTER = WEEK_SPAN / 2

        private const val TAG = "TimetableViewModel"
        private const val SCAN_FROM_MONTHS = 1L
        private const val SCAN_TO_MONTHS = 11L
    }

    var weeks by mutableStateOf<Map<Int, List<TimetableEntry>>>(emptyMap())
        private set
    var loadingWeeks by mutableStateOf<Set<Int>>(emptySet())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var subjectsById by mutableStateOf<Map<Int, MasterDataItem>>(emptyMap())
        private set
    var teachersById by mutableStateOf<Map<Int, MasterDataItem>>(emptyMap())
        private set
    var roomsById by mutableStateOf<Map<Int, MasterDataItem>>(emptyMap())
        private set
    var holidays by mutableStateOf<List<Holiday>>(emptyList())
        private set
    var timegrid by mutableStateOf<List<TimegridDay>>(emptyList())
        private set
    var timegridError by mutableStateOf<String?>(null)
        private set
    var restrictedDays by mutableStateOf<Set<Int>>(emptySet())
        private set

    private var auth: AuthData? = null
    private var staticDataLoaded = false
    private var subjectsScanned = false
    private val weekCache = HashMap<Int, List<TimetableEntry>>()
    private val failedWeeks = HashSet<Int>()
    private val flightLock = Mutex()
    private var lastFetch = 0L

    init {
        viewModelScope.launch {
            val account = AccountPrefs.getActive(getApplication())
            val cached = account?.let {
                withContext(Dispatchers.IO) { TimetableCache.load(getApplication(), AccountPrefs.accountId(it)) }
            }

            if (cached != null) {
                Log.d(TAG, "cache loaded: ${cached.weeks.size} weeks, lastFetch=${cached.lastFetch}")
                applyCached(cached)
            }

            refreshCurrentWeek()
        }
    }

    fun pageToOffset(page: Int): Int = page - CENTER
    fun offsetToPage(offset: Int): Int = offset + CENTER

    fun ensureWeekPage(page: Int) {
        ensureWeekOffset(pageToOffset(page))
    }

    fun refreshVisibleWeek(offset: Int) {
        refreshing = true

        ensureWeekOffset(offset, force = true) {
            refreshing = false
        }
    }

    fun retry() {
        error = null
        info = null

        val failed = synchronized(failedWeeks) { failedWeeks.toList() }
        failed.forEach { offset -> ensureWeekOffset(offset) }
    }

    fun clearError() {
        error = null
    }

    fun clearInfo() {
        info = null
    }

    fun rescanSubjects() {
        subjectsScanned = false
        scanSubjectsIfPossible()
    }

    private fun subjectNamesOf(entries: Collection<TimetableEntry>): Set<String> =
        entries
            .flatMap { it.subject }
            .mapNotNull { el ->
                subjectsById[el.id]?.name?.takeIf { it.isNotBlank() }
                    ?: el.name.takeIf { it.isNotBlank() }
            }
            .toSet()

    private fun addKnownSubjects(context: Context, names: Set<String>) {
        if (names.isEmpty()) return

        val merged = (SubjectColorsPrefs.getKnown(context) + names).distinct().sorted()

        if (merged != SubjectColorsPrefs.getKnown(context)) {
            SubjectColorsPrefs.setKnown(context, merged)
            Log.d(TAG, "known subjects -> ${merged.size}")
        }
    }

    private fun refreshCurrentWeek() {
        ensureWeekOffset(0, force = true)
    }

    private fun applyCached(cached: TimetableCache.Data) {
        lastFetch = cached.lastFetch

        val currentMonday = LocalDate.now().toUntisWeekStart()

        for ((key, entries) in cached.weeks) {
            val monday = key.toIntOrNull()
                ?.let { runCatching { it.toUntisLocalDate() }.getOrNull() }
                ?.toUntisWeekStart()
                ?: continue

            val offset = ChronoUnit.WEEKS.between(currentMonday, monday).toInt()

            if (offset in -CENTER until CENTER) {
                weekCache[offset] = entries
            }
        }

        weeks = weekCache.toMap()
        subjectsById = cached.subjects.associateBy { it.id }
        teachersById = cached.teachers.associateBy { it.id }
        roomsById = cached.rooms.associateBy { it.id }
        holidays = cached.holidays
        timegrid = cached.timegrid
        restrictedDays = cached.restrictedDays
    }

    private fun ensureWeekOffset(
        offset: Int,
        force: Boolean = false,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            if (!force && weekCache.containsKey(offset)) return@launch

            val claimed = flightLock.withLock {
                if (!force && offset in loadingWeeks) null else {
                    loadingWeeks = loadingWeeks + offset
                    offset
                }
            } ?: return@launch

            var success = false

            try {
                val monday = LocalDate.now().plusUntisWeeks(claimed.toLong())
                val (entries, fresh, allRefused, refusedDays) = fetchWeekForOffset(claimed, monday)

                if (fresh != null) persistSession(fresh)

                weekCache[claimed] = entries
                    .let(::mergeConsecutiveLessons)
                    .sortedWith(compareBy({ it.date }, { it.startTime }))

                addKnownSubjects(getApplication(), subjectNamesOf(weekCache[claimed] ?: emptyList()))

                synchronized(failedWeeks) { failedWeeks.remove(claimed) }

                val weekDayDates = (0 until 5).map { monday.plusDays(it.toLong()).toUntisDate() }

                restrictedDays = if (allRefused && entries.isEmpty()) {
                    restrictedDays + weekDayDates
                } else {
                    val lessonDates = entries.map { it.date }.toSet()
                    val todayDate = LocalDate.now().toUntisDate()

                    val unseen = if (claimed >= 0 && holidays.isNotEmpty()) {
                        weekDayDates.filter { date ->
                            date > todayDate &&
                                date !in lessonDates &&
                                holidays.none { h -> date >= h.startDate && date <= h.endDate }
                        }
                    } else {
                        emptyList()
                    }

                    (restrictedDays - weekDayDates.toSet()) + refusedDays.toSet() + unseen.toSet()
                }

                publishWeeks()
                lastFetch = System.currentTimeMillis()
                persistCache()
                success = true

                if (claimed == 0) info = null

                if (!staticDataLoaded) {
                    staticDataLoaded = true
                    loadStaticData()
                }
            } catch (e: Exception) {
                Log.d(TAG, "week $claimed failed: ${e.message}")
                synchronized(failedWeeks) { failedWeeks.add(claimed) }

                if (force && weekCache.isNotEmpty()) {
                    info = refreshFailureMessage(e)
                } else {
                    error = friendly(e)
                }
            } finally {
                release(claimed)
            }

            onDone?.invoke(success)
        }
    }

    private fun requireAuth(): AuthData = auth
            ?: AccountPrefs.getActive(getApplication())?.also { auth = it }
            ?: throw UntisException.BadCredentials

    private suspend fun fetchWeekForOffset(claimed: Int, monday: LocalDate): WeekFetch {
        val currentAuth = requireAuth()

        return if (claimed > 0) {
            try {
                WeekFetch(
                    UntisClient.getTimetableIntern(currentAuth, monday, monday.plusDays(6)),
                    currentAuth,
                    allRefused = false,
                )
            } catch (e: Exception) {
                Log.d(TAG, "mobile API failed for week $claimed: ${e.message}")
                weekViaLegacy(currentAuth, monday)
            }
        } else {
            weekViaLegacy(currentAuth, monday)
        }
    }

    private suspend fun weekViaLegacy(auth: AuthData, monday: LocalDate): WeekFetch {
        return try {
            val (entries, fresh) = UntisClient.withAuth(auth) { a ->
                UntisClient.getTimetable(
                    auth = a,
                    startDate = monday.toUntisDate(),
                    endDate = monday.plusDays(6).toUntisDate(),
                )
            }

            WeekFetch(entries, fresh, allRefused = false)
        } catch (e: UntisException.Api) {
            if (e.code != -7004 && e.code != -8507) throw e

            val intern = runCatching {
                UntisClient.getTimetableIntern(auth, monday, monday.plusDays(6))
            }.onFailure { Log.d(TAG, "mobile API failed: ${it.message}") }.getOrNull()

            if (!intern.isNullOrEmpty()) {
                WeekFetch(intern, auth, allRefused = false)
            } else {
                fetchWeekByDay(auth, monday)
            }
        }
    }

    private fun refreshFailureMessage(e: Exception): String {
        val age = if (lastFetch > 0) {
            val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(lastFetch).atZone(ZoneId.systemDefault()))
            " (from $formatted)"
        } else ""

        return "Couldn't refresh - showing cached data$age. ${friendly(e)}"
    }

    private data class WeekFetch(
        val entries: List<TimetableEntry>,
        val fresh: AuthData?,
        val allRefused: Boolean,
        val refusedDays: List<Int> = emptyList(),
    )

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
                val (entries, fresh) = UntisClient.withAuth(latest) { a ->
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
                if (e.code == -7004 || e.code == -8507) {
                    refused += day.toUntisDate()
                    continue
                }

                throw e
            }
        }

        return WeekFetch(all, latest, allRefused = succeeded == 0, refusedDays = refused)
    }

    private fun loadStaticData() {
        val currentAuth = auth ?: return

        viewModelScope.launch {
            try {
                coroutineScope {
                    val subjects = async { UntisClient.getSubjects(currentAuth) }
                    val teachers = async { UntisClient.getTeachers(currentAuth) }
                    val rooms = async { UntisClient.getRooms(currentAuth) }
                    val holidays = async { UntisClient.getHolidays(currentAuth) }
                    val timegrid = async { resolveTimegrid(currentAuth) }

                    this@TimetableViewModel.subjectsById = subjects.await().associateBy { it.id }
                    this@TimetableViewModel.teachersById = teachers.await().associateBy { it.id }
                    this@TimetableViewModel.roomsById = rooms.await().associateBy { it.id }
                    this@TimetableViewModel.holidays = holidays.await()

                    val grid = timegrid.await()
                    if (grid.isNotEmpty()) this@TimetableViewModel.timegrid = grid
                }

                persistCache()
                scanSubjectsIfPossible()
            } catch (e: Exception) {
                Log.d(TAG, "static data failed: ${e.message}")
            }
        }
    }

    private fun scanSubjectsIfPossible() {
        if (subjectsScanned) return
        if (subjectsById.isEmpty()) return

        val currentAuth = auth ?: return

        subjectsScanned = true

        val context: Context = getApplication()

        addKnownSubjects(context, subjectNamesOf(weekCache.values.flatten()))

        viewModelScope.launch {
            val ranges = listOf(
                SCAN_FROM_MONTHS to SCAN_TO_MONTHS,
                0L to SCAN_TO_MONTHS,
                0L to 6L,
                0L to 2L,
            )

            for ((fromMonths, toMonths) in ranges) {
                try {
                    val today = LocalDate.now()
                    val from = today.minusMonths(fromMonths).toUntisDate()
                    val to = today.plusMonths(toMonths).toUntisDate()

                    val (entries, fresh) = UntisClient.withAuth(currentAuth) { a ->
                        UntisClient.getTimetable(auth = a, startDate = from, endDate = to)
                    }

                    if (fresh != null) persistSession(fresh)

                    val names = subjectNamesOf(entries)

                    addKnownSubjects(context, names)
                    Log.d(TAG, "subject scan ($fromMonths..$toMonths months) -> ${names.size} subjects")

                    return@launch
                } catch (e: UntisException.Api) {
                    if (e.code != -8507) {
                        Log.d(TAG, "subject scan failed: ${e.message}")
                        subjectsScanned = false
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "subject scan failed: ${e.message}")
                    subjectsScanned = false
                    return@launch
                }
            }

            Log.d(TAG, "subject scan failed: no valid range")
            subjectsScanned = false
        }
    }

    private suspend fun resolveTimegrid(currentAuth: AuthData): List<TimegridDay> {
        val attempts: List<Pair<String, suspend () -> List<TimegridDay>>> = listOf(
            "getUserData2017" to { UntisClient.fetchTimegridViaUserData(currentAuth) },
            "getTimegrid (internal rpc)" to { UntisClient.fetchTimegridFromInternalRpc(currentAuth) },
            "getTimegrid (public rpc)" to { UntisClient.fetchTimegridRpc(currentAuth) },
        )

        for ((name, attempt) in attempts) {
            val days = try {
                attempt()
            } catch (e: Exception) {
                Log.d(TAG, "timegrid via $name failed: ${e.message}")
                null
            } ?: continue

            if (days.isEmpty()) {
                Log.d(TAG, "timegrid via $name -> empty")
                continue
            }

            Log.d(TAG, "timegrid via $name -> ${days.size} days")
            return days
        }

        if (timegrid.isEmpty()) {
            timegridError = "The server doesn't provide a school hour grid (tried getUserData2017 and getTimegrid). The hour column can't be shown."
        }

        return emptyList()
    }

    fun retryTimegrid() {
        timegridError = null

        val currentAuth = auth ?: return

        viewModelScope.launch {
            val days = resolveTimegrid(currentAuth)

            if (days.isNotEmpty()) {
                timegrid = days
                persistCache()
            }
        }
    }

    private fun mergeConsecutiveLessons(entries: List<TimetableEntry>): List<TimetableEntry> {
        val sorted = entries.sortedWith(
            compareBy({ it.date }, { it.startTime }, { it.endTime }),
        )

        val merged = ArrayList<TimetableEntry>(sorted.size)

        for (entry in sorted) {
            val last = merged.lastOrNull()

            if (
                last != null &&
                last.date == entry.date &&
                last.code == entry.code &&
                last.subject == entry.subject &&
                last.teacher == entry.teacher &&
                last.klasse == entry.klasse &&
                last.room == entry.room &&
                last.endTime == entry.startTime
            ) {
                val notes = listOfNotNull(last.lstext, entry.lstext)
                    .distinct()
                    .joinToString(" | ")

                merged[merged.size - 1] = last.copy(
                    endTime = entry.endTime,
                    lstext = notes.ifBlank { null },
                )
            } else {
                merged.add(entry)
            }
        }

        return merged
    }

    private fun persistSession(updated: AuthData) {
        auth = updated
        AccountPrefs.updateSession(getApplication(), updated)
    }

    private suspend fun release(offset: Int) = flightLock.withLock {
        loadingWeeks = loadingWeeks - offset
    }

    private fun publishWeeks() {
        weeks = weekCache.toMap()
    }

    private fun persistCache() {
        val currentAuth = auth ?: return
        val accountId = AccountPrefs.accountId(currentAuth)
        val previous = TimetableCache.load(getApplication(), accountId)

        val data = TimetableCache.Data(
            weeks = weekCache.mapKeys { (offset, _) ->
                LocalDate.now().plusUntisWeeks(offset.toLong()).toUntisWeekStart().toUntisDate().toString()
            },
            subjects = subjectsById.values.toList().ifEmpty { previous?.subjects.orEmpty() },
            teachers = teachersById.values.toList().ifEmpty { previous?.teachers.orEmpty() },
            rooms = roomsById.values.toList().ifEmpty { previous?.rooms.orEmpty() },
            holidays = holidays.ifEmpty { previous?.holidays.orEmpty() },
            timegrid = timegrid.ifEmpty { previous?.timegrid.orEmpty() },
            restrictedDays = restrictedDays,
            lastFetch = when {
                lastFetch > 0L -> lastFetch
                previous != null -> previous.lastFetch
                else -> System.currentTimeMillis()
            },
        )

        lastFetch = data.lastFetch
        TimetableCache.save(getApplication(), accountId, data)
    }

    private fun friendly(e: Exception): String = when (e) {
        is UntisException.Network -> e.message!!
        is UntisException.BadCredentials -> e.message!!
        is UntisException.SessionExpired -> e.message!!
        is UntisException.Api -> "Untis error ${e.code}: ${e.message}"
        else -> "Unexpected error: ${e.message}"
    }
}
