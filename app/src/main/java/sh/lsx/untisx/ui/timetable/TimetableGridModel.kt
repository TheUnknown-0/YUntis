package sh.lsx.untisx.ui.timetable

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
internal val DAY_DATE_FORMAT = DateTimeFormatter.ofPattern("d. MMM", Locale.ENGLISH)

internal val GUTTER_WIDTH = 48.dp
internal val MINUTE_HEIGHT = 1.15.dp
internal val HEADER_HEIGHT = 56.dp

internal data class LessonDisplay(
    val entry: TimetableEntry,
    val subject: String,
    val rooms: String,
    val teachers: String,
)

internal data class HourMarker(
    val start: Int,
    val end: Int,
    val label: String,
)

internal data class GridMetrics(
    val dayStart: Int,
    val dayEnd: Int,
    val height: Dp,
)

internal fun minutesOf(time: Int): Int = time / 100 * 60 + time % 100

internal fun untisTimeOf(minutes: Int): Int = minutes / 60 * 100 + minutes % 60

internal fun resolveSubject(
    entry: TimetableEntry,
    subjects: Map<Int, MasterDataItem>,
): String =
    entry.subject.firstOrNull()
        ?.let { el -> subjects[el.id]?.name?.takeIf { it.isNotBlank() } ?: el.name }
        ?.takeIf { it.isNotBlank() }
        ?: "?"

internal fun resolveNames(
    elements: List<TimetableElement>,
    data: Map<Int, MasterDataItem>,
): String =
    elements
        .mapNotNull { el -> data[el.id]?.name?.takeIf { it.isNotBlank() } ?: el.name.takeIf { it.isNotBlank() } }
        .joinToString(" ")

internal fun computeWeekDays(entries: Collection<TimetableEntry>): List<LocalDate> {
    if (entries.isEmpty()) return emptyList()

    val monday = entries.minOf { it.date }.toUntisLocalDate().toUntisWeekStart()
    val dataDays = entries.map { it.date }.distinct().sorted().map { it.toUntisLocalDate() }
    val base = (0..4).map { monday.plusDays(it.toLong()) }

    return base + dataDays.filter { it > monday.plusDays(4) }.distinct()
}

internal fun gridMetricsFrom(slots: List<HourMarker>): GridMetrics {
    val startMinutes = slots.minOfOrNull { minutesOf(it.start) } ?: 8 * 60
    val endMinutes = slots.maxOfOrNull { minutesOf(it.end) } ?: 18 * 60

    return GridMetrics(
        dayStart = untisTimeOf(startMinutes),
        dayEnd = untisTimeOf(endMinutes),
        height = MINUTE_HEIGHT * (endMinutes - startMinutes),
    )
}

internal fun hourMarkersFrom(timegrid: List<TimegridDay>): List<HourMarker> = timegrid
        .maxByOrNull { it.timeUnits.size }
        ?.timeUnits
        .orEmpty()
        .filter { it.startTime != 0 || it.endTime != 0 }
        .mapIndexed { index, unit ->
            HourMarker(
                start = unit.startTime,
                end = unit.endTime,
                label = unit.label?.trim()?.removeSuffix(".")?.takeIf(String::isNotBlank)
                    ?: (index + 1).toString(),
            )
        }

internal fun mergeEventSpans(entries: List<TimetableEntry>): List<Pair<Int, Int>> {
    val sorted = entries.sortedWith(compareBy({ it.startTime }, { it.endTime }))
    val spans = ArrayList<Triple<Int, Int, String?>>()

    for (entry in sorted) {
        val last = spans.lastOrNull()
        val gap = last?.let { minutesOf(entry.startTime) - minutesOf(it.second) } ?: Int.MAX_VALUE
        val sameEvent = last != null &&
            !entry.lstext.isNullOrBlank() &&
            entry.lstext == last.third &&
            gap <= 30

        if (sameEvent) {
            spans[spans.size - 1] = last.copy(second = maxOf(last.second, entry.endTime))
        } else {
            spans.add(Triple(entry.startTime, entry.endTime, entry.lstext))
        }
    }

    return spans.map { it.first to it.second }
}
