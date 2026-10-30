package sh.lsx.untisx.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.PeriodExam
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.wear.WearTimetablePayload
import java.time.LocalDate

class WearDaysTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val monday = today.minusDays(2)

    private fun entry(date: LocalDate, start: Int) = TimetableEntry(
        id = start,
        date = date.toUntisDate(),
        startTime = start,
        endTime = start + 45,
    )

    private fun payload(
        weeks: Map<String, List<TimetableEntry>>,
        holidays: List<Holiday> = emptyList(),
        restricted: Set<Int> = emptySet(),
    ) = WearTimetablePayload(weeks = weeks, holidays = holidays, restrictedDays = restricted)

    @Test
    fun `skips empty days but keeps today and holidays`() {
        val data = payload(
            weeks = mapOf(
                monday.toUntisDate().toString() to listOf(
                    entry(monday, 800),
                    entry(today.plusDays(1), 1000),
                    entry(today.plusDays(1), 900),
                ),
            ),
            holidays = listOf(
                Holiday(
                    id = 1,
                    name = "Herbstferien",
                    startDate = today.plusDays(2).toUntisDate(),
                    endDate = today.plusDays(4).toUntisDate(),
                ),
            ),
        )

        val days = buildDays(data, today)

        assertEquals(
            listOf(monday, today, today.plusDays(1), today.plusDays(2), today.plusDays(3), today.plusDays(4)),
            days.map { it.date },
        )

        val thursday = days.first { it.date == today.plusDays(1) }
        assertEquals(listOf(900, 1000), thursday.entries.map { it.startTime })

        val wednesday = days.first { it.date == today }
        assertTrue(wednesday.entries.isEmpty())

        val friday = days.first { it.date == today.plusDays(2) }
        assertEquals("Herbstferien", friday.holiday?.name)
    }

    @Test
    fun `keeps restricted days`() {
        val data = payload(
            weeks = mapOf(monday.toUntisDate().toString() to listOf(entry(monday, 800))),
            restricted = setOf(today.minusDays(1).toUntisDate()),
        )

        val days = buildDays(data, today)

        val tuesday = days.first { it.date == today.minusDays(1) }
        assertTrue(tuesday.restricted)
    }

    @Test fun `exam is shown only on its lesson while normal events remain`() {
        val exam = PeriodExam(id = 42, name = "Math test")
        val lesson = entry(today, 800).copy(subject = listOf(TimetableElement(id = 7, name = "MA")), exam = exam)
        val examEvent = entry(today, 800).copy(id = 42, exam = exam, info = "Math test")
        val normalEvent = entry(today, 1000).copy(info = "School assembly")
        val data = payload(mapOf(monday.toUntisDate().toString() to listOf(lesson, examEvent, normalEvent)))

        val day = buildDays(data, today).single()
        assertEquals(listOf(lesson, normalEvent), day.entries)
        assertEquals(exam, day.entries.first().exam)
    }

    @Test
    fun `today without a loaded week is unknown not free`() {
        val day = buildDays(payload(emptyMap()), today).single()
        assertEquals(today, day.date)
        assertTrue(!day.loaded)
    }

    @Test fun `today outside old cache stays unknown`() {
        val day = buildDays(payload(mapOf("20260928" to emptyList())), today).last()
        assertEquals(today, day.date)
        assertTrue(!day.loaded)
    }

    @Test fun `known empty today is loaded`() {
        assertTrue(buildDays(payload(mapOf("20261005" to emptyList())), today).single().loaded)
    }

    @Test fun `restricted today is distinguished without any loaded weeks`() {
        val day = buildDays(payload(emptyMap(), restricted = setOf(today.toUntisDate())), today).single()
        assertTrue(day.restricted)
        assertTrue(!day.loaded)
    }
}
