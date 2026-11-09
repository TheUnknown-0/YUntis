package sh.lsx.untisx.data.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class StudyPlanTest {
    private val math = listOf(TimetableElement(id = 2, name = "M"))
    private val german = listOf(TimetableElement(id = 1, name = "D"))
    private val exam = ExamInfo(id = 1, examType = "Klausur", date = 20261113, startTime = 800, endTime = 935, subjectId = 2)

    private fun lesson(date: Int, start: Int, end: Int, subject: List<TimetableElement> = math, code: String? = null) =
        TimetableEntry(id = date + start, date = date, startTime = start, endTime = end, subject = subject, code = code)

    @Test
    fun `countdown counts held lessons of the subject before the exam`() {
        val lessons = listOf(
            lesson(20261109, 800, 845),                    // already over at noon
            lesson(20261110, 800, 845),
            lesson(20261111, 800, 845, code = "cancelled"),
            lesson(20261112, 955, 1040),
            lesson(20261112, 800, 845, subject = german),
            lesson(20261113, 800, 845),                    // the exam itself
        )

        val result = StudyPlan.countdown(exam, LocalDateTime.of(2026, 11, 9, 12, 0), lessons, setOf(20261109))!!

        assertEquals(4L, result.days)
        assertEquals(2, result.lessonsLeft)
        assertTrue(result.lessonsComplete)
        assertFalse(StudyPlan.countdown(exam, LocalDateTime.of(2026, 11, 9, 12, 0), lessons, emptySet())!!.lessonsComplete)
    }

    @Test
    fun `slots prefer free periods, then afternoons, and spread up to the day before`() {
        val lessons = listOf(
            // Tuesday with a free period 9:35 to 10:45
            lesson(20261110, 800, 935, german), lesson(20261110, 1045, 1130, german),
            // Wednesday and Thursday without gaps, school ends 13:25
            lesson(20261111, 800, 1325, german), lesson(20261112, 800, 1325, german),
        )

        val slots = StudyPlan.suggest(exam, LocalDate.of(2026, 11, 10), lessons, emptyList(), sessions = 3)

        assertEquals(listOf(10, 11, 12), slots.map { it.date.dayOfMonth })
        assertEquals(StudySlotKind.FREE_PERIOD, slots[0].kind)
        assertEquals(LocalTime.of(9, 35), slots[0].start)
        assertEquals(StudySlotKind.AFTERNOON, slots[2].kind)
        assertEquals(LocalTime.of(15, 0), slots[2].start)
    }

    @Test
    fun `fewer sessions are spread with the last one closest to the exam`() {
        val slots = StudyPlan.suggest(exam, LocalDate.of(2026, 11, 1), emptyList(), emptyList(), sessions = 2)

        assertEquals(2, slots.size)
        assertEquals(LocalDate.of(2026, 11, 12), slots.last().date)
        assertEquals(StudySlotKind.DAY_OFF, slots.last().kind)
    }
}
