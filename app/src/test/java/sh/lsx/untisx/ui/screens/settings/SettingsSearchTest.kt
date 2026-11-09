package sh.lsx.untisx.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private data class E(val title: String, val desc: String = "")

    private val entries = listOf(
        E("Wecker", "Klingelt vor der ersten Stunde, nicht im Unterricht"),
        E("Ruhe im Unterricht", "Stumm während der Stunden"),
        E("Überschrift anzeigen"),
        E("Schlummerdauer"),
    )

    private fun find(q: String) = SettingsSearch.search(q, entries, { it.title }, { it.desc }).map { it.title }

    @Test
    fun `finds by title and description, title hits first`() {
        assertEquals(listOf("Wecker", "Ruhe im Unterricht"), find("stunde"))
        assertEquals(listOf("Ruhe im Unterricht", "Wecker"), find("unterricht"))
        assertEquals(listOf("Wecker"), find("wecker"))
    }

    @Test
    fun `ignores case and umlauts, needs every word`() {
        assertEquals(listOf("Überschrift anzeigen"), find("uberschrift"))
        assertEquals(listOf("Ruhe im Unterricht"), find("ruhe stumm"))
        assertTrue(find("ruhe wecker").isEmpty())
        assertTrue(find("   ").isEmpty())
    }
}
