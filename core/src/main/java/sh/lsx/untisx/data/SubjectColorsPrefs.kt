package sh.lsx.untisx.data

import android.content.Context
import kotlinx.serialization.json.Json
import androidx.core.content.edit

object SubjectColorsPrefs {

    const val KEY_COLORS = "subject_colors"
    const val KEY_TEXT_COLORS = "subject_text_colors"
    const val KEY_ALIASES = "subject_aliases"
    const val KEY_KNOWN = "known_subjects"
    const val KEY_CANCELLED_STRIKE = "cancelled_strike"
    const val KEY_STRIKE_COLOR = "strike_color"
    const val KEY_EXAM_COLOR = "exam_color"
    const val KEY_DEFAULT_COLOR = "subject_default_color"
    const val KEY_TEXT_COLOR = "lesson_text_color"
    const val KEY_SCHOOL_COLORS = "subject_school_colors"
    const val KEY_SCHOOL_TEXT_COLORS = "subject_school_text_colors"
    const val KEY_PREFER_SCHOOL = "subject_prefer_school_colors"
    const val KEY_AUTO_COLORS = "subject_auto_colors"

    val KEYS = listOf(
        KEY_COLORS,
        KEY_TEXT_COLORS,
        KEY_ALIASES,
        KEY_KNOWN,
        KEY_CANCELLED_STRIKE,
        KEY_STRIKE_COLOR,
        KEY_EXAM_COLOR,
        KEY_DEFAULT_COLOR,
        KEY_TEXT_COLOR,
        KEY_SCHOOL_COLORS,
        KEY_SCHOOL_TEXT_COLORS,
        KEY_PREFER_SCHOOL,
        KEY_AUTO_COLORS,
    )

    val DEFAULT_SUBJECT_COLOR = 0xFF78A8CF.toInt()
    const val DEFAULT_TEXT_COLOR = 0xFF111111.toInt()
    const val DEFAULT_STRIKE_COLOR = 0xFFE53935.toInt()

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

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

    fun getSubjectTextColors(context: Context): Map<String, Int> {
        val raw = prefs(context).getString(KEY_TEXT_COLORS, null) ?: return emptyMap()

        return runCatching {
            json.decodeFromString<Map<String, Int>>(raw)
        }.getOrDefault(emptyMap())
    }

    fun setSubjectTextColor(context: Context, name: String, argb: Int?) {
        val current = getSubjectTextColors(context).toMutableMap()

        if (argb == null) current.remove(name) else current[name] = argb

        prefs(context).edit {
            putString(KEY_TEXT_COLORS, json.encodeToString(current))
        }
    }

    fun getAliases(context: Context): Map<String, SubjectAlias> {
        val raw = prefs(context).getString(KEY_ALIASES, null) ?: return emptyMap()

        return runCatching {
            json.decodeFromString<Map<String, SubjectAlias>>(raw)
        }.getOrDefault(emptyMap())
    }

    fun setAlias(context: Context, name: String, short: String?, long: String?) {
        val current = getAliases(context).toMutableMap()
        val trimmedShort = short?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedLong = long?.trim()?.takeIf { it.isNotEmpty() }

        if (trimmedShort == null && trimmedLong == null) {
            current.remove(name)
        } else {
            current[name] = SubjectAlias(short = trimmedShort, long = trimmedLong)
        }

        prefs(context).edit {
            putString(KEY_ALIASES, json.encodeToString(current))
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

    fun getStrikeColor(context: Context): Int? =
        prefs(context).getInt(KEY_STRIKE_COLOR, 0).takeIf { it != 0 }

    fun setStrikeColor(context: Context, argb: Int?) {
        prefs(context).edit {
            if (argb == null) remove(KEY_STRIKE_COLOR) else putInt(KEY_STRIKE_COLOR, argb)
        }
    }

    fun getExamColor(context: Context): Int? =
        prefs(context).getInt(KEY_EXAM_COLOR, 0).takeIf { it != 0 }

    fun setExamColor(context: Context, argb: Int?) {
        prefs(context).edit {
            if (argb == null) remove(KEY_EXAM_COLOR) else putInt(KEY_EXAM_COLOR, argb)
        }
    }

    fun getSchoolColors(context: Context): Map<String, Int> = readMap(context, KEY_SCHOOL_COLORS)

    fun getSchoolTextColors(context: Context): Map<String, Int> = readMap(context, KEY_SCHOOL_TEXT_COLORS)

    /** Stores the colors the school sends for the user's subjects; writes only when something changed. */
    fun setSchoolColors(context: Context, back: Map<String, Int>, fore: Map<String, Int>) {
        if (back == getSchoolColors(context) && fore == getSchoolTextColors(context)) return

        prefs(context).edit {
            putString(KEY_SCHOOL_COLORS, json.encodeToString(back))
            putString(KEY_SCHOOL_TEXT_COLORS, json.encodeToString(fore))
        }
    }

    fun preferSchoolColors(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PREFER_SCHOOL, true)

    fun setPreferSchoolColors(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_PREFER_SCHOOL, enabled) }
    }

    fun autoColors(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_COLORS, true)

    fun setAutoColors(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO_COLORS, enabled) }
    }

    fun sources(context: Context): SubjectColorResolver.Sources = SubjectColorResolver.Sources(
        own = getColors(context),
        ownText = getSubjectTextColors(context),
        school = getSchoolColors(context),
        schoolText = getSchoolTextColors(context),
        preferSchool = preferSchoolColors(context),
        autoColors = autoColors(context),
    )

    /** Card colors as the timetable shows them (school, own and automatic colors combined). */
    fun effectiveColors(context: Context): Map<String, Int> =
        SubjectColorResolver.cardColors(getKnown(context), sources(context))

    private fun readMap(context: Context, key: String): Map<String, Int> {
        val raw = prefs(context).getString(key, null) ?: return emptyMap()

        return runCatching {
            json.decodeFromString<Map<String, Int>>(raw)
        }.getOrDefault(emptyMap())
    }

    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_COLORS)
            remove(KEY_TEXT_COLORS)
            remove(KEY_ALIASES)
            remove(KEY_KNOWN)
            remove(KEY_STRIKE_COLOR)
            remove(KEY_EXAM_COLOR)
            remove(KEY_DEFAULT_COLOR)
            remove(KEY_TEXT_COLOR)
            remove(KEY_SCHOOL_COLORS)
            remove(KEY_SCHOOL_TEXT_COLORS)
            remove(KEY_PREFER_SCHOOL)
            remove(KEY_AUTO_COLORS)
        }
    }
}
