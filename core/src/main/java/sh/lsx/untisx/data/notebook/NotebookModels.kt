package sh.lsx.untisx.data.notebook

import java.util.Locale
import kotlinx.serialization.Serializable
import sh.lsx.untisx.data.model.TimetableElement

@Serializable
enum class NotebookKind { TASK, NOTE }

@Serializable
data class SubjectRef(
    val ids: List<Int> = emptyList(),
    val name: String = "",
    val longName: String = "",
) {
    val isBlank: Boolean get() = ids.isEmpty() && name.isBlank() && longName.isBlank()

    val primaryId: Int get() = ids.firstOrNull() ?: 0

    val displayName: String get() = name.ifBlank { longName }

    fun normalized(): SubjectRef = copy(
        ids = ids.filter { it > 0 }.distinct().sorted(),
        name = name.trim(),
        longName = longName.trim(),
    )

    fun matches(other: SubjectRef?): Boolean {
        if (other == null) return false

        val left = normalized()
        val right = other.normalized()

        if (left.isBlank || right.isBlank) return false

        if (left.ids.isNotEmpty() && right.ids.isNotEmpty()) {
            return left.ids.any { it in right.ids }
        }

        val leftNames = listOf(left.name, left.longName)
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        val rightNames = listOf(right.name, right.longName)
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.ROOT) }
            .toSet()

        return leftNames.any { it in rightNames }
    }

    companion object {
        fun from(elements: List<TimetableElement>): SubjectRef? {
            val usable = elements.filter {
                it.id > 0 || it.name.isNotBlank() || it.longName?.isNotBlank() == true
            }

            if (usable.isEmpty()) return null

            return SubjectRef(
                ids = usable.map { it.id }.filter { it > 0 }.distinct().sorted(),
                name = usable.firstOrNull { it.name.isNotBlank() }?.name?.trim().orEmpty(),
                longName = usable.firstOrNull { it.longName?.isNotBlank() == true }
                    ?.longName
                    ?.trim()
                    .orEmpty(),
            )
        }

        fun freeText(text: String): SubjectRef? =
            text.trim().takeIf { it.isNotBlank() }?.let { SubjectRef(name = it) }
    }
}

@Serializable
data class LessonKey(
    val entryId: Int = 0,
    val date: Int,
    val startTime: Int,
) {
    fun matches(ref: LessonRef): Boolean = matches(ref.key)

    fun matches(key: LessonKey): Boolean =
        date == key.date && startTime == key.startTime &&
            (entryId <= 0 || key.entryId <= 0 || entryId == key.entryId)
}

@Serializable
data class LessonRef(
    val entryId: Int = 0,
    val date: Int,
    val startTime: Int,
    val endTime: Int,
    val subject: SubjectRef? = null,
    val teacher: String = "",
    val room: String = "",
) {
    val key: LessonKey get() = LessonKey(entryId, date, startTime)

    val subjectName: String get() = subject?.displayName.orEmpty()

    fun normalized(): LessonRef = copy(
        subject = subject?.normalized()?.takeIf { !it.isBlank },
        teacher = teacher.trim(),
        room = room.trim(),
    )
}

@Serializable
data class ReminderSpec(
    val date: Int,
    val minutesOfDay: Int = NotebookReminderTime.DEFAULT_MINUTES_OF_DAY,
)

@Serializable
data class DueRef(
    val date: Int,
    val lesson: LessonRef? = null,
    val reminder: ReminderSpec? = null,
)

@Serializable
data class NotebookItem(
    val id: String = "",
    val kind: NotebookKind = NotebookKind.NOTE,
    val title: String = "",
    val body: String = "",
    val subject: SubjectRef? = null,
    val origin: LessonRef? = null,
    val due: DueRef? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val pinnedAt: Long? = null,
    val archivedAt: Long? = null,
) {
    val isTask: Boolean get() = kind == NotebookKind.TASK
    val isCompleted: Boolean get() = isTask && completedAt != null
    val isPinned: Boolean get() = !isTask && pinnedAt != null
    val isArchived: Boolean get() = !isTask && archivedAt != null

    val displayTitle: String
        get() = title.ifBlank {
            body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
}

@Serializable
data class NotebookData(
    val schema: Int = NotebookSchema.CURRENT,
    val revision: Long = 0,
    val items: List<NotebookItem> = emptyList(),
    val updatedAt: Long = 0,
)

object NotebookSchema {
    const val CURRENT = 1
}

@Serializable
data class NotebookDraft(
    val id: String,
    val itemId: String? = null,
    val kind: NotebookKind = NotebookKind.NOTE,
    val title: String = "",
    val body: String = "",
    val subject: SubjectRef? = null,
    val origin: LessonRef? = null,
    val due: DueRef? = null,
    val baseUpdatedAt: Long? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class NotebookDraftData(
    val schema: Int = NotebookSchema.CURRENT,
    val revision: Long = 0,
    val drafts: Map<String, NotebookDraft> = emptyMap(),
)
