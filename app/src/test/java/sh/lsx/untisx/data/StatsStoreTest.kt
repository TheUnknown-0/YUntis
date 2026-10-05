package sh.lsx.untisx.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountId = "https://school.example/school/max"
    private val monday = LocalDate.of(2026, 10, 5)
    private val today = LocalDate.of(2026, 10, 10)
    private val year = SchoolYear(
        id = 0,
        name = "2026/2027",
        startDate = 20260801,
        endDate = 20270731,
    )

    private var nextId = 1

    @Before
    fun setUp() {
        StatsStore.clear(context, accountId)
        nextId = 1
    }

    @Test
    fun `event overlapping lessons are tracked separately`() = runBlocking {
        val entries = listOf(
            event(20261005, start = 800, end = 1300),
            lesson(20261005, start = 800, end = 845, code = "cancelled"),
            lesson(20261005, start = 1400, end = 1445),
        )

        val day = recordAndGetDay(entries, 20261005)

        assertEquals(2, day.lessons)
        assertEquals(1, day.cancelled)
        assertEquals(0, day.changes)
        assertEquals(1, day.eventLessons)
        assertEquals(1, day.eventCancelled)
        assertEquals(0, day.eventChanges)
        assertEquals(1, day.events)
        assertEquals(mapOf(3 to 1), day.subjectCounts)
        assertTrue(day.eventSubjectCounts.isEmpty())
        assertEquals(StatsStore.RULE_VERSION, day.ruleVersion)
    }

    @Test
    fun `an all day event overlaps every lesson of the day`() = runBlocking {
        val entries = listOf(
            event(20261006, start = 0, end = 0),
            lesson(20261006, start = 800, end = 845),
            lesson(20261006, start = 1400, end = 1445, code = "cancelled"),
        )

        val day = recordAndGetDay(entries, 20261006)

        assertEquals(2, day.lessons)
        assertEquals(1, day.cancelled)
        assertEquals(2, day.eventLessons)
        assertEquals(1, day.eventCancelled)
        assertEquals(mapOf(3 to 1), day.subjectCounts)
        assertEquals(mapOf(3 to 1), day.eventSubjectCounts)
        assertEquals(mapOf(2 to 1), day.eventTeacherCounts)
        assertEquals(mapOf(4 to 1), day.eventRoomCounts)
    }

    @Test
    fun `a lesson touching the end of an event does not overlap`() = runBlocking {
        val entries = listOf(
            event(20261007, start = 800, end = 1000),
            lesson(20261007, start = 1000, end = 1045, code = "cancelled"),
        )

        val day = recordAndGetDay(entries, 20261007)

        assertEquals(1, day.lessons)
        assertEquals(1, day.cancelled)
        assertEquals(0, day.eventLessons)
        assertTrue(day.subjectCounts.isEmpty())
        assertTrue(day.eventSubjectCounts.isEmpty())
    }

    @Test
    fun `cancelled lessons are not part of the subject counts`() = runBlocking {
        val entries = listOf(
            lesson(20261008, start = 800, end = 845, code = "cancelled"),
            lesson(20261008, start = 955, end = 1040),
        )

        val day = recordAndGetDay(entries, 20261008)

        assertEquals(2, day.lessons)
        assertEquals(1, day.cancelled)
        assertEquals(mapOf(3 to 1), day.subjectCounts)
    }

    @Test
    fun `legacy days are queued for the rule recheck`() {
        writeLegacyStats()

        val counted = StatsStore.countedDays(StatsStore.load(context, accountId), year)

        assertFalse(20261007 in counted)
        assertFalse(20261008 in counted)
    }

    @Test
    fun `recording the week marks the legacy day as checked`() = runBlocking {
        writeLegacyStats()

        val data = StatsStore.recordWeek(
            context = context,
            accountId = accountId,
            year = year,
            weekStart = monday,
            entries = listOf(lesson(20261007, start = 800, end = 845)),
            today = today,
        )

        val day = StatsStore.currentYear(data, year)?.days?.get("20261007")

        assertTrue(20261007 in StatsStore.countedDays(data, year))
        assertEquals(StatsStore.RULE_VERSION, requireNotNull(day).ruleVersion)
    }

    private suspend fun recordAndGetDay(
        entries: List<TimetableEntry>,
        date: Int,
    ): DayStats {
        val data = StatsStore.recordWeek(
            context = context,
            accountId = accountId,
            year = year,
            weekStart = monday,
            entries = entries,
            today = today,
        )

        return requireNotNull(StatsStore.currentYear(data, year)?.days?.get(date.toString()))
    }

    private fun writeLegacyStats() {
        accountFile(context, "stats_", accountId).writeText(
            """
            {
              "years": {
                "20260801": {
                  "name": "2026/2027",
                  "startDate": 20260801,
                  "endDate": 20270731,
                  "days": {
                    "20261007": {"lessons": 2, "cancelled": 1, "events": 1},
                    "20261008": {"lessons": 3, "events": 0}
                  },
                  "lastUpdated": 0
                }
              },
              "version": 2
            }
            """.trimIndent(),
        )
    }

    private fun lesson(
        date: Int,
        start: Int,
        end: Int,
        code: String? = null,
    ) = TimetableEntry(
        id = nextId++,
        date = date,
        startTime = start,
        endTime = end,
        klasse = listOf(TimetableElement(id = 1, name = "10a")),
        teacher = listOf(TimetableElement(id = 2, name = "Muster")),
        subject = listOf(TimetableElement(id = 3, name = "Mathe")),
        room = listOf(TimetableElement(id = 4, name = "R101")),
        code = code,
    )

    private fun event(
        date: Int,
        start: Int,
        end: Int,
    ) = TimetableEntry(
        id = nextId++,
        date = date,
        startTime = start,
        endTime = end,
        lstext = "Klassenfahrt",
    )
}
