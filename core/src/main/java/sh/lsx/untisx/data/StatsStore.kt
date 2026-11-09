package sh.lsx.untisx.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDate
import java.io.File
import java.time.LocalDate

@Serializable
data class DayStats(
    val lessons: Int = 0,
    val cancelled: Int = 0,
    val changes: Int = 0,
    val events: Int = 0,
    val eventLessons: Int = 0,
    val eventCancelled: Int = 0,
    val eventChanges: Int = 0,
    val subjectCounts: Map<Int, Int> = emptyMap(),
    val eventSubjectCounts: Map<Int, Int> = emptyMap(),
    val teacherCounts: Map<Int, Int> = emptyMap(),
    val eventTeacherCounts: Map<Int, Int> = emptyMap(),
    val roomCounts: Map<Int, Int> = emptyMap(),
    val eventRoomCounts: Map<Int, Int> = emptyMap(),
    /** Per subject: planned, substituted and cancelled lessons and minutes (since rule version 3). */
    val subjectDays: Map<Int, SubjectDayStats> = emptyMap(),
    val ruleVersion: Int = 0,
)

@Serializable
data class SchoolYearStats(
    val name: String = "",
    val startDate: Int = 0,
    val endDate: Int = 0,
    val days: Map<String, DayStats> = emptyMap(),
    val lastUpdated: Long = 0,
)

@Serializable
data class StatsData(
    val years: Map<String, SchoolYearStats> = emptyMap(),
    val version: Int = 0,
)

object StatsStore {

    private const val FILE_PREFIX = "stats_"
    private const val VERSION = 2
    private const val TAG = "StatsStore"

    internal const val RULE_VERSION = 3

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val lock = Mutex()

    fun yearKey(startDate: Int): String = startDate.toString()

    fun fallbackYear(today: LocalDate): SchoolYear {
        val startYear = if (today.monthValue >= 8) today.year else today.year - 1
        val start = LocalDate.of(startYear, 8, 1)
        val end = LocalDate.of(startYear + 1, 7, 31)

        return SchoolYear(
            id = 0,
            name = "$startYear/${startYear + 1}",
            startDate = start.toUntisDate(),
            endDate = end.toUntisDate(),
        )
    }

    fun currentYear(data: StatsData?, year: SchoolYear?): SchoolYearStats? {
        val years = data?.years ?: return null
        val key = year?.let { yearKey(it.startDate) } ?: years.keys.maxOrNull() ?: return null

        return years[key]
    }

    fun countedDays(data: StatsData?, year: SchoolYear): Set<Int> =
        data?.years?.get(yearKey(year.startDate))
            ?.days
            ?.filterValues { day -> day.ruleVersion >= RULE_VERSION }
            ?.keys
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun load(context: Context, accountId: String): StatsData {
        val text = AtomicJsonFile.readEncrypted(file(context, accountId)) ?: return StatsData()

        return runCatching {
            val data = json.decodeFromString<StatsData>(text)

            if (data.version != VERSION) StatsData() else data
        }.getOrDefault(StatsData())
    }

    fun clear(context: Context, accountId: String) {
        AtomicJsonFile.delete(file(context, accountId))
    }

    suspend fun recordWeek(
        context: Context,
        accountId: String,
        year: SchoolYear,
        weekStart: LocalDate,
        entries: List<TimetableEntry>,
        today: LocalDate,
        skipDates: Set<Int> = emptySet(),
    ): StatsData = lock.withLock {
        val data = load(context, accountId)
        val key = yearKey(year.startDate)
        val existing = data.years[key]

        val yearStart = runCatching { year.startDate.toUntisLocalDate() }.getOrNull() ?: return@withLock data
        val yearEnd = runCatching { year.endDate.toUntisLocalDate() }.getOrNull() ?: yearStart.plusYears(1).minusDays(1)
        val lastDay = minOf(today, yearEnd)

        val days = HashMap(existing?.days.orEmpty())
        var changed = false

        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())

            if (date < yearStart || date > lastDay) continue

            val dateInt = date.toUntisDate()

            if (dateInt in skipDates) continue

            days[dateInt.toString()] = aggregateDay(entries.filter { it.date == dateInt })
            changed = true
        }

        if (!changed) return@withLock data

        val updatedYear = SchoolYearStats(
            name = year.name.ifBlank { existing?.name.orEmpty() },
            startDate = year.startDate,
            endDate = year.endDate,
            days = days,
            lastUpdated = System.currentTimeMillis(),
        )

        val updated = data.copy(years = data.years + (key to updatedYear))
        save(context, accountId, updated)

        Log.d(TAG, "stats -> ${days.size} days recorded")

        updated
    }

    private fun aggregateDay(entries: List<TimetableEntry>): DayStats {
        var lessons = 0
        var cancelled = 0
        var changes = 0
        var events = 0
        var eventLessons = 0
        var eventCancelled = 0
        var eventChanges = 0
        val subjects = HashMap<Int, Int>()
        val eventSubjects = HashMap<Int, Int>()
        val teachers = HashMap<Int, Int>()
        val eventTeachers = HashMap<Int, Int>()
        val rooms = HashMap<Int, Int>()
        val eventRooms = HashMap<Int, Int>()

        val eventSpans = entries.filter { it.isEvent }.map { it.timeSpan() }
        val subjectDays = HashMap<Int, SubjectDayStats>()

        for (entry in entries) {
            if (entry.isEvent) {
                events++
                continue
            }

            val eventOverlap = eventSpans.any { it.overlaps(entry.timeSpan()) }
            val entryChanged = entry.code == "irregular" ||
                entry.subject.changed() ||
                entry.teacher.changed() ||
                entry.room.changed()

            lessons++
            if (entry.cancelled) cancelled++
            if (entryChanged) changes++

            entry.subject.firstOrNull()?.id?.takeIf { it > 0 }?.let { id ->
                val span = entry.timeSpan()
                val minutes = if (entry.endTime == 0) 0 else (span.end - span.start).coerceIn(0, 12 * 60)
                // a substitution: the lesson takes place, but with another teacher or marked irregular
                val substituted = !entry.cancelled && (entry.code == "irregular" || entry.teacher.changed())
                subjectDays[id] = (subjectDays[id] ?: SubjectDayStats()).add(
                    cancelled = entry.cancelled,
                    substituted = substituted,
                    minutes = minutes,
                    event = eventOverlap,
                )
            }

            if (eventOverlap) {
                eventLessons++
                if (entry.cancelled) eventCancelled++
                if (entryChanged) eventChanges++
            }

            if (!entry.cancelled) {
                entry.subject.firstOrNull()?.id?.takeIf { it > 0 }?.let {
                    subjects[it] = (subjects[it] ?: 0) + 1
                    if (eventOverlap) eventSubjects[it] = (eventSubjects[it] ?: 0) + 1
                }

                entry.teacher.forEach { element ->
                    element.id.takeIf { it > 0 }?.let {
                        teachers[it] = (teachers[it] ?: 0) + 1
                        if (eventOverlap) eventTeachers[it] = (eventTeachers[it] ?: 0) + 1
                    }
                }

                entry.room.forEach { element ->
                    element.id.takeIf { it > 0 }?.let {
                        rooms[it] = (rooms[it] ?: 0) + 1
                        if (eventOverlap) eventRooms[it] = (eventRooms[it] ?: 0) + 1
                    }
                }
            }
        }

        return DayStats(
            lessons = lessons,
            cancelled = cancelled,
            changes = changes,
            events = events,
            eventLessons = eventLessons,
            eventCancelled = eventCancelled,
            eventChanges = eventChanges,
            subjectCounts = subjects,
            eventSubjectCounts = eventSubjects,
            teacherCounts = teachers,
            eventTeacherCounts = eventTeachers,
            roomCounts = rooms,
            eventRoomCounts = eventRooms,
            subjectDays = subjectDays,
            ruleVersion = RULE_VERSION,
        )
    }

    private fun List<TimetableElement>.changed(): Boolean =
        any { (it.orgId != null && it.orgId > 0 && it.orgId != it.id) || !it.orgName.isNullOrBlank() }

    private fun save(context: Context, accountId: String, data: StatsData) {
        val encoded = json.encodeToString(data.copy(version = VERSION))

        if (!AtomicJsonFile.writeEncrypted(file(context, accountId), encoded)) {
            Log.d(TAG, "stats save failed")
        }
    }

    private fun file(context: Context, accountId: String): File =
        accountFile(context, FILE_PREFIX, accountId)
}

private const val MINUTES_PER_DAY = 24 * 60

private data class TimeSpan(val start: Int, val end: Int) {
    fun overlaps(other: TimeSpan): Boolean = start < other.end && other.start < end
}

private fun TimetableEntry.timeSpan(): TimeSpan {
    val start = minutesOfDay(startTime)
    val end = minutesOfDay(endTime)

    return TimeSpan(start, if (endTime == 0) MINUTES_PER_DAY else maxOf(end, start))
}

private fun minutesOfDay(untisTime: Int): Int =
    ((untisTime / 100) * 60 + (untisTime % 100)).coerceIn(0, MINUTES_PER_DAY)
