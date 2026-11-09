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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.ExamsMatcher
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.OwnExamsStore
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.model.toVisibleWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.notifications.SyncSchedule
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.data.wear.WearSync
import sh.lsx.untisx.ui.util.UiText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

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
    var lessonNotes by mutableStateOf<NotesState?>(null)
        private set
    private var serverExams by mutableStateOf<List<ExamInfo>>(emptyList())

    /** Exams the user added in the app. */
    var ownExams by mutableStateOf<List<ExamInfo>>(emptyList())
        private set

    /** School exams and own exams together; own ones are marked with [ExamInfo.own]. */
    val exams: List<ExamInfo> get() = serverExams + ownExams

    var lastFetch by mutableLongStateOf(0L)
        private set

    val events get() = eventsLoader.events
    val eventsLoading get() = eventsLoader.loading
    val eventsError get() = eventsLoader.error
    val eventsFetchedAt get() = eventsLoader.fetchedAt
    val dayMessages get() = eventsLoader.dayMessages
    val messagesFetchedAt get() = eventsLoader.messagesFetchedAt

    val stats get() = statsLoader.data
    val statsLoading get() = statsLoader.loading
    val statsProgress get() = statsLoader.progress
    val statsError get() = statsLoader.error
    val statsUpToDate get() = statsLoader.upToDate

    private var statsYear: SchoolYear? = null
    private var examsFetchedAt = 0L

    private var auth: AuthData? = null
    private var staticDataLoaded = false
    private var subjectsScanned = false
    private val weekCache = HashMap<Int, List<TimetableEntry>>()
    var weekFetchedAt by mutableStateOf<Map<Int, Long>>(emptyMap())
        private set
    private val weekAttemptAt = HashMap<Int, Long>()
    private var initialized = false
    private val examsLock = Mutex()
    private val failedWeeks = HashSet<Int>()
    private val flightLock = Mutex()
    private val persistLock = Mutex()
    private val notesCache = HashMap<Int, NotesState.Loaded>()
    private var notesRequestId = 0

    private val backgroundJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val backgroundScope = CoroutineScope(viewModelScope.coroutineContext + backgroundJob)

    private val fetch: TimetableFetch = TimetableFetch(
        context = getApplication<Application>(),
        requireAuth = { requireAuth() },
        withPassword = { withPassword() },
        onFreshSession = { persistSession(it) },
        resolveElementIds = { entries -> resolveElementIds(entries, subjectsById, teachersById, roomsById) },
    )

    private val staticData: TimetableStaticData = TimetableStaticData(
        withPassword = { withPassword() },
    )

    private val statsLoader: TimetableStatsLoader = TimetableStatsLoader(
        context = getApplication<Application>(),
        scope = backgroundScope,
        requireAuth = { requireAuth() },
        withPassword = { withPassword() },
        fetchStatsWeek = { account, week -> fetch.fetchStatsWeek(account, week) },
    )

    private val eventsLoader: TimetableEventsLoader = TimetableEventsLoader(
        scope = backgroundScope,
        requireAuth = { requireAuth() },
        fetchEvents = { account, from, to -> fetch.fetchEvents(account, from, to) },
        fetchMessages = { account, fallback -> fetch.fetchMessages(account, fallback) },
        onPersist = { persistCache() },
    )

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

            account?.let { statsLoader.load(AccountPrefs.accountId(it)) }

            ownExams = account?.let {
                withContext(Dispatchers.IO) { OwnExamsStore.load(getApplication(), AccountPrefs.accountId(it)) }
            }.orEmpty()

            val cachedExams = account?.let {
                withContext(Dispatchers.IO) { ExamsStore.load(getApplication(), AccountPrefs.accountId(it)) }
            }

            if (cachedExams != null) {
                serverExams = cachedExams.exams
                examsFetchedAt = cachedExams.fetchedAt
            }

            if (cached != null) {
                Log.d(TAG, "cache loaded: ${cached.weeks.size} weeks")
                applyCached(cached)
                account?.let { SyncSchedule.rememberStarts(getApplication(), AccountPrefs.accountId(it), cached.weeks.values.flatten()) }
            }

            initialized = true
            ensureWeek(mondayForPage(lastPage))
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

    /** Timetable shown instead of the own one, or null for the own timetable. */
    var viewTarget by mutableStateOf<TimetableTarget?>(null)
        private set
    var targetClasses by mutableStateOf<List<TimetableTarget>>(emptyList())
        private set
    var targetStudents by mutableStateOf<List<TimetableTarget>>(emptyList())
        private set
    var targetsLoading by mutableStateOf(false)
        private set
    var targetsLoaded by mutableStateOf(false)
        private set

    private var savedOwnWeeks: HashMap<Int, List<TimetableEntry>>? = null
    private var savedOwnRestricted: Set<Int> = emptySet()
    private val targetFetchedAt = HashMap<Int, Long>()

    /** The account's own class, offered first in the picker. */
    val ownClassId: Int get() = auth?.session?.klasseId ?: 0

    fun showTarget(target: TimetableTarget?) {
        if (target == viewTarget) return

        if (viewTarget == null) {
            savedOwnWeeks = HashMap(weekCache)
            savedOwnRestricted = restrictedDays
        }

        weekCache.clear()
        if (target == null) {
            savedOwnWeeks?.let { weekCache.putAll(it) }
            restrictedDays = savedOwnRestricted
            savedOwnWeeks = null
        } else {
            restrictedDays = emptySet()
        }

        targetFetchedAt.clear()
        weekAttemptAt.clear()
        loadingWeeks = emptySet()
        synchronized(failedWeeks) { failedWeeks.clear() }
        error = null
        viewTarget = target
        publishWeeks()
        ensureWeekPage(lastPage)
    }

    /** Compares names so that numbers sort by value: "7A" before "10A". */
    private fun naturalCompare(a: String, b: String): Int {
        val chunk = Regex("""\d+|\D+""")
        val left = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val right = chunk.findAll(b.lowercase()).map { it.value }.toList()

        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                compareValuesBy(x, y, { it.trimStart('0').length }, { it.trimStart('0') })
            } else {
                x.compareTo(y)
            }
            if (result != 0) return result
        }

        return left.size - right.size
    }

    /** Loads the classes and students the school shows to this account. */
    fun loadTargets(force: Boolean = false) {
        if (targetsLoading || (targetsLoaded && !force)) return
        targetsLoading = true

        backgroundScope.launch {
            try {
                val account = requireAuth()

                suspend fun list(load: suspend (AuthData) -> List<MasterDataItem>): List<MasterDataItem> = try {
                    val (items, fresh) = SessionManager.withValidSession(getApplication(), account) { load(it) }
                    if (fresh != null) persistSession(fresh)
                    items
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "target list failed: ${e.javaClass.simpleName}")
                    emptyList()
                }

                val own = account.session.klasseId
                targetClasses = list { UntisClient.getKlassen(it) }
                    .filter { it.id > 0 && it.name.isNotBlank() }
                    .sortedWith(compareBy<MasterDataItem> { it.id != own }.thenComparator { a, b -> naturalCompare(a.name, b.name) })
                    .map { TimetableTarget(TimetableTarget.CLASS, it.id, it.name, it.longName) }
                targetStudents = list { UntisClient.getStudents(it) }
                    .filter { it.id > 0 && it.id != account.session.personId }
                    .sortedBy { it.longName.lowercase() }
                    .map {
                        TimetableTarget(
                            TimetableTarget.STUDENT,
                            it.id,
                            listOf(it.foreName, it.longName).filter(String::isNotBlank).joinToString(" ").ifBlank { it.name },
                        )
                    }
                targetsLoaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "targets failed: ${e.javaClass.simpleName}")
            } finally {
                targetsLoading = false
            }
        }
    }

    fun ensureWeekPage(page: Int) {
        lastPage = page
        if (initialized) ensureWeek(mondayForPage(page))
    }

    fun refreshVisibleWeek(monday: LocalDate) {
        refreshing = true

        backgroundScope.launch { refreshExams(force = true) }

        ensureWeek(monday, force = true) {
            refreshing = false
        }
    }

    fun ensureEvents(force: Boolean = false) = eventsLoader.ensure(force)

    fun refreshEvents() = eventsLoader.refresh()

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

    fun currentYearStats(): SchoolYearStats? = statsLoader.currentYear()

    fun currentYearLessonCount(): Int = statsLoader.currentYearLessonCount()

    fun statsSchoolYear(): SchoolYear? = statsLoader.year

    fun examFor(entry: TimetableEntry): ExamInfo? = ExamsMatcher.best(entry, exams)

    fun saveOwnExam(exam: ExamInfo) {
        val account = auth ?: return
        viewModelScope.launch {
            ownExams = withContext(Dispatchers.IO) {
                OwnExamsStore.upsert(getApplication(), AccountPrefs.accountId(account), exam)
            }
            persistCache()
        }
    }

    fun deleteOwnExam(id: Int) {
        val account = auth ?: return
        viewModelScope.launch {
            ownExams = withContext(Dispatchers.IO) {
                OwnExamsStore.delete(getApplication(), AccountPrefs.accountId(account), id)
            }
            persistCache()
        }
    }

    fun loadHistoricalStats() = statsLoader.loadHistorical()

    private suspend fun refreshExams(force: Boolean) = examsLock.withLock {
        val currentAuth = auth ?: return@withLock
        val now = System.currentTimeMillis()

        if (examsFetchedAt > 0L && now - examsFetchedAt < if (force) 10_000L else EXAMS_TTL_MS) return@withLock

        try {
            val today = AppClock.today()
            val fetched = UntisClient.fetchExams(
                currentAuth,
                withPassword(),
                today.minusMonths(EXAMS_BACK_MONTHS),
                today.plusMonths(EXAMS_AHEAD_MONTHS),
            )

            serverExams = fetched
            examsFetchedAt = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                ExamsStore.save(
                    getApplication(),
                    AccountPrefs.accountId(currentAuth),
                    ExamsStore.Data(exams = fetched, fetchedAt = examsFetchedAt),
                )
            }

            Log.d(TAG, "exams -> ${fetched.size}")
            persistCache()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "exams fetch failed: ${e.javaClass.simpleName}")
        }
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

    private fun addKnownSubjects(context: Context, names: Set<String>) {
        if (names.isEmpty()) return

        val merged = (SubjectColorsPrefs.getKnown(context) + names).distinct().sorted()

        if (merged != SubjectColorsPrefs.getKnown(context)) {
            SubjectColorsPrefs.setKnown(context, merged)
            Log.d(TAG, "known subjects -> ${merged.size}")
        }
    }

    private fun applyCached(cached: TimetableCache.Data) {
        lastFetch = cached.lastFetch
        weekFetchedAt = cached.weekFetchedAt.mapNotNull { (date, at) -> date.toIntOrNull()?.let { it to at } }.toMap()

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

        subjectsById = cached.subjects.associateBy { it.id }
        publishWeeks()
        teachersById = cached.teachers.associateBy { it.id }
        roomsById = cached.rooms.associateBy { it.id }
        holidays = cached.holidays
        timegrid = cached.timegrid
        restrictedDays = cached.restrictedDays
        eventsLoader.applyCached(cached)
    }

    private fun ensureWeek(
        monday: LocalDate,
        force: Boolean = false,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        backgroundScope.launch {
            val key = monday.toUntisDate()

            val now = System.currentTimeMillis()
            val fetchedAt = if (viewTarget == null) weekFetchedAt[key] else targetFetchedAt[key]
            if (!force && weekCache.containsKey(key) && now - (fetchedAt ?: 0L) < 5 * 60_000L) {
                onDone?.invoke(true)
                return@launch
            }

            val claimed = flightLock.withLock {
                if (key in loadingWeeks || now - (weekAttemptAt[key] ?: 0L) < if (force) 10_000L else 30_000L) null else {
                    loadingWeeks = loadingWeeks + key
                    weekAttemptAt[key] = now
                    key
                }
            } ?: run {
                onDone?.invoke(false)
                return@launch
            }

            var success = false

            try {
                val forTarget = viewTarget
                val (entries, fresh, allRefused, refusedDays) = fetch.fetchWeek(claimed, monday, anchorMonday, forTarget)

                Log.d(
                    TAG,
                    "week $claimed result: ${entries.size} entries " +
                        entries.groupBy { it.date }.mapValues { it.value.size } +
                        " refused=$refusedDays allRefused=$allRefused",
                )

                if (forTarget != null) {
                    // another class or student: only shown, never cached, counted or used for notifications
                    if (fresh != null) persistSession(fresh)
                    if (forTarget == viewTarget) {
                        weekCache[claimed] = entries
                            .let(::mergeConsecutiveLessons)
                            .sortedWith(compareBy({ it.date }, { it.startTime }))
                        mergeTimetableElements()
                        synchronized(failedWeeks) { failedWeeks.remove(claimed) }
                        targetFetchedAt[claimed] = System.currentTimeMillis()
                        publishWeeks()
                        success = true
                    }
                } else {
                    if (fresh != null) persistSession(fresh)
                    auth?.let { SyncSchedule.rememberStarts(getApplication(), AccountPrefs.accountId(it), entries) }

                    try {
                        auth?.let { statsLoader.recordWeek(monday, entries, refusedDays, it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(TAG, "stats week record failed: ${e.javaClass.simpleName}")
                    }

                    weekCache[claimed] = entries
                        .let(::mergeConsecutiveLessons)
                        .sortedWith(compareBy({ it.date }, { it.startTime }))

                    addKnownSubjects(getApplication(), subjectNamesOf(weekCache[claimed] ?: emptyList(), subjectsById))
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
                    weekFetchedAt = weekFetchedAt + (claimed to lastFetch)
                    persistCache()
                    success = true

                    if (claimed == anchorMonday.toUntisDate()) info = null

                    if (!staticDataLoaded) {
                        staticDataLoaded = true
                        loadStaticData()
                    }

                    backgroundScope.launch { refreshExams(force = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "week $claimed failed: ${e.javaClass.simpleName}")
                synchronized(failedWeeks) { failedWeeks.add(claimed) }

                if (force && weekCache.isNotEmpty()) {
                    info = refreshFailureMessage(getApplication(), weekFetchedAt[claimed] ?: 0L, e)
                } else {
                    error = classifyError(e)
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

    private fun loadStaticData() {
        val currentAuth = auth ?: return

        backgroundScope.launch {
            val master = staticData.loadMasterData(currentAuth)

            if (master != null) {
                if (master.subjects.isNotEmpty()) subjectsById = master.subjects.associateBy { it.id }
                if (master.teachers.isNotEmpty()) teachersById = master.teachers.associateBy { it.id }
                if (master.rooms.isNotEmpty()) roomsById = master.rooms.associateBy { it.id }
                if (master.holidays.isNotEmpty()) holidays = master.holidays
                if (master.timegrid.isNotEmpty()) timegrid = master.timegrid
            } else {
                val legacy = staticData.loadLegacy(currentAuth)

                legacy.subjects?.let { subjectsById = it.associateBy { item -> item.id } }
                legacy.teachers?.let { list ->
                    if (list.isNotEmpty()) teachersById = list.associateBy { item -> item.id }
                }
                legacy.rooms?.let { roomsById = it.associateBy { item -> item.id } }
                legacy.holidays?.let { holidays = it }
                legacy.timegrid?.let { if (it.isNotEmpty()) timegrid = it }

                if (legacy.timegridFailed && timegrid.isEmpty()) {
                    timegridError = UiText.Res(R.string.error_timegrid_missing)
                }
            }

            weekCache.replaceAll { _, entries -> resolveElementIds(entries, subjectsById, teachersById, roomsById) }
            publishWeeks()

            mergeTimetableElements()
            persistCache()

            weekCache[anchorMonday.toUntisDate()]?.takeIf { it.isNotEmpty() && viewTarget == null }?.let { entries ->
                runCatching {
                    auth?.let { statsLoader.recordWeek(anchorMonday, entries, emptyList(), it) }
                }
            }
        }
    }

    private fun mergeTimetableElements() {
        val entries = weekCache.values.flatten()

        subjectsById = mergeMissing(subjectsById, entries) { it.subject }
        teachersById = mergeMissing(teachersById, entries) { it.teacher }
        roomsById = mergeMissing(roomsById, entries) { it.room }
    }

    private fun scanSubjectsIfPossible() {
        if (subjectsScanned) return
        if (subjectsById.isEmpty()) return

        val currentAuth = auth ?: return

        subjectsScanned = true

        val context: Context = getApplication()

        addKnownSubjects(context, subjectNamesOf(weekCache.values.flatten(), subjectsById))

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

                    val names = subjectNamesOf(entries, subjectsById)

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

    fun retryTimegrid() {
        timegridError = null

        val currentAuth = auth ?: return

        backgroundScope.launch {
            val days = staticData.resolveTimegrid(currentAuth)

            if (days != null && days.isNotEmpty()) {
                timegrid = days
                persistCache()
            } else if (timegrid.isEmpty()) {
                timegridError = UiText.Res(R.string.error_timegrid_missing)
            }
        }
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
        if (viewTarget == null) learnSchoolColors()
    }

    private fun learnSchoolColors() {
        val entries = weekCache.values.flatten()

        // Names are only known once master data is there; record them right away so
        // colors and the subject list do not wait for the next week fetch.
        addKnownSubjects(getApplication(), subjectNamesOf(entries, subjectsById))

        val relevant = SubjectColorsPrefs.getKnown(getApplication()).toSet()
        if (relevant.isEmpty()) return
        val (back, fore) = schoolColorsOf(entries, subjectsById, relevant)

        SubjectColorsPrefs.setSchoolColors(getApplication(), back, fore)
    }

    private suspend fun persistCache() = persistLock.withLock {
        val currentAuth = auth ?: return@withLock

        val weeksSnapshot = (savedOwnWeeks ?: weekCache).toMap()
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

            AccountStore.mergeTimetableCache(getApplication(), accountId, TimetableCache.Data(
                weeks = weeksSnapshot.mapKeys { (date, _) -> date.toString() },
                weekFetchedAt = weekFetchedAt.mapKeys { (date, _) -> date.toString() },
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
            ))
        } ?: return@withLock

        lastFetch = data.lastFetch
        viewModelScope.launch(Dispatchers.IO) {
            WearSync.push(getApplication(), data, currentAuth)
        }
    }
}
