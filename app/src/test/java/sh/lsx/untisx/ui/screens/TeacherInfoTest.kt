package sh.lsx.untisx.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class TeacherInfoTest {

    private fun lesson(id: Int, date: Int, start: Int, subject: Int, teacher: TimetableElement) = TimetableEntry(
        id = id,
        date = date,
        startTime = start,
        endTime = start + 45,
        subject = listOf(TimetableElement(id = subject)),
        teacher = listOf(teacher),
    )

    @Test
    fun `subjects come from regular lessons and the next lessons are listed in order`() {
        val own = TimetableElement(id = 7)
        val substitute = TimetableElement(id = 7, orgId = 9)
        val other = TimetableElement(id = 9)

        val entries = listOf(
            lesson(1, 20261109, 800, subject = 1, teacher = own),
            lesson(2, 20261110, 800, subject = 1, teacher = own),
            lesson(3, 20261111, 955, subject = 2, teacher = substitute),
            lesson(4, 20261112, 800, subject = 3, teacher = other),
            lesson(5, 20261113, 800, subject = 1, teacher = own),
        )

        val info = teacherInfo(7, entries, nowDate = 20261110, nowTime = 900)

        assertEquals(listOf(1), info.subjectIds)
        assertEquals(listOf(3, 5), info.upcoming.map { it.id })
    }
}
