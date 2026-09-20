package sh.lsx.untisx.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.DayStats
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.ui.util.rememberDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    var selectedMonth by remember { mutableStateOf<YearMonth?>(null) }

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

    val lessons = filteredDays.sumOf { it.lessons }
    val cancelled = filteredDays.sumOf { it.cancelled }
    val changes = filteredDays.sumOf { it.changes }
    val taught = (lessons - cancelled).coerceAtLeast(0)

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

    val subjects = remember(yearStats, selectedMonth, subjectsById) {
        mergeCounts(filteredDays) { it.subjectCounts }
            .map { EntryCount(subjectName(it.key, subjectsById), it.value) }
            .sortedByDescending { it.count }
    }

    val locale = LocalConfiguration.current.locales[0]
    val cancelRate = remember(lessons, cancelled, locale) { formatPercent(lessons, cancelled, locale) }
    val cancelChangeRate = remember(lessons, cancelled, changes, locale) {
        formatPercent(lessons, cancelled + changes, locale)
    }

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
            LoadCard(
                loading = loading,
                progress = progress,
                upToDate = upToDate,
                onLoadHistory = onLoadHistory,
            )
        }

        if (filteredDays.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                        value = lessons.toString(),
                        modifier = Modifier.weight(1f),
                    )

                    StatTile(
                        label = stringResource(R.string.stats_taught),
                        value = taught.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = stringResource(R.string.stats_cancelled),
                        value = cancelled.toString(),
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )

                    StatTile(
                        label = stringResource(R.string.stats_changes),
                        value = changes.toString(),
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = stringResource(R.string.stats_cancel_rate),
                        value = cancelRate,
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )

                    StatTile(
                        label = stringResource(R.string.stats_cancel_change_rate),
                        value = cancelChangeRate,
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (subjects.isNotEmpty()) {
                item {
                    BreakdownCard(
                        title = stringResource(R.string.stats_by_subject),
                        entries = subjects,
                        total = lessons,
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                val dateFormat = rememberDateFormat("d. MMM yyyy")

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

            if (lastUpdated > 0L) {
                val locale = LocalConfiguration.current.locales[0]
                val timestamp = remember(lastUpdated, locale) {
                    DateTimeFormatter.ofPattern("d. MMM, HH:mm", locale)
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
private fun LoadCard(
    loading: Boolean,
    progress: Pair<Int, Int>?,
    upToDate: Boolean,
    onLoadHistory: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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

            entries.forEach { entry ->
                CountRow(
                    label = entry.label,
                    count = entry.count,
                    total = total,
                    locale = locale,
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
) {
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
            val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
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

private fun monthLabel(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))

private fun subjectName(id: Int, data: Map<Int, MasterDataItem>): String =
    data[id]?.name?.takeIf { it.isNotBlank() } ?: "#$id"
