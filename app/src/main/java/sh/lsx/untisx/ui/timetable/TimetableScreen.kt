package sh.lsx.untisx.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.plusUntisWeeks
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    vm: TimetableViewModel = viewModel(),
    subjectColors: Map<String, Color> = emptyMap(),
    defaultColor: Color = Color(0xFF81D4FA),
    textColor: Color = Color(0xFF212121),
    cancelledStrike: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val error = vm.error
    val info = vm.info
    val refreshing = vm.refreshing

    val colorFor = remember(subjectColors, defaultColor) {
        { name: String -> subjectColors[name] ?: defaultColor }
    }

    val pagerState = rememberPagerState(
        initialPage = TimetableViewModel.CENTER,
        pageCount = { TimetableViewModel.WEEK_SPAN },
    )

    LaunchedEffect(pagerState.settledPage) {
        vm.ensureWeekPage(pagerState.settledPage)
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

    Column(modifier = modifier.fillMaxSize()) {
        error?.let { message ->
            ErrorBanner(
                text = message,
                onRetry = vm::retry,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        info?.let { message ->
            InfoBanner(
                text = message,
                onRetry = vm::retry,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        vm.timegridError?.let { message ->
            InfoBanner(
                text = message,
                onRetry = vm::retryTimegrid,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val offset = vm.pageToOffset(pagerState.settledPage)

        val slots = remember(vm.timegrid) { hourMarkersFrom(vm.timegrid) }
        val grid = remember(slots) { gridMetricsFrom(slots) }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshVisibleWeek(offset) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
                    val pageOffset = vm.pageToOffset(page)

                    WeekPage(
                        entries = vm.weeks[pageOffset],
                        loading = pageOffset in vm.loadingWeeks,
                        restrictedDays = vm.restrictedDays,
                        subjectsById = vm.subjectsById,
                        teachersById = vm.teachersById,
                        roomsById = vm.roomsById,
                        holidays = vm.holidays,
                        weekStart = LocalDate.now().plusUntisWeeks(pageOffset.toLong()),
                        dayStart = grid.dayStart,
                        dayEnd = grid.dayEnd,
                        gridHeight = grid.height,
                        colorFor = colorFor,
                        textColor = textColor,
                        cancelledStrike = cancelledStrike,
                    )
                }
            }
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
    textColor: Color,
    cancelledStrike: Boolean,
) {
    when {
        loading && entries == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        entries == null -> Box(modifier = Modifier.fillMaxSize())

        else -> {
            val displays = remember(entries, subjectsById, teachersById, roomsById) {
                entries.map { entry ->
                    LessonDisplay(
                        entry = entry,
                        subject = resolveSubject(entry, subjectsById),
                        rooms = resolveNames(entry.room, roomsById),
                        teachers = resolveNames(entry.teacher, teachersById),
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
                DayHeadersRow(
                    weekDays = weekDays,
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
                    textColor = textColor,
                    cancelledStrike = cancelledStrike,
                )
            }
        }
    }
}

@Composable
private fun DayHeadersRow(
    weekDays: List<LocalDate>,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT),
    ) {
        weekDays.forEach { date ->
            DayHeader(
                date = date,
                isToday = date == today,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )

        Text(
            text = DAY_DATE_FORMAT.format(date),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
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
    textColor: Color,
    cancelledStrike: Boolean,
) {
    val today = remember { LocalDate.now() }
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
                lessons = dayLessons.filter { it.entry.subject.isNotEmpty() },
                events = dayLessons.filter { it.entry.subject.isEmpty() },
                placeholder = when {
                    !isWeekday || dayLessons.isNotEmpty() -> null
                    isHoliday -> "Holiday"
                    isRestricted -> "Restricted"
                    else -> null
                },
                dayStart = dayStart,
                dayEnd = dayEnd,
                gridHeight = gridHeight,
                colorFor = colorFor,
                textColor = textColor,
                cancelledStrike = cancelledStrike,
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
                    text = slot.start.toUntisLocalTime().format(TIME_FORMAT),
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
                    text = slot.end.toUntisLocalTime().format(TIME_FORMAT),
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
    placeholder: String?,
    dayStart: Int,
    dayEnd: Int,
    gridHeight: Dp,
    colorFor: (String) -> Color,
    textColor: Color,
    cancelledStrike: Boolean,
    modifier: Modifier = Modifier,
) {
    val dayStartMinutes = minutesOf(dayStart)
    val gridEndMinutes = minutesOf(dayEnd)

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

        mergeEventSpans(events.map { it.entry }).forEach { (start, end) ->
            val startMin = (minutesOf(start) - dayStartMinutes).coerceAtLeast(0)
            val endMin = (minutesOf(end) - dayStartMinutes).coerceAtMost(gridEndMinutes - dayStartMinutes)

            val overlayHeight = MINUTE_HEIGHT * (endMin - startMin).coerceAtLeast(5)

            val overlayColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = MINUTE_HEIGHT * startMin)
                    .padding(horizontal = 1.dp)
                    .height(overlayHeight)
                    .background(overlayColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                    .border(1.dp, overlayColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
            )
        }

        placeholder?.let { label ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (label == "Holiday") {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
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

        lessons.forEach { lesson ->
            val entry = lesson.entry
            val topMinutes = (minutesOf(entry.startTime) - dayStartMinutes)
                .coerceIn(0, gridEndMinutes - dayStartMinutes)
            val bottomMinutes = (minutesOf(entry.endTime) - dayStartMinutes)
                .coerceIn(topMinutes, gridEndMinutes - dayStartMinutes)

            val top = MINUTE_HEIGHT * topMinutes
            val lessonHeight = MINUTE_HEIGHT * (bottomMinutes - topMinutes) - 2.dp

            if (lessonHeight > 0.dp) {
                LessonCard(
                    lesson = lesson,
                    color = colorFor(lesson.subject),
                    textColor = textColor,
                    strike = cancelledStrike,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = top)
                        .padding(horizontal = 1.dp)
                        .height(lessonHeight),
                )
            }
        }

        if (isPast || isToday) {
            val dimEnd = when {
                isPast -> gridEndMinutes
                nowMinutes <= dayStartMinutes -> dayStartMinutes
                else -> minOf(nowMinutes, gridEndMinutes)
            }

            val overlayHeight = MINUTE_HEIGHT * (dimEnd - dayStartMinutes)

            if (overlayHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(overlayHeight)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                )
            }

            val showLine = isToday && nowMinutes >= dayStartMinutes && nowMinutes < gridEndMinutes

            if (showLine) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = MINUTE_HEIGHT * (dimEnd - dayStartMinutes))
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
