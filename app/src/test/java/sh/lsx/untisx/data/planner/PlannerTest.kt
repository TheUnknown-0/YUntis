package sh.lsx.untisx.data.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.ui.timetable.freeDurationText
import sh.lsx.untisx.ui.timetable.freeGaps
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlannerTest {
    private val m = listOf(TimetableElement(id = 2, name = "M"))

    private fun lesson(date: Int, start: Int, end: Int, code: String? = null) =
        TimetableEntry(id = start, date = date, startTime = start, endTime = end, subject = m, code = code)

    @Test
    fun `free gaps ignore normal breaks and count cancelled lessons as free`() {
        val day = listOf(
            lesson(20261110, 800, 845),
            lesson(20261110, 850, 935),
            lesson(20261110, 955, 1040, code = "cancelled"),
            lesson(20261110, 1045, 1130),
        )

        val gaps = freeGaps(day)

        assertEquals(1, gaps.size)
        assertEquals(935, gaps[0].start)
        assertEquals(1045, gaps[0].end)
        assertEquals("1 h 10", freeDurationText(gaps[0].minutes))
        assertEquals("45 min", freeDurationText(45))
        assertEquals("2 h", freeDurationText(120))
    }

    @Test
    fun `late start and early end are not gaps`() {
        val day = listOf(lesson(20261110, 800, 845, code = "cancelled"), lesson(20261110, 955, 1040))
        assertTrue(freeGaps(day).isEmpty())
    }

    @Test
    fun `commute uses the first lesson that takes place`() {
        val plan = Commute.plan(
            listOf(lesson(20261110, 800, 845, "cancelled"), lesson(20261110, 850, 935, "cancelled"), lesson(20261110, 955, 1040)),
            wayMinutes = 30,
            prepMinutes = 60,
        )!!

        assertTrue(plan.startsLater)
        assertEquals(LocalTime.of(9, 25), plan.leaveAt)
        assertEquals(LocalTime.of(8, 25), plan.wakeAt)
        assertFalse(plan.endsEarlier)
    }

    @Test
    fun `commute notices a fully cancelled day and a free day`() {
        val plan = Commute.plan(listOf(lesson(20261110, 800, 845, "cancelled")), 30, 60)!!
        assertTrue(plan.allCancelled)
        assertNull(plan.leaveAt)
        assertNull(Commute.plan(emptyList(), 30, 60))
    }

    @Test
    fun `next school week starts on the coming monday`() {
        assertEquals(LocalDate.of(2026, 11, 16) to LocalDate.of(2026, 11, 20), nextSchoolWeek(LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 16), nextSchoolWeek(LocalDate.of(2026, 11, 13)).first)
        assertEquals(LocalDate.of(2026, 11, 16), nextSchoolWeek(LocalDate.of(2026, 11, 9)).first)
    }

    @Test
    fun `week preview lists changes per day, exams and a fallback line`() {
        val lines = WeekPreview.lines(
            from = LocalDate.of(2026, 11, 16),
            to = LocalDate.of(2026, 11, 20),
            lessons = listOf(lesson(20261116, 800, 845, "cancelled"), lesson(20261117, 800, 845, "irregular"), lesson(20261118, 800, 845)),
            exams = listOf(ExamInfo(id = 1, examType = "Klausur", date = 20261119, subjectId = 2)),
            events = emptyList(),
            holidays = emptyList(),
            homework = emptyList(),
            subjects = mapOf(2 to MasterDataItem(id = 2, name = "M")),
        )

        assertEquals(listOf("Mo: Entfall M", "Tu: Vertretung M", "Klausuren: Th Klausur M"), lines)

        val empty = WeekPreview.lines(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        assertEquals(listOf(PlannerWords().nothing), empty)
    }

    @Test
    fun `weekly trigger is the next chosen weekday at the chosen time`() {
        val zone = ZoneId.of("Europe/Berlin")
        val monday = ZonedDateTime.of(2026, 11, 9, 12, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 11, 15, 18, 0, 0, 0, zone),
            PlannerScheduler.nextWeekTrigger(monday, DayOfWeek.SUNDAY, 18 * 60),
        )

        val sundayLate = ZonedDateTime.of(2026, 11, 15, 19, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 22, 18, 0, 0, 0, zone),
            PlannerScheduler.nextWeekTrigger(sundayLate, DayOfWeek.SUNDAY, 18 * 60),
        )
    }
}
