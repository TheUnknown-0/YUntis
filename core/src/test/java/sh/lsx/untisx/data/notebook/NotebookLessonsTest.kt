package sh.lsx.untisx.data.notebook

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.Holiday

class NotebookLessonsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val math = SubjectRef(ids = listOf(1), name = "Math")
    private val english = SubjectRef(ids = listOf(2), name = "English")

    private val firstMath = entry(id = 1, startTime = 800, endTime = 900, subjectId = 1, subjectName = "Math")
    private val secondMath = entry(id = 2, startTime = 900, endTime = 1000, subjectId = 1, subjectName = "Math")
    private val englishLesson = entry(id = 3, startTime = 1000, endTime = 1100, subjectId = 2, subjectName = "English")
    private val cancelled = entry(id = 4, startTime = 1100, endTime = 1200, code = "cancelled")
    private val event = entry(id = 5, startTime = 1200, endTime = 1300, subjectId = 0)
    private val holidayLesson = entry(id = 6, date = 20260106, startTime = 800, endTime = 900)
    private val nextDayMath = entry(id = 7, date = 20260107, startTime = 800, endTime = 900)

    private val cache = NotebookLessonCache(
        weeks = mapOf(
            20260105 to listOf(firstMath, secondMath, englishLesson, cancelled, event),
            20260106 to listOf(holidayLesson),
            20260107 to listOf(nextDayMath),
        ),
        holidays = listOf(Holiday(id = 1, startDate = 20260106, endDate = 20260106)),
    )

    @Test
    fun `blocks skip cancelled events and holidays and merge double lessons`() {
        val blocks = NotebookLessonResolver.blocks(cache)

        assertEquals(3, blocks.size)
        assertEquals(800, blocks[0].startTime)
        assertEquals(1000, blocks[0].endTime)
        assertEquals(1, blocks[0].entry.id)
        assertEquals(1000, blocks[1].startTime)
        assertEquals(20260107, blocks[2].date)
    }

    @Test
    fun `next lesson filters by subject`() {
        val now = ZonedDateTime.of(2026, 1, 5, 7, 0, 0, 0, zone)
        val first = NotebookLessonResolver.nextBlock(cache, now, subject = math)
        assertEquals(800, first?.startTime ?: -1)
        assertEquals(20260105, first?.date ?: -1)

        val afterFirst = NotebookLessonResolver.nextBlock(
            cache,
            now,
            after = LessonKey(entryId = 1, date = 20260105, startTime = 800),
            subject = math,
        )
        assertEquals(20260107, afterFirst?.date ?: -1)
        assertEquals(800, afterFirst?.startTime ?: -1)
    }

    @Test
    fun `next lesson infers the origin subject when not passed explicitly`() {
        val now = ZonedDateTime.of(2026, 1, 5, 7, 0, 0, 0, zone)
        val next = NotebookLessonResolver.nextBlock(
            cache,
            now,
            after = LessonKey(entryId = 1, date = 20260105, startTime = 800),
        )

        assertEquals(20260107, next?.date)
        assertEquals("Math", next?.subject?.name ?: "")
    }

    @Test
    fun `next lesson returns null when exhausted`() {
        val now = ZonedDateTime.of(2026, 1, 8, 7, 0, 0, 0, zone)
        assertNull(NotebookLessonResolver.nextBlock(cache, now, subject = math))
    }

    @Test
    fun `blockAt understands merged double lessons`() {
        val block = NotebookLessonResolver.blockAt(
            cache,
            LessonKey(entryId = 0, date = 20260105, startTime = 950),
        )

        assertEquals(800, block?.startTime ?: -1)
        assertEquals(1000, block?.endTime ?: -1)
    }

    @Test
    fun `resolve finds exact lessons and preserves missing snapshots`() {
        val exact = lessonRef(entryId = 1, date = 20260105, startTime = 800, endTime = 1000, subject = math)
        assertTrue(NotebookLessonResolver.resolve(cache, exact) is LessonResolution.Found)

        val missing = lessonRef(entryId = 999, date = 20260109, startTime = 800, endTime = 900, subject = math)
        val result = NotebookLessonResolver.resolve(cache, missing)
        assertTrue(result is LessonResolution.Missing)
        assertEquals(missing, (result as LessonResolution.Missing).snapshot)
    }

    @Test
    fun `resolve never accepts a different subject in the same slot`() {
        val wrongSubject = lessonRef(entryId = 0, date = 20260105, startTime = 800, endTime = 900, subject = english)
        val result = NotebookLessonResolver.resolve(cache, wrongSubject)

        assertTrue(result is LessonResolution.Missing)
    }

    @Test
    fun `resolve reports ambiguity instead of guessing`() {
        val duplicateA = entry(id = 20, startTime = 1400, endTime = 1500, classId = 1)
        val duplicateB = entry(id = 21, startTime = 1400, endTime = 1500, classId = 2)
        val ambiguousCache = NotebookLessonCache(weeks = mapOf(20260105 to listOf(duplicateA, duplicateB)))

        val result = NotebookLessonResolver.resolve(
            ambiguousCache,
            lessonRef(entryId = 0, date = 20260105, startTime = 1400, endTime = 1500, subject = math),
        )

        assertTrue(result is LessonResolution.Ambiguous)
        assertEquals(2, (result as LessonResolution.Ambiguous).candidates.size)
    }

    @Test
    fun `entry ids that repeat across dates stay date scoped`() {
        val first = entry(id = 30, date = 20260105, startTime = 800, endTime = 900)
        val second = entry(id = 30, date = 20260108, startTime = 800, endTime = 900)
        val dateCache = NotebookLessonCache(weeks = mapOf(20260105 to listOf(first), 20260108 to listOf(second)))

        val result = NotebookLessonResolver.resolve(
            dateCache,
            lessonRef(entryId = 30, date = 20260108, startTime = 800, endTime = 900, subject = math),
        )

        assertTrue(result is LessonResolution.Found)
        assertEquals(20260108, (result as LessonResolution.Found).entry.date)
    }

    @Test
    fun `cache conversion parses week keys and keeps holidays`() {
        val converted = NotebookLessonCache.from(
            TimetableCache.Data(
                weeks = mapOf("20260105" to listOf(firstMath)),
                holidays = listOf(Holiday(id = 2, startDate = 20260110, endDate = 20260111)),
            ),
        )

        assertEquals(1, converted.weeks[20260105]?.size ?: -1)
        assertEquals(1, converted.holidays.size)
    }

    @Test
    fun `resolving never changes the stored due date`() {
        val ref = lessonRef(entryId = 1, date = 20260105, startTime = 800, endTime = 1000, subject = math)
        val due = DueRef(date = 20260105, lesson = ref)
        val before = due.copy()

        NotebookLessonResolver.resolve(cache, ref)

        assertEquals(before, due)
    }
}
