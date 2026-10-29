package sh.lsx.untisx.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookValidationTest {
    @Test
    fun `body only and title only items are valid`() {
        assertTrue(NotebookValidator.validate(note("body", body = "A short note")).isEmpty())
        assertTrue(NotebookValidator.validate(note("title", title = "A title")).isEmpty())
    }

    @Test
    fun `empty text is invalid`() {
        val issues = NotebookValidator.validate(note("empty", title = "   ", body = "\n"))

        assertTrue(issues.any { it.field == "text" })
    }

    @Test
    fun `kind incompatible fields are rejected not silently dropped`() {
        val noteWithDue = note("note", title = "Note", origin = lessonRef(1), pinnedAt = 1).copy(
            due = DueRef(20260105),
        )
        assertTrue(NotebookValidator.validate(noteWithDue).any { it.field == "due" })

        val taskWithPin = task("task", title = "Task").copy(pinnedAt = 1)
        assertTrue(NotebookValidator.validate(taskWithPin).any { it.field == "pinnedAt" })
    }

    @Test
    fun `reminder date is independent and validated`() {
        val valid = task(
            id = "task",
            title = "Task",
            due = DueRef(
                date = 20260105,
                reminder = ReminderSpec(date = 20260104, minutesOfDay = 17 * 60),
            ),
        )
        assertTrue(NotebookValidator.validate(valid).isEmpty())

        val invalid = valid.copy(
            due = valid.due?.copy(reminder = ReminderSpec(date = 20260104, minutesOfDay = 1440)),
        )
        assertTrue(NotebookValidator.validate(invalid).any { it.field == "due.reminder.minutesOfDay" })
    }

    @Test
    fun `normalize does not erase semantic state`() {
        val item = note(" n1 ", title = "  Title  ", subject = SubjectRef(ids = listOf(2, 1, 2), name = " Math "))
        val normalized = NotebookValidator.normalize(item)

        assertEquals("n1", normalized.id)
        assertEquals("Title", normalized.title)
        assertEquals(listOf(1, 2), normalized.subject?.ids)
        assertEquals("Math", normalized.subject?.name ?: "")
    }

    @Test
    fun `duplicate item ids are reported`() {
        val issues = NotebookValidator.validateItems(listOf(note("same", title = "A"), note("same", title = "B")))

        assertTrue(issues.any { it.field == "id" })
    }
}
