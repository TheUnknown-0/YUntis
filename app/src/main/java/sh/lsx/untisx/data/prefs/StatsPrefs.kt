package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.UntisXApp

object StatsPrefs {

    const val KEY_INCLUDE_EVENTS = "stats_include_event_lessons"

    fun includeEvents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_EVENTS, true)

    fun setIncludeEvents(context: Context, include: Boolean) {
        prefs(context).edit { putBoolean(KEY_INCLUDE_EVENTS, include) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
}
