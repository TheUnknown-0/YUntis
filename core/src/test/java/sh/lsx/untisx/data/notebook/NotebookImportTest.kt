package sh.lsx.untisx.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportTest {
    private val localItem = note("a", title = "Local", updatedAt = 20)
    private val local = NotebookData(revision = 7, items = listOf(localItem))

    private val identical = note("a", title = "Local", updatedAt = 20)
    private val sameIdDifferent = note("a", title = "Changed", updatedAt = 21)
    private val sameContentDifferentId = note("b", title = "Local", updatedAt = 20)
    private val olderSameId = note("a", title = "Older", updatedAt = 10)
    private val newItem = note("c", title = "New", updatedAt = 5)
    private val invalidItem = note("", title = "")

    private val baseExport = NotebookExport(
        items = listOf(identical, sameContentDifferentId, newItem, invalidItem),
    )

    @Test
    fun `preview classifies identical conflicts new and invalid`() {
        val preview = NotebookImportPlanner.preview(baseExport, local)

        assertEquals(7L, preview.localRevision)
        assertEquals(listOf("c"), preview.newItems.map { it.id })
        assertEquals(listOf("a"), preview.identical.map { it.id })
        assertEquals(1, preview.conflicts.size)
        assertEquals(
            NotebookImportConflictReason.SAME_CONTENT_DIFFERENT_ID,
            preview.conflicts.single().reason,
        )
        assertTrue(preview.invalid.isNotEmpty())
    }

    @Test
    fun `preview reports same id differences and older timestamps`() {
        val changed = NotebookImportPlanner.preview(NotebookExport(items = listOf(sameIdDifferent)), local)
        assertEquals(NotebookImportConflictReason.SAME_ID_DIFFERENT_CONTENT, changed.conflicts.single().reason)

        val older = NotebookImportPlanner.preview(NotebookExport(items = listOf(olderSameId)), local)
        assertEquals(NotebookImportConflictReason.SAME_ID_OLDER, older.conflicts.single().reason)
    }

    @Test
    fun `empty choices default to skip and never blind replace`() {
        val preview = NotebookImportPlanner.preview(baseExport, local)
        val plan = NotebookImportPlanner.plan(preview, emptyMap()) { "new-id" }

        assertEquals(listOf("c"), plan.add.map { it.id })
        assertTrue(plan.replace.isEmpty())
        assertTrue(plan.keepBoth.isEmpty())
        assertEquals(7L, plan.localRevision)
    }

    @Test
    fun `replace is only applied to explicit same id conflicts`() {
        val preview = NotebookImportPlanner.preview(
            NotebookExport(items = listOf(sameIdDifferent, sameContentDifferentId)),
            local,
        )
        val plan = NotebookImportPlanner.plan(
            preview,
            mapOf(
                "a" to NotebookImportChoice.REPLACE,
                "b" to NotebookImportChoice.REPLACE,
            ),
        ) { "new-id" }

        assertEquals(listOf("a"), plan.replace.map { it.id })
        assertEquals("Changed", plan.replace.single().title)
        assertTrue(plan.keepBoth.isEmpty())
    }

    @Test
    fun `keep both gets a new id`() {
        val preview = NotebookImportPlanner.preview(NotebookExport(items = listOf(sameIdDifferent)), local)
        val plan = NotebookImportPlanner.plan(preview, mapOf("a" to NotebookImportChoice.KEEP_BOTH)) { "new-id" }

        assertEquals(1, plan.keepBoth.size)
        assertEquals("new-id", plan.keepBoth.single().id)
        assertEquals("Changed", plan.keepBoth.single().title)
    }

    @Test
    fun `invalid items never reach a plan`() {
        val preview = NotebookImportPlanner.preview(baseExport, local)
        val plan = NotebookImportPlanner.plan(preview, emptyMap()) { "new-id" }

        assertFalse(plan.add.any { it.id.isBlank() })
        assertFalse(plan.replace.any { it.id.isBlank() })
        assertFalse(plan.keepBoth.any { it.id.isBlank() })
    }
}
