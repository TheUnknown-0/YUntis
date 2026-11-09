package sh.lsx.untisx.ui.screens

import sh.lsx.untisx.data.model.TimetableEntry

/** What the own timetable tells about one teacher. */
internal data class TeacherInfo(
    /** Subject ids the teacher regularly teaches the account, most lessons first. */
    val subjectIds: List<Int>,
    /** The next own lessons with this teacher (not yet over), earliest first. */
    val upcoming: List<TimetableEntry>,
)

/**
 * Collects the subjects and next lessons of [teacherId] from the account's own lessons.
 * A lesson the teacher only covers as a substitute does not make the subject "theirs".
 */
internal fun teacherInfo(
    teacherId: Int,
    ownEntries: List<TimetableEntry>,
    nowDate: Int,
    nowTime: Int,
    upcomingLimit: Int = 5,
): TeacherInfo {
    val withTeacher = ownEntries
        .filter { entry -> !entry.isEvent && entry.teacher.any { it.id == teacherId } }
        .distinctBy { Triple(it.id, it.date, it.startTime) }

    val regular = withTeacher.filter { entry ->
        entry.teacher.none { it.id == teacherId && (it.orgId ?: 0) > 0 && it.orgId != teacherId }
    }

    val subjectIds = regular
        .flatMap { entry -> entry.subject.map { it.id }.filter { it > 0 } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    val upcoming = withTeacher
        .filter { it.date > nowDate || (it.date == nowDate && it.endTime > nowTime) }
        .sortedWith(compareBy({ it.date }, { it.startTime }))
        .take(upcomingLimit)

    return TeacherInfo(subjectIds, upcoming)
}
