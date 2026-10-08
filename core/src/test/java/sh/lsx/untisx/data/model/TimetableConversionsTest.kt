package sh.lsx.untisx.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimetableConversionsTest {

    @Test
    fun `local date and untis date round trip`() {
        val date = LocalDate.of(2026, 10, 5)

        assertEquals(20261005, date.toUntisDate())
        assertEquals(date, 20261005.toUntisLocalDate())
    }

    @Test
    fun `invalid untis dates yield null`() {
        assertNull(0.toUntisLocalDateOrNull())
        assertNull(20261301.toUntisLocalDateOrNull())
        assertNull(20260230.toUntisLocalDateOrNull())
    }

    @Test
    fun `untis times clamp to valid clock values`() {
        assertEquals(LocalTime.of(8, 5), 805.toUntisLocalTime())
        assertEquals(LocalTime.of(23, 59), 2460.toUntisLocalTime())
        assertEquals(LocalTime.of(0, 0), 0.toUntisLocalTime())
    }

    @Test
    fun `week start is the previous or same monday`() {
        assertEquals(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 1).toUntisWeekStart())
        assertEquals(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28).toUntisWeekStart())
        assertEquals(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4).toUntisWeekStart())
    }

    @Test
    fun `visible week start skips the weekend`() {
        assertEquals(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2).toVisibleWeekStart())
        assertEquals(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3).toVisibleWeekStart())
        assertEquals(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 4).toVisibleWeekStart())
    }

    @Test
    fun `plus untis weeks keeps the week aligned`() {
        assertEquals(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1).plusUntisWeeks(1))
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 4).plusUntisWeeks(-1))
    }
}
