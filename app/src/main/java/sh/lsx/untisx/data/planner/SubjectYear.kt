package sh.lsx.untisx.data.planner

import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import java.time.DayOfWeek
import java.time.LocalDate

enum class SubjectDayState { HELD, SUBSTITUTED, PARTLY_CANCELLED, CANCELLED, PLANNED }

object SubjectYear {
    /** State of a subject on each day: recorded days from the statistics, upcoming ones from the loaded weeks. */
    fun dayStates(
        subjectId: Int,
        stats: SchoolYearStats?,
        lessons: List<TimetableEntry>,
        today: LocalDate,
    ): Map<Int, SubjectDayState> {
        val out = HashMap<Int, SubjectDayState>()
        val todayInt = today.toUntisDate()

        stats?.days?.forEach { (key, day) ->
            val date = key.toIntOrNull() ?: return@forEach
            val subject = day.subjectDays[subjectId] ?: return@forEach
            if (subject.planned <= 0) return@forEach

            out[date] = when {
                subject.cancelled >= subject.planned -> SubjectDayState.CANCELLED
                subject.cancelled > 0 -> SubjectDayState.PARTLY_CANCELLED
                subject.substituted > 0 -> SubjectDayState.SUBSTITUTED
                else -> SubjectDayState.HELD
            }
        }

        lessons.filter { it.date >= todayInt && it.subject.any { s -> s.id == subjectId } }
            .groupBy { it.date }
            .forEach { (date, day) ->
                if (date in out && date < todayInt) return@forEach
                out[date] = when {
                    day.all { it.cancelled } -> SubjectDayState.CANCELLED
                    day.any { it.cancelled } -> SubjectDayState.PARTLY_CANCELLED
                    day.any { it.irregular } -> SubjectDayState.SUBSTITUTED
                    else -> SubjectDayState.PLANNED
                }
            }

        return out
    }

    /** Average planned lessons of the subject per school week, from the loaded weeks that contain it. */
    fun lessonsPerWeek(subjectId: Int, weeks: Map<Int, List<TimetableEntry>>): Double {
        val counts = weeks.values
            .map { week -> week.filter { it.subject.any { s -> s.id == subjectId } }.distinctBy { it.date to it.startTime }.size }
            .filter { it > 0 }
        return if (counts.isEmpty()) 0.0 else counts.average()
    }

    /** School weeks (Monday to Friday not completely in the holidays) after [today] up to [end]. */
    fun schoolWeeksLeft(today: LocalDate, end: LocalDate, holidays: List<Holiday>): Int {
        var monday = today.with(DayOfWeek.MONDAY).plusWeeks(1)
        var weeks = 0
        while (!monday.isAfter(end)) {
            val days = (0..4).map { monday.plusDays(it.toLong()).toUntisDate() }
            if (days.any { date -> holidays.none { date in it.startDate..it.endDate } }) weeks++
            monday = monday.plusWeeks(1)
        }
        return weeks
    }

    /** Rough number of lessons until the end of the school year: rest of this week plus average times weeks left. */
    fun lessonsLeft(
        subjectId: Int,
        weeks: Map<Int, List<TimetableEntry>>,
        holidays: List<Holiday>,
        today: LocalDate,
        end: LocalDate,
    ): Int {
        val thisMonday = today.with(DayOfWeek.MONDAY).toUntisDate()
        val todayInt = today.toUntisDate()
        val restOfWeek = weeks[thisMonday].orEmpty()
            .filter { it.date >= todayInt && !it.cancelled && it.subject.any { s -> s.id == subjectId } }
            .distinctBy { it.date to it.startTime }
            .size
        return restOfWeek + Math.round(lessonsPerWeek(subjectId, weeks) * schoolWeeksLeft(today, end, holidays)).toInt()
    }
}
