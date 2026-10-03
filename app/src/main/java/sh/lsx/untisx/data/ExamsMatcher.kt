package sh.lsx.untisx.data

import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.TimetableEntry
import kotlin.math.abs

object ExamsMatcher {

    fun best(entry: TimetableEntry, exams: List<ExamInfo>): ExamInfo? =
        exams
            .filter { matches(entry, it) }
            .minByOrNull { distanceMinutes(entry.startTime, it.startTime) }

    fun matches(entry: TimetableEntry, exam: ExamInfo): Boolean {
        if (entry.date == 0 || entry.date != exam.date) return false
        if (entry.subject.isEmpty()) return false
        if (!timeOverlaps(entry, exam)) return false

        val knownEntrySubjects = entry.subject.map { it.id }.filter { it > 0 }
        val subjectKnown = exam.subjectId != 0

        if (subjectKnown && knownEntrySubjects.isNotEmpty() && exam.subjectId !in knownEntrySubjects) {
            return false
        }

        return true
    }

    private fun timeOverlaps(entry: TimetableEntry, exam: ExamInfo): Boolean {
        if (entry.startTime == 0 || exam.startTime == 0) return true

        val entryStart = minutes(entry.startTime)
        val entryEnd = if (entry.endTime == 0) entryStart else minutes(entry.endTime)
        val examStart = minutes(exam.startTime)
        val examEnd = if (exam.endTime == 0) examStart else minutes(exam.endTime)

        return examStart <= entryEnd && entryStart <= examEnd
    }

    private fun distanceMinutes(a: Int, b: Int): Int = abs(minutes(a) - minutes(b))

    private fun minutes(time: Int): Int = time / 100 * 60 + time % 100
}
