package sh.lsx.untisx.data.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate
import java.time.LocalDateTime

class SchoolDayPlanTest {
    private val m = listOf(TimetableElement(id = 2, name = "M"))

    private fun lesson(date: Int, start: Int, end: Int, code: String? = null) =
        TimetableEntry(id = date + start, date = date, startTime = start, endTime = end, subject = m, code = code)

    // Tuesday 10 Nov: first lesson cancelled, so school starts at 08:50
    private val lessons = listOf(
        lesson(20261110, 800, 845, "cancelled"),
        lesson(20261110, 850, 935),
        lesson(20261110, 955, 1040),
        lesson(20261110, 1045, 1130),
        lesson(20261110, 1240, 1325),
        lesson(20261111, 800, 845),
    )

    @Test
    fun `wake time uses the first lesson that takes place`() {
        val plan = SchoolDayPlan.nextWake(LocalDateTime.of(2026, 11, 9, 20, 0), lessons, wayMinutes = 30, prepMinutes = 60)!!
        assertEquals(LocalDateTime.of(2026, 11, 10, 7, 20), plan.wakeAt)
        assertEquals(850, plan.firstLesson.startTime)
    }

    @Test
    fun `after the wake time the next school day is taken, holidays are skipped`() {
        val next = SchoolDayPlan.nextWake(LocalDateTime.of(2026, 11, 10, 7, 30), lessons, 30, 60)!!
        assertEquals(LocalDate.of(2026, 11, 11), next.wakeAt.toLocalDate())

        val holiday = Holiday(id = 1, startDate = 20261111, endDate = 20261111)
        assertNull(SchoolDayPlan.nextWake(LocalDateTime.of(2026, 11, 10, 7, 30), lessons, 30, 60, listOf(holiday), daysAhead = 3))
    }

    @Test
    fun `short changes stay quiet, the long break and free periods do not`() {
        val day = SchoolDayPlan.quietIntervals(lessons, LocalDate.of(2026, 11, 10))
        // 08:50 to 11:30 (breaks of 20 and 5 minutes: the 20 minute one splits), then 12:40 to 13:25
        assertEquals(
            listOf("08:50-09:35", "09:55-11:30", "12:40-13:25"),
            day.map { "${it.start.toLocalTime()}-${it.end.toLocalTime()}" },
        )
        assertTrue(SchoolDayPlan.isQuiet(LocalDateTime.of(2026, 11, 10, 10, 42), lessons))
        assertFalse(SchoolDayPlan.isQuiet(LocalDateTime.of(2026, 11, 10, 12, 0), lessons))
    }

    @Test
    fun `next transition switches on at a lesson and off after it`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 10, 8, 50) to true,
            SchoolDayPlan.nextTransition(LocalDateTime.of(2026, 11, 10, 7, 0), lessons),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 10, 11, 30) to false,
            SchoolDayPlan.nextTransition(LocalDateTime.of(2026, 11, 10, 10, 0), lessons),
        )
    }
}
