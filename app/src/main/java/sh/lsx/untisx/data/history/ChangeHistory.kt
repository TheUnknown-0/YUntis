package sh.lsx.untisx.data.history

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.notifications.ChangeType
import sh.lsx.untisx.data.notifications.ScheduleChange

/** One timetable change the app noticed, kept so it can be looked up later. */
@Serializable
data class ChangeRecord(
    val detectedAt: Long,
    val type: String,
    val date: Int,
    val startTime: Int,
    val endTime: Int,
    val subject: String,
    val previous: String? = null,
    val current: String? = null,
) {
    /** The same change noticed twice (by the app and by the background check) is kept once. */
    val identity: String get() = listOf(type, date, startTime, subject, previous, current).joinToString("|")
}

/** Chronicle of noticed changes per account, newest first, at most [LIMIT] entries. */
object ChangeHistory {
    const val LIMIT = 400
    private const val NAME = "change_history"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(context: Context, accountId: String): List<ChangeRecord> {
        val text = prefs(context).getString(accountId, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ChangeRecord>>(text) }.getOrElse { emptyList() }
    }

    fun merge(existing: List<ChangeRecord>, added: List<ChangeRecord>): List<ChangeRecord> {
        val known = existing.mapTo(HashSet()) { it.identity }
        val fresh = added.filter { known.add(it.identity) }
        return (fresh + existing).sortedByDescending { it.detectedAt }.take(LIMIT)
    }

    fun record(context: Context, accountId: String, changes: List<ScheduleChange>, now: Long = System.currentTimeMillis()) {
        if (changes.isEmpty()) return

        val added = changes.map { change ->
            ChangeRecord(
                detectedAt = now,
                type = change.type.name,
                date = change.entry.date,
                startTime = change.entry.startTime,
                endTime = change.entry.endTime,
                subject = change.subject,
                previous = change.previousValue,
                current = change.newValue,
            )
        }

        synchronized(lock) {
            val merged = merge(load(context, accountId), added)
            prefs(context).edit().putString(accountId, json.encodeToString(merged)).apply()
        }
    }

    fun clear(context: Context, accountId: String) {
        synchronized(lock) { prefs(context).edit().remove(accountId).apply() }
    }

    fun typeOf(record: ChangeRecord): ChangeType? = ChangeType.entries.firstOrNull { it.name == record.type }
}
