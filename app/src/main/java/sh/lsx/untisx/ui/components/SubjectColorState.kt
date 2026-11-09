package sh.lsx.untisx.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import sh.lsx.untisx.data.SubjectColorResolver

/**
 * Where each subject color comes from. Screens that only show colors use the
 * effective maps passed down as parameters; the color settings need the raw sources.
 */
@Immutable
data class SubjectColorState(
    val sources: SubjectColorResolver.Sources = SubjectColorResolver.Sources(),
    val effective: Map<String, Int> = emptyMap(),
    val onPreferSchool: (Boolean) -> Unit = {},
    val onAutoColors: (Boolean) -> Unit = {},
) {
    fun origin(name: String): SubjectColorResolver.Origin = SubjectColorResolver.origin(name, sources)

    /** True when the school's color is shown and the own color cannot override it. */
    fun lockedBySchool(name: String): Boolean =
        sources.preferSchool && name in sources.school

    /** Color the subject shows when no own color is set. */
    fun fallbackColor(name: String, globalDefault: Int): Int {
        sources.school[name]?.let { return it }
        if (!sources.autoColors) return globalDefault

        val withoutOwn = sources.copy(own = sources.own - name)
        return SubjectColorResolver.cardColors(effective.keys + name, withoutOwn)[name] ?: globalDefault
    }
}

val LocalSubjectColorState = staticCompositionLocalOf { SubjectColorState() }
