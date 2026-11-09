package sh.lsx.untisx.data.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroRulesTest {
    @Test
    fun `a study block gets as many rounds as fit`() {
        assertEquals(3, PomodoroRules.roundsFor(105, 25, 5))
        assertEquals(1, PomodoroRules.roundsFor(20, 25, 5))
        assertEquals(8, PomodoroRules.roundsFor(600, 25, 5))
    }

    @Test
    fun `study, break, study and done after the last round`() {
        var s = PomodoroRules.start("M", "e", "s", 25, 5, 2, now = 0)
        assertEquals(PomodoroPhase.WORK, s.phase)
        assertEquals(25 * 60_000L, s.endsAt)

        s = PomodoroRules.next(s, now = 1000)
        assertEquals(PomodoroPhase.BREAK, s.phase)
        assertEquals(1000 + 5 * 60_000L, s.endsAt)

        s = PomodoroRules.next(s, now = 2000)
        assertEquals(PomodoroPhase.WORK, s.phase)
        assertEquals(2, s.round)

        s = PomodoroRules.next(s, now = 3000)
        assertEquals(PomodoroPhase.DONE, s.phase)
    }

    @Test
    fun `pausing keeps the time left`() {
        val s = PomodoroRules.start("M", "", "", 25, 5, 1, now = 0)
        val paused = PomodoroRules.pause(s, now = 60_000)
        assertTrue(paused.paused)
        assertEquals(24 * 60_000L, paused.remaining(now = 999_999))

        val resumed = PomodoroRules.resume(paused, now = 100_000)
        assertEquals(100_000 + 24 * 60_000L, resumed.endsAt)
    }
}
