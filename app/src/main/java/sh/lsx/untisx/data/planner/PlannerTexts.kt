package sh.lsx.untisx.data.planner

import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notebook.NotebookItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Words used in the planner notifications, kept out of the logic so it can be tested. */
data class PlannerWords(
    val dayShort: (DayOfWeek) -> String = { it.name.take(2).lowercase().replaceFirstChar(Char::uppercase) },
    val cancelled: String = "Entfall",
    val substituted: String = "Vertretung",
    val exams: String = "Klausuren",
    val events: String = "Termine",
    val homework: String = "Hausaufgaben",
    val holidays: String = "Ferien",
    val nothing: String = "Keine Ausfälle, Vertretungen oder Klausuren.",
)

/** The Monday to Friday after [today] (on a Monday the following week). */
fun nextSchoolWeek(today: LocalDate): Pair<LocalDate, LocalDate> {
    val monday = today.plusDays(((DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7).toLong().let { if (it == 0L) 7L else it })
    return monday to monday.plusDays(4)
}

object WeekPreview {
    fun lines(
        from: LocalDate,
        to: LocalDate,
        lessons: List<TimetableEntry>,
        exams: List<ExamInfo>,
        events: List<TimetableEntry>,
        holidays: List<Holiday>,
        homework: List<NotebookItem>,
        subjects: Map<Int, MasterDataItem>,
        words: PlannerWords = PlannerWords(),
    ): List<String> {
        val start = from.toUntisDate()
        val end = to.toUntisDate()
        val out = ArrayList<String>()

        fun subject(id: Int, fallback: String) = subjects[id]?.name?.takeIf { it.isNotBlank() } ?: fallback
        fun day(date: Int) = date.toUntisLocalDateOrNull()?.let { words.dayShort(it.dayOfWeek) }.orEmpty()

        holidays.filter { it.startDate <= end && it.endDate >= start }.forEach { holiday ->
            out += "${words.holidays}: ${holiday.longName.ifBlank { holiday.name }}"
        }

        lessons
            .filter { it.date in start..end && it.subject.isNotEmpty() && (it.cancelled || it.irregular) }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .groupBy { it.date }
            .forEach { (date, dayLessons) ->
                fun names(list: List<TimetableEntry>) = list
                    .map { subject(it.subject.first().id, it.subject.first().name) }
                    .distinct()
                    .joinToString(", ")

                val cancelled = dayLessons.filter { it.cancelled }
                val substituted = dayLessons.filter { it.irregular && !it.cancelled }
                val parts = listOfNotNull(
                    cancelled.takeIf { it.isNotEmpty() }?.let { "${words.cancelled} ${names(it)}" },
                    substituted.takeIf { it.isNotEmpty() }?.let { "${words.substituted} ${names(it)}" },
                )

                out += "${day(date)}: ${parts.joinToString(" · ")}"
            }

        exams.filter { it.date in start..end }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .takeIf { it.isNotEmpty() }
            ?.let { list ->
                out += "${words.exams}: " + list.joinToString(", ") { exam ->
                    listOf(day(exam.date), exam.examType, subject(exam.subjectId, exam.subjectName))
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            }

        events.filter { it.isEvent && it.date in start..end }
            .distinctBy { it.date to it.eventTitle }
            .takeIf { it.isNotEmpty() }
            ?.let { list ->
                out += "${words.events}: " + list.joinToString(", ") { "${day(it.date)} ${it.eventTitle.orEmpty()}".trim() }
            }

        homework.filter { it.isTask && !it.isCompleted && (it.due?.date ?: 0) in start..end }
            .sortedBy { it.due?.date }
            .takeIf { it.isNotEmpty() }
            ?.let { list ->
                out += "${words.homework}: " + list.joinToString(", ") { task ->
                    listOf(day(task.due!!.date), task.subject?.displayName.orEmpty().ifBlank { task.displayTitle })
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            }

        if (out.isEmpty()) out += words.nothing

        return out
    }
}

/** When to get up and leave for a school day, from its lessons (cancelled ones ignored). */
data class CommutePlan(
    val plannedStart: Int,
    val actualStart: Int?,
    val plannedEnd: Int,
    val actualEnd: Int?,
    val leaveAt: LocalTime?,
    val wakeAt: LocalTime?,
) {
    val allCancelled: Boolean get() = actualStart == null
    val startsLater: Boolean get() = actualStart != null && actualStart > plannedStart
    val endsEarlier: Boolean get() = actualEnd != null && actualEnd < plannedEnd
    val changed: Boolean get() = allCancelled || startsLater || endsEarlier
}

object Commute {
    fun plan(lessons: List<TimetableEntry>, wayMinutes: Int, prepMinutes: Int): CommutePlan? {
        val real = lessons.filter { it.subject.isNotEmpty() && it.endTime > it.startTime }
        if (real.isEmpty()) return null

        val held = real.filter { !it.cancelled }
        val actualStart = held.minOfOrNull { it.startTime }
        val leave = actualStart?.let { time(it).minusMinutes(wayMinutes.toLong()) }

        return CommutePlan(
            plannedStart = real.minOf { it.startTime },
            actualStart = actualStart,
            plannedEnd = real.maxOf { it.endTime },
            actualEnd = held.maxOfOrNull { it.endTime },
            leaveAt = leave,
            wakeAt = leave?.minusMinutes(prepMinutes.toLong()),
        )
    }

    fun time(untis: Int): LocalTime = LocalTime.of((untis / 100).coerceIn(0, 23), (untis % 100).coerceIn(0, 59))
}
