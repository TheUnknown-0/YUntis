package sh.lsx.untisx.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind

class SearchTest {
    private val data = SearchData(
        subjects = mapOf(2 to MasterDataItem(id = 2, name = "M", longName = "Mathematik")),
        teachers = mapOf(1 to MasterDataItem(id = 1, name = "MÜL", longName = "Müller", foreName = "Anna")),
        events = listOf(TimetableEntry(id = 9, date = 20261120, lstext = "Wandertag")),
        notebook = listOf(NotebookItem(id = "n", kind = NotebookKind.NOTE, title = "Formelsammlung Mathe", body = "Seite 12")),
    )

    @Test
    fun `umlauts and case do not matter`() {
        val hits = Search.run("muller", data)
        assertEquals(listOf(SearchKind.TEACHER), hits.map { it.kind })
        assertEquals("Anna Müller", hits.single().title)
    }

    @Test
    fun `all words must match, across kinds`() {
        assertEquals(setOf(SearchKind.SUBJECT, SearchKind.NOTE), Search.run("mathe", data).map { it.kind }.toSet())
        assertEquals(listOf(SearchKind.NOTE), Search.run("mathe seite", data).map { it.kind })
        assertTrue(Search.run("   ", data).isEmpty())
        assertEquals(SearchTarget.Event(data.events.single()), Search.run("wander", data).single().target)
    }
}
