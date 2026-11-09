package sh.lsx.untisx.ui.timetable

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import sh.lsx.untisx.data.ExamsMatcher
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.notebook.LessonKey
import sh.lsx.untisx.data.notebook.NotebookLessonSummary
import sh.lsx.untisx.data.subjectShortLabel
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.theme.currentDayHighlight
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.tapHaptic
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
internal enum class DayPlaceholder {
    HOLIDAY,
    RESTRICTED,
}

private fun focusPageFor(vm: TimetableViewModel, date: Int): Int {
    if (date <= 0) return TimetableViewModel.CENTER

    val target = runCatching { date.toUntisLocalDate().toUntisWeekStart() }.getOrNull()
        ?: return TimetableViewModel.CENTER

    return vm.pageForMonday(target)
}

@Composable
private fun DayPlaceholder.label(): String = when (this) {
    DayPlaceholder.HOLIDAY -> stringResource(R.string.timetable_holiday)
    DayPlaceholder.RESTRICTED -> stringResource(R.string.timetable_restricted)
}

@Composable
fun TimetableScreen(
    vm: TimetableViewModel = viewModel(),
    subjectColors: Map<String, Color> = emptyMap(),
    defaultColor: Color = Color(0xFF81D4FA),
    textColor: Color = Color(0xFF212121),
    subjectTextColors: Map<String, Color> = emptyMap(),
    subjectAliases: Map<String, SubjectAlias> = emptyMap(),
    cancelledStrike: Boolean = true,
    cancelledDesaturation: Float = 1f,
    cancelledBrightness: Float = 1f,
    pastDim: Float = 0.12f,
    lessonOptions: LessonCardOptions = LessonCardOptions(),
    hideCancelled: Boolean = false,
    showLastRefreshed: Boolean = true,
    onLessonClick: (TimetableEntry) -> Unit = {},
    onEventClick: (TimetableEntry) -> Unit = {},
    selectingLesson: Boolean = false,
    onLessonSelected: (TimetableEntry) -> Unit = {},
    notebookBadges: Map<LessonKey, NotebookLessonSummary> = emptyMap(),
    focusDate: Int = 0,
    focusNonce: Int = 0,
    onFocusConsumed: () -> Unit = {},
    compare: CompareSource? = null,
    modifier: Modifier = Modifier,
) {
    val error = vm.error
    val info = vm.info
    val refreshing = vm.refreshing
    val animations = LocalAnimationsEnabled.current
    val lessonClick: (TimetableEntry) -> Unit = if (selectingLesson) onLessonSelected else onLessonClick
    val eventClick: (TimetableEntry) -> Unit = if (selectingLesson) {
        { _: TimetableEntry -> }
    } else {
        onEventClick
    }

    val colorFor = remember(subjectColors, defaultColor) {
        { name: String -> subjectColors[name] ?: defaultColor }
    }

    val textColorFor = remember(subjectTextColors, textColor) {
        { name: String -> subjectTextColors[name] ?: textColor }
    }

    val pagerState = rememberPagerState(
        initialPage = vm.lastPage.coerceIn(0, TimetableViewModel.WEEK_SPAN - 1),
        pageCount = { TimetableViewModel.WEEK_SPAN },
    )
    val scope = rememberCoroutineScope()
    var highlightedDate by remember { mutableStateOf(0) }

    // day view: the shown weekday (1 = Monday), or null for the whole week
    // kept in the view model so returning from the lesson details shows the same day again
    var focusedDay by vm::focusedDay

    // comparing two accounts always uses the day view, starting with today (Monday on weekends)
    val activeDay = focusedDay ?: if (compare != null) LocalDate.now().dayOfWeek.value.takeIf { it <= 5 } ?: 1 else null

    BackHandler(enabled = focusedDay != null || compare != null) {
        if (compare != null) compare.onExit() else focusedDay = null
    }

    fun stepDay(direction: Int) {
        val current = activeDay ?: return
        val next = current + direction

        when {
            next in 1..5 -> focusedDay = next
            direction > 0 -> {
                focusedDay = 1
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(TimetableViewModel.WEEK_SPAN - 1)) }
            }
            else -> {
                focusedDay = 5
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
            }
        }
    }
    val highlight = remember { Animatable(0f) }

    LaunchedEffect(focusNonce) {
        if (focusDate > 0) {
            val date = focusDate
            val target = focusPageFor(vm, focusDate)

            if (animations && abs(target - pagerState.currentPage) <= 2) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }

            onFocusConsumed()
            vm.ensureWeekPage(target)
            withTimeoutOrNull(30_000) {
                snapshotFlow { vm.weeks.containsKey(vm.mondayForPage(target).toUntisDate()) }.first { it }
            }
            highlightedDate = date
            if (animations) {
                repeat(2) {
                    highlight.animateTo(0.24f, tween(180))
                    highlight.animateTo(0f, tween(260))
                    delay(120)
                }
            } else {
                highlight.snapTo(0.18f)
                delay(1000)
                highlight.snapTo(0f)
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        vm.ensureWeekPage(pagerState.settledPage)
    }

    LaunchedEffect(pagerState.settledPage, compare?.vm) {
        compare?.vm?.ensureWeekPage(pagerState.settledPage)
    }

    LifecycleResumeEffect(pagerState) {
        val visibleMonday = vm.mondayForPage(pagerState.settledPage)
        val previousAnchor = vm.anchorMonday

        vm.reanchorIfNeeded()

        if (vm.anchorMonday != previousAnchor) {
            val target = vm.pageForMonday(visibleMonday)

            scope.launch { pagerState.scrollToPage(target) }
            vm.ensureWeekPage(target)
        }

        val refreshJob = scope.launch {
            while (true) {
                vm.ensureWeekPage(pagerState.settledPage)
                compare?.vm?.ensureWeekPage(pagerState.settledPage)
                delay(60_000)
            }
        }
        onPauseOrDispose { refreshJob.cancel() }
    }

    LaunchedEffect(error) {
        if (error != null) {
            delay(3000)
            vm.clearError()
        }
    }

    LaunchedEffect(info) {
        if (info != null) {
            delay(3000)
            vm.clearInfo()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val visibleMonday = vm.mondayForPage(pagerState.settledPage)

        val slots = remember(vm.timegrid) { hourMarkersFrom(vm.timegrid) }
        val grid = remember(slots) { gridMetricsFrom(slots) }

        if (compare != null) {
            CompareHeader(compare, modifier = Modifier.align(Alignment.TopStart))
        }

        PullToRefreshBox(
            isRefreshing = refreshing || compare?.vm?.refreshing == true,
            onRefresh = {
                vm.refreshVisibleWeek(visibleMonday)
                compare?.vm?.refreshVisibleWeek(visibleMonday)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (compare != null) COMPARE_HEADER_HEIGHT else 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column {
                    Spacer(Modifier.height(HEADER_HEIGHT))

                    PeriodGutter(
                        slots = slots,
                        dayStart = grid.dayStart,
                        gridHeight = grid.height,
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    // in the day view a swipe moves by one day instead of one week
                    userScrollEnabled = activeDay == null,
                    modifier = Modifier
                        .weight(1f)
                        .height(grid.height + HEADER_HEIGHT),
                ) { page ->
                    val weekStart = vm.mondayForPage(page)
                    val weekKey = weekStart.toUntisDate()

                    WeekPage(
                        highlightedDate = highlightedDate,
                        highlightAlpha = { highlight.value },
                        entries = vm.weeks[weekKey],
                        loading = weekKey in vm.loadingWeeks,
                        restrictedDays = vm.restrictedDays,
                        subjectsById = vm.subjectsById,
                        teachersById = vm.teachersById,
                        roomsById = vm.roomsById,
                        subjectAliases = subjectAliases,
                        holidays = vm.holidays,
                        weekStart = weekStart,
                        dayStart = grid.dayStart,
                        dayEnd = grid.dayEnd,
                        gridHeight = grid.height,
                        colorFor = colorFor,
                        textColorFor = textColorFor,
                        cancelledStrike = cancelledStrike,
                        cancelledDesaturation = cancelledDesaturation,
                        cancelledBrightness = cancelledBrightness,
                        pastDim = pastDim,
                        lessonOptions = lessonOptions,
                        hideCancelled = hideCancelled,
                        notebookBadges = notebookBadges,
                        onLessonClick = lessonClick,
                        onEventClick = eventClick,
                        // own exams belong to the own timetable, not to another class
                        exams = if (vm.viewTarget == null) vm.exams else emptyList(),
                        focusedDay = activeDay,
                        onDayHeaderClick = { date ->
                            if (compare == null) focusedDay = if (focusedDay == null) date.dayOfWeek.value else null
                        },
                        onSwipeDay = ::stepDay,
                        compare = compare?.let { source ->
                            val other = source.vm
                            val otherKey = other.mondayForPage(page).toUntisDate()

                            CompareWeek(
                                entries = other.weeks[otherKey],
                                loading = otherKey in other.loadingWeeks,
                                restrictedDays = other.restrictedDays,
                                subjectsById = other.subjectsById,
                                teachersById = other.teachersById,
                                roomsById = other.roomsById,
                                holidays = other.holidays,
                                exams = other.exams,
                                onLessonClick = if (selectingLesson) { _ -> } else source.onLessonClick,
                            )
                        },
                    )
                }
            }
        }

        if (vm.targetRefused && vm.viewTarget != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.timetable_target_refused),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.showTarget(null) }) {
                    Text(stringResource(R.string.timetable_target_back))
                }
            }
        }

        if (showLastRefreshed && vm.weeks.isNotEmpty()) {
            LastRefreshedLabel(
                lastFetch = vm.weekFetchedAt[visibleMonday.toUntisDate()] ?: 0L,
                schoolImport = vm.schoolImportTime,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 52.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun WeekPage(
    highlightedDate: Int,
    highlightAlpha: () -> Float,
    entries: List<TimetableEntry>?,
    loading: Boolean,
    restrictedDays: Set<Int>,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
    subjectAliases: Map<String, SubjectAlias>,
    holidays: List<Holiday>,
    weekStart: LocalDate,
    dayStart: Int,
    dayEnd: Int,
    gridHeight: Dp,
    colorFor: (String) -> Color,
    textColorFor: (String) -> Color,
    cancelledStrike: Boolean,
    cancelledDesaturation: Float,
    cancelledBrightness: Float,
    pastDim: Float,
    lessonOptions: LessonCardOptions,
    hideCancelled: Boolean,
    notebookBadges: Map<LessonKey, NotebookLessonSummary>,
    onLessonClick: (TimetableEntry) -> Unit,
    onEventClick: (TimetableEntry) -> Unit,
    exams: List<ExamInfo>,
    focusedDay: Int? = null,
    onDayHeaderClick: (LocalDate) -> Unit = {},
    onSwipeDay: (Int) -> Unit = {},
    compare: CompareWeek? = null,
) {
    val compareDisplays = compare?.let { other ->
        remember(other.entries, other.exams, other.subjectsById, other.teachersById, other.roomsById, subjectAliases) {
            lessonDisplays(
                withExamBlocks(other.entries.orEmpty(), other.exams, other.subjectsById),
                other.exams,
                other.subjectsById,
                other.teachersById,
                other.roomsById,
                subjectAliases,
            )
        }
    }

    Crossfade(
        targetState = loading && entries == null,
        animationSpec = motionEffects(),
        label = "weekLoading",
    ) { showSpinner ->
        if (showSpinner) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val safeEntries = remember(entries, exams, subjectsById) {
                withExamBlocks(entries.orEmpty(), exams, subjectsById)
            }

            val displays = remember(safeEntries, subjectsById, teachersById, roomsById, exams, subjectAliases) {
                lessonDisplays(safeEntries, exams, subjectsById, teachersById, roomsById, subjectAliases)
            }

            val weekDays = remember(displays, weekStart) {
                if (displays.isEmpty()) {
                    (0 until 5).map { weekStart.plusDays(it.toLong()) }
                } else {
                    computeWeekDays(displays.map { it.entry })
                }
            }

            val shownDays = if (focusedDay != null) {
                listOf(weekStart.plusDays(focusedDay - 1L))
            } else {
                weekDays
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (focusedDay != null) {
                            Modifier.pointerInput(Unit) {
                                var dragged = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { dragged = 0f },
                                    onDragEnd = {
                                        val threshold = 64.dp.toPx()
                                        if (dragged <= -threshold) onSwipeDay(1)
                                        if (dragged >= threshold) onSwipeDay(-1)
                                    },
                                ) { _, amount -> dragged += amount }
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val allDayEvents = remember(displays) {
                    displays
                        .filter { it.entry.isEvent && it.entry.startTime == 0 && it.entry.endTime == 0 }
                        .map { it.entry }
                        .groupBy { it.date }
                }

                DayHeadersRow(
                    weekDays = shownDays,
                    allDayEvents = allDayEvents,
                    onEventClick = onEventClick,
                    fullNames = focusedDay != null,
                    onDayClick = onDayHeaderClick,
                    modifier = Modifier.height(HEADER_HEIGHT),
                )

                WeekGrid(
                    highlightedDate = highlightedDate,
                    highlightAlpha = highlightAlpha,
                    weekDays = shownDays,
                    displays = displays,
                    holidays = holidays,
                    restrictedDays = restrictedDays,
                    dayStart = dayStart,
                    dayEnd = dayEnd,
                    gridHeight = gridHeight,
                    colorFor = colorFor,
                    textColorFor = textColorFor,
                    cancelledStrike = cancelledStrike,
                    cancelledDesaturation = cancelledDesaturation,
                    cancelledBrightness = cancelledBrightness,
                    pastDim = pastDim,
                    lessonOptions = lessonOptions,
                    hideCancelled = hideCancelled,
                    notebookBadges = notebookBadges,
                    onLessonClick = onLessonClick,
                    onEventClick = onEventClick,
                    compare = compareDisplays?.let { other ->
                        CompareColumns(
                            displays = other,
                            holidays = compare.holidays,
                            restrictedDays = compare.restrictedDays,
                            loading = compare.loading && compare.entries == null,
                            onLessonClick = compare.onLessonClick,
                        )
                    },
                )
            }
        }
    }
}

private fun lessonDisplays(
    entries: List<TimetableEntry>,
    exams: List<ExamInfo>,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
    subjectAliases: Map<String, SubjectAlias>,
): List<LessonDisplay> = entries.map { entry ->
    val subject = resolveSubject(entry, subjectsById)

    LessonDisplay(
        entry = entry,
        subject = subject,
        label = subjectShortLabel(subjectAliases, subject),
        rooms = resolveNames(entry.room, roomsById),
        teachers = resolveNames(entry.teacher, teachersById),
        origRooms = resolveOriginals(entry.room, roomsById),
        origTeachers = resolveOriginals(entry.teacher, teachersById),
        exam = ExamsMatcher.best(entry, exams) ?: entry.exam?.let { periodExam ->
            ExamInfo(
                id = periodExam.id,
                examType = periodExam.examtype.orEmpty(),
                name = periodExam.name.orEmpty(),
                text = periodExam.text.orEmpty(),
                date = entry.date,
                startTime = entry.startTime,
                endTime = entry.endTime,
                subjectId = entry.subject.firstOrNull()?.id ?: 0,
            )
        },
    )
}

/** The other account's week while comparing. */
private class CompareWeek(
    val entries: List<TimetableEntry>?,
    val loading: Boolean,
    val restrictedDays: Set<Int>,
    val subjectsById: Map<Int, MasterDataItem>,
    val teachersById: Map<Int, MasterDataItem>,
    val roomsById: Map<Int, MasterDataItem>,
    val holidays: List<Holiday>,
    val exams: List<ExamInfo>,
    val onLessonClick: (TimetableEntry) -> Unit,
)

/** What [WeekGrid] needs to draw the other account's column next to each day. */
private class CompareColumns(
    val displays: List<LessonDisplay>,
    val holidays: List<Holiday>,
    val restrictedDays: Set<Int>,
    val loading: Boolean,
    val onLessonClick: (TimetableEntry) -> Unit,
)

private val COMPARE_HEADER_HEIGHT = 36.dp

@Composable
private fun CompareHeader(compare: CompareSource, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(COMPARE_HEADER_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(GUTTER_WIDTH), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.compare_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .tapHaptic(compare.onExit)
                    .padding(6.dp),
            )
        }

        listOf(
            compare.ownLabel to MaterialTheme.colorScheme.primary,
            compare.otherLabel to MaterialTheme.colorScheme.tertiary,
        ).forEach { (label, color) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.12f))
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            today = now.toLocalDate()
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis())
        }
    }

    return today
}

@Composable
private fun DayHeadersRow(
    weekDays: List<LocalDate>,
    allDayEvents: Map<Int, List<TimetableEntry>>,
    onEventClick: (TimetableEntry) -> Unit,
    modifier: Modifier = Modifier,
    fullNames: Boolean = false,
    onDayClick: (LocalDate) -> Unit = {},
) {
    val today = rememberToday()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT),
    ) {
        weekDays.forEach { date ->
            DayHeader(
                date = date,
                isToday = date == today,
                allDayEvents = allDayEvents[date.toUntisDate()].orEmpty(),
                onEventClick = onEventClick,
                fullName = fullNames,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .tapHaptic { onDayClick(date) },
            )
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    allDayEvents: List<TimetableEntry>,
    onEventClick: (TimetableEntry) -> Unit,
    modifier: Modifier = Modifier,
    fullName: Boolean = false,
) {
    val dateFormat = rememberDateFormat(if (fullName) "dMMMM" else "dMMM")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(
                if (fullName) TextStyle.FULL else TextStyle.SHORT,
                LocalConfiguration.current.locales[0],
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            modifier = if (isToday) Modifier.currentDayHighlight() else Modifier,
        )

        Text(
            text = dateFormat.format(date),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )

        if (allDayEvents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .tapHaptic { onEventClick(allDayEvents.first()) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(9.dp),
                )

                Spacer(Modifier.width(2.dp))

                Text(
                    text = if (allDayEvents.size > 1) {
                        allDayEvents.size.toString()
                    } else {
                        allDayEvents.first().eventTitle ?: stringResource(R.string.event)
                    },
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    highlightedDate: Int,
    highlightAlpha: () -> Float,
    weekDays: List<LocalDate>,
    displays: List<LessonDisplay>,
    holidays: List<Holiday>,
    restrictedDays: Set<Int>,
    dayStart: Int,
    dayEnd: Int,
    gridHeight: Dp,
    colorFor: (String) -> Color,
    textColorFor: (String) -> Color,
    cancelledStrike: Boolean,
    cancelledDesaturation: Float,
    cancelledBrightness: Float,
    pastDim: Float,
    lessonOptions: LessonCardOptions,
    hideCancelled: Boolean,
    notebookBadges: Map<LessonKey, NotebookLessonSummary>,
    onLessonClick: (TimetableEntry) -> Unit,
    onEventClick: (TimetableEntry) -> Unit,
    compare: CompareColumns? = null,
) {
    val today = rememberToday()
    val byDate = remember(displays) { displays.groupBy { it.entry.date } }
    val compareByDate = remember(compare?.displays) { compare?.displays?.groupBy { it.entry.date } }
    val highlightColor = MaterialTheme.colorScheme.primary

    @Composable
    fun Day(
        date: LocalDate,
        byDate: Map<Int, List<LessonDisplay>>,
        holidays: List<Holiday>,
        restrictedDays: Set<Int>,
        onLessonClick: (TimetableEntry) -> Unit,
        modifier: Modifier,
    ) {
            val dateInt = date.toUntisDate()
            val dayLessons = byDate[dateInt].orEmpty()
            val isWeekday = date.dayOfWeek.value <= 5
            val isHoliday = holidays.any { dateInt >= it.startDate && dateInt <= it.endDate }
            val isRestricted = dateInt in restrictedDays

            DayColumn(
                isToday = date == today,
                isPast = date < today,
                lessons = dayLessons.filter {
                    it.entry.subject.isNotEmpty() && !(hideCancelled && it.entry.cancelled)
                },
                events = dayLessons.filter { it.entry.subject.isEmpty() },
                placeholder = when {
                    !isWeekday || dayLessons.isNotEmpty() -> null
                    isHoliday -> DayPlaceholder.HOLIDAY
                    isRestricted -> DayPlaceholder.RESTRICTED
                    else -> null
                },
                dayStart = dayStart,
                dayEnd = dayEnd,
                gridHeight = gridHeight,
                colorFor = colorFor,
                textColorFor = textColorFor,
                cancelledStrike = cancelledStrike,
                cancelledDesaturation = cancelledDesaturation,
                cancelledBrightness = cancelledBrightness,
                pastDim = pastDim,
                lessonOptions = lessonOptions,
                hideCancelled = hideCancelled,
                notebookBadges = notebookBadges,
                onLessonClick = onLessonClick,
                onEventClick = onEventClick,
                modifier = modifier.drawWithContent {
                    drawContent()
                    if (dateInt == highlightedDate) drawRect(highlightColor.copy(alpha = highlightAlpha()))
                },
            )
    }

    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        weekDays.forEach { date ->
            Day(date, byDate, holidays, restrictedDays, onLessonClick, Modifier.weight(1f))

            if (compare != null && compareByDate != null) {
                Box(modifier = Modifier.weight(1f)) {
                    Day(date, compareByDate, compare.holidays, compare.restrictedDays, compare.onLessonClick, Modifier.fillMaxWidth())

                    if (compare.loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodGutter(
    slots: List<HourMarker>,
    dayStart: Int,
    gridHeight: Dp,
) {
    val timeFormat = rememberTimeFormat()

    Box(
        modifier = Modifier
            .width(GUTTER_WIDTH)
            .height(gridHeight),
    ) {
        slots.forEach { slot ->
            val topMinutes = minutesOf(slot.start) - minutesOf(dayStart)
            val slotHeight = MINUTE_HEIGHT * (minutesOf(slot.end) - minutesOf(slot.start))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = MINUTE_HEIGHT * topMinutes)
                    .height(slotHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = slot.start.toUntisLocalTime().format(timeFormat),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )

                Text(
                    text = "${slot.label}.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = slot.end.toUntisLocalTime().format(timeFormat),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    isToday: Boolean,
    isPast: Boolean,
    lessons: List<LessonDisplay>,
    events: List<LessonDisplay>,
    placeholder: DayPlaceholder?,
    dayStart: Int,
    dayEnd: Int,
    gridHeight: Dp,
    colorFor: (String) -> Color,
    textColorFor: (String) -> Color,
    cancelledStrike: Boolean,
    cancelledDesaturation: Float,
    cancelledBrightness: Float,
    pastDim: Float,
    lessonOptions: LessonCardOptions,
    hideCancelled: Boolean,
    notebookBadges: Map<LessonKey, NotebookLessonSummary>,
    onLessonClick: (TimetableEntry) -> Unit,
    onEventClick: (TimetableEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayStartMinutes = minutesOf(dayStart)
    val gridEndMinutes = minutesOf(dayEnd)
    val animations = LocalAnimationsEnabled.current

    var nowMinutes by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }

    if (isToday) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
            }
        }
    }

    Box(
        modifier = modifier
            .height(gridHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )

        val eventSpans = mergeEventSpans(events.map { it.entry })

        eventSpans.forEach { span ->
            if (span.allDay) return@forEach

            val startMin = (minutesOf(span.start) - dayStartMinutes).coerceAtLeast(0)
            val endMin = (minutesOf(span.end) - dayStartMinutes).coerceAtMost(gridEndMinutes - dayStartMinutes)

            val overlayHeight = MINUTE_HEIGHT * (endMin - startMin).coerceAtLeast(5)

            TimetableEventOverlay(
                span = span,
                height = overlayHeight,
                onClick = { onEventClick(span.entry) },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = MINUTE_HEIGHT * startMin)
                    .padding(horizontal = 1.dp),
            )
        }

        placeholder?.let { type ->
            val label = type.label()

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (type == DayPlaceholder.HOLIDAY) {
                    Icon(
                        painter = painterResource(R.drawable.ic_holiday),
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )

                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )

                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        val gaps = remember(lessons, lessonOptions.showFreePeriods) {
            if (lessonOptions.showFreePeriods) freeGaps(lessons.map { it.entry }) else emptyList()
        }
        val freeColor = MaterialTheme.colorScheme.tertiary

        gaps.forEach { gap ->
            val top = (minutesOf(gap.start) - dayStartMinutes).coerceAtLeast(0)
            val bottom = (minutesOf(gap.end) - dayStartMinutes).coerceAtMost(gridEndMinutes - dayStartMinutes)

            if (bottom > top) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = MINUTE_HEIGHT * top)
                        .height(MINUTE_HEIGHT * (bottom - top))
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(freeColor.copy(alpha = 0.08f)),
                )
            }
        }

        var openStack by remember { mutableStateOf<LessonStack?>(null) }
        val stacks = remember(lessons) { stackParallelLessons(lessons) }

        openStack?.let { stack ->
            ParallelLessonsDialog(
                lessons = stack.all,
                colorFor = colorFor,
                textColorFor = textColorFor,
                onLessonClick = { entry ->
                    openStack = null
                    onLessonClick(entry)
                },
                onDismiss = { openStack = null },
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            stacks.forEach { stack ->
                val lesson = stack.shown
                val entry = lesson.entry
                val topMinutes = (minutesOf(entry.startTime) - dayStartMinutes)
                    .coerceIn(0, gridEndMinutes - dayStartMinutes)
                val bottomMinutes = (minutesOf(entry.endTime) - dayStartMinutes)
                    .coerceIn(topMinutes, gridEndMinutes - dayStartMinutes)

                val top = MINUTE_HEIGHT * topMinutes
                val lessonHeight = MINUTE_HEIGHT * (bottomMinutes - topMinutes) - 2.dp

                if (lessonHeight > 0.dp) {
                    val startFraction = (entry.layoutStartPosition / 1000f).coerceIn(0f, 1f)
                    val widthFraction = (entry.layoutWidth / 1000f).coerceIn(0.2f, 1f)
                    val left = maxWidth * startFraction
                    val cardWidth = (maxWidth * widthFraction).coerceAtMost(maxWidth - left)

                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .offset(x = left, y = top)
                            .padding(horizontal = 1.dp)
                            .height(lessonHeight),
                    ) {
                        LessonCard(
                            lesson = lesson,
                            color = colorFor(lesson.subject),
                            textColor = textColorFor(lesson.subject),
                            strike = cancelledStrike,
                            cancelledDesaturation = cancelledDesaturation,
                            cancelledBrightness = cancelledBrightness,
                            options = lessonOptions,
                            notebookBadge = notebookBadges[
                                LessonKey(entry.id, entry.date, entry.startTime)
                            ],
                            modifier = Modifier
                                .fillMaxSize()
                                .tapHaptic {
                                    if (stack.hidden.isEmpty()) onLessonClick(entry) else openStack = stack
                                },
                        )

                        if (stack.hidden.isNotEmpty()) {
                            val more = stringResource(R.string.timetable_parallel_more, stack.hidden.size)

                            Text(
                                text = "+${stack.hidden.size}",
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .semantics { contentDescription = more },
                            )
                        }
                    }
                }
            }
        }

        gaps.forEach { gap ->
            val top = (minutesOf(gap.start) - dayStartMinutes).coerceAtLeast(0)
            val label = stringResource(R.string.timetable_free, freeDurationText(gap.minutes))

            Text(
                text = freeDurationText(gap.minutes),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = MINUTE_HEIGHT * top + 1.dp)
                    .clip(RoundedCornerShape(50))
                    .background(freeColor)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .semantics { contentDescription = label },
            )
        }

        if (isPast || isToday) {
            val dimEnd = when {
                isPast -> gridEndMinutes
                nowMinutes <= dayStartMinutes -> dayStartMinutes
                else -> minOf(nowMinutes, gridEndMinutes)
            }

            val overlayHeight by animateDpAsState(
                targetValue = (MINUTE_HEIGHT * (dimEnd - dayStartMinutes)).coerceAtLeast(0.dp),
                animationSpec = if (animations) AppMotion.spatial() else snap(),
                label = "dimOverlay",
            )

            if (overlayHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(overlayHeight)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = pastDim)),
                )
            }

            val showLine = isToday && nowMinutes >= dayStartMinutes && nowMinutes < gridEndMinutes

            if (showLine) {
                val lineOffset by animateDpAsState(
                    targetValue = MINUTE_HEIGHT * (dimEnd - dayStartMinutes),
                    animationSpec = if (animations) AppMotion.spatial() else snap(),
                    label = "nowLine",
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = lineOffset)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun LastRefreshedLabel(
    lastFetch: Long,
    modifier: Modifier = Modifier,
    schoolImport: Long = 0L,
) {
    if (lastFetch <= 0L) return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastFetch) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    Text(
        text = stringResource(R.string.timetable_last_refreshed, relativeTimeText(lastFetch, now)) +
            if (schoolImport > 0L) " · " + stringResource(R.string.timetable_school_import, importTimeText(schoolImport)) else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** "07:12" for today, "Mo., 07:12" for an earlier day. */
@Composable
private fun importTimeText(millis: Long): String {
    val time = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val clock = time.toLocalTime().format(rememberTimeFormat())
    return if (time.toLocalDate() == LocalDate.now()) clock else rememberDateFormat("EEE").format(time.toLocalDate()) + ", " + clock
}

@Composable
private fun relativeTimeText(timestamp: Long, now: Long): String {
    val elapsed = (now - timestamp).coerceAtLeast(0L)

    return when {
        elapsed < 45_000L -> stringResource(R.string.time_now)

        elapsed < 60 * 60_000L -> {
            val minutes = (elapsed / 60_000L).toInt()
            pluralStringResource(R.plurals.time_minutes_ago, minutes, minutes)
        }

        elapsed < 24 * 60 * 60_000L -> {
            val hours = (elapsed / 3_600_000L).toInt()
            pluralStringResource(R.plurals.time_hours_ago, hours, hours)
        }

        elapsed < 7 * 24 * 60 * 60_000L -> {
            val days = (elapsed / 86_400_000L).toInt()
            pluralStringResource(R.plurals.time_days_ago, days, days)
        }

        else -> rememberDateFormat("dMMMjm")
            .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    }
}

/**
 * Lists lessons that run at the same time; tapping one opens its details. The title shows the
 * time of the tapped card; a row shows its own time only when it differs from that.
 */
@Composable
private fun ParallelLessonsDialog(
    lessons: List<LessonDisplay>,
    colorFor: (String) -> Color,
    textColorFor: (String) -> Color,
    onLessonClick: (TimetableEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeFormat = rememberTimeFormat()
    val reference = lessons.first().entry
    fun range(entry: TimetableEntry) =
        entry.startTime.toUntisLocalTime().format(timeFormat) + "–" + entry.endTime.toUntisLocalTime().format(timeFormat)
    val sorted = remember(lessons) {
        lessons.sortedWith(compareBy({ it.entry.startTime }, { it.entry.cancelled }, { it.label.lowercase() }))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(range(reference)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sorted.forEach { lesson ->
                    val entry = lesson.entry
                    val sameTime = entry.startTime == reference.startTime && entry.endTime == reference.endTime
                    val details = listOf(lesson.teachers, lesson.rooms, if (sameTime) "" else range(entry))
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .tapHaptic { onLessonClick(entry) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lesson.label,
                            fontWeight = FontWeight.Bold,
                            color = textColorFor(lesson.subject),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null,
                            modifier = Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorFor(lesson.subject))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (entry.cancelled) {
                                Text(
                                    text = stringResource(R.string.lesson_cancelled),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
