package sh.lsx.untisx.ui.timetable

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
        .associate { it.id to MasterDataItem(id = it.id, name = it.name) }

    if (derived.isEmpty()) return current

    val merged = HashMap(current)

    for ((id, item) in derived) {
        if (merged[id]?.name.isNullOrBlank()) merged[id] = item
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
