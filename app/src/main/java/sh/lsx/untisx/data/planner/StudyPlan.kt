package sh.lsx.untisx.data.planner

import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.ui.timetable.freeGaps
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class StudySlotKind { FREE_PERIOD, AFTERNOON, DAY_OFF }

/** A suggested time to study for an exam. */
data class StudySlot(val date: LocalDate, val start: LocalTime, val end: LocalTime, val kind: StudySlotKind) {
    val key: String get() = "${date.toUntisDate()}"
}

data class ExamCountdown(
    val days: Long,
    /** Lessons of the subject that still take place before the exam. */
    val lessonsLeft: Int,
    /** False when some weeks up to the exam are not loaded yet, so [lessonsLeft] is a lower bound. */
    val lessonsComplete: Boolean,
)

object StudyPlan {
    /** Earliest start of an afternoon session and the latest end. */
    private val AFTERNOON_EARLIEST = LocalTime.of(15, 0)
    private val EVENING_LATEST = LocalTime.of(20, 0)
    private val DAY_OFF_START = LocalTime.of(10, 0)

    private fun time(untis: Int) = LocalTime.of((untis / 100).coerceIn(0, 23), (untis % 100).coerceIn(0, 59))

    fun matchesSubject(lesson: TimetableEntry, exam: ExamInfo): Boolean {
        val element = lesson.subject.firstOrNull() ?: return false
        return if (exam.subjectId > 0) {
            element.id == exam.subjectId
        } else {
            exam.subjectName.isNotBlank() && (element.name.equals(exam.subjectName, true) || element.longName?.equals(exam.subjectName, true) == true)
        }
    }

    fun countdown(exam: ExamInfo, now: LocalDateTime, lessons: List<TimetableEntry>, loadedWeeks: Set<Int>): ExamCountdown? {
        val examDay = exam.date.toUntisLocalDateOrNull() ?: return null
        val examStart = examDay.atTime(time(exam.startTime))
        val nowDate = now.toLocalDate().toUntisDate()
        val nowTime = now.hour * 100 + now.minute

        val left = lessons
            .filter { !it.cancelled && matchesSubject(it, exam) }
            .filter { it.date > nowDate || (it.date == nowDate && it.startTime >= nowTime) }
            .filter { it.date < exam.date || (it.date == exam.date && it.endTime <= exam.startTime) }
            .distinctBy { Triple(it.date, it.startTime, it.subject.firstOrNull()?.id) }
            .size

        // every Monday from now up to the exam week must be loaded for an exact number
        var monday = now.toLocalDate().with(DayOfWeek.MONDAY)
        var complete = true
        while (!monday.isAfter(examDay)) {
            if (monday.toUntisDate() !in loadedWeeks) complete = false
            monday = monday.plusWeeks(1)
        }

        return ExamCountdown(
            days = ChronoUnit.DAYS.between(now.toLocalDate(), examDay).coerceAtLeast(0),
            lessonsLeft = left,
            lessonsComplete = complete,
        ).takeIf { !examStart.isBefore(now) }
    }

    /**
     * Up to [sessions] study slots of [minutes] each, one per day, spread evenly over the days from
     * [today] to the day before the exam. Free periods are preferred over afternoons.
     */
    fun suggest(
        exam: ExamInfo,
        today: LocalDate,
        lessons: List<TimetableEntry>,
        holidays: List<Holiday>,
        sessions: Int,
        minutes: Int = 45,
        otherExamDays: Set<Int> = emptySet(),
        /** On [today] only slots starting after this time count. */
        earliestToday: LocalTime? = null,
    ): List<StudySlot> {
        val examDay = exam.date.toUntisLocalDateOrNull() ?: return emptyList()
        if (!examDay.isAfter(today) || sessions <= 0) return emptyList()

        val byDate = lessons.filter { it.subject.isNotEmpty() }.groupBy { it.date }
        val candidates = ArrayList<StudySlot>()
        var day = today

        while (day.isBefore(examDay)) {
            val date = day.toUntisDate()
            val dayLessons = byDate[date].orEmpty()
            val isHoliday = holidays.any { date in it.startDate..it.endDate }
            val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

            val slot = when {
                date in otherExamDays -> null
                weekend || isHoliday || dayLessons.none { !it.cancelled } ->
                    StudySlot(day, DAY_OFF_START, DAY_OFF_START.plusMinutes(minutes.toLong()), StudySlotKind.DAY_OFF)
                else -> {
                    val gap = freeGaps(dayLessons).firstOrNull { it.minutes >= minutes }
                    if (gap != null) {
                        StudySlot(day, time(gap.start), time(gap.start).plusMinutes(minutes.toLong()), StudySlotKind.FREE_PERIOD)
                    } else {
                        val lastEnd = time(dayLessons.filter { !it.cancelled }.maxOf { it.endTime })
                        val start = maxOf(lastEnd.plusMinutes(60), AFTERNOON_EARLIEST)
                        val end = start.plusMinutes(minutes.toLong())
                        if (end.isAfter(EVENING_LATEST)) null else StudySlot(day, start, end, StudySlotKind.AFTERNOON)
                    }
                }
            }

            val tooLate = day == today && earliestToday != null && slot != null && !slot.start.isAfter(earliestToday)
            if (slot != null && !tooLate) candidates += slot
            day = day.plusDays(1)
        }

        if (candidates.size <= sessions) return candidates

        // spread evenly, always ending with the day closest to the exam
        val step = candidates.size.toDouble() / sessions
        return (0 until sessions).map { i ->
            candidates[(candidates.size - 1 - (i * step).toInt()).coerceIn(0, candidates.lastIndex)]
        }.distinct().sortedBy { it.date }
    }
}
