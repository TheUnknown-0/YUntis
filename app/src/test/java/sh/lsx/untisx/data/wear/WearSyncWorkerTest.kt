package sh.lsx.untisx.data.wear

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.ui.timetable.WeekFetch
import java.time.LocalDate

class WearSyncWorkerTest {
    private val monday = LocalDate.of(2026, 10, 5)

    @Test fun `completed weeks are persisted and reused by the next request`() = runTest {
        var cache = TimetableCache.Data()
        var calls = 0

        suspend fun refresh() = refreshWearWeeks(monday, cache, { true }, {
            calls++
            WeekFetch(emptyList(), null, false)
        }, { TimetableCache.merge(cache, it).also { cache = it } }, { 1000L })

        refresh()
        refresh()
        assertEquals(2, calls)
        assertEquals(2, cache.weeks.size)
    }

    @Test fun `first week survives a failure fetching the second`() = runTest {
        var cache = TimetableCache.Data()

        try {
            refreshWearWeeks(monday, cache, { true }, {
                if (it != monday) error("offline")
                WeekFetch(listOf(TimetableEntry(id = 1)), null, false)
            }, { TimetableCache.merge(cache, it).also { cache = it } }, { 1000L })
            fail("expected failure")
        } catch (_: IllegalStateException) { }

        assertEquals(1, cache.weeks["20261005"]?.single()?.id)
    }

    @Test fun `account switch during fetch discards result`() = runTest {
        var active = true
        var saves = 0

        refreshWearWeeks(monday, TimetableCache.Data(), { active }, {
            active = false
            WeekFetch(emptyList(), null, false)
        }, { saves++; it }, { 1000L })

        assertEquals(0, saves)
    }

    @Test fun `refused access is persisted as restricted not known empty`() = runTest {
        var cache = TimetableCache.Data()

        refreshWearWeeks(monday, cache, { true }, { WeekFetch(emptyList(), null, true) }, {
            TimetableCache.merge(cache, it).also { cache = it }
        }, { 1000L })

        assertTrue(cache.weeks.isEmpty())
        assertEquals(14, cache.restrictedDays.size)
    }
}
