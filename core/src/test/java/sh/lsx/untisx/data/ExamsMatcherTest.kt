package sh.lsx.untisx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class ExamsMatcherTest {

    @Test
    fun examEndingAtPeriodStartDoesNotMatchFollowingPeriod() {
        val exam = exam(start = 955, end = 1040)
        val followingPeriod = entry(start = 1040, end = 1125)

        assertFalse(ExamsMatcher.matches(followingPeriod, exam))
    }

    @Test
    fun overlappingExamMatches() {
        val exam = exam(start = 955, end = 1040)
        val lesson = entry(start = 1000, end = 1045)

        assertTrue(ExamsMatcher.matches(lesson, exam))
    }

    @Test
    fun pointExamMatchesOnlyContainingPeriod() {
        val exam = exam(start = 1040, end = 0)

        assertFalse(ExamsMatcher.matches(entry(start = 955, end = 1040), exam))
        assertTrue(ExamsMatcher.matches(entry(start = 1040, end = 1125), exam))
        assertFalse(ExamsMatcher.matches(entry(start = 1125, end = 1210), exam))
    }

    @Test
    fun pointExamMatchesEntryWithoutEndTimeOnlyOnSameStart() {
        val exam = exam(start = 1040, end = 0)

        assertTrue(ExamsMatcher.matches(entry(start = 1040, end = 0), exam))
        assertFalse(ExamsMatcher.matches(entry(start = 955, end = 0), exam))
    }

    @Test
    fun differentDateDoesNotMatch() {
        val exam = exam(date = DATE, start = 955, end = 1040)
        val lesson = entry(date = DATE + 1, start = 955, end = 1040)

        assertFalse(ExamsMatcher.matches(lesson, exam))
    }

    @Test
    fun entryWithoutSubjectsDoesNotMatch() {
        val exam = exam(start = 955, end = 1040)
        val event = TimetableEntry(
            date = DATE,
            startTime = 955,
            endTime = 1040,
            klasse = listOf(TimetableElement(id = 10, name = "Klasse")),
        )

        assertFalse(ExamsMatcher.matches(event, exam))
    }

    @Test
    fun bestPicksTheMatchingExamWithTheNearestStart() {
        val entry = entry(start = 955, end = 1125)
        val exact = exam(start = 955, end = 1000)
        val later = exam(start = 1010, end = 1050)

        assertEquals(exact, ExamsMatcher.best(entry, listOf(later, exact)))
    }

    @Test
    fun bestReturnsNullWithoutAMatch() {
        val entry = entry(start = 955, end = 1040)
        val otherDay = exam(date = DATE + 1, start = 955, end = 1040)

        assertNull(ExamsMatcher.best(entry, listOf(otherDay)))
    }

    private fun entry(
        date: Int = DATE,
        start: Int,
        end: Int,
        subjectId: Int = SUBJECT_ID,
    ): TimetableEntry = TimetableEntry(
        date = date,
        startTime = start,
        endTime = end,
        klasse = listOf(TimetableElement(id = 10, name = "Klasse")),
        subject = listOf(TimetableElement(id = subjectId, name = "Mathe")),
    )

    private fun exam(
        date: Int = DATE,
        start: Int,
        end: Int,
        subjectId: Int = SUBJECT_ID,
    ): ExamInfo = ExamInfo(
        date = date,
        startTime = start,
        endTime = end,
        subjectId = subjectId,
    )

    private companion object {
        const val DATE = 20261005
        const val SUBJECT_ID = 1
    }
}
