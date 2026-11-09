package sh.lsx.untisx.ui.timetable

import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.PeriodExam
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

private fun TimetableEntry.overlaps(exam: ExamInfo): Boolean =
    date == exam.date && minutesOf(startTime) < minutesOf(exam.endTime) && minutesOf(exam.startTime) < minutesOf(endTime)

/**
 * The subject an exam belongs to. Untis often leaves it empty for joint exams such as
 * "Klausur LK-A Abilänge" with the text "Eng; Ge; Ma"; then it is the subject of one of the
 * account's own lessons during the exam whose code ("MA" in "MA_L") or name ("Mathematik")
 * matches a word of the exam text. Null when that is not unambiguous.
 */
internal fun examSubject(
    exam: ExamInfo,
    entries: List<TimetableEntry>,
    subjects: Map<Int, MasterDataItem>,
): TimetableElement? {
    fun element(id: Int, fallback: String = ""): TimetableElement? {
        val name = subjects[id]?.name?.takeIf { it.isNotBlank() } ?: fallback
        return if (id > 0 || name.isNotBlank()) TimetableElement(id = id, name = name) else null
    }

    if (exam.subjectId != 0) return element(exam.subjectId, exam.subjectName)

    val words = Regex("""\p{L}{2,}""").findAll("${exam.name} ${exam.text}")
        .map { it.value.lowercase() }
        .toSet()
    if (words.isEmpty()) return null

    val candidates = entries
        .filter { !it.isEvent && it.overlaps(exam) }
        .flatMap { it.subject }
        .distinctBy { it.id }
        .filter { el ->
            val name = subjects[el.id]?.name?.takeIf { it.isNotBlank() } ?: el.name
            val code = name.substringBefore('_').lowercase()
            val long = (subjects[el.id]?.longName?.takeIf { it.isNotBlank() } ?: el.longName.orEmpty()).lowercase()

            code in words || (long.isNotBlank() && words.any { long.startsWith(it) })
        }

    return candidates.singleOrNull()?.let { element(it.id, it.name) }
}

/**
 * Adds one card per exam that covers lessons of other subjects (a block exam over several
 * periods), so the day shows the subject actually written instead of the planned lessons.
 * The planned lessons stay in the list and end up behind the exam card as "+N".
 */
internal fun withExamBlocks(
    entries: List<TimetableEntry>,
    exams: List<ExamInfo>,
    subjects: Map<Int, MasterDataItem>,
): List<TimetableEntry> {
    val blocks = exams.mapNotNull { exam ->
        if (exam.date == 0 || exam.startTime == 0 || exam.endTime <= exam.startTime) return@mapNotNull null

        val covered = entries.filter { !it.isEvent && it.overlaps(exam) }
        if (covered.isEmpty()) return@mapNotNull null

        val subject = examSubject(exam, entries, subjects) ?: return@mapNotNull null
        val coversOthers = covered.any { lesson -> lesson.subject.none { it.id == subject.id } }
        if (!coversOthers) return@mapNotNull null

        TimetableEntry(
            id = -(exam.id.takeIf { it != 0 } ?: (exam.date * 31 + exam.startTime)),
            date = exam.date,
            startTime = exam.startTime,
            endTime = exam.endTime,
            klasse = exam.klasseIds.distinct().map { TimetableElement(id = it) },
            teacher = exam.teacherIds.distinct().map { TimetableElement(id = it) },
            subject = listOf(subject),
            room = exam.roomIds.distinct().map { TimetableElement(id = it) },
            exam = PeriodExam(
                id = exam.id,
                examtype = exam.examType.takeIf { it.isNotBlank() },
                name = exam.name.takeIf { it.isNotBlank() },
                text = exam.text.takeIf { it.isNotBlank() },
            ),
        )
    }

    return if (blocks.isEmpty()) entries else entries + blocks
}
