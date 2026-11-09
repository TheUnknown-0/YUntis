package sh.lsx.untisx.ui.game

import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.SubjectColorResolver
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.data.subjectShortLabel
import sh.lsx.untisx.ui.timetable.resolveSubject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Turns the loaded timetable into game days: today (or the next school day) first,
 * then the following days. Falls back to [sampleDays] when nothing is loaded.
 */
internal fun buildDinoDays(
    weeks: Map<Int, List<TimetableEntry>>,
    subjectsById: Map<Int, MasterDataItem>,
    aliases: Map<String, SubjectAlias>,
    colors: Map<String, Int>,
    textColors: Map<String, Int>,
    defaultColor: Int,
    defaultTextColor: Int,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): List<DinoDay> {
    val byDate = weeks.values.flatten()
        .filter { !it.isEvent && it.startTime > 0 && it.endTime > it.startTime }
        .groupBy { it.date }

    val todayKey = today.toUntisDate()
    val upcoming = byDate.keys.filter { it >= todayKey }.sorted()
    val dates = upcoming.ifEmpty { byDate.keys.sortedDescending().take(5).sorted() }.take(5)

    val formatter = DateTimeFormatter.ofPattern("EEE d. MMM", locale)

    val days = dates.mapNotNull { date ->
        val lessons = byDate[date].orEmpty()
            .distinctBy { Triple(it.startTime, it.endTime, it.subject.firstOrNull()?.id) }
            .sortedBy { it.startTime }
            .map { entry ->
                val name = resolveSubject(entry, subjectsById)
                val id = entry.subject.firstOrNull()?.id
                DinoLesson(
                    label = subjectShortLabel(aliases, name),
                    name = subjectLongLabel(aliases, name).let { label ->
                        if (label == name) subjectsById[id]?.longName?.takeIf { it.isNotBlank() } ?: name else label
                    },
                    color = colors[name] ?: defaultColor,
                    textColor = textColors[name] ?: defaultTextColor,
                    start = entry.startTime / 100 * 60 + entry.startTime % 100,
                    end = entry.endTime / 100 * 60 + entry.endTime % 100,
                    cancelled = entry.cancelled,
                )
            }
        if (lessons.isEmpty()) return@mapNotNull null

        val title = date.toUntisLocalDateOrNull()?.format(formatter)
            ?: date.toUntisLocalDateOrNull()?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
            ?: ""
        DinoDay(title = title, lessons = lessons)
    }

    return days
}

/** A made-up school day for when no timetable is loaded yet. */
internal fun sampleDays(): List<DinoDay> {
    val palette = SubjectColorResolver.AUTO_PALETTE
    val dark = 0xFF111111.toInt()
    fun l(label: String, name: String, i: Int, start: Int, end: Int, cancelled: Boolean = false) =
        DinoLesson(label, name, palette[i], dark, start, end, cancelled)

    return listOf(
        DinoDay(
            "Beispieltag",
            listOf(
                l("M", "Mathematik", 0, 480, 525),
                l("M", "Mathematik", 0, 530, 575),
                l("D", "Deutsch", 3, 595, 640),
                l("E", "Englisch", 1, 645, 690, cancelled = true),
                l("Bio", "Biologie", 2, 710, 755),
                l("Sp", "Sport", 8, 760, 805),
            ),
        ),
    )
}
