package sh.lsx.untisx.data.notebook

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import java.time.LocalDateTime

/** Turns relative reminder rules into concrete reminder times. */
object HomeworkReminders {

    /** Used as due moment when a task has a due date but no lesson. */
    const val DUE_WITHOUT_LESSON_MINUTES = 8 * 60

    fun dueMinutesOfDay(due: DueRef): Int {
        val start = due.lesson?.takeIf { it.date == due.date }?.startTime ?: return DUE_WITHOUT_LESSON_MINUTES
        return start / 100 * 60 + start % 100
    }

    fun resolve(rule: ReminderRule, due: DueRef): ReminderSpec? {
        val dueDate = due.date.toUntisLocalDateOrNull() ?: return null

        if (rule.atMinutes != null) {
            val minutes = rule.atMinutes.coerceIn(0, 23 * 60 + 59)
            val date = dueDate.minusDays(rule.daysBefore.coerceIn(0, 60).toLong())
            return ReminderSpec(date = date.toUntisDate(), minutesOfDay = minutes, rule = rule)
        }

        val dueMoment = dueDate.atStartOfDay().plusMinutes(dueMinutesOfDay(due).toLong())
        val at: LocalDateTime = dueMoment.minusMinutes(rule.minutesBefore.coerceIn(0, 60 * 24 * 60).toLong())
        return ReminderSpec(
            date = at.toLocalDate().toUntisDate(),
            minutesOfDay = at.hour * 60 + at.minute,
            rule = rule,
        )
    }

    /** Re-derives a rule based reminder after the due date or lesson changed. */
    fun refresh(due: DueRef?): DueRef? {
        val rule = due?.reminder?.rule ?: return due
        return due.copy(reminder = resolve(rule, due) ?: due.reminder)
    }
}

/** Defaults for new homework, set in the notification settings. */
object HomeworkPrefs {
    const val KEY_AUTO_DUE = "homework_auto_due_next_lesson"
    const val KEY_REMINDER_ENABLED = "homework_default_reminder"
    const val KEY_REMINDER_MINUTES = "homework_default_reminder_minutes"
    const val KEY_REMINDER_DAYS_BEFORE = "homework_default_reminder_days_before"

    val KEYS = setOf(KEY_AUTO_DUE, KEY_REMINDER_ENABLED, KEY_REMINDER_MINUTES, KEY_REMINDER_DAYS_BEFORE)

    const val DEFAULT_REMINDER_MINUTES = 18 * 60
    const val DEFAULT_DAYS_BEFORE = 1

    private fun prefs(context: Context) =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun autoDue(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_DUE, true)
    fun setAutoDue(context: Context, enabled: Boolean) = prefs(context).edit { putBoolean(KEY_AUTO_DUE, enabled) }

    fun reminderEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REMINDER_ENABLED, true)
    fun setReminderEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_REMINDER_ENABLED, enabled) }

    fun reminderMinutes(context: Context): Int =
        prefs(context).getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES).coerceIn(0, 23 * 60 + 59)
    fun setReminderMinutes(context: Context, minutes: Int) =
        prefs(context).edit { putInt(KEY_REMINDER_MINUTES, minutes.coerceIn(0, 23 * 60 + 59)) }

    fun reminderDaysBefore(context: Context): Int =
        prefs(context).getInt(KEY_REMINDER_DAYS_BEFORE, DEFAULT_DAYS_BEFORE).coerceIn(0, 14)
    fun setReminderDaysBefore(context: Context, days: Int) =
        prefs(context).edit { putInt(KEY_REMINDER_DAYS_BEFORE, days.coerceIn(0, 14)) }

    /** The default reminder rule, or null when new homework gets no reminder. */
    fun defaultRule(context: Context): ReminderRule? =
        if (!reminderEnabled(context)) null
        else ReminderRule(daysBefore = reminderDaysBefore(context), atMinutes = reminderMinutes(context))
}
