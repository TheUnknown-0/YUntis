package sh.lsx.untisx.data.absences

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.Absence
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import java.time.LocalDate

/** An absence the user entered in the app (the school may not share its own records). */
@Serializable
data class OwnAbsence(
    val id: String,
    val date: Int,
    val startTime: Int = 0,
    val endTime: Int = 0,
    val reason: String = "",
    val excused: Boolean = false,
) {
    val wholeDay: Boolean get() = startTime == 0 && endTime == 0
}

/** One row of the absence list, from the school or entered by the user. */
data class AbsenceItem(
    val date: Int,
    val startTime: Int,
    val endTime: Int,
    val reason: String,
    val excused: Boolean,
    val status: String,
    val own: OwnAbsence? = null,
) {
    val wholeDay: Boolean get() = startTime == 0 && endTime == 0

    /** Length in minutes; a whole day without known lessons counts as 0. */
    val minutes: Int get() = if (wholeDay || endTime <= startTime) 0 else (endTime / 100 * 60 + endTime % 100) - (startTime / 100 * 60 + startTime % 100)

    companion object {
        fun of(absence: Absence) = AbsenceItem(
            date = absence.date,
            startTime = absence.startTime,
            endTime = if (absence.endDate == absence.date) absence.endTime else 2359,
            reason = listOf(absence.reason, absence.text).filter { it.isNotBlank() }.joinToString(" · "),
            excused = absence.excused,
            status = absence.excuseStatus,
        )

        fun of(own: OwnAbsence) = AbsenceItem(
            date = own.date,
            startTime = own.startTime,
            endTime = own.endTime,
            reason = own.reason,
            excused = own.excused,
            status = "",
            own = own,
        )
    }
}

object OwnAbsencesStore {
    private const val NAME = "own_absences"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(context: Context, accountId: String): List<OwnAbsence> =
        prefs(context).getString(accountId, null)
            ?.let { runCatching { json.decodeFromString<List<OwnAbsence>>(it) }.getOrNull() }
            .orEmpty()

    fun save(context: Context, accountId: String, list: List<OwnAbsence>) {
        prefs(context).edit().putString(accountId, json.encodeToString(list)).apply()
    }

    fun upsert(context: Context, accountId: String, absence: OwnAbsence) =
        save(context, accountId, load(context, accountId).filterNot { it.id == absence.id } + absence)

    fun delete(context: Context, accountId: String, id: String) =
        save(context, accountId, load(context, accountId).filterNot { it.id == id })

    /** Last absence list the school sent, so it stays visible offline. */
    fun loadSchoolCache(context: Context, accountId: String): List<Absence>? =
        prefs(context).getString("school_$accountId", null)
            ?.let { runCatching { json.decodeFromString<List<Absence>>(it) }.getOrNull() }

    fun saveSchoolCache(context: Context, accountId: String, list: List<Absence>) {
        prefs(context).edit().putString("school_$accountId", json.encodeToString(list)).apply()
    }

    fun deadlineDays(context: Context) = prefs(context).getInt("deadline_days", 7)
    fun setDeadlineDays(context: Context, days: Int) = prefs(context).edit().putInt("deadline_days", days.coerceIn(1, 60)).apply()

    fun excuseName(context: Context) = prefs(context).getString("excuse_name", "").orEmpty()
    fun setExcuseName(context: Context, name: String) = prefs(context).edit().putString("excuse_name", name).apply()
}

object AbsenceAssistant {
    /** Lessons of that day that overlap the absence; cancelled ones are left out. */
    fun affectedLessons(item: AbsenceItem, lessons: List<TimetableEntry>): List<TimetableEntry> =
        lessons
            .filter { it.date == item.date && it.subject.isNotEmpty() && !it.cancelled }
            .filter { item.wholeDay || (it.startTime < item.endTime && it.endTime > item.startTime) }
            .distinctBy { Triple(it.startTime, it.endTime, it.subject.firstOrNull()?.id) }
            .sortedBy { it.startTime }

    fun deadline(item: AbsenceItem, days: Int): LocalDate? =
        item.date.toUntisLocalDateOrNull()?.plusDays(days.toLong())

    fun subjectNames(lessons: List<TimetableEntry>, subjects: Map<Int, MasterDataItem>): List<String> =
        lessons.mapNotNull { lesson ->
            val element = lesson.subject.firstOrNull() ?: return@mapNotNull null
            subjects[element.id]?.longName?.takeIf { it.isNotBlank() }
                ?: element.longName?.takeIf { it.isNotBlank() }
                ?: element.name.takeIf { it.isNotBlank() }
        }.distinct()
}
