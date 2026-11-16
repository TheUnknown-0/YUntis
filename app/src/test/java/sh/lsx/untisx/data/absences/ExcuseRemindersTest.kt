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
    fun `one school day after Friday is Monday`() {
        assertEquals(LocalDate.of(2026, 11, 9), AbsenceAssistant.deadline(school(20261106), 1))
    }

    @Test
    fun `weekend absences start counting on Monday`() {
        for (date in listOf(20261107, 20261108)) {
            assertEquals(LocalDate.of(2026, 11, 9), AbsenceAssistant.deadline(school(date), 1))
            assertEquals(LocalDate.of(2026, 11, 10), AbsenceAssistant.deadline(school(date), 2))
        }
    }

    @Test
    fun `seven school days skip both intervening weekends`() {
        val friday = LocalDate.of(2026, 11, 6)
        val due = ExcuseReminders.due(listOf(school(20261105)), emptySet(), friday, 7).single()
        assertEquals(LocalDate.of(2026, 11, 16), due.deadline)
        assertEquals(6L, due.daysLeft(friday))
    }

    @Test
    fun `remaining days skip the weekend before a Monday deadline`() {
        val due = ExcuseReminders.due(listOf(school(20261106)), emptySet(), today, 1).single()
        for (day in 6..8) {
            assertEquals(1L, due.daysLeft(LocalDate.of(2026, 11, day)))
        }
        assertEquals(0L, due.daysLeft(today))
        assertEquals(-1L, due.daysLeft(today.plusDays(1)))
    }

    @Test
    fun `Friday deadline stays overdue throughout the weekend`() {
        val due = ExcuseReminders.due(listOf(school(20261105)), emptySet(), today, 1).single()
        assertEquals(LocalDate.of(2026, 11, 6), due.deadline)
        assertEquals(0L, due.daysLeft(LocalDate.of(2026, 11, 6)))
        for (day in 7..9) {
            assertEquals(-1L, due.daysLeft(LocalDate.of(2026, 11, day)))
        }
        assertEquals(-2L, due.daysLeft(LocalDate.of(2026, 11, 10)))
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
