package sh.lsx.untisx.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate
import java.time.LocalTime

class ShareCodecTest {
    @Test
    fun `payloads survive the link round trip`() {
        val payloads = listOf(
            SharePayload.Homework("anna", "S. 42 Nr. 3", "a bis d, mit Rechenweg", "Mathematik", 20261111),
            SharePayload.Exam("anna", "Klausur", "Deutsch", 20261125, 800, 935, "Erörterung"),
            SharePayload.Week("anna", 20261109, listOf(Busy(20261109, 800, 935), Busy(20261110, 955, 1325))),
        )
        payloads.forEach { payload ->
            val link = ShareCodec.encode(payload)
            assertTrue(link.startsWith("yuntis://share?v=1&d="))
            assertEquals(payload, ShareCodec.decode(link))
        }
    }

    @Test
    fun `foreign or damaged links are rejected`() {
        assertNull(ShareCodec.decode("https://example.org"))
        assertNull(ShareCodec.decode("yuntis://share?v=1&d=kaputt!"))
        assertNull(ShareCodec.decode("yuntis://share?v=99&d=AAAA"))
    }

    @Test
    fun `a week becomes lesson stretches without subjects`() {
        val m = listOf(TimetableElement(id = 2, name = "M"))
        val lessons = listOf(
            TimetableEntry(id = 1, date = 20261109, startTime = 800, endTime = 845, subject = m),
            TimetableEntry(id = 2, date = 20261109, startTime = 845, endTime = 930, subject = m),
            TimetableEntry(id = 3, date = 20261109, startTime = 1000, endTime = 1045, subject = m, code = "cancelled"),
        )
        val week = ShareCodec.weekOf("anna", LocalDate.of(2026, 11, 11), lessons)
        assertEquals(20261109, week.monday)
        assertEquals(listOf(Busy(20261109, 800, 930)), week.busy)
    }

    @Test
    fun `common free time leaves out both sides' lessons`() {
        val mine = listOf(Busy(20261109, 800, 1130))
        val theirs = listOf(Busy(20261109, 900, 1325))
        val free = ShareCodec.freeTogether(mine, theirs)
        assertEquals(listOf(LocalTime.of(13, 25) to LocalTime.of(17, 0)), free.map { it.start to it.end })
    }
}
