package sh.lsx.untisx.data.notebook

import java.time.ZonedDateTime
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull

data class NotebookLessonCache(
    val weeks: Map<Int, List<TimetableEntry>> = emptyMap(),
    val holidays: List<Holiday> = emptyList(),
) {
    companion object {
        fun from(cache: TimetableCache.Data?): NotebookLessonCache {
            if (cache == null) return NotebookLessonCache()

            val weeks = cache.weeks.mapNotNull { (key, entries) ->
                key.toIntOrNull()?.let { it to entries }
            }.toMap()

            return NotebookLessonCache(weeks = weeks, holidays = cache.holidays)
        }
    }
}

data class LessonBlock(
    val entry: TimetableEntry,
    val date: Int,
    val startTime: Int,
    val endTime: Int,
) {
    val subject: SubjectRef? get() = SubjectRef.from(entry.subject)

    fun toRef(
        subject: SubjectRef? = null,
        teacher: String = "",
        room: String = "",
    ): LessonRef {
        val resolvedSubject = subject ?: SubjectRef.from(entry.subject)
        val resolvedTeacher = teacher.ifBlank { entry.teacher.firstOrNull()?.name.orEmpty() }
        val resolvedRoom = room.ifBlank { entry.room.firstOrNull()?.name.orEmpty() }

        return LessonRef(
            entryId = entry.id,
            date = date,
            startTime = startTime,
            endTime = endTime,
            subject = resolvedSubject?.normalized()?.takeIf { !it.isBlank },
            teacher = resolvedTeacher.trim(),
            room = resolvedRoom.trim(),
        )
    }
}

sealed interface LessonResolution {
    data class Found(val entry: TimetableEntry) : LessonResolution
    data class Missing(val snapshot: LessonRef) : LessonResolution
    data class Ambiguous(
        val snapshot: LessonRef,
        val candidates: List<TimetableEntry>,
    ) : LessonResolution
}

object NotebookLessonResolver {
    fun blocks(cache: NotebookLessonCache): List<LessonBlock> {
        val raw = cache.weeks.values
            .asSequence()
            .flatten()
            .filter { entry ->
                !entry.isEvent &&
                    !entry.cancelled &&
                    NotebookValidator.isValidDate(entry.date) &&
                    NotebookValidator.isValidTime(entry.startTime) &&
                    NotebookValidator.isValidTime(entry.endTime) &&
                    entry.endTime > entry.startTime &&
                    !isHoliday(entry.date, cache.holidays)
            }
            .distinctBy { Triple(it.date, it.startTime, it.id) }
            .sortedWith(compareBy<TimetableEntry>({ it.date }, { it.startTime }, { it.endTime }, { it.id }))
            .toList()

        val merged = mutableListOf<LessonBlock>()

        raw.forEach { entry ->
            val previous = merged.lastOrNull()

            if (previous != null && canMerge(previous, entry)) {
                merged[merged.lastIndex] = previous.copy(endTime = entry.endTime)
            } else {
                merged += LessonBlock(entry, entry.date, entry.startTime, entry.endTime)
            }
        }

        return merged
    }

    fun nextBlock(
        cache: NotebookLessonCache,
        now: ZonedDateTime,
        after: LessonKey? = null,
        subject: SubjectRef? = null,
    ): LessonBlock? {
        val all = blocks(cache)
        val explicit = subject?.takeIf { !it.isBlank }?.normalized()
        val inferred = if (explicit == null && after != null) subjectAt(all, after) else null

        if (explicit == null && after != null && inferred == null) return null

        val filter = explicit ?: inferred

        return all.firstOrNull { block ->
            val afterOk = if (after == null) {
                val start = blockStart(block, now) ?: return@firstOrNull false
                !start.isBefore(now)
            } else {
                block.date > after.date ||
                    (block.date == after.date && block.startTime > after.startTime)
            }

            val subjectOk = filter == null || block.subject?.matches(filter) == true

            afterOk && subjectOk
        }
    }

    fun blockAt(cache: NotebookLessonCache, key: LessonKey): LessonBlock? {
        val all = blocks(cache)

        return all.firstOrNull { block ->
            block.date == key.date &&
                block.startTime == key.startTime &&
                (key.entryId <= 0 || block.entry.id == key.entryId)
        } ?: all.firstOrNull { block ->
            block.date == key.date && block.startTime == key.startTime
        } ?: all.firstOrNull { block ->
            block.date == key.date && key.startTime >= block.startTime && key.startTime < block.endTime
        }
    }

    fun resolve(cache: NotebookLessonCache, ref: LessonRef): LessonResolution {
        val all = blocks(cache)
        val wantedSubject = ref.subject?.takeIf { !it.isBlank }?.normalized()

        fun subjectMatches(block: LessonBlock): Boolean =
            wantedSubject == null || block.subject?.matches(wantedSubject) == true

        if (ref.entryId > 0) {
            val exact = all.filter {
                it.entry.id == ref.entryId &&
                    it.date == ref.date &&
                    it.startTime == ref.startTime &&
                    subjectMatches(it)
            }

            when (exact.size) {
                1 -> return LessonResolution.Found(exact.first().entry)
                in 2..Int.MAX_VALUE -> return LessonResolution.Ambiguous(ref, exact.map { it.entry })
            }

            val byEntry = all.filter {
                it.entry.id == ref.entryId &&
                    it.date == ref.date &&
                    subjectMatches(it)
            }

            when (byEntry.size) {
                1 -> return LessonResolution.Found(byEntry.first().entry)
                in 2..Int.MAX_VALUE -> return LessonResolution.Ambiguous(ref, byEntry.map { it.entry })
            }
        }

        val sameSlot = all.filter {
            it.date == ref.date && it.startTime == ref.startTime && subjectMatches(it)
        }

        when (sameSlot.size) {
            1 -> return LessonResolution.Found(sameSlot.first().entry)
            in 2..Int.MAX_VALUE -> return LessonResolution.Ambiguous(ref, sameSlot.map { it.entry })
        }

        val contained = all.filter {
            it.date == ref.date &&
                ref.startTime >= it.startTime &&
                ref.startTime < it.endTime &&
                subjectMatches(it)
        }

        return when (contained.size) {
            1 -> LessonResolution.Found(contained.first().entry)
            in 2..Int.MAX_VALUE -> LessonResolution.Ambiguous(ref, contained.map { it.entry })
            else -> LessonResolution.Missing(ref)
        }
    }

    private fun subjectAt(all: List<LessonBlock>, key: LessonKey?): SubjectRef? {
        if (key == null) return null

        val byId = if (key.entryId > 0) {
            all.filter { it.date == key.date && it.entry.id == key.entryId }
        } else {
            emptyList<LessonBlock>()
        }

        val matching = if (byId.isNotEmpty()) {
            byId
        } else {
            all.filter {
                it.date == key.date &&
                    (
                        it.startTime == key.startTime ||
                            (key.startTime >= it.startTime && key.startTime < it.endTime)
                        )
            }
        }

        val subjects = matching.mapNotNull { it.subject?.normalized()?.takeIf { subject -> !subject.isBlank } }
            .distinct()

        return subjects.singleOrNull()
    }

    private fun blockStart(block: LessonBlock, now: ZonedDateTime) =
        block.date.toUntisLocalDateOrNull()
            ?.atTime(block.startTime / 100, block.startTime % 100)
            ?.atZone(now.zone)

    private fun canMerge(previous: LessonBlock, next: TimetableEntry): Boolean {
        val first = previous.entry

        return previous.date == next.date &&
            previous.endTime == next.startTime &&
            first.code == next.code &&
            first.activityType == next.activityType &&
            first.exam == next.exam &&
            first.subject == next.subject &&
            first.teacher == next.teacher &&
            first.room == next.room &&
            first.klasse == next.klasse
    }

    private fun isHoliday(date: Int, holidays: List<Holiday>): Boolean =
        holidays.any { holiday ->
            NotebookValidator.isValidDate(holiday.startDate) &&
                NotebookValidator.isValidDate(holiday.endDate) &&
                holiday.startDate <= holiday.endDate &&
                date in holiday.startDate..holiday.endDate
        }
}
