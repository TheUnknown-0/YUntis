package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.UntisXApp

object AppearancePrefs {

    const val KEY_CANCELLED_DIM = "cancelled_dim"
    const val KEY_PAST_DIM = "past_dim"

    const val DEFAULT_CANCELLED_DIM = 1.0f
    const val DEFAULT_PAST_DIM = 0.12f
    const val MAX_PAST_DIM = 0.5f

    val KEYS = listOf(KEY_CANCELLED_DIM, KEY_PAST_DIM)

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun getCancelledDim(context: Context): Float =
        prefs(context).getFloat(KEY_CANCELLED_DIM, DEFAULT_CANCELLED_DIM).coerceIn(0f, 1f)

    fun setCancelledDim(context: Context, value: Float) {
        prefs(context).edit {
            putFloat(KEY_CANCELLED_DIM, value.coerceIn(0f, 1f))
        }
    }

    fun getPastDim(context: Context): Float =
        prefs(context).getFloat(KEY_PAST_DIM, DEFAULT_PAST_DIM).coerceIn(0f, MAX_PAST_DIM)

    fun setPastDim(context: Context, value: Float) {
        prefs(context).edit {
            putFloat(KEY_PAST_DIM, value.coerceIn(0f, MAX_PAST_DIM))
        }
    }
}
