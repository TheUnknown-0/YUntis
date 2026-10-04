package sh.lsx.untisx.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationTimesTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, berlin)

    @Test
    fun `07_00 stays 07_00 on the spring forward day`() {
        val now = at(2026, 3, 28, 18, 0)
        val next = NotificationTimes.nextOccurrence(7 * 60, now)

        assertEquals(LocalDateTime.of(2026, 3, 29, 7, 0), next.toLocalDateTime())
        assertEquals(berlin, next.zone)
    }

    @Test
    fun `07_00 stays 07_00 on the fall back day`() {
        val now = at(2026, 10, 24, 18, 0)
        val next = NotificationTimes.nextOccurrence(7 * 60, now)

        assertEquals(LocalDateTime.of(2026, 10, 25, 7, 0), next.toLocalDateTime())
    }

    @Test
    fun `07_00 stays 07_00 on a normal day`() {
        val now = at(2026, 10, 4, 12, 0)
        val next = NotificationTimes.nextOccurrence(7 * 60, now)

        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), next.toLocalDateTime())
    }

    @Test
    fun `time inside the spring forward gap moves forward instead of crashing`() {
        val now = at(2026, 3, 28, 18, 0)
        val next = NotificationTimes.nextOccurrence(2 * 60 + 30, now)

        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun `passed time rolls over to tomorrow`() {
        val now = at(2026, 10, 4, 8, 0)
        val next = NotificationTimes.nextOccurrence(7 * 60, now)

        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), next.toLocalDateTime())
    }

    @Test
    fun `later today is kept`() {
        val now = at(2026, 10, 4, 6, 30)
        val next = NotificationTimes.nextOccurrence(7 * 60, now)

        assertEquals(LocalDateTime.of(2026, 10, 4, 7, 0), next.toLocalDateTime())
        assertTrue(next.isAfter(now))
    }

    @Test
    fun `overview before noon targets today`() {
        val today = LocalDate.of(2026, 10, 4)

        assertEquals(today, NotificationTimes.overviewTargetDate(today, 11 * 60 + 59))
    }

    @Test
    fun `overview at noon targets tomorrow`() {
        val today = LocalDate.of(2026, 10, 4)

        assertEquals(today.plusDays(1), NotificationTimes.overviewTargetDate(today, 12 * 60))
    }

    @Test
    fun `minutesOf converts untis times`() {
        assertEquals(485, NotificationTimes.minutesOf(805))
        assertEquals(0, NotificationTimes.minutesOf(0))
    }

    @Test
    fun `lesson span covers the earliest start and latest end of today`() {
        val entries = listOf(
            lesson(start = 800, end = 845),
            lesson(start = 955, end = 1040),
        )

        assertTrue(NotificationTimes.isWithinLessonSpan(entries, today = DATE, nowMinute = 480))
        assertTrue(NotificationTimes.isWithinLessonSpan(entries, today = DATE, nowMinute = 540))
        assertTrue(NotificationTimes.isWithinLessonSpan(entries, today = DATE, nowMinute = 640))
        assertFalse(NotificationTimes.isWithinLessonSpan(entries, today = DATE, nowMinute = 479))
        assertFalse(NotificationTimes.isWithinLessonSpan(entries, today = DATE, nowMinute = 641))
    }

    @Test
    fun `lesson span ignores cancelled, subjectless and other day entries`() {
        val entries = listOf(
            lesson(start = 700, end = 800, code = "cancelled"),
            lesson(start = 700, end = 800, subject = false),
            lesson(date = DATE + 1, start = 700, end = 800),
        )

        assertFalse(NotificationTimes.isWithinLessonSpan(entries, today = DATE, nowMinute = 450))
        assertFalse(NotificationTimes.isWithinLessonSpan(emptyList(), today = DATE, nowMinute = 450))
    }

    private fun lesson(
        date: Int = DATE,
        start: Int,
        end: Int,
        code: String? = null,
        subject: Boolean = true,
    ) = TimetableEntry(
        date = date,
        startTime = start,
        endTime = end,
        subject = if (subject) listOf(TimetableElement(id = 1, name = "Mathe")) else emptyList(),
        code = code,
    )

    private companion object {
        const val DATE = 20261005
    }
}
