package sh.lsx.untisx.ui.timetable

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.plusUntisWeeks
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.util.UiText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface LoadError {
        val text: UiText

        data class Network(override val text: UiText) : LoadError
        data class Auth(override val text: UiText) : LoadError
        data class Server(override val text: UiText) : LoadError
        data class Unknown(override val text: UiText) : LoadError
    }

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
    var error by mutableStateOf<LoadError?>(null)
        private set
    var info by mutableStateOf<UiText?>(null)
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
    var timegridError by mutableStateOf<UiText?>(null)
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
                mergeTimetableElements()

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
                Log.d(TAG, "week $claimed failed")
                synchronized(failedWeeks) { failedWeeks.add(claimed) }

                if (force && weekCache.isNotEmpty()) {
                    info = refreshFailureMessage(e)
                } else {
                    error = classify(e)
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

    private fun withPassword(): String {
        val id = AccountPrefs.accountId(requireAuth())

        return PasswordVault.retrieve(getApplication(), id)
            ?: throw UntisException.BadCredentials
    }

    private suspend fun fetchWeekForOffset(claimed: Int, monday: LocalDate): WeekFetch {
        val currentAuth = requireAuth()

        return if (claimed > 0) {
            try {
                WeekFetch(
                    UntisClient.getTimetableIntern(
                        currentAuth,
                        withPassword(),
                        monday,
                        monday.plusDays(6),
                    ),
                    currentAuth,
                    allRefused = false,
                )
            } catch (e: Exception) {
                Log.d(TAG, "mobile API failed for week $claimed")
                weekViaLegacy(currentAuth, monday)
            }
        } else {
            weekViaLegacy(currentAuth, monday)
        }
    }

    private suspend fun weekViaLegacy(auth: AuthData, monday: LocalDate): WeekFetch {
        return try {
            val (entries, fresh) = SessionManager.withValidSession(getApplication(), auth) { a ->
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
                UntisClient.getTimetableIntern(auth, withPassword(), monday, monday.plusDays(6))
            }.onFailure { Log.d(TAG, "mobile API failed") }.getOrNull()

            if (!intern.isNullOrEmpty()) {
                WeekFetch(intern, auth, allRefused = false)
            } else {
                fetchWeekByDay(auth, monday)
            }
        }
    }

    private fun refreshFailureMessage(e: Exception): UiText {
        if (lastFetch > 0) {
            val timestamp = UiText.Raw(
                DateTimeFormatter.ofPattern("EEE, d. MMM, HH:mm", Locale.getDefault())
                    .format(Instant.ofEpochMilli(lastFetch).atZone(ZoneId.systemDefault())),
            )

            return UiText.Res(
                R.string.error_refresh_cached_at,
                listOf(timestamp, friendly(e)),
            )
        }

        return UiText.Res(R.string.error_refresh_cached, listOf(friendly(e)))
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
                val (entries, fresh) = SessionManager.withValidSession(getApplication(), latest) { a ->
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
            val master = optional("masterData") {
                UntisClient.fetchMasterData(currentAuth, withPassword())
            }

            if (master != null) {
                if (master.subjects.isNotEmpty()) subjectsById = master.subjects.associateBy { it.id }
                if (master.teachers.isNotEmpty()) teachersById = master.teachers.associateBy { it.id }
                if (master.rooms.isNotEmpty()) roomsById = master.rooms.associateBy { it.id }
                if (master.holidays.isNotEmpty()) holidays = master.holidays
                if (master.timegrid.isNotEmpty()) timegrid = master.timegrid
            } else {
                loadStaticDataLegacy(currentAuth)
            }

            mergeTimetableElements()
            persistCache()
            scanSubjectsIfPossible()
        }
    }

    private suspend fun loadStaticDataLegacy(currentAuth: AuthData) {
        coroutineScope {
            val subjects = async { optional("getSubjects") { UntisClient.getSubjects(currentAuth) } }
            val teachers = async { loadTeachers(currentAuth) }
            val rooms = async { optional("getRooms") { UntisClient.getRooms(currentAuth) } }
            val holidays = async { optional("getHolidays") { UntisClient.getHolidays(currentAuth) } }
            val timegrid = async { optional("getTimegrid") { resolveTimegrid(currentAuth) } }

            subjects.await()?.let { subjectsById = it.associateBy { item -> item.id } }
            teachers.await()?.let { list ->
                if (list.isNotEmpty()) teachersById = list.associateBy { item -> item.id }
            }
            rooms.await()?.let { roomsById = it.associateBy { item -> item.id } }
            holidays.await()?.let { this@TimetableViewModel.holidays = it }

            val grid = timegrid.await()
            if (!grid.isNullOrEmpty()) this@TimetableViewModel.timegrid = grid
        }
    }

    private suspend fun <T> optional(label: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "static data: $label failed: ${e.message}")
            null
        }

    private suspend fun loadTeachers(currentAuth: AuthData): List<MasterDataItem>? {
        optional("getTeachers") { UntisClient.getTeachers(currentAuth) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return optional("getTeachers (user data)") {
            UntisClient.fetchTeachersViaUserData(currentAuth, withPassword())
        }
    }

    private fun mergeTimetableElements() {
        subjectsById = mergeMissing(subjectsById) { it.subject }
        teachersById = mergeMissing(teachersById) { it.teacher }
        roomsById = mergeMissing(roomsById) { it.room }
    }

    private fun mergeMissing(
        current: Map<Int, MasterDataItem>,
        selector: (TimetableEntry) -> List<TimetableElement>,
    ): Map<Int, MasterDataItem> {
        val derived = weekCache.values
            .asSequence()
            .flatten()
            .flatMap { entry -> selector(entry).asSequence() }
            .filter { it.id > 0 && it.name.isNotBlank() }
            .associate { it.id to MasterDataItem(id = it.id, name = it.name) }

        if (derived.isEmpty()) return current

        val merged = HashMap(current)

        for ((id, item) in derived) {
            if (merged[id]?.name.isNullOrBlank()) merged[id] = item
        }

        return merged
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

                    val (entries, fresh) = SessionManager.withValidSession(getApplication(), currentAuth) { a ->
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
        val password = { withPassword() }

        val attempts: List<Pair<String, suspend () -> List<TimegridDay>>> = listOf(
            "getUserData2017" to { UntisClient.fetchTimegridViaUserData(currentAuth, password()) },
            "getTimegrid (internal rpc)" to { UntisClient.fetchTimegridFromInternalRpc(currentAuth) },
            "getTimegrid (public rpc)" to { UntisClient.fetchTimegridRpc(currentAuth) },
        )

        for ((name, attempt) in attempts) {
            val days = try {
                attempt()
            } catch (e: Exception) {
                Log.d(TAG, "timegrid via $name failed")
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
            timegridError = UiText.Res(R.string.error_timegrid_missing)
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

    private fun classify(e: Exception): LoadError = when (e) {
        is UntisException.Network -> LoadError.Network(friendly(e))
        is UntisException.BadCredentials -> LoadError.Auth(friendly(e))
        is UntisException.SessionExpired -> LoadError.Auth(friendly(e))
        is UntisException.Api -> LoadError.Server(friendly(e))
        else -> LoadError.Unknown(friendly(e))
    }

    private fun friendly(e: Exception): UiText = when (e) {
        is UntisException.Network -> UiText.Res(R.string.error_network)
        is UntisException.BadCredentials -> UiText.Res(R.string.error_bad_credentials)
        is UntisException.SessionExpired -> UiText.Res(R.string.error_session_expired)
        is UntisException.Api -> UiText.Res(
            R.string.error_untis_generic,
            listOf(UiText.Raw(e.code.toString()), UiText.Raw(e.message)),
        )
        else -> UiText.Res(
            R.string.error_unexpected,
            listOf(UiText.Raw(e.message ?: "")),
        )
    }
}
