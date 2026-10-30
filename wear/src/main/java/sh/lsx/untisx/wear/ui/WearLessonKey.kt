package sh.lsx.untisx.wear.ui

import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.wear.WearTimetablePayload

data class WearLessonKey(val id: Int, val date: Int, val start: Int, val subjects: List<Int>)

fun TimetableEntry.wearKey() = WearLessonKey(
    id, date, startTime, subject.map { it.id }.sorted(),
)

fun WearTimetablePayload.findLesson(key: WearLessonKey?): TimetableEntry? {
    if (key == null) return null

    val entries = weeks.values.asSequence().flatten()
    return entries.firstOrNull { it.wearKey() == key }
        ?: if (key.id == 0) null else entries.filter { it.id == key.id && it.date == key.date }.toList().singleOrNull()
}
