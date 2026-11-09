package sh.lsx.untisx.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class RestRefusedDaysTest {

    @Test
    fun `days the school has not released yet are reported`() {
        val json = UntisRpc.json.parseToJsonElement(
            """
            {"days":[
              {"date":"2026-11-12","status":"REGULAR","dayEntries":[],"gridEntries":[{"ids":[1]}]},
              {"date":"2026-11-13","resourceType":"STUDENT","status":"NOT_ALLOWED","dayEntries":[],"gridEntries":[],"backEntries":[]}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("2026-11-13"), UntisTimetableApi.refusedDaysV1(json))
        assertEquals(emptyList<String>(), UntisTimetableApi.refusedDaysV1(null))
    }
}
