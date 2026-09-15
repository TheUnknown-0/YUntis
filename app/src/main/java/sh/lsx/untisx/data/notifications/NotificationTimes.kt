package sh.lsx.untisx.data.notifications

import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate

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
}
