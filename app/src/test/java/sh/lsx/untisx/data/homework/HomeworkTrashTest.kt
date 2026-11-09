package sh.lsx.untisx.data.homework

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind

class HomeworkTrashTest {
    private fun entry(id: String, trashedAt: Long) =
        TrashedHomework(NotebookItem(id = id, kind = NotebookKind.TASK, title = id, completedAt = 1L), trashedAt)

    @Test
    fun `entries stay two days, then they are gone`() {
        val now = 10 * HomeworkTrash.KEEP_MILLIS
        val fresh = entry("a", now - 1000)
        val almost = entry("b", now - HomeworkTrash.KEEP_MILLIS + 60_000)
        val expired = entry("c", now - HomeworkTrash.KEEP_MILLIS)
        assertEquals(listOf("a", "b"), HomeworkTrash.purge(listOf(fresh, almost, expired), now).map { it.item.id })
    }

    @Test
    fun `expiry is two days after trashing`() {
        assertEquals(1000 + 2 * 24 * 60 * 60 * 1000L, entry("a", 1000).expiresAt())
    }
}
