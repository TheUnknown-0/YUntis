package sh.lsx.untisx.data.notebook

import java.time.ZoneId
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull

object NotebookReminderTime {
    const val DEFAULT_MINUTES_OF_DAY = 8 * 60

    fun triggerAt(reminder: ReminderSpec, zone: ZoneId): Long? =
        triggerAt(reminder.date, reminder.minutesOfDay, zone)

    fun triggerAt(date: Int, minutesOfDay: Int, zone: ZoneId): Long? {
        if (!NotebookValidator.isValidDate(date)) return null
        if (!NotebookValidator.isValidMinutesOfDay(minutesOfDay)) return null

        val localDate = date.toUntisLocalDateOrNull() ?: return null

        return runCatching {
            localDate
                .atTime(minutesOfDay / 60, minutesOfDay % 60)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
