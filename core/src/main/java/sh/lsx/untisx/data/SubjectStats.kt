package sh.lsx.untisx.data

import kotlinx.serialization.Serializable

/** One subject on one day. Event fields count the part that overlaps school events (trips, exams weeks, ...). */
@Serializable
data class SubjectDayStats(
    val planned: Int = 0,
    val cancelled: Int = 0,
    val substituted: Int = 0,
    val plannedMinutes: Int = 0,
    val cancelledMinutes: Int = 0,
    val eventPlanned: Int = 0,
    val eventCancelled: Int = 0,
    val eventSubstituted: Int = 0,
    val eventPlannedMinutes: Int = 0,
    val eventCancelledMinutes: Int = 0,
) {
    fun add(cancelled: Boolean, substituted: Boolean, minutes: Int, event: Boolean): SubjectDayStats = copy(
        planned = planned + 1,
        cancelled = this.cancelled + if (cancelled) 1 else 0,
        substituted = this.substituted + if (substituted) 1 else 0,
        plannedMinutes = plannedMinutes + minutes,
        cancelledMinutes = cancelledMinutes + if (cancelled) minutes else 0,
        eventPlanned = eventPlanned + if (event) 1 else 0,
        eventCancelled = eventCancelled + if (event && cancelled) 1 else 0,
        eventSubstituted = eventSubstituted + if (event && substituted) 1 else 0,
        eventPlannedMinutes = eventPlannedMinutes + if (event) minutes else 0,
        eventCancelledMinutes = eventCancelledMinutes + if (event && cancelled) minutes else 0,
    )
}

/**
 * One teacher on one day, counted for the teacher who was planned (the original one when
 * another teacher covers). Lessons during school events are left out: a trip or exam week
 * cancels lessons without the teacher being absent.
 */
@Serializable
data class TeacherDayStats(
    val planned: Int = 0,
    val cancelled: Int = 0,
    val substituted: Int = 0,
)

/** How often a teacher's planned lessons with the account did not take place with them. */
data class TeacherTotals(
    val planned: Int,
    val cancelled: Int,
    val substituted: Int,
) {
    val absent: Int get() = cancelled + substituted
    val absentRate: Float get() = if (planned == 0) 0f else absent.toFloat() / planned
}

/**
 * Totals for one subject. Every planned lesson is exactly one of: held regularly, substituted, cancelled.
 * The cancellation rate only relates to this subject's own lessons.
 */
data class SubjectTotals(
    val subjectId: Int,
    val planned: Int,
    val cancelled: Int,
    val substituted: Int,
    val plannedMinutes: Int,
    val cancelledMinutes: Int,
) {
    val held: Int get() = (planned - cancelled - substituted).coerceAtLeast(0)
    val cancelRate: Float get() = if (planned == 0) 0f else cancelled.toFloat() / planned
    val substituteRate: Float get() = if (planned == 0) 0f else substituted.toFloat() / planned
    val heldRate: Float get() = if (planned == 0) 0f else held.toFloat() / planned
}

object SubjectStats {

    fun totals(days: Collection<DayStats>, includeEvents: Boolean): List<SubjectTotals> {
        val sums = HashMap<Int, IntArray>()

        for (day in days) {
            for ((id, s) in day.subjectDays) {
                val acc = sums.getOrPut(id) { IntArray(5) }
                if (includeEvents) {
                    acc[0] += s.planned; acc[1] += s.cancelled; acc[2] += s.substituted
                    acc[3] += s.plannedMinutes; acc[4] += s.cancelledMinutes
                } else {
                    acc[0] += s.planned - s.eventPlanned
                    acc[1] += s.cancelled - s.eventCancelled
                    acc[2] += s.substituted - s.eventSubstituted
                    acc[3] += s.plannedMinutes - s.eventPlannedMinutes
                    acc[4] += s.cancelledMinutes - s.eventCancelledMinutes
                }
            }
        }

        return sums
            .map { (id, a) -> SubjectTotals(id, a[0], a[1], a[2], a[3], a[4]) }
            .filter { it.planned > 0 }
            .sortedWith(compareByDescending<SubjectTotals> { it.planned }.thenBy { it.subjectId })
    }

    fun teacherTotals(days: Collection<DayStats>, teacherId: Int): TeacherTotals {
        var planned = 0
        var cancelled = 0
        var substituted = 0

        for (day in days) {
            val s = day.teacherDays[teacherId] ?: continue
            planned += s.planned
            cancelled += s.cancelled
            substituted += s.substituted
        }

        return TeacherTotals(planned, cancelled, substituted)
    }

    /** True when some counted days were recorded before per-subject data existed. */
    fun hasOldDays(days: Collection<DayStats>): Boolean =
        days.any { it.lessons > 0 && it.subjectDays.isEmpty() }
}
