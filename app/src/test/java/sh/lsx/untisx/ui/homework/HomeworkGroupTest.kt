package sh.lsx.untisx.ui.homework

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import java.time.LocalDate

class HomeworkGroupTest {

    // Wednesday
    private val today = LocalDate.of(2026, 11, 11)

    private fun task(id: String, due: Int?, done: Boolean = false) = NotebookItem(
        id = id,
        kind = NotebookKind.TASK,
        title = id,
        due = due?.let { DueRef(date = it) },
        completedAt = if (done) 1L else null,
    )

    @Test
    fun `homework is grouped by due date`() {
        assertEquals(HomeworkGroup.OVERDUE, homeworkGroup(task("a", 20261110), today))
        assertEquals(HomeworkGroup.TODAY, homeworkGroup(task("a", 20261111), today))
        assertEquals(HomeworkGroup.TOMORROW, homeworkGroup(task("a", 20261112), today))
        assertEquals(HomeworkGroup.THIS_WEEK, homeworkGroup(task("a", 20261115), today))
        assertEquals(HomeworkGroup.LATER, homeworkGroup(task("a", 20261116), today))
        assertEquals(HomeworkGroup.NO_DATE, homeworkGroup(task("a", null), today))
    }

    @Test
    fun `only open tasks are listed, in order`() {
        val items = listOf(
            task("later", 20261120),
            task("done", 20261111, done = true),
            task("tomorrow", 20261112),
            task("overdue", 20261101),
            NotebookItem(id = "note", kind = NotebookKind.NOTE, title = "note"),
        )
        val groups = groupOpenHomework(items, today)
        assertEquals(listOf(HomeworkGroup.OVERDUE, HomeworkGroup.TOMORROW, HomeworkGroup.LATER), groups.keys.toList())
        assertEquals(listOf("overdue", "tomorrow", "later"), groups.values.flatten().map { it.id })
    }
}
