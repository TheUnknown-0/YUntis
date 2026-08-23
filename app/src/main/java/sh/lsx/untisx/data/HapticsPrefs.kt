package sh.lsx.untisx.data

import android.content.Context
import sh.lsx.untisx.UntisXApp
import androidx.core.content.edit

object HapticsPrefs {

    const val KEY = "haptics_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY, enabled)
        }
    }
}
