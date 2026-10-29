package sh.lsx.untisx.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookSearchTest {
    private val math = SubjectRef(ids = listOf(1), name = "Math", longName = "Mathematics")
    private val english = SubjectRef(ids = listOf(2), name = "English")
    private val lesson = lessonRef(entryId = 10, date = 20260105, startTime = 800, endTime = 900, subject = math)
    private val otherLesson = lessonRef(entryId = 11, date = 20260105, startTime = 900, endTime = 1000, subject = english)

    private val taskFromLesson = task(
        id = "t1",
        title = "Homework",
        subject = math,
        origin = lesson,
        due = DueRef(date = 20260105, lesson = lesson),
    )

    private val dateOnlyTask = task(
        id = "t2",
        title = "Date only",
        due = DueRef(date = 20260105, lesson = null),
    )

    private val pinnedMathNote = note(
        id = "n1",
        title = "Formula",
        subject = math,
        pinnedAt = 10,
    )

    private val ordinaryMathNote = note(
        id = "n2",
        title = "Vocabulary",
        body = "Words",
        subject = math,
    )

    private val sourcedNote = note(
        id = "n3",
        title = "Lesson notes",
        origin = lesson,
    )

    private val archivedMathNote = note(
        id = "n4",
        title = "Old notes",
        subject = math,
        archivedAt = 20,
    )

    private val englishTask = task(
        id = "t3",
        title = "Essay",
        subject = english,
        due = DueRef(date = 20260105, lesson = otherLesson),
    )

    private val items = listOf(
        taskFromLesson,
        dateOnlyTask,
        pinnedMathNote,
        ordinaryMathNote,
        sourcedNote,
        archivedMathNote,
        englishTask,
    )

    @Test
    fun `text search covers title body and subject`() {
        assertEquals(listOf("t1"), NotebookSearch.apply(items, NotebookQuery(text = "homework")).map { it.id })
        assertEquals(listOf("n2"), NotebookSearch.apply(items, NotebookQuery(text = "words")).map { it.id })
        assertEquals(
            listOf("t1", "n1", "n2", "n4"),
            NotebookSearch.apply(items, NotebookQuery(text = "mathematics")).map { it.id },
        )
    }

    @Test
    fun `kind and status filters are distinct`() {
        assertEquals(
            listOf("t1", "t2", "t3"),
            NotebookSearch.apply(items, NotebookQuery(kinds = setOf(NotebookKind.TASK))).map { it.id },
        )
        assertEquals(
            listOf("n1", "n2", "n3"),
            NotebookSearch.apply(items, NotebookQuery(status = NotebookStatusFilter.OPEN, kinds = setOf(NotebookKind.NOTE))).map { it.id },
        )
        assertEquals(
            listOf("n4"),
            NotebookSearch.apply(items, NotebookQuery(status = NotebookStatusFilter.ARCHIVED)).map { it.id },
        )
        assertEquals(
            listOf("n1"),
            NotebookSearch.apply(items, NotebookQuery(status = NotebookStatusFilter.PINNED)).map { it.id },
        )
    }

    @Test
    fun `subject and linked lesson filters do not use due date alone`() {
        assertEquals(
            listOf("t1", "n1", "n2", "n4"),
            NotebookSearch.apply(items, NotebookQuery(subject = math)).map { it.id },
        )

        val linked = NotebookSearch.apply(items, NotebookQuery(linkedLesson = lesson.key)).map { it.id }
        assertEquals(listOf("t1", "n3"), linked)
        assertFalse(linked.contains("t2"))
    }

    @Test
    fun `lesson sections separate sourced due and pinned`() {
        val links = NotebookSearch.linksForLesson(items, lesson)

        assertEquals(listOf("n3"), links.sourced.map { it.id })
        assertEquals(listOf("t1"), links.due.map { it.id })
        assertEquals(listOf("n1"), links.pinned.map { it.id })
    }

    @Test
    fun `summary includes ordinary notes and deduplicates origin and due`() {
        val summary = NotebookSearch.summarize(items, lesson)

        assertEquals(1, summary.openTasks)
        assertEquals(1, summary.dueTasks)
        assertEquals(1, summary.pinnedNotes)
        assertEquals(3, summary.noteCount)
        assertEquals(4, summary.badgeCount)
    }

    @Test
    fun `sort puts pinned first and archived or completed last`() {
        val sorted = NotebookSearch.sort(items)

        assertEquals("n1", sorted.first().id)
        assertEquals("n4", sorted.last().id)
    }
}
