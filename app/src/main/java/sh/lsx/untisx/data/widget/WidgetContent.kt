package sh.lsx.untisx.data.widget

import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** One line of the day widget. */
data class WidgetLesson(
    val start: Int,
    val end: Int,
    val subject: String,
    /** Full subject name from the school, empty if it has none. */
    val subjectLong: String = "",
    val room: String,
    val teacher: String = "",
    val cancelled: Boolean,
    val changed: Boolean,
    val info: String,
)

/** What the widgets show: the day (today, or the next school day once today is over) and the next lesson. */
data class WidgetDay(
    val date: LocalDate,
    val lessons: List<WidgetLesson>,
    /** The lesson running now or coming next on [date]; null when the day is over. */
    val next: WidgetLesson?,
    val running: Boolean,
)

object WidgetContent {
    fun lessonsOf(
        day: List<TimetableEntry>,
        subjects: Map<Int, MasterDataItem>,
        rooms: Map<Int, MasterDataItem>,
        teachers: Map<Int, MasterDataItem> = emptyMap(),
    ): List<WidgetLesson> =
        day.filter { it.subject.isNotEmpty() }
            .sortedWith(compareBy({ it.startTime }, { it.cancelled }))
            .distinctBy { Triple(it.startTime, it.subject.first().id, it.cancelled) }
            // merge double lessons of the same subject and room into one line
            .fold(ArrayList<WidgetLesson>()) { acc, entry ->
                val subject = entry.subject.first().let { subjects[it.id]?.name?.takeIf(String::isNotBlank) ?: it.name }
                val room = entry.room.joinToString(", ") { r -> rooms[r.id]?.name?.takeIf(String::isNotBlank) ?: r.name }
                val line = WidgetLesson(
                    start = entry.startTime,
                    end = entry.endTime,
                    subject = subject,
                    subjectLong = entry.subject.first().let { subjects[it.id]?.longName?.takeIf(String::isNotBlank) ?: it.longName.orEmpty() },
                    room = room,
                    teacher = entry.teacher.joinToString(", ") { t -> teachers[t.id]?.name?.takeIf(String::isNotBlank) ?: t.name },
                    cancelled = entry.cancelled,
                    changed = entry.irregular || entry.room.any { (it.orgId ?: 0) > 0 } || entry.teacher.any { (it.orgId ?: 0) > 0 },
                    info = listOfNotNull(entry.lstext, entry.substText).firstOrNull { it.isNotBlank() }.orEmpty(),
                )
                val last = acc.lastOrNull()
                if (last != null && last.subject == line.subject && last.room == line.room && last.cancelled == line.cancelled &&
                    line.start - last.end in 0..20
                ) {
                    acc[acc.lastIndex] = last.copy(end = line.end, changed = last.changed || line.changed)
                } else {
                    acc += line
                }
                acc
            }

    /** Chooses the day to show: today while lessons are left, otherwise the next day with lessons (up to a week ahead). */
    fun build(
        now: LocalDateTime,
        entries: List<TimetableEntry>,
        subjects: Map<Int, MasterDataItem>,
        rooms: Map<Int, MasterDataItem>,
        teachers: Map<Int, MasterDataItem> = emptyMap(),
    ): WidgetDay {
        val byDate = entries.groupBy { it.date }
        val nowTime = now.hour * 100 + now.minute
        val today = now.toLocalDate()

        val todayLessons = lessonsOf(byDate[today.toUntisDate()].orEmpty(), subjects, rooms, teachers)
        val upcomingToday = todayLessons.filter { !it.cancelled && it.end > nowTime }

        if (upcomingToday.isNotEmpty()) {
            val next = upcomingToday.first()
            return WidgetDay(today, todayLessons, next, running = next.start <= nowTime)
        }

        var day = today.plusDays(1)
        repeat(7) {
            val lessons = lessonsOf(byDate[day.toUntisDate()].orEmpty(), subjects, rooms, teachers)
            if (lessons.isNotEmpty()) return WidgetDay(day, lessons, lessons.firstOrNull { !it.cancelled }, running = false)
            day = day.plusDays(1)
        }

        // nothing loaded ahead: show the next weekday empty
        var weekday = today.plusDays(1)
        while (weekday.dayOfWeek == DayOfWeek.SATURDAY || weekday.dayOfWeek == DayOfWeek.SUNDAY) weekday = weekday.plusDays(1)
        return WidgetDay(weekday, emptyList(), null, running = false)
    }

    /** The next time the widgets must change: the next lesson start or end today, or the next midnight. */
    fun nextRefresh(now: LocalDateTime, day: WidgetDay): LocalDateTime {
        val nowTime = now.hour * 100 + now.minute
        val boundary = if (day.date == now.toLocalDate()) {
            day.lessons.flatMap { listOf(it.start, it.end) }.filter { it > nowTime }.minOrNull()
        } else {
            null
        }
        return boundary?.let { now.toLocalDate().atTime(it / 100, it % 100) }
            ?: now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
    }
}
