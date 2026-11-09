package sh.lsx.untisx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectStatsTest {

    private val math = 2
    private val german = 1

    private fun day(vararg lessons: Pair<Int, String>, event: Boolean = false): DayStats {
        val map = HashMap<Int, SubjectDayStats>()
        for ((id, kind) in lessons) {
            map[id] = (map[id] ?: SubjectDayStats()).add(
                cancelled = kind == "x",
                substituted = kind == "v",
                minutes = 45,
                event = event,
            )
        }
        return DayStats(lessons = lessons.size, subjectDays = map, ruleVersion = 3)
    }

    @Test
    fun `cancel rate only relates to the subject's own lessons`() {
        // 10 math lessons, only 2 take place -> 80 %; plenty of German lessons must not dilute that
        val days = listOf(
            day(math to "x", math to "x", german to "o", german to "o"),
            day(math to "x", math to "x", german to "o", german to "o"),
            day(math to "x", math to "x", german to "o", german to "o"),
            day(math to "x", math to "x", german to "o", german to "o"),
            day(math to "o", math to "o", german to "o", german to "o"),
        )

        val totals = SubjectStats.totals(days, includeEvents = true).associateBy { it.subjectId }
        val m = totals.getValue(math)

        assertEquals(10, m.planned)
        assertEquals(8, m.cancelled)
        assertEquals(2, m.held)
        assertEquals(0.8f, m.cancelRate, 0.0001f)
        assertEquals(8 * 45, m.cancelledMinutes)
        assertEquals(0f, totals.getValue(german).cancelRate, 0.0001f)
    }

    @Test
    fun `held, substitution and cancelled add up to planned`() {
        val totals = SubjectStats.totals(listOf(day(math to "o", math to "v", math to "x", math to "o")), true).single()
        assertEquals(4, totals.planned)
        assertEquals(2, totals.held)
        assertEquals(1, totals.substituted)
        assertEquals(1, totals.cancelled)
        assertEquals(totals.planned, totals.held + totals.substituted + totals.cancelled)
        assertEquals(0.25f, totals.cancelRate, 0.0001f)
    }

    @Test
    fun `lessons during school events can be left out`() {
        val days = listOf(day(math to "o", math to "o"), day(math to "x", math to "x", event = true))
        assertEquals(1f / 2f, SubjectStats.totals(days, includeEvents = true).single().cancelRate, 0.0001f)
        assertEquals(0f, SubjectStats.totals(days, includeEvents = false).single().cancelRate, 0.0001f)
        assertEquals(2, SubjectStats.totals(days, includeEvents = false).single().planned)
    }

    @Test
    fun `old days without subject data are detected`() {
        assertTrue(SubjectStats.hasOldDays(listOf(DayStats(lessons = 5, ruleVersion = 2))))
        assertFalse(SubjectStats.hasOldDays(listOf(day(math to "o"))))
        assertFalse(SubjectStats.hasOldDays(listOf(DayStats(lessons = 0))))
    }
}
