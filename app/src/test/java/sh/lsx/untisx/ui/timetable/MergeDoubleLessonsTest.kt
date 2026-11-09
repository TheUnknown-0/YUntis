package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class MergeDoubleLessonsTest {
    private val math = listOf(TimetableElement(id = 2, name = "M"))
    private val history = listOf(TimetableElement(id = 7, name = "G"))
    private val teacher = listOf(TimetableElement(id = 1, name = "MÜL"))
    private val room = listOf(TimetableElement(id = 5, name = "A101"))

    private fun lesson(start: Int, end: Int, subject: List<TimetableElement> = math, code: String? = null) =
        TimetableEntry(id = start, date = 20261109, startTime = start, endTime = end, subject = subject, teacher = teacher, room = room, code = code)

    @Test
    fun `same course with a five minute break becomes one block`() {
        val merged = mergeConsecutiveLessons(listOf(lesson(800, 845), lesson(850, 935)))
        assertEquals(listOf(800 to 935), merged.map { it.startTime to it.endTime })
    }

    @Test
    fun `a longer break, another status or another subject stays separate`() {
        assertEquals(2, mergeConsecutiveLessons(listOf(lesson(935, 940), lesson(955, 1040))).size)
        assertEquals(2, mergeConsecutiveLessons(listOf(lesson(800, 845), lesson(850, 935, code = "cancelled"))).size)
        assertEquals(2, mergeConsecutiveLessons(listOf(lesson(800, 845), lesson(850, 935, subject = history))).size)
    }

    @Test
    fun `parallel courses in the same slots are merged per course`() {
        val merged = mergeConsecutiveLessons(
            listOf(lesson(800, 845), lesson(800, 845, history), lesson(850, 935), lesson(850, 935, history)),
        )
        assertEquals(listOf(800 to 935, 800 to 935), merged.map { it.startTime to it.endTime })
        assertEquals(setOf(2, 7), merged.map { it.subject.first().id }.toSet())
    }
}
