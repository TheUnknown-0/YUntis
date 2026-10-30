package sh.lsx.untisx.wear.ui

import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.wear.WearTimetablePayload
import java.time.LocalDate

data class WearDay(
    val date: LocalDate,
    val entries: List<TimetableEntry>,
    val holiday: Holiday?,
    val restricted: Boolean,
    val loaded: Boolean = true,
)

fun buildDays(payload: WearTimetablePayload, today: LocalDate = AppClock.today()): List<WearDay> {
    val weekStarts = payload.weeks.keys.mapNotNull { it.toIntOrNull()?.toUntisLocalDateOrNull() }

    val byDate = payload.weeks.values.asSequence()
        .flatten()
        .filterNot { it.isEvent && it.exam != null }
        .mapNotNull { entry -> entry.date.toUntisLocalDateOrNull()?.let { it to entry } }
        .groupBy({ it.first }, { it.second })

    var start = weekStarts.minOrNull() ?: today
    var end = weekStarts.maxOrNull()?.plusDays(6) ?: today

    if (today.isBefore(start)) start = today
    if (today.isAfter(end)) end = today

    val days = ArrayList<WearDay>()
    var cursor = start

    while (!cursor.isAfter(end)) {
        val entries = byDate[cursor].orEmpty().sortedBy { it.startTime }
        val date = cursor.toUntisDate()
        val holiday = payload.holidays.firstOrNull { holiday ->
            holiday.startDate > 0 && holiday.endDate >= holiday.startDate &&
                date in holiday.startDate..holiday.endDate
        }
        val restricted = date in payload.restrictedDays

        if (entries.isNotEmpty() || holiday != null || restricted || cursor == today) {
            val loaded = cursor.toUntisWeekStart().toUntisDate().toString() in payload.weeks
            days += WearDay(cursor, entries, holiday, restricted, loaded)
        }

        cursor = cursor.plusDays(1)
    }

    return days
}
