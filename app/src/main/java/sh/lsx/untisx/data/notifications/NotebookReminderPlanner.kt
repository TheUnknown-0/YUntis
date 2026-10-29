package sh.lsx.untisx.data.notifications

import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookReminderTime
import java.time.ZoneId

data class NotebookReminderPlan(
    val triggerAt: Long,
    val revision: Long,
    val reminderDate: Int,
    val minutesOfDay: Int,
)

object NotebookReminderPlanner {

    const val GRACE_MILLIS = 60L * 60L * 1000L

    const val EARLY_TOLERANCE_MILLIS = 2L * 60L * 1000L

    fun plan(item: NotebookItem, nowMillis: Long, zone: ZoneId): NotebookReminderPlan? {
        if (!item.isTask || item.isCompleted) return null

        val due = item.due ?: return null
        val reminder = due.reminder ?: return null

        val reminderDate = reminder.date.takeIf { it > 0 } ?: due.date
        val triggerAt = NotebookReminderTime.triggerAt(reminderDate, reminder.minutesOfDay, zone)
            ?: return null

        if (triggerAt < nowMillis - GRACE_MILLIS) return null

        return NotebookReminderPlan(
            triggerAt = triggerAt,
            revision = item.updatedAt,
            reminderDate = reminderDate,
            minutesOfDay = reminder.minutesOfDay,
        )
    }
}

internal object NotebookReminderClock {

    @Volatile
    var now: () -> Long = { System.currentTimeMillis() }

    @Volatile
    var zone: () -> ZoneId = { ZoneId.systemDefault() }

    fun reset() {
        now = { System.currentTimeMillis() }
        zone = { ZoneId.systemDefault() }
    }
}
