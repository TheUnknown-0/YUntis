package sh.lsx.untisx.ui.timetable

import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

internal fun previewLesson(subject: String = "MA", cancelled: Boolean = false): LessonDisplay =
    LessonDisplay(
        entry = TimetableEntry(
            id = 0,
            date = 20250101,
            startTime = 800,
            endTime = 845,
            subject = listOf(TimetableElement(id = 1, name = subject)),
            teacher = listOf(TimetableElement(id = 1, name = "MÜ")),
            room = listOf(TimetableElement(id = 1, name = "E01")),
            code = if (cancelled) "cancelled" else null,
        ),
        subject = subject,
        rooms = "E01",
        teachers = "MÜ",
    )
