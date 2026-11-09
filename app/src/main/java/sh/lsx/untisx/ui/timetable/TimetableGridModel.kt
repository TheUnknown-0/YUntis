package sh.lsx.untisx.ui.timetable

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisWeekStart
import java.time.LocalDate

internal val GUTTER_WIDTH = 48.dp
internal val MINUTE_HEIGHT = 1.15.dp
internal val HEADER_HEIGHT = 56.dp

internal data class LessonDisplay(
    val entry: TimetableEntry,
    val subject: String,
    val label: String = subject,
    val rooms: String,
    val teachers: String,
    val origRooms: String? = null,
    val origTeachers: String? = null,
    val exam: ExamInfo? = null,
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
        val orgName = el.orgName?.takeIf { it.isNotBlank() }
        val orgId = el.orgId?.takeIf { it > 0 && it != el.id }

        when {
            orgId != null -> data[orgId]?.name?.takeIf { it.isNotBlank() } ?: orgName ?: "?"
            orgName != null -> orgName
            else -> null
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

internal data class EventSpan(
    val start: Int,
    val end: Int,
    val title: String?,
    val entry: TimetableEntry,
) {
    val allDay: Boolean get() = start == 0 && end == 0
}

internal fun mergeEventSpans(entries: List<TimetableEntry>): List<EventSpan> {
    val sorted = entries.sortedWith(compareBy({ it.startTime }, { it.endTime }))
    val spans = ArrayList<EventSpan>()

    for (entry in sorted) {
        val last = spans.lastOrNull()
        val gap = last?.let { minutesOf(entry.startTime) - minutesOf(it.end) } ?: Int.MAX_VALUE
        val sameEvent = last != null &&
            !entry.eventTitle.isNullOrBlank() &&
            entry.eventTitle == last.title &&
            gap <= 30

        if (sameEvent) {
            spans[spans.size - 1] = last.copy(end = maxOf(last.end, entry.endTime))
        } else {
            spans.add(EventSpan(start = entry.startTime, end = entry.endTime, title = entry.eventTitle, entry = entry))
        }
    }

    return spans
}

/** One card in a day column. [hidden] lessons run at the same time and are listed when the card is tapped. */
internal data class LessonStack(
    val shown: LessonDisplay,
    val hidden: List<LessonDisplay> = emptyList(),
) {
    val all: List<LessonDisplay> get() = listOf(shown) + hidden
}

private fun TimetableEntry.hasServerLayout(): Boolean = layoutStartPosition != 0 || layoutWidth < 1000

private fun overlaps(a: TimetableEntry, b: TimetableEntry): Boolean =
    minutesOf(a.startTime) < minutesOf(b.endTime) && minutesOf(b.startTime) < minutesOf(a.endTime)

/**
 * Puts lessons that run at the same time into one card, so parallel courses (for example
 * in a class timetable of the upper school) do not hide each other. Lessons with a layout
 * from the server keep it. The shown lesson is the earliest, preferring held over cancelled
 * and longer over shorter lessons.
 */
internal fun stackParallelLessons(lessons: List<LessonDisplay>): List<LessonStack> {
    val (laidOut, free) = lessons.partition { it.entry.hasServerLayout() }

    val ordered = free.sortedWith(
        compareBy<LessonDisplay>(
            { minutesOf(it.entry.startTime) },
            { it.entry.cancelled },
            { -(minutesOf(it.entry.endTime) - minutesOf(it.entry.startTime)) },
            { it.label.lowercase() },
        ),
    )

    val stacks = ArrayList<Pair<LessonDisplay, MutableList<LessonDisplay>>>()
    for (lesson in ordered) {
        val host = stacks.firstOrNull { overlaps(it.first.entry, lesson.entry) }
        if (host != null) host.second += lesson else stacks += lesson to mutableListOf()
    }

    return laidOut.map { LessonStack(it) } + stacks.map { (shown, hidden) -> LessonStack(shown, hidden) }
}
