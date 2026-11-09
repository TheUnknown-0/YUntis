package sh.lsx.untisx.data.planner

import android.content.Context
import java.time.DayOfWeek

/** Settings of the weekly preview and the get-up / leave planner. */
object PlannerPrefs {
    private const val NAME = "planner"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun weekEnabled(context: Context) = prefs(context).getBoolean("week_enabled", true)
    fun setWeekEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("week_enabled", on).apply()

    fun weekDay(context: Context): DayOfWeek = DayOfWeek.of(prefs(context).getInt("week_day", DayOfWeek.SUNDAY.value).coerceIn(1, 7))
    fun setWeekDay(context: Context, day: DayOfWeek) = prefs(context).edit().putInt("week_day", day.value).apply()

    fun weekMinute(context: Context) = prefs(context).getInt("week_minute", 18 * 60)
    fun setWeekMinute(context: Context, minute: Int) = prefs(context).edit().putInt("week_minute", minute).apply()

    fun commuteEnabled(context: Context) = prefs(context).getBoolean("commute_enabled", true)
    fun setCommuteEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("commute_enabled", on).apply()

    /** Only notify when the day starts later, ends earlier or is cancelled completely. */
    fun commuteOnlyChanges(context: Context) = prefs(context).getBoolean("commute_only_changes", true)
    fun setCommuteOnlyChanges(context: Context, on: Boolean) = prefs(context).edit().putBoolean("commute_only_changes", on).apply()

    fun commuteMinute(context: Context) = prefs(context).getInt("commute_minute", 20 * 60)
    fun setCommuteMinute(context: Context, minute: Int) = prefs(context).edit().putInt("commute_minute", minute).apply()

    /** Minutes from leaving home to the first lesson. */
    fun wayMinutes(context: Context) = prefs(context).getInt("way_minutes", 30)
    fun setWayMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt("way_minutes", minutes).apply()

    /** Minutes from getting up to leaving home. */
    fun prepMinutes(context: Context) = prefs(context).getInt("prep_minutes", 60)
    fun setPrepMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt("prep_minutes", minutes).apply()

    /** Study sessions wanted for one exam (key from [examKey]). */
    fun sessions(context: Context, examKey: String) = prefs(context).getInt("sessions_$examKey", 3)
    fun setSessions(context: Context, examKey: String, count: Int) =
        prefs(context).edit().putInt("sessions_$examKey", count.coerceIn(0, 10)).apply()

    fun doneSlots(context: Context, examKey: String): Set<String> =
        prefs(context).getStringSet("done_$examKey", emptySet()).orEmpty().toSet()

    fun setSlotDone(context: Context, examKey: String, slotKey: String, done: Boolean) {
        val current = doneSlots(context, examKey)
        prefs(context).edit().putStringSet("done_$examKey", if (done) current + slotKey else current - slotKey).apply()
    }

    fun examKey(id: Int, date: Int, start: Int, own: Boolean) = "$id-$date-$start-${if (own) "o" else "s"}"
}
