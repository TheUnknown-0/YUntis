package sh.lsx.untisx.ui.notebook

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.DayStats
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.StatsData
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.LessonKey
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookDraft
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookLessonCache
import sh.lsx.untisx.data.notebook.NotebookLessonLinks
import sh.lsx.untisx.data.notebook.SubjectRef

class NotebookFormattingTest {

    private val today = LocalDate.of(2026, 10, 29)
    private val todayInt = today.toUntisDate()

    @Test
    fun `tasks are grouped overdue today upcoming and without date`() {
        val items = listOf(
            task("overdue", todayInt - 1),
            task("today", todayInt),
            task("upcoming", todayInt + 1),
            task("nodate", null),
            task("done", todayInt - 2, completedAt = 1L),
        )

        val sections = notebookTaskSections(items, today)

        assertEquals(
            listOf(
                NotebookTaskGroup.OVERDUE,
                NotebookTaskGroup.TODAY,
                NotebookTaskGroup.UPCOMING,
                NotebookTaskGroup.NO_DATE,
            ),
            sections.map { it.group },
        )
        assertEquals(listOf("overdue"), sections[0].items.map { it.id })
        assertEquals(listOf("today"), sections[1].items.map { it.id })
        assertEquals(listOf("upcoming"), sections[2].items.map { it.id })
        assertEquals(listOf("nodate"), sections[3].items.map { it.id })
    }

    @Test
    fun `completed tasks are never part of the open groups`() {
        val sections = notebookTaskSections(
            listOf(task("done", todayInt, completedAt = 5L)),
            today,
        )

        assertTrue(sections.isEmpty())
        assertEquals(0, notebookOpenTaskCount(listOf(task("done", todayInt, completedAt = 5L))))
        assertEquals(1, notebookOpenTaskCount(listOf(task("open", todayInt))))
    }

    @Test
    fun `notes sort pinned first then recent and archived last`() {
        val notes = listOf(
            note("plain", updatedAt = 10L),
            note("pinned", updatedAt = 5L, pinnedAt = 1L),
            note("archived", updatedAt = 50L, archivedAt = 1L),
        )

        assertEquals(listOf("pinned", "plain", "archived"), notebookNotes(notes).map { it.id })
    }

    @Test
    fun `without subject filter keeps only items without a subject`() {
        val items = listOf(
            note("math").copy(subject = SubjectRef(ids = listOf(1), name = "MA")),
            note("free").copy(subject = SubjectRef.freeText("DE")),
            note("none"),
        )

        assertEquals(listOf("none"), notebookFilterWithoutSubject(items).map { it.id })
    }

    @Test
    fun `badge counts open tasks and ordinary notes but not completed or date-only tasks`() {
        val date = todayInt
        val entry = TimetableEntry(
            id = 7,
            date = date,
            startTime = 800,
            endTime = 845,
            subject = listOf(TimetableElement(id = 1, name = "MA")),
        )
        val lesson = notebookEntryRef(entry)
        val subject = SubjectRef(ids = listOf(1), name = "MA")

        val items = listOf(
            task("open", date).copy(origin = lesson),
            task("done", date, completedAt = 1L).copy(origin = lesson),
            task("date-only", date),
            note("lesson-note").copy(subject = subject),
            note("general-note"),
        )

        val badges = notebookBadgeMap(listOf(entry), items)
        val summary = badges[LessonKey(7, date, 800)]

        assertEquals(2, summary?.badgeCount ?: -1)
        assertEquals(1, summary?.openTasks ?: -1)
        assertEquals(1, summary?.noteCount ?: -1)
    }

    @Test
    fun `lesson sections deduplicate source due and pinned items`() {
        val sourced = note("a")
        val sourcedAndPinned = note("b", pinnedAt = 1L)
        val due = task("c", todayInt)
        val pinned = note("d", pinnedAt = 1L)

        val sections = notebookLessonSections(
            NotebookLessonLinks(
                sourced = listOf(sourced, sourcedAndPinned),
                due = listOf(sourced, due),
                pinned = listOf(sourcedAndPinned, pinned),
            ),
        )

        assertEquals(
            listOf(
                NotebookLessonSectionKind.SOURCE,
                NotebookLessonSectionKind.DUE,
                NotebookLessonSectionKind.PINNED,
            ),
            sections.map { it.kind },
        )
        assertEquals(listOf("a", "b"), sections[0].items.map { it.id })
        assertEquals(listOf("c"), sections[1].items.map { it.id })
        assertEquals(listOf("d"), sections[2].items.map { it.id })
    }

    @Test
    fun `task due in the current lesson jumps back to its source day`() {
        val origin = LessonRef(entryId = 1, date = 20261104, startTime = 800, endTime = 845)
        val target = origin.copy(entryId = 2, date = 20261111)
        val item = task("linked", target.date).copy(
            origin = origin,
            due = DueRef(date = target.date, lesson = target),
        )

        assertEquals(origin.date, notebookLessonJumpDate(item, NotebookLessonSectionKind.DUE))
        assertEquals(target.date, notebookLessonJumpDate(item, NotebookLessonSectionKind.SOURCE))
    }

    @Test
    fun `target lesson day takes precedence over a separately kept due date for navigation`() {
        val target = LessonRef(entryId = 2, date = 20261111, startTime = 800, endTime = 845)
        val item = task("linked", 20261110).copy(due = DueRef(date = 20261110, lesson = target))

        assertEquals(target.date, notebookLessonJumpDate(item, NotebookLessonSectionKind.SOURCE))
        assertEquals(20261110, item.due?.date)
    }

    @Test
    fun `missing source does not make due section link back to itself`() {
        val item = task("without-source", 20261111)

        assertNull(notebookLessonJumpDate(item, NotebookLessonSectionKind.DUE))
        assertEquals(20261111, notebookLessonJumpDate(item, NotebookLessonSectionKind.SOURCE))
        assertNull(notebookLessonJumpDate(note("no-links"), NotebookLessonSectionKind.SOURCE))
    }

    @Test
    fun `lesson warning reports cancelled missing and ambiguous snapshots`() {
        val date = todayInt
        val cancelled = TimetableEntry(
            id = 3,
            date = date,
            startTime = 800,
            endTime = 845,
            subject = listOf(TimetableElement(id = 1, name = "MA")),
            code = "cancelled",
        )
        val cache = NotebookLessonCache(weeks = mapOf(1 to listOf(cancelled)))

        assertEquals(
            NotebookLessonWarning.CANCELLED,
            notebookLessonWarning(cache, notebookEntryRef(cancelled)),
        )
        assertEquals(
            NotebookLessonWarning.MISSING,
            notebookLessonWarning(
                NotebookLessonCache(),
                LessonRef(entryId = 99, date = date, startTime = 800, endTime = 845),
            ),
        )

        val found = cancelled.copy(id = 4, code = null)
        assertNull(
            notebookLessonWarning(
                NotebookLessonCache(weeks = mapOf(1 to listOf(found))),
                notebookEntryRef(found),
            ),
        )
    }

    @Test
    fun `previous reminder day never shifts the due date itself`() {
        assertEquals(todayInt - 1, notebookPreviousDay(todayInt))
        assertEquals(0, notebookPreviousDay(0))
    }

    @Test
    fun `next lesson for a due-only task uses the due lesson subject and anchor`() {
        val due = LessonRef(
            entryId = 10,
            date = 20261029,
            startTime = 800,
            endTime = 845,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )
        val cache = NotebookLessonCache(
            weeks = mapOf(
                1 to listOf(
                    entry(10, 20261029, 800, 845, 1, "MA"),
                    entry(11, 20261030, 800, 845, 1, "MA"),
                    entry(12, 20261030, 900, 945, 2, "DE"),
                ),
            ),
        )
        val now = ZonedDateTime.of(2026, 10, 29, 7, 0, 0, 0, ZoneId.of("UTC"))

        assertEquals(
            11,
            notebookNextLesson(
                cache = cache,
                now = now,
                subject = null,
                dueLesson = due,
                origin = null,
            )?.entry?.id,
        )

        assertNull(
            notebookNextLesson(
                cache = cache,
                now = now,
                subject = null,
                dueLesson = null,
                origin = null,
            ),
        )
    }

    @Test
    fun `item content change ignores state-only changes`() {
        val original = task("t1", todayInt)
        val completed = original.copy(completedAt = 5L, updatedAt = 10L)

        assertFalse(notebookItemContentChanged(original, completed))
        assertTrue(notebookItemContentChanged(original, original.copy(title = "Changed")))
    }

    @Test
    fun `dirty comparison ignores item state but detects editable changes`() {
        val item = note("a").copy(title = "Title", body = "Body", updatedAt = 10L)
        val unchanged = NotebookDraft(
            id = "draft",
            itemId = item.id,
            kind = item.kind,
            title = item.title,
            body = item.body,
        )

        assertFalse(notebookHasUnsavedChanges(unchanged, item))
        assertTrue(notebookHasUnsavedChanges(unchanged.copy(body = "Changed"), item))
        assertTrue(notebookHasUnsavedChanges(unchanged.copy(subject = SubjectRef.freeText("DE")), item))
        assertFalse(notebookHasUnsavedChanges(NotebookDraft(id = "new"), null))
        assertTrue(notebookHasUnsavedChanges(NotebookDraft(id = "new", title = "x"), null))
    }

    @Test
    fun `editor save and mutate respect read only and saving states`() {
        val draft = NotebookDraft(id = "draft", title = "Title")

        val ready = NotebookEditorState.Ready(
            draft = draft,
            lessonCache = NotebookLessonCache(),
            isNew = true,
            isDirty = true,
            saving = false,
            error = null,
        )

        assertTrue(notebookEditorCanSave(ready))
        assertTrue(notebookEditorCanMutate(ready))
        assertFalse(notebookEditorCanSave(ready.copy(readOnly = true)))
        assertFalse(notebookEditorCanSave(ready.copy(saving = true)))
        assertFalse(notebookEditorCanSave(ready.copy(draft = draft.copy(title = "", body = ""))))
    }

    @Test
    fun `find saved item prefers item id and falls back to content and timestamp`() {
        val existing = note("existing").copy(title = "Existing", updatedAt = 5L)
        val newItem = note("new").copy(title = "New", body = "Body", updatedAt = 20L)
        val data = sh.lsx.untisx.data.notebook.NotebookData(
            revision = 1,
            items = listOf(existing, newItem),
            updatedAt = 20L,
        )

        assertEquals(
            existing,
            notebookFindSavedItem(
                data,
                NotebookDraft(id = "draft", itemId = existing.id, title = "Ignored"),
            ),
        )
        assertEquals(
            newItem,
            notebookFindSavedItem(
                data,
                NotebookDraft(id = "draft", title = "New", body = "Body"),
            ),
        )
    }

    @Test
    fun `current school year subjects use only stats and cached evidence with stable ids`() {
        val cache = TimetableCache.Data(
            subjects = listOf(
                MasterDataItem(id = 1, name = "MA", longName = "Mathematik"),
                MasterDataItem(id = 2, name = "DE", longName = "Deutsch"),
                MasterDataItem(id = 3, name = "EN", longName = "Englisch"),
                MasterDataItem(id = 4, name = "KU", longName = "Kunst"),
                MasterDataItem(id = 5, name = "PH", longName = "Physik"),
                MasterDataItem(id = 6, name = "EV", longName = "Event"),
                MasterDataItem(id = 7, name = "GE", longName = "Geschichte"),
                MasterDataItem(id = 8, name = "BI", longName = "Biologie"),
            ),
            weeks = mapOf(
                "20261026" to listOf(
                    entry(10, 20261027, 800, 845, 2, "DE"),
                    entry(11, 20261028, 800, 845, 4, "KU"),
                    entry(12, 20261028, 900, 945, 5, "PH").copy(code = "cancelled"),
                    TimetableEntry(
                        id = 13,
                        date = 20261029,
                        startTime = 1000,
                        endTime = 1100,
                        lstext = "Event",
                    ),
                ),
                "20251027" to listOf(
                    entry(14, 20251028, 800, 845, 3, "EN"),
                ),
            ),
        )
        val stats = StatsData(
            years = mapOf(
                "20260801" to SchoolYearStats(
                    name = "2026/2027",
                    startDate = 20260801,
                    endDate = 20270731,
                    days = mapOf(
                        "20261029" to DayStats(subjectCounts = mapOf(1 to 3)),
                        "20251029" to DayStats(subjectCounts = mapOf(8 to 2)),
                    ),
                ),
            ),
        )

        val snapshot = notebookCurrentSchoolYearSubjects(
            cache = cache,
            stats = stats,
            today = today,
        )

        assertEquals(listOf(2, 4, 1), snapshot.subjects.map { it.primaryId })
        assertEquals("2026/2027", snapshot.yearName)
        assertFalse(snapshot.estimatedYear)
        assertEquals(listOf(2), snapshot.subjects.first { it.primaryId == 2 }.ids)
        assertFalse(snapshot.subjects.any { it.primaryId == 7 })
        assertFalse(snapshot.subjects.any { it.primaryId == 8 })
    }

    @Test
    fun `current school year subjects never fall back to the master data catalog`() {
        val cache = TimetableCache.Data(
            subjects = listOf(
                MasterDataItem(id = 1, name = "MA", longName = "Mathematik"),
                MasterDataItem(id = 2, name = "DE", longName = "Deutsch"),
            ),
        )

        val snapshot = notebookCurrentSchoolYearSubjects(
            cache = cache,
            stats = null,
            today = today,
        )

        assertTrue(snapshot.subjects.isEmpty())
        assertEquals("2026/2027", snapshot.yearName)
        assertTrue(snapshot.estimatedYear)
    }

    @Test
    fun `an existing old subject is shown as the current picker value only`() {
        val current = listOf(
            SubjectRef(ids = listOf(1), name = "MA"),
            SubjectRef(ids = listOf(2), name = "DE"),
        )
        val old = SubjectRef(ids = listOf(9), name = "ALT")

        assertEquals(
            listOf(2, 1),
            notebookSubjectPickerChoices(current, selected = null).map { it.primaryId },
        )
        assertEquals(
            listOf(9, 2, 1),
            notebookSubjectPickerChoices(current, selected = old).map { it.primaryId },
        )
        assertEquals(
            listOf(2, 1),
            notebookSubjectPickerChoices(
                current,
                selected = SubjectRef.freeText("MA"),
            ).map { it.primaryId },
        )
    }

    @Test
    fun `subject search matches names aliases and known ids without expanding the catalog`() {
        val math = SubjectRef(ids = listOf(17, 23), name = "MA", longName = "Mathematik")
        val german = SubjectRef(ids = listOf(2), name = "DE", longName = "Deutsch")
        val subjects = listOf(math, german)
        val aliases = mapOf("MA" to SubjectAlias(short = "Mathe", long = "Algebra und Geometrie"))

        for (query in listOf("ma", "MATHEMATIK", "mathe", "geometrie", "17", "23", "  ALGEBRA  ma  ")) {
            assertEquals(query, listOf(math), notebookSearchSubjects(subjects, query, aliases))
        }
        assertEquals(subjects, notebookSearchSubjects(subjects, "  ", aliases))
        assertEquals(listOf(german), notebookSearchSubjects(subjects, "deutsch", aliases))
        assertTrue(notebookSearchSubjects(subjects, "Biologie", aliases).isEmpty())
        assertTrue(notebookSearchSubjects(emptyList(), "Mathe", aliases).isEmpty())
    }

    @Test
    fun `subject search retains saved current choice but never adds historical or schoolwide subjects`() {
        val current = SubjectRef(ids = listOf(1), name = "MA")
        val saved = SubjectRef(ids = listOf(9), name = "ALT")
        val choices = notebookSubjectPickerChoices(listOf(current), saved)
        assertEquals(listOf(saved), notebookSearchSubjects(choices, "alt", emptyMap()))
        assertTrue(notebookSearchSubjects(choices, "Geschichte", emptyMap()).isEmpty())
        assertEquals(listOf(current), notebookSearchSubjects(
            notebookSubjectPickerChoices(listOf(current), null), "", emptyMap(),
        ))
    }

    @Test
    fun `tapping the second period of a double lesson returns the merged block ref`() {
        val first = entry(20, todayInt, 800, 845, 1, "MA")
        val second = entry(21, todayInt, 845, 930, 1, "MA")
        val cache = NotebookLessonCache(weeks = mapOf(1 to listOf(first, second)))

        val ref = notebookLessonRef(cache, second)

        assertEquals(20, ref.entryId)
        assertEquals(800, ref.startTime)
        assertEquals(930, ref.endTime)
        assertEquals(listOf(1), ref.subject?.ids ?: emptyList<Int>())
    }

    private fun task(
        id: String,
        dueDate: Int?,
        completedAt: Long? = null,
    ): NotebookItem = NotebookItem(
        id = id,
        kind = NotebookKind.TASK,
        title = id,
        due = dueDate?.let { DueRef(date = it) },
        updatedAt = 1L,
        completedAt = completedAt,
    )

    private fun note(
        id: String,
        updatedAt: Long = 1L,
        pinnedAt: Long? = null,
        archivedAt: Long? = null,
    ): NotebookItem = NotebookItem(
        id = id,
        kind = NotebookKind.NOTE,
        title = id,
        updatedAt = updatedAt,
        pinnedAt = pinnedAt,
        archivedAt = archivedAt,
    )

    private fun entry(
        id: Int,
        date: Int,
        startTime: Int,
        endTime: Int,
        subjectId: Int,
        subjectName: String,
    ): TimetableEntry = TimetableEntry(
        id = id,
        date = date,
        startTime = startTime,
        endTime = endTime,
        subject = listOf(TimetableElement(id = subjectId, name = subjectName)),
    )
}
