package sh.lsx.untisx.data

import android.content.Context
import sh.lsx.untisx.UntisXApp
import androidx.core.content.edit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemePreset(val seed: Int) {
    DEVICE(0),
    ORANGE(0xFFEF6C00.toInt()),
    BLUE(0xFF1976D2.toInt()),
    GREEN(0xFF388E3C.toInt()),
    PURPLE(0xFF7B1FA2.toInt()),
    RED(0xFFD32F2F.toInt()),
}

data class AppTheme(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val preset: ThemePreset = ThemePreset.DEVICE,
)

object ThemePrefs {
    private const val KEY_MODE = "theme_mode"
    private const val KEY_PRESET = "theme_color"

    val KEYS = listOf(KEY_MODE, KEY_PRESET)

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): AppTheme {
        val prefs = prefs(context)

        val mode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        val preset = runCatching { ThemePreset.valueOf(prefs.getString(KEY_PRESET, ThemePreset.DEVICE.name)!!) }
            .getOrDefault(ThemePreset.DEVICE)

        return AppTheme(mode = mode, preset = preset)
    }

    fun save(context: Context, theme: AppTheme) {
        prefs(context).edit {
            putString(KEY_MODE, theme.mode.name)
            putString(KEY_PRESET, theme.preset.name)
        }
    }
}
