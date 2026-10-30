package sh.lsx.untisx.wear.ui

import org.junit.Assert.*
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.wear.WearTimetablePayload

class WearLessonKeyTest {
    @Test fun `selected lesson survives sync with updated details`() {
        val old = TimetableEntry(id = 42, date = 20261005, startTime = 800)
        val changed = old.copy(info = "Changed", startTime = 900)
        val payload = WearTimetablePayload(weeks = mapOf("20261005" to listOf(changed)))
        assertEquals(changed, payload.findLesson(old.wearKey()))
        assertNull(payload.copy(weeks = emptyMap()).findLesson(old.wearKey()))
    }

    @Test fun `zero id fallback distinguishes lessons by date time and subjects`() {
        val first = TimetableEntry(date = 20261005, startTime = 800)
        val other = first.copy(startTime = 900)
        val payload = WearTimetablePayload(weeks = mapOf("20261005" to listOf(other)))
        assertNull(payload.findLesson(first.wearKey()))
    }

    @Test fun `duplicate ids do not select a different period`() {
        val first = TimetableEntry(id = 42, date = 20261005, startTime = 800)
        val second = first.copy(startTime = 900)
        val payload = WearTimetablePayload(weeks = mapOf("20261005" to listOf(first, second)))
        assertEquals(second, payload.findLesson(second.wearKey()))
    }
}
