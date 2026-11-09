package sh.lsx.untisx.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsencesParsingTest {

    @Test
    fun `absences are parsed with times, reason and excuse`() {
        val json = UntisRpc.json.parseToJsonElement(
            """
            {"absences":[
              {"id":2,"startDateTime":"2026-10-12T09:55","endDateTime":"2026-10-12T11:30","absenceReason":"Arzt","excused":false,"text":""},
              {"id":1,"startDateTime":"2026-10-05T08:00","endDateTime":"2026-10-05T13:25","absenceReason":"Krankheit","excused":true,
               "excuse":{"excuseStatus":"entsch.","excused":true}}
            ]}
            """.trimIndent(),
        )

        val absences = UntisAbsencesApi.parseAbsences(json)

        assertEquals(listOf(1, 2), absences.map { it.id })
        val first = absences[0]
        assertEquals(20261005, first.date)
        assertEquals(800, first.startTime)
        assertEquals(1325, first.endTime)
        assertEquals(325, first.minutes)
        assertTrue(first.excused)
        assertEquals("entsch.", first.excuseStatus)
        assertFalse(absences[1].excused)
        assertEquals("Arzt", absences[1].reason)
    }

    @Test
    fun `empty or unexpected results give no absences`() {
        assertEquals(0, UntisAbsencesApi.parseAbsences(UntisRpc.json.parseToJsonElement("{}")).size)
        assertEquals(0, UntisAbsencesApi.parseAbsences(UntisRpc.json.parseToJsonElement("""{"absences":[{"id":1}]}""")).size)
    }
}
