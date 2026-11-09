package sh.lsx.untisx.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectStats
import sh.lsx.untisx.data.DayStats
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.prefs.StatsPrefs
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.AppDateFormats
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.rememberDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class EntryCount(val label: String, val count: Int)

@Composable
fun StatsScreen(
    yearStats: SchoolYearStats?,
    schoolYear: SchoolYear?,
    subjectsById: Map<Int, MasterDataItem>,
    loading: Boolean,
    progress: Pair<Int, Int>?,
    upToDate: Boolean,
    onLoadHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val startDate = schoolYear?.startDate ?: yearStats?.startDate
    val endDate = schoolYear?.endDate ?: yearStats?.endDate
    val start = startDate?.let { runCatching { it.toUntisLocalDate() }.getOrNull() }
    val end = endDate?.let { runCatching { it.toUntisLocalDate() }.getOrNull() }
    val lastDay = end?.let { minOf(today, it) }

    // weeks the timetable was never opened for are missing; fetch only those, once per visit
    LaunchedEffect(Unit) { onLoadHistory() }

    var selectedMonth by remember { mutableStateOf<YearMonth?>(null) }

    val context = LocalContext.current
    var includeEvents by remember { mutableStateOf(StatsPrefs.includeEvents(context)) }

    val months = remember(start, lastDay) {
        if (start == null || lastDay == null) {
            emptyList()
        } else {
            generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(YearMonth.from(lastDay)) }
                .toList()
        }
    }

    val filteredDays = remember(yearStats, selectedMonth) {
        yearStats?.days?.entries
            ?.filter { (key, _) ->
                if (selectedMonth == null) {
                    true
                } else {
                    val date = key.toIntOrNull()?.let { runCatching { it.toUntisLocalDate() }.getOrNull() }
                    date != null && YearMonth.from(date) == selectedMonth
                }
            }
            ?.map { it.value }
            .orEmpty()
    }

    val eventLessons = filteredDays.sumOf { it.eventLessons }
    val eventCancelled = filteredDays.sumOf { it.eventCancelled }
    val eventChanges = filteredDays.sumOf { it.eventChanges }

    val lessons = filteredDays.sumOf { it.lessons } - if (includeEvents) 0 else eventLessons
    val cancelled = filteredDays.sumOf { it.cancelled } - if (includeEvents) 0 else eventCancelled
    val changes = filteredDays.sumOf { it.changes } - if (includeEvents) 0 else eventChanges
    val taught = (lessons - cancelled).coerceAtLeast(0)

    val excludedLessons = if (includeEvents) 0 else eventLessons
    val excludedDays = if (includeEvents) 0 else filteredDays.count { it.eventLessons > 0 }

    val rangeStart = selectedMonth?.atDay(1) ?: start
    val rangeEnd = selectedMonth?.let { minOf(it.atEndOfMonth(), today) } ?: lastDay

    val totalWeekdays = remember(rangeStart, rangeEnd) {
        if (rangeStart == null || rangeEnd == null) 0 else countWeekdays(rangeStart, rangeEnd)
    }

    val collectedWeekdays = remember(yearStats, rangeStart, rangeEnd) {
        if (rangeStart == null || rangeEnd == null) {
            0
        } else {
            yearStats?.days?.keys?.count { key ->
                val date = key.toIntOrNull()?.let { runCatching { it.toUntisLocalDate() }.getOrNull() }

                date != null &&
                    date.dayOfWeek.value <= 5 &&
                    date >= rangeStart &&
                    date <= rangeEnd
            } ?: 0
        }
    }

    val subjects = remember(yearStats, selectedMonth, subjectsById, includeEvents) {
        val counts = HashMap(mergeCounts(filteredDays) { it.subjectCounts })

        if (!includeEvents) {
            mergeCounts(filteredDays) { it.eventSubjectCounts }.forEach { (key, value) ->
                counts[key] = (counts[key] ?: 0) - value
            }
        }

        counts
            .filterValues { it > 0 }
            .map { EntryCount(subjectName(it.key, subjectsById), it.value) }
            .sortedByDescending { it.count }
    }

    val locale = LocalConfiguration.current.locales[0]
    val subjectTotals = remember(filteredDays, includeEvents) { SubjectStats.totals(filteredDays, includeEvents) }
    val olderDaysMissing = remember(filteredDays) { SubjectStats.hasOldDays(filteredDays) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeaderCard(
                yearName = yearStats?.name?.takeIf { it.isNotBlank() }
                    ?: schoolYear?.name?.takeIf { it.isNotBlank() }
                    ?: "-",
                start = start,
                end = end,
                countedDays = collectedWeekdays,
                totalWeekdays = totalWeekdays,
                lastUpdated = yearStats?.lastUpdated ?: 0L,
                eventLessons = excludedLessons,
                eventDays = excludedDays,
            )
        }

        if (months.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedMonth == null,
                        onClick = { selectedMonth = null },
                        label = { Text(stringResource(R.string.stats_period_year)) },
                    )

                    months.forEach { month ->
                        FilterChip(
                            selected = selectedMonth == month,
                            onClick = { selectedMonth = month },
                            label = { Text(monthLabel(month, locale)) },
                        )
                    }
                }
            }
        }

        item {
            EventLessonsCard(
                included = includeEvents,
                onIncludedChange = {
                    includeEvents = it
                    StatsPrefs.setIncludeEvents(context, it)
                },
            )
        }

        item {
            LoadCard(
                loading = loading,
                progress = progress,
                upToDate = upToDate,
                onLoadHistory = onLoadHistory,
            )
        }

        if (filteredDays.isEmpty()) {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = appCardShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = stringResource(R.string.stats_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = stringResource(R.string.stats_total),
                        target = lessons.toFloat(),
                        modifier = Modifier.weight(1f),
                    )

                    StatTile(
                        label = stringResource(R.string.stats_taught),
                        target = taught.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = stringResource(R.string.stats_cancelled),
                        target = cancelled.toFloat(),
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )

                    StatTile(
                        label = stringResource(R.string.stats_changes),
                        target = changes.toFloat(),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = stringResource(R.string.stats_cancel_rate),
                        target = percentValue(lessons, cancelled),
                        accent = MaterialTheme.colorScheme.error,
                        valueFormat = { formatPercentValue(it, locale) },
                        modifier = Modifier.weight(1f),
                    )

                    StatTile(
                        label = stringResource(R.string.stats_cancel_change_rate),
                        target = percentValue(lessons, cancelled + changes),
                        accent = MaterialTheme.colorScheme.tertiary,
                        valueFormat = { formatPercentValue(it, locale) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (subjectTotals.isNotEmpty()) {
                item {
                    SubjectStatsCard(
                        totals = subjectTotals,
                        nameFor = { subjectName(it, subjectsById) },
                        locale = locale,
                        olderDaysMissing = olderDaysMissing,
                    )
                }
            } else if (subjects.isNotEmpty()) {
                item {
                    BreakdownCard(
                        title = stringResource(R.string.stats_by_subject),
                        entries = subjects,
                        total = taught,
                        locale = locale,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    yearName: String,
    start: LocalDate?,
    end: LocalDate?,
    countedDays: Int,
    totalWeekdays: Int,
    lastUpdated: Long,
    eventLessons: Int,
    eventDays: Int,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = yearName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (start != null && end != null) {
                val dateFormat = rememberDateFormat("dMMMyyyy")

                Text(
                    text = "${dateFormat.format(start)} – ${dateFormat.format(end)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.stats_days_collected, countedDays, totalWeekdays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (eventLessons > 0) {
                Text(
                    text = stringResource(R.string.stats_events_excluded, eventLessons, eventDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (lastUpdated > 0L) {
                val context = LocalContext.current
                val locale = LocalConfiguration.current.locales[0]
                val timestamp = remember(lastUpdated, locale) {
                    AppDateFormats.formatter(context, "dMMMjm")
                        .format(java.time.Instant.ofEpochMilli(lastUpdated).atZone(java.time.ZoneId.systemDefault()))
                }

                Text(
                    text = stringResource(R.string.stats_last_updated, timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun EventLessonsCard(
    included: Boolean,
    onIncludedChange: (Boolean) -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_include_events),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(
                        if (included) R.string.stats_include_events_on else R.string.stats_include_events_off,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = included,
                onCheckedChange = onIncludedChange,
            )
        }
    }
}

@Composable
private fun LoadCard(
    loading: Boolean,
    progress: Pair<Int, Int>?,
    upToDate: Boolean,
    onLoadHistory: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onLoadHistory,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = progress?.let { (done, total) ->
                            stringResource(R.string.stats_loading_week, done, total)
                        } ?: stringResource(R.string.stats_load_history),
                    )
                } else {
                    Text(stringResource(R.string.stats_load_history))
                }
            }

            Text(
                text = if (upToDate) {
                    stringResource(R.string.stats_up_to_date)
                } else {
                    stringResource(R.string.stats_load_history_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    target: Float,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    valueFormat: (Float) -> String = { it.roundToInt().toString() },
) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
    }

    val animations = LocalAnimationsEnabled.current
    val animated by animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis = 550, easing = LinearOutSlowInEasing),
        label = "statValue",
    )

    AppCard(
        modifier = modifier,
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = valueFormat(if (animations) animated else target),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    entries: List<EntryCount>,
    total: Int,
    locale: Locale,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            entries.forEachIndexed { index, entry ->
                CountRow(
                    label = entry.label,
                    count = entry.count,
                    total = total,
                    locale = locale,
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun CountRow(
    label: String,
    count: Int,
    total: Int,
    locale: Locale,
    index: Int = 0,
) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
    }

    val animations = LocalAnimationsEnabled.current
    val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = if (started) fraction else 0f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = index.coerceAtMost(10) * 40,
            easing = LinearOutSlowInEasing,
        ),
        label = "statBar",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = "${formatPercent(total, count, locale)} · $count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (animations) animatedFraction else fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

private fun countWeekdays(start: LocalDate, end: LocalDate): Int {
    var count = 0
    var date = start

    while (!date.isAfter(end)) {
        if (date.dayOfWeek.value <= 5) count++
        date = date.plusDays(1)
    }

    return count
}

private fun mergeCounts(
    days: Collection<DayStats>,
    selector: (DayStats) -> Map<Int, Int>,
): Map<Int, Int> {
    val result = HashMap<Int, Int>()

    days.forEach { day ->
        selector(day).forEach { (key, value) ->
            result[key] = (result[key] ?: 0) + value
        }
    }

    return result
}

private fun formatPercent(lessons: Int, part: Int, locale: Locale): String {
    val value = if (lessons > 0) part * 100.0 / lessons else 0.0

    return String.format(locale, "%.1f%%", value)
}

private fun percentValue(lessons: Int, part: Int): Float =
    if (lessons > 0) part * 100f / lessons else 0f

private fun formatPercentValue(value: Float, locale: Locale): String =
    String.format(locale, "%.1f%%", value)

private fun monthLabel(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern(AppDateFormats.pattern(locale, "MMMyyyy"), locale))

private fun subjectName(id: Int, data: Map<Int, MasterDataItem>): String =
    data[id]?.name?.takeIf { it.isNotBlank() } ?: "#$id"
