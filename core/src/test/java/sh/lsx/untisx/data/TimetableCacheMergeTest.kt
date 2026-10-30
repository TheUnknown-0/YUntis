package sh.lsx.untisx.data

import org.junit.Assert.*
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableEntry

class TimetableCacheMergeTest {
    @Test fun `late phone snapshot retains newer watch week and its restrictions`() {
        val newer = TimetableCache.Data(
            weeks = mapOf("20261005" to listOf(TimetableEntry(id = 2))),
            weekFetchedAt = mapOf("20261005" to 200L), restrictedDays = setOf(20261006),
        )
        val older = newer.copy(weeks = mapOf("20261005" to listOf(TimetableEntry(id = 1))),
            weekFetchedAt = mapOf("20261005" to 100L), restrictedDays = emptySet())
        val merged = TimetableCache.merge(newer, older)

        assertEquals(2, merged.weeks["20261005"]?.single()?.id)
        assertEquals(setOf(20261006), merged.restrictedDays)
    }

    @Test fun `week patch preserves other weeks and newer auxiliary data`() {
        val previous = TimetableCache.Data(weeks = mapOf("20261005" to emptyList()),
            weekFetchedAt = mapOf("20261005" to 200L),
            messagesFetchedAt = 100L, restrictedDays = setOf(20261006))
        val patch = TimetableCache.Data(weeks = mapOf("20261012" to emptyList()),
            weekFetchedAt = mapOf("20261012" to 300L))
        val merged = TimetableCache.merge(previous, patch)

        assertEquals(2, merged.weeks.size)
        assertEquals(100L, merged.messagesFetchedAt)
        assertEquals(setOf(20261006), merged.restrictedDays)
    }

    @Test fun `new successful empty week clears old entries and restrictions`() {
        val old = TimetableCache.Data(weeks = mapOf("20261005" to listOf(TimetableEntry(id = 1))),
            weekFetchedAt = mapOf("20261005" to 100L), restrictedDays = setOf(20261006))
        val fresh = TimetableCache.Data(weeks = mapOf("20261005" to emptyList()),
            weekFetchedAt = mapOf("20261005" to 200L))
        val merged = TimetableCache.merge(old, fresh)

        assertTrue(merged.weeks["20261005"]!!.isEmpty())
        assertTrue(merged.restrictedDays.isEmpty())
    }
}
