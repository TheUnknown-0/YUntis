package sh.lsx.untisx.data

import android.content.Context
import kotlinx.serialization.json.Json
import sh.lsx.untisx.UntisXApp
import androidx.core.content.edit

object SubjectColorsPrefs {

    const val KEY_COLORS = "subject_colors"
    const val KEY_KNOWN = "known_subjects"
    const val KEY_CANCELLED_STRIKE = "cancelled_strike"
    const val KEY_DEFAULT_COLOR = "subject_default_color"
    const val KEY_TEXT_COLOR = "lesson_text_color"

    val KEYS = listOf(
        KEY_COLORS,
        KEY_KNOWN,
        KEY_CANCELLED_STRIKE,
        KEY_DEFAULT_COLOR,
        KEY_TEXT_COLOR,
    )

    const val DEFAULT_SUBJECT_COLOR = 0xFF78A8CF.toInt()
    const val DEFAULT_TEXT_COLOR = 0xFF111111.toInt()

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun getColors(context: Context): Map<String, Int> {
        val raw = prefs(context).getString(KEY_COLORS, null) ?: return emptyMap()

        return runCatching {
            json.decodeFromString<Map<String, Int>>(raw)
        }.getOrDefault(emptyMap())
    }

    fun setColor(context: Context, name: String, argb: Int?) {
        val current = getColors(context).toMutableMap()

        if (argb == null) current.remove(name) else current[name] = argb

        prefs(context).edit {
            putString(KEY_COLORS, json.encodeToString(current))
        }
    }

    fun getDefaultColor(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT_COLOR, DEFAULT_SUBJECT_COLOR)

    fun setDefaultColor(context: Context, argb: Int) {
        prefs(context).edit {
            putInt(KEY_DEFAULT_COLOR, argb)
        }
    }

    fun getTextColor(context: Context): Int =
        prefs(context).getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR)

    fun setTextColor(context: Context, argb: Int) {
        prefs(context).edit {
            putInt(KEY_TEXT_COLOR, argb)
        }
    }

    fun getKnown(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_KNOWN, null) ?: return emptyList()

        return runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }

    fun setKnown(context: Context, names: List<String>) {
        prefs(context).edit {
            putString(KEY_KNOWN, json.encodeToString(names))
        }
    }

    fun isCancelledStrike(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CANCELLED_STRIKE, true)

    fun setCancelledStrike(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_CANCELLED_STRIKE, enabled)
        }
    }

    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_COLORS)
            remove(KEY_KNOWN)
            remove(KEY_DEFAULT_COLOR)
            remove(KEY_TEXT_COLOR)
        }
    }
}
