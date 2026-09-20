package sh.lsx.untisx.ui.timetable

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisWeekStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

internal val GUTTER_WIDTH = 48.dp
internal val MINUTE_HEIGHT = 1.15.dp
internal val HEADER_HEIGHT = 56.dp

internal data class LessonDisplay(
    val entry: TimetableEntry,
    val subject: String,
    val rooms: String,
    val teachers: String,
    val origRooms: String? = null,
    val origTeachers: String? = null,
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

internal fun resolveOriginals(
    elements: List<TimetableElement>,
    data: Map<Int, MasterDataItem>,
): String? {
    val originals = elements.mapNotNull { el ->
        el.orgId?.takeIf { it > 0 && it != el.id }?.let { orgId ->
            data[orgId]?.name?.takeIf { it.isNotBlank() } ?: el.orgName?.takeIf { it.isNotBlank() } ?: "?"
        }
    }

    return originals.takeIf { it.isNotEmpty() }?.joinToString(" ").takeIf { !it.isNullOrBlank() }
}

internal fun computeWeekDays(entries: Collection<TimetableEntry>): List<LocalDate> {
    val dates = entries.mapNotNull { it.date.toUntisLocalDateOrNull() }

    if (dates.isEmpty()) return emptyList()

    val monday = dates.min().toUntisWeekStart()
    val base = (0..4).map { monday.plusDays(it.toLong()) }

    return base + dates.filter { it > monday.plusDays(4) }.distinct().sorted()
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
        .ifEmpty { defaultHourMarkers() }

private val DEFAULT_PERIODS = listOf(
    800 to 845,
    850 to 935,
    955 to 1040,
    1040 to 1125,
    1205 to 1250,
    1250 to 1335,
    1345 to 1430,
    1435 to 1520,
    1520 to 1605,
    1605 to 1650,
    1650 to 1735,
    1735 to 1820,
)

internal fun defaultHourMarkers(): List<HourMarker> = DEFAULT_PERIODS
        .mapIndexed { index, (start, end) ->
            HourMarker(start = start, end = end, label = (index + 1).toString())
        }

internal fun mergeEventSpans(entries: List<TimetableEntry>): List<Pair<Int, Int>> {
    val sorted = entries.sortedWith(compareBy({ it.startTime }, { it.endTime }))
    val spans = ArrayList<Triple<Int, Int, String?>>()

    for (entry in sorted) {
        val last = spans.lastOrNull()
        val gap = last?.let { minutesOf(entry.startTime) - minutesOf(it.second) } ?: Int.MAX_VALUE
        val sameEvent = last != null &&
            !entry.eventTitle.isNullOrBlank() &&
            entry.eventTitle == last.third &&
            gap <= 30

        if (sameEvent) {
            spans[spans.size - 1] = last.copy(second = maxOf(last.second, entry.endTime))
        } else {
            spans.add(Triple(entry.startTime, entry.endTime, entry.eventTitle))
        }
    }

    return spans.map { it.first to it.second }
}
