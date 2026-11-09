package sh.lsx.untisx.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarItemsTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val all = CalendarSelection(CalendarCategory.entries.toSet())
    private val subjects = mapOf(2 to MasterDataItem(id = 2, name = "M", longName = "Mathematik"))

    private fun source(
        exams: List<ExamInfo> = emptyList(),
        events: List<TimetableEntry> = emptyList(),
        lessons: List<TimetableEntry> = emptyList(),
        holidays: List<Holiday> = emptyList(),
        notebook: List<NotebookItem> = emptyList(),
        label: String? = null,
    ) = CalendarSource(
        accountId = "acc",
        accountLabel = label,
        exams = exams,
        events = events,
        lessons = lessons,
        holidays = holidays,
        notebook = notebook,
        subjects = subjects,
    )

    @Test
    fun `exam becomes a timed entry with type and subject`() {
        val exam = ExamInfo(id = 5, examType = "Klausur", date = 20261112, startTime = 800, endTime = 935, subjectId = 2)
        val item = CalendarItems.build(source(exams = listOf(exam)), all, today).single()

        assertEquals("Klausur Mathematik", item.title)
        assertEquals(LocalDateTime.of(2026, 11, 12, 8, 0), item.start)
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 35), item.end)
        assertEquals(false, item.allDay)
        assertEquals("acc:exam:5:20261112:800", item.key)
    }

    @Test
    fun `excluded exam types and switched off categories are left out`() {
        val exams = listOf(
            ExamInfo(id = 1, examType = "Test", date = 20261112, startTime = 800, endTime = 845, subjectId = 2),
            ExamInfo(id = 2, examType = "Klausur", date = 20261113, startTime = 800, endTime = 845, subjectId = 2),
        )
        val selection = CalendarSelection(setOf(CalendarCategory.EXAMS), excludedExamTypes = setOf("test"))

        val items = CalendarItems.build(source(exams = exams), selection, today)

        assertEquals(listOf("Klausur Mathematik"), items.map { it.title })
        assertTrue(CalendarItems.build(source(exams = exams), CalendarSelection(emptySet()), today).isEmpty())
    }

    @Test
    fun `account label is put in front when several accounts are exported`() {
        val exam = ExamInfo(id = 1, examType = "Klausur", date = 20261112, startTime = 800, endTime = 845, subjectId = 2)
        val item = CalendarItems.build(source(exams = listOf(exam), label = "ben"), all, today).single()

        assertEquals("[ben] Klausur Mathematik", item.title)
    }

    @Test
    fun `holiday spans all its days as one all day entry`() {
        val holiday = Holiday(id = 1, name = "Weih", longName = "Weihnachtsferien", startDate = 20261221, endDate = 20270102)
        val item = CalendarItems.build(source(holidays = listOf(holiday)), all, today).single()

        assertTrue(item.allDay)
        assertEquals(LocalDate.of(2026, 12, 21).atStartOfDay(), item.start)
        assertEquals(LocalDate.of(2027, 1, 3).atStartOfDay(), item.end)
    }

    @Test
    fun `open homework is placed on its due day, done homework is not`() {
        val open = NotebookItem(id = "a", kind = NotebookKind.TASK, title = "S. 42", due = DueRef(date = 20261111))
        val done = open.copy(id = "b", completedAt = 1L)

        val items = CalendarItems.build(source(notebook = listOf(open, done)), all, today)

        assertEquals(listOf("HA: S. 42"), items.map { it.title })
        assertTrue(items.single().allDay)
    }

    @Test
    fun `cancelled and substituted lessons from today on, past ones skipped`() {
        val su = listOf(TimetableElement(id = 2, name = "M"))
        val lessons = listOf(
            TimetableEntry(id = 1, date = 20261110, startTime = 800, endTime = 845, subject = su, code = "cancelled"),
            TimetableEntry(
                id = 2, date = 20261111, startTime = 800, endTime = 845, subject = su, code = "irregular",
                teacher = listOf(TimetableElement(id = 8, name = "RIC", orgId = 1, orgName = "MÜL")),
            ),
            TimetableEntry(id = 3, date = 20261105, startTime = 800, endTime = 845, subject = su, code = "cancelled"),
            TimetableEntry(id = 4, date = 20261110, startTime = 900, endTime = 945, subject = su),
        )

        val items = CalendarItems.build(source(lessons = lessons), all, today)

        assertEquals(listOf("Entfall: Mathematik", "Vertretung: Mathematik"), items.map { it.title })
        assertEquals("Lehrkraft: RIC (statt MÜL)", items[1].description)
    }

    @Test
    fun `whole day event without times becomes all day`() {
        val event = TimetableEntry(id = 9, date = 20261120, lstext = "Wandertag")
        val item = CalendarItems.build(source(events = listOf(event)), all, today).single()

        assertEquals("Wandertag", item.title)
        assertTrue(item.allDay)
    }
}
