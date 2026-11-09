package sh.lsx.untisx.data.automation

import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The wake-up of a school day: when to get up and which lesson it is for. */
data class WakePlan(val wakeAt: LocalDateTime, val firstLesson: TimetableEntry)

/** A stretch of the day with lessons that take place (short changes between lessons included). */
data class QuietInterval(val start: LocalDateTime, val end: LocalDateTime)

object SchoolDayPlan {
    private fun time(untis: Int) = LocalTime.of((untis / 100).coerceIn(0, 23), (untis % 100).coerceIn(0, 59))

    private fun held(lessons: List<TimetableEntry>, date: LocalDate): List<TimetableEntry> {
        val day = date.toUntisDate()
        return lessons.filter { it.date == day && it.subject.isNotEmpty() && !it.cancelled && it.endTime > it.startTime }
    }

    /**
     * The next wake-up after [now]: first lesson that takes place minus [wayMinutes] and [prepMinutes].
     * Days without loaded lessons or in the holidays are skipped; looks at most [daysAhead] days ahead.
     */
    fun nextWake(
        now: LocalDateTime,
        lessons: List<TimetableEntry>,
        wayMinutes: Int,
        prepMinutes: Int,
        holidays: List<Holiday> = emptyList(),
        daysAhead: Int = 7,
    ): WakePlan? {
        for (offset in 0..daysAhead) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val dateInt = date.toUntisDate()
            if (holidays.any { dateInt in it.startDate..it.endDate }) continue

            val first = held(lessons, date).minByOrNull { it.startTime } ?: continue
            val wake = date.atTime(time(first.startTime)).minusMinutes((wayMinutes + prepMinutes).toLong())
            if (wake.isAfter(now)) return WakePlan(wake, first)
        }
        return null
    }

    /** Lesson stretches of one day; gaps up to [mergeGapMinutes] (changes between rooms) do not break them. */
    fun quietIntervals(lessons: List<TimetableEntry>, date: LocalDate, mergeGapMinutes: Int = 15): List<QuietInterval> {
        val out = ArrayList<QuietInterval>()
        held(lessons, date).sortedBy { it.startTime }.forEach { lesson ->
            val start = date.atTime(time(lesson.startTime))
            val end = date.atTime(time(lesson.endTime))
            val last = out.lastOrNull()
            if (last != null && !start.isAfter(last.end.plusMinutes(mergeGapMinutes.toLong()))) {
                if (end.isAfter(last.end)) out[out.lastIndex] = last.copy(end = end)
            } else {
                out += QuietInterval(start, end)
            }
        }
        return out
    }

    /** Whether [now] falls into a lesson stretch. */
    fun isQuiet(now: LocalDateTime, lessons: List<TimetableEntry>, mergeGapMinutes: Int = 15): Boolean =
        quietIntervals(lessons, now.toLocalDate(), mergeGapMinutes).any { !now.isBefore(it.start) && now.isBefore(it.end) }

    /** The next moment after [now] when the quiet state changes, with the state from then on; null if none within [daysAhead]. */
    fun nextTransition(
        now: LocalDateTime,
        lessons: List<TimetableEntry>,
        mergeGapMinutes: Int = 15,
        daysAhead: Int = 7,
    ): Pair<LocalDateTime, Boolean>? {
        for (offset in 0..daysAhead) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            quietIntervals(lessons, date, mergeGapMinutes).forEach { interval ->
                if (interval.start.isAfter(now)) return interval.start to true
                if (interval.end.isAfter(now)) return interval.end to false
            }
        }
        return null
    }
}
