package sh.lsx.untisx.data.notifications

import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

enum class ChangeType {
    CANCELLED,
    ROOM,
    TEACHER,
}

data class ScheduleChange(
    val type: ChangeType,
    val entry: TimetableEntry,
    val subject: String,
    val previousValue: String? = null,
    val newValue: String? = null,
)

data class ChangeFilter(
    val cancelled: Boolean = true,
    val room: Boolean = true,
    val teacher: Boolean = true,
)

object ScheduleChangeDetector {

    fun detect(
        previous: List<TimetableEntry>,
        fresh: List<TimetableEntry>,
        subjects: Map<Int, MasterDataItem> = emptyMap(),
        teachers: Map<Int, MasterDataItem> = emptyMap(),
        rooms: Map<Int, MasterDataItem> = emptyMap(),
        filter: ChangeFilter = ChangeFilter(),
    ): List<ScheduleChange> {
        val remaining = previous.groupBy { slotKey(it) }
            .mapValues { (_, value) -> value.toMutableList() }

        val changes = ArrayList<ScheduleChange>()

        for (entry in fresh.sortedWith(compareBy({ it.date }, { it.startTime }))) {
            val old = remaining[slotKey(entry)]?.removeFirstOrNull()

            if (old == null) continue
            if (old.code == "cancelled" && entry.code != "cancelled") continue

            val subject = names(entry.subject, subjects).ifBlank { "?" }

            if (entry.code == "cancelled" && old.code != "cancelled") {
                if (filter.cancelled) changes += ScheduleChange(ChangeType.CANCELLED, entry, subject)
                continue
            }

            val oldRooms = names(old.room, rooms)
            val newRooms = names(entry.room, rooms)

            if (filter.room && signatures(old.room) != signatures(entry.room)) {
                changes += ScheduleChange(
                    type = ChangeType.ROOM,
                    entry = entry,
                    subject = subject,
                    previousValue = oldRooms,
                    newValue = newRooms,
                )
            }

            val oldTeachers = names(old.teacher, teachers)
            val newTeachers = names(entry.teacher, teachers)

            if (filter.teacher && signatures(old.teacher) != signatures(entry.teacher)) {
                changes += ScheduleChange(
                    type = ChangeType.TEACHER,
                    entry = entry,
                    subject = subject,
                    previousValue = oldTeachers,
                    newValue = newTeachers,
                )
            }
        }

        return changes
    }

    private fun slotKey(entry: TimetableEntry): String {
        val subject = entry.subject.map { it.id }.sorted().joinToString(",")
        val klasse = entry.klasse.map { it.id }.sorted().joinToString(",")

        return if (subject.isEmpty() && klasse.isEmpty()) {
            "event|${entry.date}|${entry.startTime}"
        } else {
            "${entry.date}|${entry.startTime}|$subject|$klasse"
        }
    }

    private fun names(
        elements: List<TimetableElement>,
        data: Map<Int, MasterDataItem>,
    ): String = elements
        .mapNotNull { element ->
            data[element.id]?.name?.takeIf { it.isNotBlank() }
                ?: element.name.takeIf { it.isNotBlank() }
                ?: element.longName?.takeIf { it.isNotBlank() }
        }
        .joinToString(", ")

    private fun signatures(elements: List<TimetableElement>): List<String> =
        elements.map { "${it.id}:${it.name}" }
}
