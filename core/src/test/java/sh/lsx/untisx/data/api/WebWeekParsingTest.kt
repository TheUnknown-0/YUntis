package sh.lsx.untisx.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebWeekParsingTest {

    private val sample = """
        {"data":{"result":{"data":{
          "elementIds":[42],
          "elementPeriods":{"42":[
            {"id":1,"date":20261109,"startTime":800,"endTime":845,"cellState":"STANDARD",
             "elements":[{"type":1,"id":42,"orgId":0},{"type":2,"id":7,"orgId":0},{"type":3,"id":30,"orgId":0},{"type":4,"id":50,"orgId":0}]},
            {"id":2,"date":20261109,"startTime":850,"endTime":935,"cellState":"CANCEL",
             "elements":[{"type":3,"id":31,"orgId":0}]},
            {"id":3,"date":20261110,"startTime":955,"endTime":1040,"cellState":"ROOMSUBSTITUTION","substText":"Raumwechsel",
             "elements":[{"type":3,"id":30,"orgId":0},{"type":4,"id":51,"orgId":50}]},
            {"id":4,"date":20261111,"startTime":800,"endTime":845,"cellState":"EXAM","periodText":"Klausur Mathe",
             "elements":[{"type":3,"id":30,"orgId":0}]}
          ]},
          "elements":[
            {"type":1,"id":42,"name":"12","longName":"Klasse 12"},
            {"type":2,"id":7,"name":"Ab","longName":"Abel"},
            {"type":3,"id":30,"name":"MA_L","longName":"Mathematik"},
            {"type":3,"id":31,"name":"DE_G1","longName":"Deutsch"},
            {"type":4,"id":50,"name":"R. 110","longName":"Raum 110"},
            {"type":4,"id":51,"name":"R. 204","longName":"Raum 204"}
          ]
        }}}}
    """.trimIndent()

    @Test
    fun `periods are parsed with names, states and original elements`() {
        val entries = UntisTimetableApi.parseWebWeek(UntisRpc.json.parseToJsonElement(sample), 42)

        assertNotNull(entries)
        entries!!
        assertEquals(listOf(1, 2, 3, 4), entries.map { it.id })

        val first = entries[0]
        assertEquals(20261109, first.date)
        assertEquals(800, first.startTime)
        assertEquals(845, first.endTime)
        assertEquals("12", first.klasse.single().name)
        assertEquals("Ab", first.teacher.single().name)
        assertEquals("MA_L", first.subject.single().name)
        assertEquals("Mathematik", first.subject.single().longName)
        assertEquals("R. 110", first.room.single().name)
        assertNull(first.code)

        assertEquals("cancelled", entries[1].code)

        val moved = entries[2]
        assertEquals("irregular", moved.code)
        assertEquals("R. 204", moved.room.single().name)
        assertEquals(50, moved.room.single().orgId)
        assertEquals("R. 110", moved.room.single().orgName)
        assertEquals("Raumwechsel", moved.substText)

        assertEquals("Klausur Mathe", entries[3].exam?.name)
    }

    @Test
    fun `an answer without timetable data is null and an empty week is empty`() {
        val refused = UntisRpc.json.parseToJsonElement("""{"errorCode":"FORBIDDEN","errorMessage":"no right"}""")
        assertNull(UntisTimetableApi.parseWebWeek(refused, 42))

        val empty = UntisRpc.json.parseToJsonElement("""{"data":{"result":{"data":{"elementPeriods":{},"elements":[]}}}}""")
        assertEquals(emptyList<Any>(), UntisTimetableApi.parseWebWeek(empty, 42))
    }
}
