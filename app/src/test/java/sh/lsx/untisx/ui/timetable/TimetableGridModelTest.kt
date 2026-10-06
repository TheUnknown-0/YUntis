package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class TimetableGridModelTest {

    @Test
    fun `resolveSubject prefers master data name`() {
        val entry = TimetableEntry(subject = listOf(TimetableElement(id = 1, name = "MA")))
        val subjects = mapOf(1 to MasterDataItem(id = 1, name = "Mathematik", longName = "Mathematik lang"))

        assertEquals("Mathematik", resolveSubject(entry, subjects))
    }

    @Test
    fun `resolveSubject falls back to entry name without master data`() {
        val entry = TimetableEntry(subject = listOf(TimetableElement(id = 1, name = "EK")))

        assertEquals("EK", resolveSubject(entry, emptyMap()))
        assertEquals("?", resolveSubject(TimetableEntry(), emptyMap()))
    }
}
