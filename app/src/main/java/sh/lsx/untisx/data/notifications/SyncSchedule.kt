package sh.lsx.untisx.data.notifications

import android.content.Context
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import java.time.ZonedDateTime

enum class SyncProfile(val changesMinutes: Int, val messagesMinutes: Int, val eventsMinutes: Int, val announcementsMinutes: Int) {
    LOW(120, 360, 1440, 360),
    MEDIUM(60, 180, 720, 180),
    HIGH(30, 60, 360, 120),
}

object SyncSchedule {
    private fun prefs(context: Context) = context.getSharedPreferences("sync_schedule", Context.MODE_PRIVATE)

    fun due(context: Context, accountId: String, kind: String, minutes: Int, now: Long): Boolean {
        val last = prefs(context).getLong("$accountId|$kind", 0L)
        return last <= 0 || now < last || now - last >= minutes * 60_000L
    }

    fun completed(context: Context, accountId: String, kind: String, now: Long) {
        prefs(context).edit().putLong("$accountId|$kind", now).apply()
    }

    fun rememberStarts(context: Context, accountId: String, entries: List<TimetableEntry>) {
        val prefs = prefs(context)
        val editor = prefs.edit()
        entries.filter { it.subject.isNotEmpty() && it.startTime > 0 }.groupBy { it.date }.forEach { (date, lessons) ->
            val key = "$accountId|start|$date"
            val first = lessons.minOf { it.startTime }
            editor.putInt(key, minOf(prefs.getInt(key, first), first))
        }
        val cutoff = AppClock.today().minusDays(7).toUntisDate()
        prefs.all.keys.filter { it.startsWith("$accountId|start|") && (it.substringAfterLast('|').toIntOrNull() ?: 0) < cutoff }
            .forEach(editor::remove)
        editor.apply()
    }

    fun isBoostWindow(context: Context, accountId: String, now: ZonedDateTime = AppClock.now()): Boolean {
        if (!NotificationPrefs.morningBoost(context) || !NotificationPrefs.areChangesEnabled(context)) return false

        val cache = TimetableCache.load(context, accountId)
        val date = now.toLocalDate().toUntisDate()
        if (cache?.holidays?.any { date in it.startDate..it.endDate } == true) return false
        if (cache != null) rememberStarts(context, accountId, cache.weeks.values.flatten())

        val start = prefs(context).getInt("$accountId|start|$date", 0)
        if (start <= 0) return false

        return inBoostWindow(now, start)
    }

    internal fun inBoostWindow(now: ZonedDateTime, startTime: Int): Boolean {
        val start = now.toLocalDate().atTime(startTime.toUntisLocalTime()).atZone(now.zone)
        return !now.isBefore(start.minusHours(2)) && now.isBefore(start)
    }

    fun clear(context: Context, accountId: String) {
        val prefs = prefs(context)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$accountId|") }.forEach(editor::remove)
        editor.apply()
    }
}
