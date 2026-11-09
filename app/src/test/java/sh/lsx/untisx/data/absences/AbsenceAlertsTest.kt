package sh.lsx.untisx.data.absences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.Absence

class AbsenceAlertsTest {
    private val a = Absence(id = 1, date = 20261109, startTime = 800, endDate = 20261109, endTime = 935)
    private val b = Absence(id = 2, date = 20261110, startTime = 800, endDate = 20261110, endTime = 845)
    private val noId = Absence(id = 0, date = 20261111, startTime = 955, endDate = 20261111, endTime = 1040)

    @Test
    fun `first run only builds the baseline`() {
        assertTrue(AbsenceAlerts.newOnes(listOf(a, b), known = null).isEmpty())
    }

    @Test
    fun `only absences not seen before are new`() {
        val known = setOf(AbsenceAlerts.keyOf(a))
        assertEquals(listOf(b, noId), AbsenceAlerts.newOnes(listOf(a, b, noId), known))
        assertEquals("t:20261111-955-20261111-1040", AbsenceAlerts.keyOf(noId))
    }
}
