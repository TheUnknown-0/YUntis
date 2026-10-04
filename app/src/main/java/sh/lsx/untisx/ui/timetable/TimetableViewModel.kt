package sh.lsx.untisx.ui.timetable

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.ExamsMatcher
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.StatsData
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.model.toVisibleWeekStart
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.util.UiText
import sh.lsx.untisx.ui.util.AppDateFormats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface LoadError {
        val text: UiText

        data class Network(override val text: UiText) : LoadError
        data class Auth(override val text: UiText) : LoadError
        data class Server(override val text: UiText) : LoadError
        data class Unknown(override val text: UiText) : LoadError
    }

    sealed interface NotesState {
        data object Loading : NotesState

        data class Loaded(
            val info: String?,
            val substitution: String?,
            val topic: String?,
            val notes: List<String>,
        ) : NotesState

        data object Unavailable : NotesState
    }

    companion object {
        const val WEEK_SPAN = 104
        const val CENTER = WEEK_SPAN / 2

        private const val TAG = "TimetableViewModel"
        private const val SCAN_FROM_MONTHS = 1L
        private const val SCAN_TO_MONTHS = 11L
        private const val EVENTS_AHEAD_MONTHS = 6L
        private const val EXAMS_BACK_MONTHS = 2L
        private const val EXAMS_AHEAD_MONTHS = 6L
        private const val EXAMS_TTL_MS = 12 * 60 * 60 * 1000L
    }

    var weeks by mutableStateOf<Map<Int, List<TimetableEntry>>>(emptyMap())
        private set
    var lastPage by mutableIntStateOf(CENTER)
        private set
    var anchorMonday by mutableStateOf(AppClock.today().toVisibleWeekStart())
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
    var events by mutableStateOf<List<TimetableEntry>>(emptyList())
        private set
    var eventsLoading by mutableStateOf(false)
        private set
    var eventsError by mutableStateOf<UiText?>(null)
        private set
    var eventsFetchedAt by mutableLongStateOf(0L)
        private set
    var dayMessages by mutableStateOf<List<DayMessage>>(emptyList())
        private set
    var messagesFetchedAt by mutableLongStateOf(0L)
        private set
    var lessonNotes by mutableStateOf<NotesState?>(null)
        private set
    var stats by mutableStateOf<StatsData?>(null)
        private set
    var statsLoading by mutableStateOf(false)
        private set
    var statsProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var statsError by mutableStateOf<UiText?>(null)
        private set
    var statsUpToDate by mutableStateOf(false)
        private set
    var exams by mutableStateOf<List<ExamInfo>>(emptyList())
        private set

    private var statsYear: SchoolYear? = null
    private var examsFetchedAt = 0L

    private var auth: AuthData? = null
    private var staticDataLoaded = false
    private var subjectsScanned = false
    private var eventsRequested = false
    private val weekCache = HashMap<Int, List<TimetableEntry>>()
    private val failedWeeks = HashSet<Int>()
    private val flightLock = Mutex()
    private val persistLock = Mutex()
    private val notesCache = HashMap<Int, NotesState.Loaded>()
    private var notesRequestId = 0

    var lastFetch by mutableLongStateOf(0L)
        private set

    private val backgroundJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val backgroundScope = CoroutineScope(viewModelScope.coroutineContext + backgroundJob)

    fun cancelBackgroundWork() {
        backgroundJob.cancelChildren()
    }

    init {
        backgroundScope.launch {
            AccountStore.ensureLoaded(getApplication())

            val account = AccountStore.active
            val cached = account?.let {
                withContext(Dispatchers.IO) { TimetableCache.load(getApplication(), AccountPrefs.accountId(it)) }
            }

            stats = account?.let {
                withContext(Dispatchers.IO) { StatsStore.load(getApplication(), AccountPrefs.accountId(it)) }
            }

            val cachedExams = account?.let {
                withContext(Dispatchers.IO) { ExamsStore.load(getApplication(), AccountPrefs.accountId(it)) }
            }

            if (cachedExams != null) {
                exams = cachedExams.exams
                examsFetchedAt = cachedExams.fetchedAt
            }

            if (cached != null) {
                Log.d(TAG, "cache loaded: ${cached.weeks.size} weeks")
                applyCached(cached)
            }

            refreshCurrentWeek()
            refreshExams(force = false)
        }
    }

    fun pageToOffset(page: Int): Int = page - CENTER

    fun mondayForPage(page: Int): LocalDate = anchorMonday.plusWeeks(pageToOffset(page).toLong())

    fun pageForMonday(monday: LocalDate): Int =
        (CENTER + ChronoUnit.WEEKS.between(anchorMonday, monday).toInt())
            .coerceIn(0, WEEK_SPAN - 1)

    fun reanchorIfNeeded() {
        val today = AppClock.today().toVisibleWeekStart()

        if (today != anchorMonday) anchorMonday = today
    }

    fun ensureWeekPage(page: Int) {
        lastPage = page
        ensureWeek(mondayForPage(page))
    }

    fun refreshVisibleWeek(monday: LocalDate) {
        refreshing = true

        backgroundScope.launch { refreshExams(force = true) }

        ensureWeek(monday, force = true) {
            refreshing = false
        }
    }

    fun ensureEvents(force: Boolean = false) {
        val messagesStale = messagesFetchedAt <= 0L ||
            Instant.ofEpochMilli(messagesFetchedAt).atZone(ZoneId.systemDefault()).toLocalDate() != AppClock.today()

        if (!force && (eventsRequested || eventsFetchedAt > 0L) && !messagesStale) {
            eventsError = null
            return
        }

        eventsRequested = true
        refreshEvents()
    }

    fun refreshEvents() {
        if (eventsLoading) return

        eventsLoading = true
        eventsError = null

        backgroundScope.launch {
            try {
                val currentAuth = requireAuth()
                val from = AppClock.today()
                val to = from.plusMonths(EVENTS_AHEAD_MONTHS)

                val fetched = fetchEvents(currentAuth, from, to)
                Log.d(TAG, "events fetch -> ${fetched.size} periods, ${fetched.count { it.isEvent }} events")

                events = mergeConsecutiveEvents(
                    fetched
                        .filter { it.isEvent }
                        .sortedWith(compareBy({ it.date }, { it.startTime })),
                )

                eventsFetchedAt = System.currentTimeMillis()

                dayMessages = fetchMessages(currentAuth)
                messagesFetchedAt = System.currentTimeMillis()

                persistCache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "events fetch failed: ${e.javaClass.simpleName}")
                eventsError = friendly(e)
            } finally {
                eventsLoading = false
            }
        }
    }

    fun loadLessonNotes(entry: TimetableEntry) {
        val id = entry.id
        val date = entry.date.toUntisLocalDateOrNull()

        if (id <= 0 || date == null) {
            lessonNotes = NotesState.Unavailable
            return
        }

        notesCache[id]?.let {
            lessonNotes = it
            return
        }

        lessonNotes = NotesState.Loading
        val requestId = ++notesRequestId

        backgroundScope.launch {
            val result = try {
                val currentAuth = requireAuth()
                val detail = UntisClient.fetchLessonDetails(
                    auth = currentAuth,
                    password = withPassword(),
                    entryId = id,
                    date = date,
                    startTime = entry.startTime,
                    endTime = entry.endTime,
                )

                if (detail == null) {
                    NotesState.Unavailable
                } else {
                    NotesState.Loaded(
                        info = detail.info,
                        substitution = detail.substitution,
                        topic = detail.topic,
                        notes = detail.notes,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "lesson detail failed: ${e.javaClass.simpleName}")
                NotesState.Unavailable
            }

            if (requestId != notesRequestId) return@launch

            if (result is NotesState.Loaded) notesCache[id] = result

            lessonNotes = result
        }
    }

    private fun mergeConsecutiveEvents(entries: List<TimetableEntry>): List<TimetableEntry> {
        val merged = ArrayList<TimetableEntry>(entries.size)

        for (entry in entries) {
            val last = merged.lastOrNull()

            if (
                last != null &&
                last.date == entry.date &&
                last.startTime == entry.startTime &&
                last.endTime == entry.endTime &&
                last.eventTitle == entry.eventTitle &&
                last.room == entry.room
            ) {
                continue
            }

            merged.add(entry)
        }

        return merged
    }

    fun currentYearStats(): SchoolYearStats? = StatsStore.currentYear(stats, statsYear)

    fun currentYearLessonCount(): Int =
        currentYearStats()?.days?.values?.sumOf { it.lessons } ?: 0

    fun statsSchoolYear(): SchoolYear? = statsYear

    fun examFor(entry: TimetableEntry): ExamInfo? = ExamsMatcher.best(entry, exams)

    private suspend fun refreshExams(force: Boolean) {
        val currentAuth = auth ?: return
        val now = System.currentTimeMillis()

        if (!force && examsFetchedAt > 0L && now - examsFetchedAt < EXAMS_TTL_MS) return

        try {
            val today = AppClock.today()
            val fetched = UntisClient.fetchExams(
                currentAuth,
                withPassword(),
                today.minusMonths(EXAMS_BACK_MONTHS),
                today.plusMonths(EXAMS_AHEAD_MONTHS),
            )

            exams = fetched
            examsFetchedAt = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                ExamsStore.save(
                    getApplication(),
                    AccountPrefs.accountId(currentAuth),
                    ExamsStore.Data(exams = fetched, fetchedAt = examsFetchedAt),
                )
            }

            Log.d(TAG, "exams -> ${fetched.size}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "exams fetch failed: ${e.javaClass.simpleName}")
        }
    }

    fun loadHistoricalStats() {
        if (statsLoading) return

        statsLoading = true
        statsError = null

        backgroundScope.launch {
            try {
                val currentAuth = requireAuth()
                val year = resolveStatsYear(currentAuth)
                statsYear = year

                val today = AppClock.today()
                val start = year.startDate.toUntisLocalDate()
                val end = if (year.endDate > 0) {
                    runCatching { year.endDate.toUntisLocalDate() }.getOrDefault(start.plusYears(1).minusDays(1))
                } else {
                    start.plusYears(1).minusDays(1)
                }
                val lastDay = minOf(today, end)

                if (lastDay >= start) {
                    val counted = StatsStore.countedDays(stats, year)
                    val accountId = AccountPrefs.accountId(currentAuth)

                    val weeks = generateSequence(start.toUntisWeekStart()) { it.plusWeeks(1) }
                        .takeWhile { it <= lastDay }
                        .filter { weekStart ->
                            (0..6).any { offset ->
                                val date = weekStart.plusDays(offset.toLong())
                                date >= start && date <= lastDay && date.toUntisDate() !in counted
                            }
                        }
                        .toList()

                    statsUpToDate = weeks.isEmpty()
                    statsProgress = 0 to weeks.size

                    for ((index, weekStart) in weeks.withIndex()) {
                        val entries = fetchStatsWeek(currentAuth, weekStart)

                        stats = withContext(Dispatchers.IO) {
                            StatsStore.recordWeek(
                                context = getApplication(),
                                accountId = accountId,
                                year = year,
                                weekStart = weekStart,
                                entries = entries,
                                today = today,
                            )
                        }

                        statsProgress = index + 1 to weeks.size
                    }
                }

                stats = withContext(Dispatchers.IO) {
                    StatsStore.load(getApplication(), AccountPrefs.accountId(currentAuth))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "stats backfill failed: ${e.javaClass.simpleName}")
                statsError = friendly(e)
            } finally {
                statsLoading = false
                statsProgress = null
            }
        }
    }

    private suspend fun resolveStatsYear(auth: AuthData): SchoolYear {
        statsYear?.let { return it }

        val today = AppClock.today()
        val stored = stats?.years?.values
            ?.filter { it.startDate > 0 && it.endDate > 0 }
            ?.filter { today.toUntisDate() in it.startDate..it.endDate }
            ?.maxByOrNull { it.startDate }

        if (stored != null) {
            return SchoolYear(
                id = 0,
                name = stored.name,
                startDate = stored.startDate,
                endDate = stored.endDate,
            ).also { statsYear = it }
        }

        val password = runCatching { withPassword() }.getOrNull()

        return (password?.let { UntisClient.fetchCurrentSchoolYear(auth, it) }
            ?: StatsStore.fallbackYear(today))
            .also { statsYear = it }
    }

    private suspend fun recordStatsWeek(
        monday: LocalDate,
        entries: List<TimetableEntry>,
        refusedDates: List<Int>,
    ) {
        val currentAuth = auth ?: return
        val year = resolveStatsYear(currentAuth)

        stats = withContext(Dispatchers.IO) {
            StatsStore.recordWeek(
                context = getApplication(),
                accountId = AccountPrefs.accountId(currentAuth),
                year = year,
                weekStart = monday,
                entries = entries,
                today = AppClock.today(),
                skipDates = refusedDates.toSet(),
            )
        }
    }

    private suspend fun fetchStatsWeek(
        auth: AuthData,
        weekStart: LocalDate,
    ): List<TimetableEntry> {
        try {
            return UntisClient.getTimetableIntern(auth, withPassword(), weekStart, weekStart.plusDays(6))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "stats week via mobile API failed: ${e.javaClass.simpleName}")
        }

        val (entries, fresh) = SessionManager.withValidSession(getApplication(), auth) { a ->
            UntisClient.getTimetable(
                auth = a,
                startDate = weekStart.toUntisDate(),
                endDate = weekStart.plusDays(6).toUntisDate(),
            )
        }

        if (fresh != null) persistSession(fresh)

        return entries
    }

    fun retry() {
        error = null
        info = null

        val failed = synchronized(failedWeeks) { failedWeeks.toList() }
        failed.forEach { date -> date.toUntisLocalDateOrNull()?.let { ensureWeek(it) } }
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
        ensureWeek(anchorMonday, force = true)
    }

    private fun applyCached(cached: TimetableCache.Data) {
        lastFetch = cached.lastFetch

        for ((key, entries) in cached.weeks) {
            val monday = key.toIntOrNull()
                ?.let { runCatching { it.toUntisLocalDate() }.getOrNull() }
                ?.toUntisWeekStart()
                ?: continue

            val offset = ChronoUnit.WEEKS.between(anchorMonday, monday).toInt()

            if (offset in -CENTER until CENTER) {
                weekCache[monday.toUntisDate()] = entries
            }
        }

        weeks = weekCache.toMap()
        subjectsById = cached.subjects.associateBy { it.id }
        teachersById = cached.teachers.associateBy { it.id }
        roomsById = cached.rooms.associateBy { it.id }
        holidays = cached.holidays
        timegrid = cached.timegrid
        restrictedDays = cached.restrictedDays
        events = cached.events
        eventsFetchedAt = cached.eventsFetchedAt
        dayMessages = cached.messages
        messagesFetchedAt = cached.messagesFetchedAt
    }

    private fun ensureWeek(
        monday: LocalDate,
        force: Boolean = false,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        backgroundScope.launch {
            val key = monday.toUntisDate()

            if (!force && weekCache.containsKey(key)) return@launch

            val claimed = flightLock.withLock {
                if (!force && key in loadingWeeks) null else {
                    loadingWeeks = loadingWeeks + key
                    key
                }
            } ?: return@launch

            var success = false

            try {
                val (entries, fresh, allRefused, refusedDays) = fetchWeek(claimed, monday)

                Log.d(
                    TAG,
                    "week $claimed result: ${entries.size} entries " +
                        entries.groupBy { it.date }.mapValues { it.value.size } +
                        " refused=$refusedDays allRefused=$allRefused",
                )

                if (fresh != null) persistSession(fresh)

                try {
                    recordStatsWeek(monday, entries, refusedDays)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "stats week record failed: ${e.javaClass.simpleName}")
                }

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
                    val todayDate = AppClock.today().toUntisDate()

                    val unseen = if (claimed >= anchorMonday.toUntisDate() && holidays.isNotEmpty()) {
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

                if (claimed == anchorMonday.toUntisDate()) info = null

                if (!staticDataLoaded) {
                    staticDataLoaded = true
                    loadStaticData()
                }

                backgroundScope.launch { refreshExams(force = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "week $claimed failed: ${e.javaClass.simpleName}")
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

    private suspend fun requireAuth(): AuthData {
        auth?.let { return it }

        AccountStore.ensureLoaded(getApplication())

        val stored = AccountPrefs.getActive()
            ?: throw UntisException.BadCredentials

        auth = stored

        return stored
    }

    private suspend fun withPassword(): String {
        val id = AccountPrefs.accountId(requireAuth())

        return withContext(Dispatchers.IO) { PasswordVault.retrieve(getApplication(), id) }
            ?: throw UntisException.BadCredentials
    }

    private suspend fun fetchWeek(claimed: Int, monday: LocalDate): WeekFetch {
        val currentAuth = requireAuth()

        if (!monday.isBefore(anchorMonday)) {
            val rest = try {
                UntisClient.fetchTimetableEntries(currentAuth, withPassword(), monday, monday.plusDays(6))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "REST week $claimed failed: ${e.javaClass.simpleName}")
                null
            }

            if (rest != null) {
                Log.d(TAG, "week $claimed via REST -> ${rest.size} entries")
                return WeekFetch(resolveElementIds(rest), currentAuth, allRefused = false)
            }
        }

        val intern = try {
            UntisClient.getTimetableIntern(currentAuth, withPassword(), monday, monday.plusDays(6))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "intern week $claimed failed: ${e.javaClass.simpleName}")
            null
        }

        if (!intern.isNullOrEmpty()) {
            Log.d(TAG, "week $claimed via intern -> ${intern.size} entries")
            return WeekFetch(intern, currentAuth, allRefused = false)
        }

        return weekViaLegacy(currentAuth, monday)
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

            Log.d(TAG, "week ${monday.toUntisDate()} via legacy -> ${entries.size} entries")

            WeekFetch(entries, fresh, allRefused = false)
        } catch (e: UntisException.Api) {
            if (e.code != UntisClient.RPC_NO_TIMETABLE && e.code != UntisClient.RPC_NO_RIGHTS) throw e

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
                AppDateFormats.formatter(getApplication(), "EEEdMMMjm")
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
                if (e.code == UntisClient.RPC_NO_TIMETABLE || e.code == UntisClient.RPC_NO_RIGHTS) {
                    refused += day.toUntisDate()
                    continue
                }

                throw e
            }
        }

        return WeekFetch(all, latest, allRefused = succeeded == 0, refusedDays = refused)
    }

    private suspend fun fetchMessages(auth: AuthData): List<DayMessage> {
        val password = try {
            withPassword()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "messages fetch failed: ${e.javaClass.simpleName}")
            return dayMessages
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
            } catch (e: Exception) {
                Log.d(TAG, "messages fetch failed: ${e.javaClass.simpleName}")
            }
        }

        return if (anySuccess) found.values.toList() else dayMessages
    }

    private suspend fun fetchEvents(
        auth: AuthData,
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry> {
        val internal = try {
            UntisClient.getTimetableIntern(auth, withPassword(), from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "events via mobile API failed: ${e.javaClass.simpleName}")
            null
        }

        if (!internal.isNullOrEmpty()) return internal

        try {
            val (entries, fresh) = SessionManager.withValidSession(getApplication(), auth) { a ->
                UntisClient.getTimetable(
                    auth = a,
                    startDate = from.toUntisDate(),
                    endDate = to.toUntisDate(),
                )
            }

            if (fresh != null) persistSession(fresh)

            return entries
        } catch (e: CancellationException) {
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

            val (entries, fresh) = SessionManager.withValidSession(getApplication(), latest) { a ->
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

    private fun loadStaticData() {
        val currentAuth = auth ?: return

        backgroundScope.launch {
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

            weekCache.replaceAll { _, entries -> resolveElementIds(entries) }
            publishWeeks()

            mergeTimetableElements()
            persistCache()
            scanSubjectsIfPossible()

            weekCache[anchorMonday.toUntisDate()]?.takeIf { it.isNotEmpty() }?.let { entries ->
                runCatching {
                    recordStatsWeek(anchorMonday, entries, emptyList())
                }
            }
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
            Log.d(TAG, "static data: $label failed: ${e.javaClass.simpleName}")
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

    private fun resolveElementIds(entries: List<TimetableEntry>): List<TimetableEntry> {
        if (subjectsById.isEmpty() && teachersById.isEmpty() && roomsById.isEmpty()) return entries

        fun resolve(
            elements: List<TimetableElement>,
            data: Map<Int, MasterDataItem>,
        ): List<TimetableElement> = elements.map { element ->
            if (element.id > 0 || element.name.isBlank()) {
                element
            } else {
                val match = data.values.firstOrNull { item ->
                    item.name.equals(element.name, ignoreCase = true) ||
                        (element.longName != null && item.longName.equals(element.longName, ignoreCase = true))
                }

                if (match != null) element.copy(id = match.id) else element
            }
        }

        return entries.map { entry ->
            entry.copy(
                subject = resolve(entry.subject, subjectsById),
                teacher = resolve(entry.teacher, teachersById),
                room = resolve(entry.room, roomsById),
            )
        }
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

        backgroundScope.launch {
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
                    if (e.code != UntisClient.RPC_NO_RIGHTS) {
                        Log.d(TAG, "subject scan failed: ${e.javaClass.simpleName}")
                        subjectsScanned = false
                        return@launch
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "subject scan failed: ${e.javaClass.simpleName}")
                    subjectsScanned = false
                    return@launch
                }
            }

            Log.d(TAG, "subject scan failed: no valid range")
            subjectsScanned = false
        }
    }

    private suspend fun resolveTimegrid(currentAuth: AuthData): List<TimegridDay> {
        val password: suspend () -> String = { withPassword() }

        val attempts: List<Pair<String, suspend () -> List<TimegridDay>>> = listOf(
            "getUserData2017" to { UntisClient.fetchTimegridViaUserData(currentAuth, password()) },
            "getTimegrid (internal rpc)" to { UntisClient.fetchTimegridFromInternalRpc(currentAuth) },
            "getTimegrid (public rpc)" to { UntisClient.fetchTimegridRpc(currentAuth) },
        )

        for ((name, attempt) in attempts) {
            val days = try {
                attempt()
            } catch (e: CancellationException) {
                throw e
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

        backgroundScope.launch {
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

    private suspend fun persistSession(updated: AuthData) {
        auth = updated
        AccountPrefs.updateSession(getApplication(), updated)
    }

    private suspend fun release(offset: Int) = flightLock.withLock {
        loadingWeeks = loadingWeeks - offset
    }

    private fun publishWeeks() {
        weeks = weekCache.toMap()
    }

    private suspend fun persistCache() = persistLock.withLock {
        val currentAuth = auth ?: return@withLock

        val weeksSnapshot = weekCache.toMap()
        val subjectsSnapshot = subjectsById.values.toList()
        val teachersSnapshot = teachersById.values.toList()
        val roomsSnapshot = roomsById.values.toList()
        val holidaysSnapshot = holidays
        val timegridSnapshot = timegrid
        val restrictedSnapshot = restrictedDays
        val eventsSnapshot = events
        val eventsFetchedSnapshot = eventsFetchedAt
        val messagesSnapshot = dayMessages
        val messagesFetchedSnapshot = messagesFetchedAt
        val lastFetchSnapshot = lastFetch

        val data = withContext(Dispatchers.IO) {
            val accountId = AccountPrefs.accountId(currentAuth)
            val previous = TimetableCache.load(getApplication(), accountId)

            TimetableCache.Data(
                weeks = weeksSnapshot.mapKeys { (date, _) -> date.toString() },
                subjects = subjectsSnapshot.ifEmpty { previous?.subjects.orEmpty() },
                teachers = teachersSnapshot.ifEmpty { previous?.teachers.orEmpty() },
                rooms = roomsSnapshot.ifEmpty { previous?.rooms.orEmpty() },
                holidays = holidaysSnapshot.ifEmpty { previous?.holidays.orEmpty() },
                timegrid = timegridSnapshot.ifEmpty { previous?.timegrid.orEmpty() },
                restrictedDays = restrictedSnapshot,
                events = if (eventsFetchedSnapshot > 0L) eventsSnapshot else previous?.events.orEmpty(),
                eventsFetchedAt = if (eventsFetchedSnapshot > 0L) eventsFetchedSnapshot else previous?.eventsFetchedAt ?: 0L,
                messages = if (messagesFetchedSnapshot > 0L) messagesSnapshot else previous?.messages.orEmpty(),
                messagesFetchedAt = if (messagesFetchedSnapshot > 0L) messagesFetchedSnapshot else previous?.messagesFetchedAt ?: 0L,
                lastFetch = when {
                    lastFetchSnapshot > 0L -> lastFetchSnapshot
                    previous != null -> previous.lastFetch
                    else -> System.currentTimeMillis()
                },
            ).also { TimetableCache.save(getApplication(), accountId, it) }
        }

        lastFetch = data.lastFetch
    }

    private fun classify(e: Exception): LoadError = when (e) {
        is UntisException.Network -> LoadError.Network(friendly(e))
        is UntisException.Certificate -> LoadError.Network(friendly(e))
        is UntisException.BadCredentials -> LoadError.Auth(friendly(e))
        is UntisException.SessionExpired -> LoadError.Auth(friendly(e))
        is UntisException.Api -> LoadError.Server(friendly(e))
        else -> LoadError.Unknown(friendly(e))
    }

    private fun friendly(e: Exception): UiText = when (e) {
        is UntisException.Network -> UiText.Res(R.string.error_network)
        is UntisException.Certificate -> UiText.Res(R.string.error_certificate)
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
