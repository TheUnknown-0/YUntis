package sh.lsx.untisx.data.notebook

import java.time.LocalTime
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull

data class NotebookValidationIssue(
    val itemId: String?,
    val field: String,
    val message: String,
)

object NotebookValidator {
    const val MAX_ITEMS = 5_000
    const val MAX_DRAFTS = 100
    const val MAX_TITLE = 200
    const val MAX_BODY = 20_000
    const val MAX_SUBJECT = 100
    const val MAX_ID = 64

    private const val MIN_DATE = 19_000_101
    private const val MAX_DATE = 29_991_231

    fun isValidDate(value: Int): Boolean =
        value in MIN_DATE..MAX_DATE && value.toUntisLocalDateOrNull() != null

    fun isValidTime(value: Int): Boolean =
        value in 0..2359 && runCatching { LocalTime.of(value / 100, value % 100) }.isSuccess

    fun isValidMinutesOfDay(value: Int): Boolean = value in 0..1439

    fun normalize(item: NotebookItem): NotebookItem = item.copy(
        id = item.id.trim(),
        title = item.title.trim(),
        subject = item.subject?.normalized()?.takeIf { !it.isBlank },
        origin = item.origin?.normalized(),
        due = item.due?.let { due -> due.copy(lesson = due.lesson?.normalized()) },
    )

    fun normalizeDraft(draft: NotebookDraft): NotebookDraft = draft.copy(
        id = draft.id.trim(),
        itemId = draft.itemId?.trim(),
        title = draft.title.trim(),
        subject = draft.subject?.normalized()?.takeIf { !it.isBlank },
        origin = draft.origin?.normalized(),
        due = draft.due?.let { due -> due.copy(lesson = due.lesson?.normalized()) },
    )

    fun validate(item: NotebookItem): List<NotebookValidationIssue> = buildList {
        validateId(item.id, item.id, "id", this)

        if (item.title.length > MAX_TITLE) {
            add(NotebookValidationIssue(item.id, "title", "Title is longer than $MAX_TITLE characters."))
        }
        if (item.body.length > MAX_BODY) {
            add(NotebookValidationIssue(item.id, "body", "Body is longer than $MAX_BODY characters."))
        }
        if (item.title.isBlank() && item.body.isBlank()) {
            add(NotebookValidationIssue(item.id, "text", "A note or task needs text."))
        }

        validateSubject(item.subject, item.id, this)
        validateLesson(item.origin, item.id, "origin", this)
        validateDue(item.due, item.id, this)

        if (item.createdAt < 0 || item.updatedAt < 0) {
            add(NotebookValidationIssue(item.id, "timestamps", "Timestamps must not be negative."))
        }

        if (item.kind == NotebookKind.TASK) {
            if (item.pinnedAt != null) {
                add(NotebookValidationIssue(item.id, "pinnedAt", "Tasks cannot be pinned."))
            }
            if (item.archivedAt != null) {
                add(NotebookValidationIssue(item.id, "archivedAt", "Tasks cannot be archived."))
            }
        } else {
            if (item.due != null) {
                add(NotebookValidationIssue(item.id, "due", "Notes cannot have a due date."))
            }
            if (item.completedAt != null) {
                add(NotebookValidationIssue(item.id, "completedAt", "Notes cannot be completed."))
            }
        }
    }

    fun validateItems(items: List<NotebookItem>): List<NotebookValidationIssue> = buildList {
        if (items.size > MAX_ITEMS) {
            add(NotebookValidationIssue(null, "items", "At most $MAX_ITEMS items are supported."))
        }

        val seen = mutableSetOf<String>()

        items.forEach { item ->
            addAll(validate(item))

            if (item.id.isNotBlank() && !seen.add(item.id)) {
                add(NotebookValidationIssue(item.id, "id", "Item IDs must be unique."))
            }
        }
    }

    fun validate(data: NotebookData): List<NotebookValidationIssue> = buildList {
        if (data.schema != NotebookSchema.CURRENT) {
            add(NotebookValidationIssue(null, "schema", "Unsupported notebook schema ${data.schema}."))
        }
        if (data.revision < 0) {
            add(NotebookValidationIssue(null, "revision", "Revision must not be negative."))
        }
        if (data.updatedAt < 0) {
            add(NotebookValidationIssue(null, "updatedAt", "Timestamp must not be negative."))
        }

        addAll(validateItems(data.items))
    }

    fun validateDraft(draft: NotebookDraft): List<NotebookValidationIssue> = buildList {
        validateId(draft.id, draft.id, "id", this)

        val draftItemId = draft.itemId
        if (draftItemId != null) {
            if (draftItemId.isBlank()) {
                add(NotebookValidationIssue(draft.id, "itemId", "Item ID must not be blank."))
            } else if (draftItemId.length > MAX_ID) {
                add(NotebookValidationIssue(draft.id, "itemId", "Item ID is too long."))
            }
        }

        if (draft.title.length > MAX_TITLE) {
            add(NotebookValidationIssue(draft.id, "title", "Title is longer than $MAX_TITLE characters."))
        }
        if (draft.body.length > MAX_BODY) {
            add(NotebookValidationIssue(draft.id, "body", "Body is longer than $MAX_BODY characters."))
        }

        validateSubject(draft.subject, draft.id, this)
        validateLesson(draft.origin, draft.id, "origin", this)
        validateDue(draft.due, draft.id, this)

        if (draft.updatedAt < 0) {
            add(NotebookValidationIssue(draft.id, "updatedAt", "Timestamp must not be negative."))
        }
        val baseUpdatedAt = draft.baseUpdatedAt
        if (baseUpdatedAt != null && baseUpdatedAt < 0) {
            add(NotebookValidationIssue(draft.id, "baseUpdatedAt", "Timestamp must not be negative."))
        }
    }

    fun validateDrafts(data: NotebookDraftData): List<NotebookValidationIssue> = buildList {
        if (data.schema != NotebookSchema.CURRENT) {
            add(NotebookValidationIssue(null, "schema", "Unsupported draft schema ${data.schema}."))
        }
        if (data.revision < 0) {
            add(NotebookValidationIssue(null, "revision", "Revision must not be negative."))
        }
        if (data.drafts.size > MAX_DRAFTS) {
            add(NotebookValidationIssue(null, "drafts", "At most $MAX_DRAFTS drafts are supported."))
        }

        data.drafts.forEach { (key, draft) ->
            if (key != draft.id) {
                add(NotebookValidationIssue(draft.id, "draftKey", "Draft map key does not match its ID."))
            }
            addAll(validateDraft(draft))
        }
    }

    private fun validateId(value: String, itemId: String?, field: String, sink: MutableList<NotebookValidationIssue>) {
        if (value.isBlank()) {
            sink.add(NotebookValidationIssue(itemId, field, "ID must not be blank."))
        } else if (value.length > MAX_ID) {
            sink.add(NotebookValidationIssue(itemId, field, "ID is longer than $MAX_ID characters."))
        }
    }

    private fun validateSubject(
        subject: SubjectRef?,
        itemId: String?,
        sink: MutableList<NotebookValidationIssue>,
    ) {
        if (subject == null) return

        if (subject.ids.any { it <= 0 }) {
            sink.add(NotebookValidationIssue(itemId, "subject.ids", "Subject IDs must be positive."))
        }
        if (subject.ids.size != subject.ids.distinct().size) {
            sink.add(NotebookValidationIssue(itemId, "subject.ids", "Subject IDs must be unique."))
        }
        if (subject.name.length > MAX_SUBJECT) {
            sink.add(NotebookValidationIssue(itemId, "subject.name", "Subject is longer than $MAX_SUBJECT characters."))
        }
        if (subject.longName.length > MAX_SUBJECT) {
            sink.add(NotebookValidationIssue(itemId, "subject.longName", "Subject is longer than $MAX_SUBJECT characters."))
        }
    }

    private fun validateLesson(
        lesson: LessonRef?,
        itemId: String?,
        field: String,
        sink: MutableList<NotebookValidationIssue>,
    ) {
        if (lesson == null) return

        if (!isValidDate(lesson.date)) {
            sink.add(NotebookValidationIssue(itemId, "$field.date", "Invalid lesson date."))
        }
        if (!isValidTime(lesson.startTime)) {
            sink.add(NotebookValidationIssue(itemId, "$field.startTime", "Invalid lesson start time."))
        }
        if (!isValidTime(lesson.endTime)) {
            sink.add(NotebookValidationIssue(itemId, "$field.endTime", "Invalid lesson end time."))
        }
        if (isValidTime(lesson.startTime) && isValidTime(lesson.endTime) && lesson.endTime <= lesson.startTime) {
            sink.add(NotebookValidationIssue(itemId, "$field.endTime", "Lesson end must be after its start."))
        }
        if (lesson.entryId < 0) {
            sink.add(NotebookValidationIssue(itemId, "$field.entryId", "Lesson entry ID must not be negative."))
        }

        validateSubject(lesson.subject, itemId, sink)
    }

    private fun validateDue(
        due: DueRef?,
        itemId: String?,
        sink: MutableList<NotebookValidationIssue>,
    ) {
        if (due == null) return

        if (!isValidDate(due.date)) {
            sink.add(NotebookValidationIssue(itemId, "due.date", "Invalid due date."))
        }

        validateLesson(due.lesson, itemId, "due.lesson", sink)

        due.reminder?.let { reminder ->
            if (!isValidDate(reminder.date)) {
                sink.add(NotebookValidationIssue(itemId, "due.reminder.date", "Invalid reminder date."))
            }
            if (!isValidMinutesOfDay(reminder.minutesOfDay)) {
                sink.add(
                    NotebookValidationIssue(
                        itemId,
                        "due.reminder.minutesOfDay",
                        "Reminder time must be between 00:00 and 23:59.",
                    ),
                )
            }
        }
    }
}
