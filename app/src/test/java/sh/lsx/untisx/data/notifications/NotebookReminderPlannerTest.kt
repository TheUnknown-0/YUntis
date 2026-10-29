package sh.lsx.untisx.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.ReminderSpec
import java.time.Instant
import java.time.ZoneOffset

class NotebookReminderPlannerTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-29T07:00:00Z").toEpochMilli()

    private fun task(
        dueDate: Int = 20261030,
        reminderDate: Int = 20261029,
        minutesOfDay: Int = 8 * 60,
        completedAt: Long? = null,
        kind: NotebookKind = NotebookKind.TASK,
        updatedAt: Long = 10,
    ) = NotebookItem(
        id = "item-1",
        kind = kind,
        title = "Math homework",
        due = DueRef(
            date = dueDate,
            reminder = ReminderSpec(date = reminderDate, minutesOfDay = minutesOfDay),
        ),
        updatedAt = updatedAt,
        completedAt = completedAt,
    )

    @Test
    fun `reminder date is independent from due date`() {
        val plan = NotebookReminderPlanner.plan(task(), now, zone)

        assertEquals(Instant.parse("2026-10-29T08:00:00Z").toEpochMilli(), plan?.triggerAt)
        assertEquals(20261029, plan?.reminderDate)
        assertEquals(8 * 60, plan?.minutesOfDay)
    }

    @Test
    fun `zero reminder date falls back to the due date`() {
        val plan = NotebookReminderPlanner.plan(
            task(dueDate = 20261030, reminderDate = 0),
            now,
            zone,
        )

        assertEquals(Instant.parse("2026-10-30T08:00:00Z").toEpochMilli(), plan?.triggerAt)
    }

    @Test
    fun `completed tasks have no reminder`() {
        assertNull(NotebookReminderPlanner.plan(task(completedAt = 1L), now, zone))
    }

    @Test
    fun `notes have no reminder`() {
        assertNull(NotebookReminderPlanner.plan(task(kind = NotebookKind.NOTE), now, zone))
    }

    @Test
    fun `invalid minutes have no reminder`() {
        assertNull(NotebookReminderPlanner.plan(task(minutesOfDay = 1440), now, zone))
        assertNull(NotebookReminderPlanner.plan(task(minutesOfDay = -1), now, zone))
    }

    @Test
    fun `reminder inside the grace window is still planned`() {
        val trigger = Instant.parse("2026-10-29T08:00:00Z").toEpochMilli()
        val justInside = trigger + NotebookReminderPlanner.GRACE_MILLIS - 1

        assertEquals(trigger, NotebookReminderPlanner.plan(task(), justInside, zone)?.triggerAt)
    }

    @Test
    fun `reminder older than the grace window is stale`() {
        val trigger = Instant.parse("2026-10-29T08:00:00Z").toEpochMilli()
        val tooOld = trigger + NotebookReminderPlanner.GRACE_MILLIS + 1

        assertNull(NotebookReminderPlanner.plan(task(), tooOld, zone))
    }
}
