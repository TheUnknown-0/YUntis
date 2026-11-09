package sh.lsx.untisx.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSwapsTest {
    private fun entry(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `substituted teacher replaces the removed one`() {
        val swaps = UntisLessonApi.swapsOf(
            entry("""{"teachers":[{"shortName":"WEB","longName":"Weber","status":"REMOVED"},{"shortName":"RIC","longName":"Richter","status":"SUBSTITUTED"}]}"""),
            "teachers",
            "teacher",
        )
        assertEquals(listOf(UntisClient.Swap("teacher", "Richter", "Weber")), swaps)
    }

    @Test
    fun `regular entries and missing fields give no swap`() {
        assertTrue(UntisLessonApi.swapsOf(entry("""{"rooms":[{"shortName":"A101","status":"REGULAR"}]}"""), "rooms", "room").isEmpty())
        assertTrue(UntisLessonApi.swapsOf(entry("""{}"""), "rooms", "room").isEmpty())
    }
}
