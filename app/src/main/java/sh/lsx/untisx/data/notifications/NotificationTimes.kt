package sh.lsx.untisx.data.notifications

import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal object NotificationTimes {

    const val NOON_MINUTE = 12 * 60

    fun overviewTargetDate(today: LocalDate, overviewMinute: Int): LocalDate =
        if (overviewMinute < NOON_MINUTE) today else today.plusDays(1)

    fun isWithinLessonSpan(
        entries: List<TimetableEntry>,
        today: Int,
        nowMinute: Int,
    ): Boolean {
        val lessons = entries.filter { entry ->
            entry.date == today &&
                entry.subject.isNotEmpty() &&
                entry.code != "cancelled"
        }

        if (lessons.isEmpty()) return false

        val start = lessons.minOf { minutesOf(it.startTime) }
        val end = lessons.maxOf { minutesOf(it.endTime) }

        return nowMinute in start..end
    }

    fun minutesOf(time: Int): Int = time / 100 * 60 + time % 100

    fun nextOccurrence(minuteOfDay: Int, now: ZonedDateTime): ZonedDateTime {
        val zone = now.zone
        val time = LocalTime.of(
            (minuteOfDay / 60).coerceIn(0, 23),
            (minuteOfDay % 60).coerceIn(0, 59),
        )

        val today = now.toLocalDate().atTime(time).atZone(zone)

        return if (today.isAfter(now)) {
            today
        } else {
            now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        }
    }
}
