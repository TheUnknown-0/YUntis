package sh.lsx.untisx.data.notebook

import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

internal fun subject(id: Int = 1, name: String = "Math"): SubjectRef =
    SubjectRef(ids = listOf(id), name = name)

internal fun lessonRef(
    entryId: Int,
    date: Int = 20260105,
    startTime: Int = 800,
    endTime: Int = 900,
    subject: SubjectRef? = subject(),
): LessonRef = LessonRef(
    entryId = entryId,
    date = date,
    startTime = startTime,
    endTime = endTime,
    subject = subject,
)

internal fun note(
    id: String,
    title: String = "",
    body: String = "",
    subject: SubjectRef? = null,
    origin: LessonRef? = null,
    pinnedAt: Long? = null,
    archivedAt: Long? = null,
    updatedAt: Long = 0,
): NotebookItem = NotebookItem(
    id = id,
    kind = NotebookKind.NOTE,
    title = title,
    body = body,
    subject = subject,
    origin = origin,
    pinnedAt = pinnedAt,
    archivedAt = archivedAt,
    updatedAt = updatedAt,
)

internal fun task(
    id: String,
    title: String = "",
    body: String = "",
    subject: SubjectRef? = null,
    origin: LessonRef? = null,
    due: DueRef? = null,
    completedAt: Long? = null,
    updatedAt: Long = 0,
): NotebookItem = NotebookItem(
    id = id,
    kind = NotebookKind.TASK,
    title = title,
    body = body,
    subject = subject,
    origin = origin,
    due = due,
    completedAt = completedAt,
    updatedAt = updatedAt,
)

internal fun entry(
    id: Int,
    date: Int = 20260105,
    startTime: Int = 800,
    endTime: Int = 900,
    subjectId: Int = 1,
    subjectName: String = "Math",
    code: String? = null,
    teacher: String = "Teacher",
    room: String = "Room",
    classId: Int = 1,
): TimetableEntry = TimetableEntry(
    id = id,
    date = date,
    startTime = startTime,
    endTime = endTime,
    klasse = listOf(TimetableElement(id = classId, name = "Class $classId")),
    teacher = listOf(TimetableElement(id = 10, name = teacher)),
    subject = if (subjectId > 0) {
        listOf(TimetableElement(id = subjectId, name = subjectName))
    } else {
        emptyList()
    },
    room = listOf(TimetableElement(id = 20, name = room)),
    code = code,
)
