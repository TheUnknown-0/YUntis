package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class LessonCardTextTest {

    @Test
    fun `changed text keeps the original when enabled`() {
        assertEquals("A01 → E01", changedText("A01", "E01").text)
        assertEquals("A01 → E01", changedText("A01", "E01", showChanges = true).text)
    }

    @Test
    fun `changed text hides the original when disabled`() {
        assertEquals("E01", changedText("A01", "E01", showChanges = false).text)
    }

    @Test
    fun `blank original stays plain`() {
        assertEquals("E01", changedText(null, "E01").text)
        assertEquals("E01", changedText("", "E01").text)
        assertEquals("E01", changedText(null, "E01", showChanges = false).text)
    }
}
