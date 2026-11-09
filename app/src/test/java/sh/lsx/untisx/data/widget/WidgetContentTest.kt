package sh.lsx.untisx.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetContentTest {
    private val m = listOf(TimetableElement(id = 2, name = "M"))
    private val d = listOf(TimetableElement(id = 1, name = "D"))
    private val room = listOf(TimetableElement(id = 5, name = "A101"))

    private fun lesson(date: Int, start: Int, end: Int, subject: List<TimetableElement>, code: String? = null) =
        TimetableEntry(id = date + start, date = date, startTime = start, endTime = end, subject = subject, room = room, code = code)

    private val entries = listOf(
        lesson(20261109, 800, 845, m),
        lesson(20261109, 850, 935, m),
        lesson(20261109, 955, 1040, d, code = "cancelled"),
        lesson(20261109, 1045, 1130, d),
        lesson(20261110, 800, 845, d),
    )

    @Test
    fun `double lessons are merged and the running lesson is next`() {
        val day = WidgetContent.build(LocalDateTime.of(2026, 11, 9, 8, 30), entries, emptyMap(), emptyMap())

        assertEquals(LocalDate.of(2026, 11, 9), day.date)
        assertEquals(listOf(800 to 935, 955 to 1040, 1045 to 1130), day.lessons.map { it.start to it.end })
        assertEquals(800, day.next?.start)
        assertTrue(day.running)
    }

    @Test
    fun `cancelled lessons are skipped for next, and after school the next day is shown`() {
        val beforeThird = WidgetContent.build(LocalDateTime.of(2026, 11, 9, 9, 40), entries, emptyMap(), emptyMap())
        assertEquals(1045, beforeThird.next?.start)

        val evening = WidgetContent.build(LocalDateTime.of(2026, 11, 9, 18, 0), entries, emptyMap(), emptyMap())
        assertEquals(LocalDate.of(2026, 11, 10), evening.date)
        assertEquals("D", evening.next?.subject)
    }

    @Test
    fun `refresh happens at the next lesson boundary`() {
        val now = LocalDateTime.of(2026, 11, 9, 8, 30)
        val day = WidgetContent.build(now, entries, emptyMap(), emptyMap())
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 35), WidgetContent.nextRefresh(now, day))
    }
}
