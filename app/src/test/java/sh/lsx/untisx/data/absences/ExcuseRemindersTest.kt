package sh.lsx.untisx.data.absences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExcuseRemindersTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun school(date: Int, excused: Boolean = false) =
        AbsenceItem(date = date, startTime = 800, endTime = 935, reason = "", excused = excused, status = "")

    private fun own(id: String, date: Int, excused: Boolean = false) = AbsenceItem.of(OwnAbsence(id = id, date = date, excused = excused))

    @Test
    fun `reminds from the day after the absence on`() {
        val due = ExcuseReminders.due(listOf(school(20261109), school(20261108)), emptySet(), today, 7)
        assertEquals(listOf(20261108), due.map { it.item.date })
        assertEquals(6L, due.single().daysLeft(today))
    }

    @Test
    fun `excused and handed in absences are left out`() {
        val items = listOf(school(20261102, excused = true), own("a", 20261103), own("b", 20261104, excused = true), school(20261105))
        val handed = setOf(ExcuseReminders.keyOf(items[3]))
        val due = ExcuseReminders.due(items, handed, today, 7)
        assertEquals(listOf("o:a"), due.map { it.key })
    }

    @Test
    fun `overdue ones keep coming, very old ones stop`() {
        val due = ExcuseReminders.due(listOf(school(20261020), school(20260801)), emptySet(), today, 7)
        assertEquals(listOf(20261020), due.map { it.item.date })
        assertTrue(due.single().daysLeft(today)!! < 0)
    }
}
