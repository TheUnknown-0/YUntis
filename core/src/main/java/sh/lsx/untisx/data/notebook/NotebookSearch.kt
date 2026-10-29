package sh.lsx.untisx.data.notebook

import java.util.Locale

enum class NotebookStatusFilter { ALL, OPEN, COMPLETED, ARCHIVED, PINNED }

data class NotebookQuery(
    val text: String = "",
    val kinds: Set<NotebookKind> = emptySet(),
    val status: NotebookStatusFilter = NotebookStatusFilter.ALL,
    val subject: SubjectRef? = null,
    val linkedLesson: LessonKey? = null,
)

data class NotebookLessonSummary(
    val openTasks: Int = 0,
    val dueTasks: Int = 0,
    val pinnedNotes: Int = 0,
    val noteCount: Int = 0,
) {
    val badgeCount: Int get() = openTasks + noteCount
}

data class NotebookLessonLinks(
    val sourced: List<NotebookItem> = emptyList(),
    val due: List<NotebookItem> = emptyList(),
    val pinned: List<NotebookItem> = emptyList(),
)

object NotebookSearch {
    fun apply(items: List<NotebookItem>, query: NotebookQuery): List<NotebookItem> {
        val text = query.text.trim()

        return items.filter { item ->
            (query.kinds.isEmpty() || item.kind in query.kinds) &&
                matchesStatus(item, query.status) &&
                (query.subject == null || item.subject?.matches(query.subject) == true) &&
                (query.linkedLesson == null || matchesLinkedLesson(item, query.linkedLesson)) &&
                (text.isBlank() || matchesText(item, text))
        }
    }

    fun sort(items: List<NotebookItem>): List<NotebookItem> =
        items.sortedWith(Comparator { left, right -> compare(left, right) })

    fun summarize(items: List<NotebookItem>, lesson: LessonRef): NotebookLessonSummary {
        var openTasks = 0
        var dueTasks = 0
        var pinnedNotes = 0
        var noteCount = 0

        items.forEach { item ->
            val originMatch = lessonMatches(item.origin, lesson)
            val dueMatch = lessonMatches(item.due?.lesson, lesson)
            val subjectMatch = item.subject?.matches(lesson.subject) == true

            if (item.isTask) {
                if (!item.isCompleted && (originMatch || dueMatch)) openTasks++
                if (!item.isCompleted && dueMatch) dueTasks++
            } else if (!item.isArchived) {
                val related = originMatch || dueMatch || subjectMatch

                if (related) {
                    noteCount++
                    if (item.isPinned) pinnedNotes++
                }
            }
        }

        return NotebookLessonSummary(
            openTasks = openTasks,
            dueTasks = dueTasks,
            pinnedNotes = pinnedNotes,
            noteCount = noteCount,
        )
    }

    fun linksForLesson(items: List<NotebookItem>, lesson: LessonRef): NotebookLessonLinks {
        val due = items.filter {
            it.isTask && lessonMatches(it.due?.lesson, lesson)
        }
        val dueIds = due.mapTo(mutableSetOf<String>()) { it.id }
        val sourced = items.filter { it.id !in dueIds && lessonMatches(it.origin, lesson) }
        val pinned = items.filter { item ->
            !item.isTask && item.isPinned && !item.isArchived &&
                (
                    lessonMatches(item.origin, lesson) ||
                        lessonMatches(item.due?.lesson, lesson) ||
                        item.subject?.matches(lesson.subject) == true
                    )
        }

        return NotebookLessonLinks(sourced = sourced, due = due, pinned = pinned)
    }

    private fun matchesText(item: NotebookItem, text: String): Boolean {
        val needle = text.lowercase(Locale.ROOT)

        return item.title.lowercase(Locale.ROOT).contains(needle) ||
            item.body.lowercase(Locale.ROOT).contains(needle) ||
            item.subject?.name?.lowercase(Locale.ROOT)?.contains(needle) == true ||
            item.subject?.longName?.lowercase(Locale.ROOT)?.contains(needle) == true
    }

    private fun matchesStatus(item: NotebookItem, status: NotebookStatusFilter): Boolean = when (status) {
        NotebookStatusFilter.ALL -> true
        NotebookStatusFilter.OPEN -> if (item.isTask) !item.isCompleted else !item.isArchived
        NotebookStatusFilter.COMPLETED -> item.isTask && item.isCompleted
        NotebookStatusFilter.ARCHIVED -> !item.isTask && item.isArchived
        NotebookStatusFilter.PINNED -> !item.isTask && item.isPinned && !item.isArchived
    }

    private fun matchesLinkedLesson(item: NotebookItem, key: LessonKey): Boolean =
        item.origin?.key?.matches(key) == true || item.due?.lesson?.key?.matches(key) == true

    private fun lessonMatches(stored: LessonRef?, lesson: LessonRef): Boolean {
        if (stored == null) return false
        if (!stored.key.matches(lesson.key)) return false

        val storedSubject = stored.subject
        val lessonSubject = lesson.subject

        return storedSubject == null ||
            lessonSubject == null ||
            storedSubject.matches(lessonSubject)
    }

    private fun compare(left: NotebookItem, right: NotebookItem): Int {
        val leftRank = rank(left)
        val rightRank = rank(right)

        if (leftRank != rightRank) return leftRank.compareTo(rightRank)

        if (leftRank == RANK_OPEN_TASK) {
            val leftDue = left.due?.date ?: Int.MAX_VALUE
            val rightDue = right.due?.date ?: Int.MAX_VALUE

            if (leftDue != rightDue) return leftDue.compareTo(rightDue)
        }

        val updated = right.updatedAt.compareTo(left.updatedAt)
        if (updated != 0) return updated

        return left.id.compareTo(right.id)
    }

    private fun rank(item: NotebookItem): Int = when {
        item.isTask && item.isCompleted -> RANK_TERMINAL
        item.isTask -> RANK_OPEN_TASK
        item.isArchived -> RANK_TERMINAL
        item.isPinned -> RANK_PINNED_NOTE
        else -> RANK_NOTE
    }

    private const val RANK_PINNED_NOTE = 0
    private const val RANK_OPEN_TASK = 1
    private const val RANK_NOTE = 2
    private const val RANK_TERMINAL = 3
}
