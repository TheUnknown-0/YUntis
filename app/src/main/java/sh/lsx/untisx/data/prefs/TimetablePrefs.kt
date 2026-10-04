package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.UntisXApp

object TimetablePrefs {

    const val KEY_SHOW_LAST_REFRESHED = "show_last_refreshed"

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun showLastRefreshed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_LAST_REFRESHED, true)

    fun setShowLastRefreshed(context: Context, show: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_SHOW_LAST_REFRESHED, show)
        }
    }
}
