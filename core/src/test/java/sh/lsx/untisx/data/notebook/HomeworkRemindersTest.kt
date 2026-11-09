package sh.lsx.untisx.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeworkRemindersTest {

    private val lesson = LessonRef(entryId = 1, date = 20261112, startTime = 955, endTime = 1040)
    private val due = DueRef(date = 20261112, lesson = lesson)

    @Test
    fun `day before at 18 00`() {
        val spec = HomeworkReminders.resolve(ReminderRule(daysBefore = 1, atMinutes = 18 * 60), due)!!
        assertEquals(20261111, spec.date)
        assertEquals(18 * 60, spec.minutesOfDay)
    }

    @Test
    fun `hours before count from the start of the due lesson`() {
        val spec = HomeworkReminders.resolve(ReminderRule(minutesBefore = 60), due)!!
        assertEquals(20261112, spec.date)
        assertEquals(8 * 60 + 55, spec.minutesOfDay)
    }

    @Test
    fun `minutes before can cross into the previous day`() {
        val spec = HomeworkReminders.resolve(ReminderRule(minutesBefore = 12 * 60), due)!!
        assertEquals(20261111, spec.date)
        assertEquals(21 * 60 + 55, spec.minutesOfDay)
    }

    @Test
    fun `days before as minutes`() {
        val spec = HomeworkReminders.resolve(ReminderRule(minutesBefore = 2 * 24 * 60), due)!!
        assertEquals(20261110, spec.date)
        assertEquals(9 * 60 + 55, spec.minutesOfDay)
    }

    @Test
    fun `without a lesson the due moment is 8 o'clock`() {
        val spec = HomeworkReminders.resolve(ReminderRule(minutesBefore = 30), DueRef(date = 20261112))!!
        assertEquals(7 * 60 + 30, spec.minutesOfDay)
    }

    @Test
    fun `a rule based reminder follows a moved due date`() {
        val rule = ReminderRule(daysBefore = 1, atMinutes = 18 * 60)
        val withReminder = due.copy(reminder = HomeworkReminders.resolve(rule, due))
        val moved = HomeworkReminders.refresh(withReminder.copy(date = 20261119, lesson = lesson.copy(date = 20261119)))!!
        assertEquals(20261118, moved.reminder!!.date)
        assertEquals(rule, moved.reminder!!.rule)
    }

    @Test
    fun `a fixed reminder stays where it is`() {
        val fixed = due.copy(reminder = ReminderSpec(date = 20261110, minutesOfDay = 7 * 60))
        val moved = HomeworkReminders.refresh(fixed.copy(date = 20261119))!!
        assertEquals(20261110, moved.reminder!!.date)
        assertNull(moved.reminder!!.rule)
    }
}
