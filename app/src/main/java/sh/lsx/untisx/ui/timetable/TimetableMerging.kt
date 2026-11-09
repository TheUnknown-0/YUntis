package sh.lsx.untisx.ui.timetable

import sh.lsx.untisx.data.SubjectColorResolver
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

internal fun mergeConsecutiveLessons(entries: List<TimetableEntry>): List<TimetableEntry> {
    val sorted = entries.sortedWith(
        compareBy({ it.date }, { it.startTime }, { it.endTime }),
    )

    val merged = ArrayList<TimetableEntry>(sorted.size)

    for (entry in sorted) {
        val last = merged.lastOrNull()

        if (
            last != null &&
            last.date == entry.date &&
            last.code == entry.code &&
            last.subject == entry.subject &&
            last.teacher == entry.teacher &&
            last.klasse == entry.klasse &&
            last.room == entry.room &&
            last.endTime == entry.startTime
        ) {
            val notes = listOfNotNull(last.lstext, entry.lstext)
                .distinct()
                .joinToString(" | ")

            merged[merged.size - 1] = last.copy(
                endTime = entry.endTime,
                lstext = notes.ifBlank { null },
            )
        } else {
            merged.add(entry)
        }
    }

    return merged
}

internal fun mergeConsecutiveEvents(entries: List<TimetableEntry>): List<TimetableEntry> {
    val merged = ArrayList<TimetableEntry>(entries.size)

    for (entry in entries) {
        val last = merged.lastOrNull()

        if (
            last != null &&
            last.date == entry.date &&
            last.startTime == entry.startTime &&
            last.endTime == entry.endTime &&
            last.eventTitle == entry.eventTitle &&
            last.room == entry.room
        ) {
            continue
        }

        merged.add(entry)
    }

    return merged
}

internal fun resolveElementIds(
    entries: List<TimetableEntry>,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
): List<TimetableEntry> {
    if (subjectsById.isEmpty() && teachersById.isEmpty() && roomsById.isEmpty()) return entries

    fun resolve(
        elements: List<TimetableElement>,
        data: Map<Int, MasterDataItem>,
    ): List<TimetableElement> = elements.map { element ->
        if (element.id > 0 || element.name.isBlank()) {
            element
        } else {
            val match = data.values.firstOrNull { item ->
                item.name.equals(element.name, ignoreCase = true) ||
                    (element.longName != null && item.longName.equals(element.longName, ignoreCase = true))
            }

            if (match != null) element.copy(id = match.id) else element
        }
    }

    return entries.map { entry ->
        entry.copy(
            subject = resolve(entry.subject, subjectsById),
            teacher = resolve(entry.teacher, teachersById),
            room = resolve(entry.room, roomsById),
        )
    }
}

internal fun mergeMissing(
    current: Map<Int, MasterDataItem>,
    entries: Collection<TimetableEntry>,
    selector: (TimetableEntry) -> List<TimetableElement>,
): Map<Int, MasterDataItem> {
    val derived = entries
        .asSequence()
        .flatMap { entry -> selector(entry).asSequence() }
        .filter { it.id > 0 && it.name.isNotBlank() }
        .associate { it.id to MasterDataItem(id = it.id, name = it.name, longName = it.longName.orEmpty()) }

    if (derived.isEmpty()) return current

    val merged = HashMap(current)

    for ((id, item) in derived) {
        val known = merged[id]
        when {
            known == null || known.name.isBlank() -> merged[id] = item
            // keep master data, but fill in a full name the timetable knows and master data lacks
            known.longName.isBlank() && item.longName.isNotBlank() -> merged[id] = known.copy(longName = item.longName)
        }
    }

    return merged
}

internal fun subjectNamesOf(
    entries: Collection<TimetableEntry>,
    subjectsById: Map<Int, MasterDataItem>,
): Set<String> =
    entries
        .flatMap { it.subject }
        .mapNotNull { el ->
            subjectsById[el.id]?.name?.takeIf { it.isNotBlank() }
                ?: el.name.takeIf { it.isNotBlank() }
        }
        .toSet()

/**
 * Colors the school assigned to subjects, from master data and from the lessons themselves.
 * Many schools leave most subjects at one Untis default color; a color shared by a large part
 * of all subjects is therefore treated as "no color". Only subjects in [relevant] are returned.
 */
internal fun schoolColorsOf(
    entries: Collection<TimetableEntry>,
    subjectsById: Map<Int, MasterDataItem>,
    relevant: Set<String>,
): Pair<Map<String, Int>, Map<String, Int>> {
    val back = HashMap<String, Int>()
    val fore = HashMap<String, Int>()
    val placeholder = placeholderColor(subjectsById.values.mapNotNull { SubjectColorResolver.parseUntisColor(it.backColor) })

    fun put(name: String, backRaw: String?, foreRaw: String?, overwrite: Boolean) {
        if (name !in relevant) return
        val color = SubjectColorResolver.parseUntisColor(backRaw) ?: return
        if (color == placeholder) return
        if (overwrite) back[name] = color else back.putIfAbsent(name, color)
        SubjectColorResolver.parseUntisColor(foreRaw)?.let { if (overwrite) fore[name] = it else fore.putIfAbsent(name, it) }
    }

    for (item in subjectsById.values) {
        val name = item.name.takeIf { it.isNotBlank() } ?: continue
        put(name, item.backColor, item.foreColor, overwrite = true)
    }

    for (entry in entries) {
        val element = entry.subject.firstOrNull() ?: continue
        val name = subjectsById[element.id]?.name?.takeIf { it.isNotBlank() }
            ?: element.name.takeIf { it.isNotBlank() }
            ?: continue
        put(name, entry.backColor, entry.foreColor, overwrite = false)
    }

    // a color shared by three or more of the user's own subjects does not tell them apart
    val shared = back.values.groupingBy { it }.eachCount().filterValues { it >= 3 }.keys
    if (shared.isNotEmpty()) {
        val drop = back.filterValues { it in shared }.keys
        back.keys.removeAll(drop)
        fore.keys.removeAll(drop)
    }

    return back to fore
}

/** The color most subjects share, if it is clearly a default (at least 5 subjects and 25 %). */
internal fun placeholderColor(colors: List<Int>): Int? {
    if (colors.size < 5) return null
    val (color, count) = colors.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
    return color.takeIf { count >= 5 && count * 4 >= colors.size }
}
