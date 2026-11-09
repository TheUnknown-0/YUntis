package sh.lsx.untisx.data.calendar

import android.content.Context
import android.content.SharedPreferences

/** Settings of the calendar export. All accounts share one target calendar and one category choice. */
object CalendarExportPrefs {
    const val NAME = "calendar_export"

    /** Target id meaning "the app's own local calendar". */
    const val OWN_CALENDAR = -1L

    const val KEY_ENABLED = "enabled"
    const val KEY_TARGET = "target"
    const val KEY_CATEGORIES = "categories"
    const val KEY_EXCLUDED_TYPES = "excluded_exam_types"
    const val KEY_DISABLED_ACCOUNTS = "disabled_accounts"
    const val KEY_INTERVAL = "interval_hours"
    const val KEY_LAST_RUN = "last_run"
    const val KEY_LAST_RESULT = "last_result"
    const val KEY_RUNNING = "running"

    val INTERVALS = listOf(0, 1, 3, 6, 12, 24)

    private val DEFAULT_CATEGORIES = setOf(
        CalendarCategory.EXAMS,
        CalendarCategory.OWN_EXAMS,
        CalendarCategory.EVENTS,
    )

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun target(context: Context) = prefs(context).getLong(KEY_TARGET, OWN_CALENDAR)
    fun setTarget(context: Context, id: Long) = prefs(context).edit().putLong(KEY_TARGET, id).apply()

    fun categories(context: Context): Set<CalendarCategory> =
        prefs(context).getStringSet(KEY_CATEGORIES, null)
            ?.mapNotNull { name -> CalendarCategory.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: DEFAULT_CATEGORIES

    fun setCategory(context: Context, category: CalendarCategory, on: Boolean) {
        val next = if (on) categories(context) + category else categories(context) - category
        prefs(context).edit().putStringSet(KEY_CATEGORIES, next.map { it.name }.toSet()).apply()
    }

    fun excludedExamTypes(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXCLUDED_TYPES, emptySet()).orEmpty().toSet()

    fun setExamType(context: Context, type: String, on: Boolean) {
        val key = CalendarItems.examTypeKey(type)
        val next = if (on) excludedExamTypes(context) - key else excludedExamTypes(context) + key
        prefs(context).edit().putStringSet(KEY_EXCLUDED_TYPES, next).apply()
    }

    fun isAccountEnabled(context: Context, accountId: String) =
        accountId !in prefs(context).getStringSet(KEY_DISABLED_ACCOUNTS, emptySet()).orEmpty()

    fun setAccountEnabled(context: Context, accountId: String, on: Boolean) {
        val current = prefs(context).getStringSet(KEY_DISABLED_ACCOUNTS, emptySet()).orEmpty()
        val next = if (on) current - accountId else current + accountId
        prefs(context).edit().putStringSet(KEY_DISABLED_ACCOUNTS, next).apply()
    }

    /** Hours between automatic runs; 0 means only manually. */
    fun intervalHours(context: Context) = prefs(context).getInt(KEY_INTERVAL, 6)
    fun setIntervalHours(context: Context, hours: Int) = prefs(context).edit().putInt(KEY_INTERVAL, hours).apply()

    fun lastRun(context: Context) = prefs(context).getLong(KEY_LAST_RUN, 0L)
    fun lastResult(context: Context): String = prefs(context).getString(KEY_LAST_RESULT, "").orEmpty()

    fun isRunning(context: Context) = prefs(context).getBoolean(KEY_RUNNING, false)
    fun setRunning(context: Context, running: Boolean) = prefs(context).edit().putBoolean(KEY_RUNNING, running).apply()

    fun saveResult(context: Context, result: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_RUN, System.currentTimeMillis())
            .putString(KEY_LAST_RESULT, result)
            .apply()
    }
}
