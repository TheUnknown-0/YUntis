package sh.lsx.untisx.data.prefs

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.UntisXApp

object AppearancePrefs {

    const val KEY_CANCELLED_DESATURATION = "cancelled_desaturation"
    const val KEY_CANCELLED_BRIGHTNESS = "cancelled_brightness"
    const val KEY_PAST_DIM = "past_dim"
    const val KEY_SHOW_TEACHER = "show_teacher"
    const val KEY_SHOW_ROOM = "show_room"
    const val KEY_SHOW_CHANGES = "show_changes"
    const val KEY_HIDE_CANCELLED = "hide_cancelled"
    const val KEY_SHOW_EXAM_BADGE = "show_exam_badge"
    const val KEY_SHOW_FREE_PERIODS = "show_free_periods"

    const val DEFAULT_CANCELLED_DESATURATION = 1.0f
    const val DEFAULT_CANCELLED_BRIGHTNESS = 1.0f
    const val DEFAULT_PAST_DIM = 0.12f
    const val MAX_PAST_DIM = 0.5f

    val KEYS = listOf(
        KEY_CANCELLED_DESATURATION,
        KEY_CANCELLED_BRIGHTNESS,
        KEY_PAST_DIM,
        KEY_SHOW_TEACHER,
        KEY_SHOW_ROOM,
        KEY_SHOW_CHANGES,
        KEY_HIDE_CANCELLED,
        KEY_SHOW_EXAM_BADGE,
        KEY_SHOW_FREE_PERIODS,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun getCancelledDesaturation(context: Context): Float =
        prefs(context)
            .getFloat(KEY_CANCELLED_DESATURATION, DEFAULT_CANCELLED_DESATURATION)
            .coerceIn(0f, 1f)

    fun setCancelledDesaturation(context: Context, value: Float) {
        prefs(context).edit {
            putFloat(KEY_CANCELLED_DESATURATION, value.coerceIn(0f, 1f))
        }
    }

    fun getCancelledBrightness(context: Context): Float =
        prefs(context)
            .getFloat(KEY_CANCELLED_BRIGHTNESS, DEFAULT_CANCELLED_BRIGHTNESS)
            .coerceIn(0f, 1f)

    fun setCancelledBrightness(context: Context, value: Float) {
        prefs(context).edit {
            putFloat(KEY_CANCELLED_BRIGHTNESS, value.coerceIn(0f, 1f))
        }
    }

    fun getPastDim(context: Context): Float =
        prefs(context).getFloat(KEY_PAST_DIM, DEFAULT_PAST_DIM).coerceIn(0f, MAX_PAST_DIM)

    fun setPastDim(context: Context, value: Float) {
        prefs(context).edit {
            putFloat(KEY_PAST_DIM, value.coerceIn(0f, MAX_PAST_DIM))
        }
    }

    fun showTeacher(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_TEACHER, true)

    fun setShowTeacher(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_TEACHER, value) }
    }

    fun showRoom(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_ROOM, true)

    fun setShowRoom(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_ROOM, value) }
    }

    fun showChanges(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CHANGES, true)

    fun setShowChanges(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_CHANGES, value) }
    }

    fun hideCancelled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_CANCELLED, false)

    fun setHideCancelled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_HIDE_CANCELLED, value) }
    }

    fun showExamBadge(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_EXAM_BADGE, true)

    fun setShowExamBadge(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_EXAM_BADGE, value) }
    }

    fun showFreePeriods(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_FREE_PERIODS, true)

    fun setShowFreePeriods(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_FREE_PERIODS, value) }
    }
}
