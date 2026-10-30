package sh.lsx.untisx.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.AnimatedPage
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import kotlinx.coroutines.flow.drop
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.subjectShortLabel
import sh.lsx.untisx.data.wear.WearTimetablePayload
import sh.lsx.untisx.wear.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun WearRoot(vm: WearViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, vm) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    vm.onForegroundTick()
                    delay(30_000L)
                }
            } finally {
                vm.onBackground()
            }
        }
    }

    AppScaffold(timeText = { TimeText() }) {
        when (val current = state) {
            WearUiState.Loading -> LoadingScreen()
            WearUiState.NoData -> NoDataScreen(onRefresh = vm::refresh, refreshing = refreshing, syncState = syncState)

            is WearUiState.Content -> TimetablePager(
                payload = current.payload,
                refreshing = refreshing,
                onRefresh = vm::refresh,
                syncState = syncState,
                today = today,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoDataScreen(onRefresh: () -> Unit, refreshing: Boolean, syncState: WearSyncState) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_waiting),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.wear_open_phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (syncState == WearSyncState.DELAYED || syncState == WearSyncState.FAILED) {
                item { SyncStatus(syncState) }
            }

            item {
                Button(onClick = onRefresh) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(stringResource(R.string.wear_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetablePager(payload: WearTimetablePayload, refreshing: Boolean, onRefresh: () -> Unit, syncState: WearSyncState, today: LocalDate) {
    val days = remember(payload, today) { buildDays(payload, today) }
    var selected by remember(payload.accountId) { mutableStateOf<WearLessonKey?>(null) }
    val selectedLesson = payload.findLesson(selected)

    LaunchedEffect(payload, selected) {
        if (selectedLesson == null) selected = null
    }

    if (days.isEmpty()) {
        NoDataScreen(onRefresh = onRefresh, refreshing = refreshing, syncState = syncState)
        return
    }

    val pagerState = rememberPagerState(initialPage = remember(days) { initialPage(days) }) { days.size }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(today) {
        pagerState.scrollToPage(days.indexOfFirst { it.date == today }.coerceAtLeast(0))
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      HorizontalPagerScaffold(pagerState = pagerState, pageIndicator = {}) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            rotaryScrollableBehavior = null,
        ) { page ->
          AnimatedPage(pageIndex = page, pagerState = pagerState) {
            DayPage(
                day = days[page],
                today = today,
                payload = payload,
                refreshing = refreshing,
                onRefresh = onRefresh,
                syncState = syncState,
                onLesson = { selected = it.wearKey() },
            )
          }
        }
      }

        val current = selectedLesson

        if (current != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                LessonDetail(entry = current, payload = payload, onClose = { selected = null })
            }
        }
    }
}

@Composable
private fun DayPage(
    day: WearDay,
    today: LocalDate,
    payload: WearTimetablePayload,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLesson: (TimetableEntry) -> Unit,
    syncState: WearSyncState,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onRefresh,
                modifier = Modifier.scrollable(
                    listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    overscrollEffect = rememberOverscrollEffect(),
                ),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Text(
                        text = stringResource(R.string.wear_refresh),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                DayHeader(day, payload, today)
            }

            if (syncState == WearSyncState.DELAYED || syncState == WearSyncState.FAILED) {
                item { SyncStatus(syncState) }
            }

            val holiday = day.holiday

            if (holiday != null) {
                item {
                    HolidayChip(holiday.longName.ifBlank { holiday.name })
                }
            } else if (day.restricted) {
                item { StatusChip(stringResource(R.string.wear_restricted)) }
            } else if (!day.loaded) {
                item { StatusChip(stringResource(R.string.wear_not_loaded)) }
            } else if (day.entries.isEmpty()) {
                item { StatusChip(stringResource(R.string.wear_no_lessons)) }
            }

            items(day.entries) { entry ->
                LessonCard(
                    entry = entry,
                    payload = payload,
                    onClick = { onLesson(entry) },
                    modifier = Modifier
                        .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: WearDay, payload: WearTimetablePayload, today: LocalDate) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = dayTitle(day.date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textDecoration = if (day.date == today) TextDecoration.Underline else null,
        )

        val fetchedAt = payload.weekFetchedAt[day.date.toUntisWeekStart().toUntisDate().toString()] ?: 0L
        if (fetchedAt > 0L) {
            Text(
                text = stringResource(R.string.wear_updated, updateLabel(fetchedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncStatus(state: WearSyncState) {
    val label = when (state) {
        WearSyncState.IDLE, WearSyncState.CACHE, WearSyncState.WAITING, WearSyncState.UPDATED -> return
        WearSyncState.DELAYED -> R.string.wear_sync_delayed
        WearSyncState.FAILED -> R.string.wear_sync_failed
    }

    Text(stringResource(label), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun LessonCard(
    entry: TimetableEntry,
    payload: WearTimetablePayload,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    val subjectName = entry.subject.firstOrNull()?.name.orEmpty()
    val label = if (entry.isEvent) entry.eventTitle.orEmpty() else subjectShortLabel(payload.subjectAliases, subjectName)
    val color = if (entry.isEvent) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color(payload.subjectColors[subjectName] ?: payload.defaultColor)
    }
    val room = entry.room.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }.joinToString(", ")
    val teacher = entry.teacher.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }.joinToString(", ")
    val info = listOf(room, teacher).filter { it.isNotBlank() }.joinToString(" · ")

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        border = if (entry.exam != null) BorderStroke(1.dp, EXAM_COLOR) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeRange(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                if (entry.cancelled || entry.irregular) {
                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = stringResource(
                            if (entry.cancelled) R.string.wear_cancelled else R.string.wear_irregular,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.cancelled) MaterialTheme.colorScheme.error else EXAM_COLOR,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.cancelled) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null,
                    maxLines = 1,
                )

                if (entry.exam != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wear_exam),
                        style = MaterialTheme.typography.labelSmall,
                        color = EXAM_COLOR,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            if (info.isNotBlank()) {
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun initialPage(days: List<WearDay>): Int {
    val today = AppClock.today()
    val exact = days.indexOfFirst { it.date == today }

    if (exact >= 0) return exact

    val next = days.indexOfFirst { it.date.isAfter(today) }

    return if (next >= 0) next else days.lastIndex
}
