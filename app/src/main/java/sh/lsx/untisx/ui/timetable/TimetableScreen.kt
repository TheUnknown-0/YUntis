package sh.lsx.untisx.ui.timetable

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import sh.lsx.untisx.data.ExamsMatcher
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.tapHaptic
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
    cancelledStrike: Boolean = true,
    cancelledDesaturation: Float = 1f,
    cancelledBrightness: Float = 1f,
    pastDim: Float = 0.12f,
    lessonOptions: LessonCardOptions = LessonCardOptions(),
    hideCancelled: Boolean = false,
    showLastRefreshed: Boolean = true,
    onLessonClick: (TimetableEntry) -> Unit = {},
    onEventClick: (TimetableEntry) -> Unit = {},
    focusDate: Int = 0,
    focusNonce: Int = 0,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val error = vm.error
    val info = vm.info
    val refreshing = vm.refreshing
    val animations = LocalAnimationsEnabled.current

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

    LaunchedEffect(focusNonce) {
        if (focusDate > 0) {
            val target = focusPageFor(vm, focusDate)

            if (animations && abs(target - pagerState.currentPage) <= 2) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }

            onFocusConsumed()
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        vm.ensureWeekPage(pagerState.settledPage)
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

        onPauseOrDispose { }
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

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshVisibleWeek(visibleMonday) },
            modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier
                        .weight(1f)
                        .height(grid.height + HEADER_HEIGHT),
                ) { page ->
                    val weekStart = vm.mondayForPage(page)
                    val weekKey = weekStart.toUntisDate()

                    WeekPage(
                        entries = vm.weeks[weekKey],
                        loading = weekKey in vm.loadingWeeks,
                        restrictedDays = vm.restrictedDays,
                        subjectsById = vm.subjectsById,
                        teachersById = vm.teachersById,
                        roomsById = vm.roomsById,
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
                        onLessonClick = onLessonClick,
                        onEventClick = onEventClick,
                        exams = vm.exams,
                    )
                }
            }
        }

        if (showLastRefreshed && vm.weeks.isNotEmpty()) {
            LastRefreshedLabel(
                lastFetch = vm.lastFetch,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 52.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun WeekPage(
    entries: List<TimetableEntry>?,
    loading: Boolean,
    restrictedDays: Set<Int>,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
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
    onLessonClick: (TimetableEntry) -> Unit,
    onEventClick: (TimetableEntry) -> Unit,
    exams: List<ExamInfo>,
) {
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
            val safeEntries = entries.orEmpty()

            val displays = remember(safeEntries, subjectsById, teachersById, roomsById, exams) {
                safeEntries.map { entry ->
                    LessonDisplay(
                        entry = entry,
                        subject = resolveSubject(entry, subjectsById),
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
            }

            val weekDays = remember(displays, weekStart) {
                if (displays.isEmpty()) {
                    (0 until 5).map { weekStart.plusDays(it.toLong()) }
                } else {
                    computeWeekDays(displays.map { it.entry })
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                val allDayEvents = remember(displays) {
                    displays
                        .filter { it.entry.isEvent && it.entry.startTime == 0 && it.entry.endTime == 0 }
                        .map { it.entry }
                        .groupBy { it.date }
                }

                DayHeadersRow(
                    weekDays = weekDays,
                    allDayEvents = allDayEvents,
                    onEventClick = onEventClick,
                    modifier = Modifier.height(HEADER_HEIGHT),
                )

                WeekGrid(
                    weekDays = weekDays,
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
                    onLessonClick = onLessonClick,
                    onEventClick = onEventClick,
                )
            }
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
) {
    val dateFormat = rememberDateFormat("dMMM")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(
                TextStyle.SHORT,
                LocalConfiguration.current.locales[0],
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
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
    onLessonClick: (TimetableEntry) -> Unit,
    onEventClick: (TimetableEntry) -> Unit,
) {
    val today = rememberToday()
    val byDate = remember(displays) { displays.groupBy { it.entry.date } }

    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        weekDays.forEach { date ->
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
                onLessonClick = onLessonClick,
                onEventClick = onEventClick,
                modifier = Modifier.weight(1f),
            )
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

            val overlayColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = MINUTE_HEIGHT * startMin)
                    .padding(horizontal = 1.dp)
                    .height(overlayHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(overlayColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                    .border(1.dp, overlayColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .tapHaptic { onEventClick(span.entry) },
                contentAlignment = Alignment.TopStart,
            ) {
                if (overlayHeight >= 18.dp) {
                    Text(
                        text = span.title.orEmpty(),
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
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

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            lessons.forEach { lesson ->
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

                    LessonCard(
                        lesson = lesson,
                        color = colorFor(lesson.subject),
                        textColor = textColorFor(lesson.subject),
                        strike = cancelledStrike,
                        cancelledDesaturation = cancelledDesaturation,
                        cancelledBrightness = cancelledBrightness,
                        options = lessonOptions,
                        modifier = Modifier
                            .width(cardWidth)
                            .offset(x = left, y = top)
                            .padding(horizontal = 1.dp)
                            .height(lessonHeight)
                            .tapHaptic { onLessonClick(entry) },
                    )
                }
            }
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
        text = stringResource(R.string.timetable_last_refreshed, relativeTimeText(lastFetch, now)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
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
