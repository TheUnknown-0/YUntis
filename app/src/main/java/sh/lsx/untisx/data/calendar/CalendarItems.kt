package sh.lsx.untisx.data.calendar

import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notebook.NotebookItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The kinds of entries that can be copied into a calendar; each can be switched on or off. */
enum class CalendarCategory {
    EXAMS,
    OWN_EXAMS,
    EVENTS,
    HOLIDAYS,
    HOMEWORK,
    CANCELLED,
    SUBSTITUTED,
}

/** One entry to be written into the device calendar. [key] identifies it across runs. */
data class CalendarItem(
    val key: String,
    val category: CalendarCategory,
    val title: String,
    val description: String,
    val location: String,
    val allDay: Boolean,
    /** For all-day items the first day at midnight; [end] is then the day after the last day. */
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    /** Changes whenever something visible in the calendar changes. */
    val fingerprint: String get() = listOf(title, description, location, allDay, start, end).joinToString("|")
}

/** Localised words used in titles, so the builder stays free of Android resources. */
data class CalendarTexts(
    val exam: String = "Prüfung",
    val event: String = "Veranstaltung",
    val homework: String = "HA",
    val cancelled: String = "Entfall",
    val substituted: String = "Vertretung",
    val teacher: String = "Lehrkraft",
    val room: String = "Raum",
    val instead: String = "statt",
)

/** Everything one account contributes to the export. */
data class CalendarSource(
    val accountId: String,
    /** Put in front of every title when several accounts are exported, e.g. "[ben] ". */
    val accountLabel: String? = null,
    val exams: List<ExamInfo> = emptyList(),
    val ownExams: List<ExamInfo> = emptyList(),
    val events: List<TimetableEntry> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val notebook: List<NotebookItem> = emptyList(),
    val lessons: List<TimetableEntry> = emptyList(),
    val subjects: Map<Int, MasterDataItem> = emptyMap(),
    val teachers: Map<Int, MasterDataItem> = emptyMap(),
    val rooms: Map<Int, MasterDataItem> = emptyMap(),
)

data class CalendarSelection(
    val categories: Set<CalendarCategory>,
    /** Exam types (lower case) the user switched off, e.g. "test". */
    val excludedExamTypes: Set<String> = emptySet(),
)

object CalendarItems {
    /** How far back items are still written; older ones are left alone in the calendar. */
    const val DAYS_BACK = 14L

    fun examTypeKey(type: String): String = type.trim().lowercase()

    fun build(
        source: CalendarSource,
        selection: CalendarSelection,
        today: LocalDate,
        texts: CalendarTexts = CalendarTexts(),
    ): List<CalendarItem> {
        val from = today.minusDays(DAYS_BACK).toUntisDate()
        val prefix = source.accountLabel?.takeIf { it.isNotBlank() }?.let { "[$it] " }.orEmpty()
        val out = ArrayList<CalendarItem>()

        fun key(kind: String, vararg parts: Any) = (listOf(source.accountId, kind) + parts.map { it.toString() }).joinToString(":")

        fun subjectName(id: Int, fallback: String = ""): String {
            val item = source.subjects[id]
            return item?.longName?.takeIf { it.isNotBlank() } ?: item?.name?.takeIf { it.isNotBlank() } ?: fallback
        }

        fun names(ids: List<Int>, map: Map<Int, MasterDataItem>) =
            ids.mapNotNull { id -> map[id]?.let { it.longName.ifBlank { it.name } }?.takeIf { it.isNotBlank() } }

        fun timed(date: Int, start: Int, end: Int): Pair<LocalDateTime, LocalDateTime>? {
            val day = date.toUntisLocalDateOrNull() ?: return null
            val s = day.atTime(untisTime(start))
            val e = day.atTime(untisTime(end)).takeIf { it.isAfter(s) } ?: s.plusMinutes(45)
            return s to e
        }

        fun exam(exam: ExamInfo, category: CalendarCategory) {
            if (exam.date < from) return
            if (examTypeKey(exam.examType) in selection.excludedExamTypes) return

            val subject = subjectName(exam.subjectId, exam.subjectName)
            val type = exam.examType.ifBlank { texts.exam }
            val name = exam.name.takeIf { it.isNotBlank() && !it.equals(type, true) && !it.equals(subject, true) }
            val title = prefix + listOf(type, subject).filter { it.isNotBlank() }.joinToString(" ") +
                (name?.let { ": $it" } ?: "")

            val description = listOfNotNull(
                exam.text.takeIf { it.isNotBlank() },
                names(exam.teacherIds, source.teachers).takeIf { it.isNotEmpty() }?.let { "${texts.teacher}: ${it.joinToString(", ")}" },
            ).joinToString("\n")

            val allDay = exam.startTime == 0 && exam.endTime == 0
            val (start, end) = if (allDay) {
                val day = exam.date.toUntisLocalDateOrNull() ?: return
                day.atStartOfDay() to day.plusDays(1).atStartOfDay()
            } else {
                timed(exam.date, exam.startTime, exam.endTime) ?: return
            }

            out += CalendarItem(
                key = key(if (category == CalendarCategory.OWN_EXAMS) "own" else "exam", exam.id, exam.date, exam.startTime),
                category = category,
                title = title,
                description = description,
                location = names(exam.roomIds, source.rooms).joinToString(", "),
                allDay = allDay,
                start = start,
                end = end,
            )
        }

        if (CalendarCategory.EXAMS in selection.categories) source.exams.forEach { exam(it, CalendarCategory.EXAMS) }
        if (CalendarCategory.OWN_EXAMS in selection.categories) source.ownExams.forEach { exam(it, CalendarCategory.OWN_EXAMS) }

        if (CalendarCategory.EVENTS in selection.categories) {
            source.events.filter { it.isEvent && it.date >= from }.forEach { event ->
                val title = event.eventTitle?.trim()?.ifBlank { null } ?: texts.event
                val allDay = event.startTime == 0 && event.endTime == 0
                val (start, end) = if (allDay) {
                    val day = event.date.toUntisLocalDateOrNull() ?: return@forEach
                    day.atStartOfDay() to day.plusDays(1).atStartOfDay()
                } else {
                    timed(event.date, event.startTime, event.endTime) ?: return@forEach
                }

                out += CalendarItem(
                    key = key("event", event.date, event.startTime, title.hashCode()),
                    category = CalendarCategory.EVENTS,
                    title = prefix + title,
                    description = listOfNotNull(event.info, event.substText)
                        .filter { it.isNotBlank() && it != title }
                        .distinct()
                        .joinToString("\n"),
                    location = names(event.room.map { it.id }, source.rooms).ifEmpty { event.room.map { it.name } }.joinToString(", "),
                    allDay = allDay,
                    start = start,
                    end = end,
                )
            }
        }

        if (CalendarCategory.HOLIDAYS in selection.categories) {
            source.holidays.filter { it.endDate >= from }.forEach { holiday ->
                val first = holiday.startDate.toUntisLocalDateOrNull() ?: return@forEach
                val last = holiday.endDate.toUntisLocalDateOrNull()?.takeIf { !it.isBefore(first) } ?: first

                out += CalendarItem(
                    key = key("holiday", holiday.id, holiday.startDate),
                    category = CalendarCategory.HOLIDAYS,
                    title = prefix + holiday.longName.ifBlank { holiday.name },
                    description = "",
                    location = "",
                    allDay = true,
                    start = first.atStartOfDay(),
                    end = last.plusDays(1).atStartOfDay(),
                )
            }
        }

        if (CalendarCategory.HOMEWORK in selection.categories) {
            source.notebook.filter { it.isTask && !it.isCompleted && (it.due?.date ?: 0) >= from }.forEach { task ->
                val due = task.due ?: return@forEach
                val day = due.date.toUntisLocalDateOrNull() ?: return@forEach
                val subject = task.subject?.displayName.orEmpty()
                val title = listOf(texts.homework, subject).filter { it.isNotBlank() }.joinToString(" ") +
                    task.displayTitle.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()

                out += CalendarItem(
                    key = key("hw", task.id),
                    category = CalendarCategory.HOMEWORK,
                    title = prefix + title,
                    description = task.body,
                    location = "",
                    allDay = true,
                    start = day.atStartOfDay(),
                    end = day.plusDays(1).atStartOfDay(),
                )
            }
        }

        val wantCancelled = CalendarCategory.CANCELLED in selection.categories
        val wantSubstituted = CalendarCategory.SUBSTITUTED in selection.categories

        if (wantCancelled || wantSubstituted) {
            val todayDate = today.toUntisDate()

            source.lessons
                .filter { it.subject.isNotEmpty() && it.date >= todayDate }
                .distinctBy { Triple(it.id, it.date, it.startTime) }
                .forEach { lesson ->
                    val category = when {
                        lesson.cancelled && wantCancelled -> CalendarCategory.CANCELLED
                        lesson.irregular && !lesson.cancelled && wantSubstituted -> CalendarCategory.SUBSTITUTED
                        else -> return@forEach
                    }
                    val (start, end) = timed(lesson.date, lesson.startTime, lesson.endTime) ?: return@forEach
                    val subject = lesson.subject.first().let { subjectName(it.id, it.longName ?: it.name) }
                    val word = if (category == CalendarCategory.CANCELLED) texts.cancelled else texts.substituted

                    val changes = buildList {
                        lesson.teacher.forEach { t ->
                            val now = names(listOf(t.id), source.teachers).firstOrNull() ?: t.name
                            val before = t.orgId?.takeIf { it > 0 && it != t.id }
                                ?.let { names(listOf(it), source.teachers).firstOrNull() ?: t.orgName }
                            if (before != null) add("${texts.teacher}: $now (${texts.instead} $before)")
                        }
                        lesson.room.forEach { r ->
                            val now = names(listOf(r.id), source.rooms).firstOrNull() ?: r.name
                            val before = r.orgId?.takeIf { it > 0 && it != r.id }
                                ?.let { names(listOf(it), source.rooms).firstOrNull() ?: r.orgName }
                            if (before != null) add("${texts.room}: $now (${texts.instead} $before)")
                        }
                        listOfNotNull(lesson.lstext, lesson.substText, lesson.info)
                            .filter { it.isNotBlank() && !it.trim().equals(word, ignoreCase = true) }
                            .forEach { add(it) }
                    }

                    out += CalendarItem(
                        key = key("chg", lesson.id, lesson.date, lesson.startTime),
                        category = category,
                        title = "$prefix$word: $subject",
                        description = changes.distinct().joinToString("\n"),
                        location = if (category == CalendarCategory.CANCELLED) "" else names(lesson.room.map { it.id }, source.rooms).joinToString(", "),
                        allDay = false,
                        start = start,
                        end = end,
                    )
                }
        }

        return out.distinctBy { it.key }
    }

    private fun untisTime(value: Int): LocalTime =
        LocalTime.of((value / 100).coerceIn(0, 23), (value % 100).coerceIn(0, 59))
}
