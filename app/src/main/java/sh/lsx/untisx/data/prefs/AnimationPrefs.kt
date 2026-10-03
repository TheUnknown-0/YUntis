package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.UntisXApp

object AnimationPrefs {

    const val KEY = "animations_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY, enabled)
        }
    }
}
