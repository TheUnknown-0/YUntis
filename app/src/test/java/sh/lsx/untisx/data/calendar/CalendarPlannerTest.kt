package sh.lsx.untisx.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CalendarPlannerTest {
    private val day = 24 * 60 * 60 * 1000L
    private val today = LocalDateTime.of(2026, 11, 9, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun item(key: String, title: String = "T", daysAhead: Long = 1) = CalendarItem(
        key = "acc:$key",
        category = CalendarCategory.EXAMS,
        title = title,
        description = "",
        location = "",
        allDay = false,
        start = LocalDateTime.of(2026, 11, 9, 8, 0).plusDays(daysAhead),
        end = LocalDateTime.of(2026, 11, 9, 9, 0).plusDays(daysAhead),
    )

    private fun millis(item: CalendarItem) = item.start.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun mapped(item: CalendarItem, id: Long, calendar: Long = 1, fingerprint: String = item.fingerprint) =
        MappedEvent(id, fingerprint, millis(item), calendar)

    private fun plan(items: List<CalendarItem>, mapping: Map<String, MappedEvent>, exists: (Long) -> Boolean = { true }) =
        CalendarPlanner.plan("acc", items, mapping, calendarId = 1, todayStartMillis = today, startMillis = ::millis, exists = exists)

    @Test
    fun `new items are inserted, unchanged ones left alone, changed ones updated`() {
        val same = item("a")
        val changed = item("b", title = "neu")
        val fresh = item("c")

        val result = plan(
            listOf(same, changed, fresh),
            mapOf(same.key to mapped(same, 10), changed.key to mapped(changed, 11, fingerprint = "alt")),
        )

        assertEquals(listOf(fresh), result.inserts)
        assertEquals(listOf(11L to changed), result.updates)
        assertTrue(result.deletes.isEmpty())
    }

    @Test
    fun `future entries that disappeared are deleted, past ones stay in the calendar`() {
        val future = item("gone", daysAhead = 3)
        val past = item("old", daysAhead = -3)
        val ancient = item("ancient", daysAhead = -40)

        val result = plan(
            emptyList(),
            mapOf(future.key to mapped(future, 20), past.key to mapped(past, 21), ancient.key to mapped(ancient, 22)),
        )

        assertEquals(listOf(20L), result.deletes)
        assertEquals(setOf(future.key, ancient.key), result.dropKeys.toSet())
    }

    @Test
    fun `a new target calendar moves every entry`() {
        val a = item("a")
        val result = plan(listOf(a), mapOf(a.key to mapped(a, 30, calendar = 2)))

        assertEquals(listOf(30L), result.deletes)
        assertEquals(listOf(a), result.inserts)
    }

    @Test
    fun `an entry the user deleted by hand is written again`() {
        val a = item("a")
        val result = plan(listOf(a), mapOf(a.key to mapped(a, 40)), exists = { false })

        assertEquals(listOf(a), result.inserts)
        assertTrue(result.deletes.isEmpty())
    }

    @Test
    fun `other accounts are not touched`() {
        val other = item("x").copy(key = "other:x")
        val result = plan(emptyList(), mapOf(other.key to mapped(other, 50)))

        assertTrue(result.deletes.isEmpty())
        assertTrue(result.dropKeys.isEmpty())
    }
}
