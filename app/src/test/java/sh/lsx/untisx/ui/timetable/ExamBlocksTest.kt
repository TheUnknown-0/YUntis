package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class ExamBlocksTest {

    private val subjects = listOf(
        MasterDataItem(id = 1, name = "MA_L", longName = "Mathematik"),
        MasterDataItem(id = 2, name = "STUB_Z1"),
        MasterDataItem(id = 3, name = "DE_G3", longName = "Deutsch"),
        MasterDataItem(id = 4, name = "GE_G4", longName = "Geschichte"),
    ).associateBy { it.id }

    private fun lesson(subject: Int, start: Int, end: Int) = TimetableEntry(
        id = subject * 100 + start,
        date = 20261012,
        startTime = start,
        endTime = end,
        subject = listOf(TimetableElement(id = subject)),
    )

    private val day = listOf(
        lesson(2, 800, 845),
        lesson(2, 850, 935),
        lesson(3, 955, 1125),
        lesson(1, 1140, 1325),
        lesson(4, 1345, 1430),
    )

    private val blockExam = ExamInfo(
        id = 77,
        name = "Klausur LK-A Abilänge",
        text = "Klausur LK-A Abilänge Eng; Ge; Ma",
        date = 20261012,
        startTime = 800,
        endTime = 1315,
        roomIds = listOf(9, 9),
    )

    @Test
    fun `the subject of a joint exam comes from the own lesson named in its text`() {
        // GE is named too, but its lesson lies after the exam
        assertEquals(1, examSubject(blockExam, day, subjects)?.id)
    }

    @Test
    fun `an ambiguous or unnamed exam has no subject`() {
        assertNull(examSubject(blockExam.copy(text = "Eng; Ge; Ma; De", name = ""), day, subjects))
        assertNull(examSubject(blockExam.copy(text = "Klausur", name = ""), day, subjects))
    }

    @Test
    fun `a block exam over other subjects becomes one card`() {
        val result = withExamBlocks(day, listOf(blockExam), subjects)
        val block = result - day.toSet()

        assertEquals(1, block.size)
        val card = block.single()
        assertEquals(800, card.startTime)
        assertEquals(1315, card.endTime)
        assertEquals(1, card.subject.single().id)
        assertEquals(listOf(9), card.room.map { it.id })

        // the card hides the planned lessons it covers, the lesson after it stays visible
        val stacks = stackParallelLessons(result.map { LessonDisplay(it, subject = "${it.subject.single().id}", rooms = "", teachers = "") })
        assertEquals(listOf(card.id, 400 + 1345), stacks.map { it.shown.entry.id })
        assertEquals(4, stacks.first().hidden.size)
    }

    @Test
    fun `an exam inside its own subject's lessons adds no card`() {
        val maths = ExamInfo(id = 5, subjectId = 1, date = 20261012, startTime = 1140, endTime = 1325)

        assertEquals(day, withExamBlocks(day, listOf(maths), subjects))
    }
}
