package sh.lsx.untisx.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.UntisXApp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `existing installs default to default style`() {
        assertEquals(ThemeStyle.DEFAULT, ThemePrefs.get(context).style)
    }

    @Test
    fun `notebook round trips independently of color and mode`() {
        val theme = AppTheme(ThemeMode.DARK, ThemePreset.CUSTOM, 0xFF123456.toInt(), ThemeStyle.NOTEBOOK)

        ThemePrefs.save(context, theme)

        assertEquals(theme, ThemePrefs.get(context))

        ThemePrefs.save(context, theme.copy(style = ThemeStyle.DEFAULT))

        assertEquals(theme.copy(style = ThemeStyle.DEFAULT), ThemePrefs.get(context))
    }

    @Test
    fun `backup import restores notebook style`() {
        val backup = SettingsBackup(appearance = AppearanceSettingsBackup(themeStyle = "NOTEBOOK"))

        assertEquals(SettingsImportResult.Success, SettingsBackupManager.import(context, SettingsBackupCodec.encode(backup)))
        assertEquals(ThemeStyle.NOTEBOOK, ThemePrefs.get(context).style)
    }

    @Test
    fun `old or unknown backup styles preserve current style`() {
        ThemePrefs.save(context, AppTheme(style = ThemeStyle.NOTEBOOK))

        for (style in listOf(null, "FUTURE_STYLE")) {
            val backup = SettingsBackup(appearance = AppearanceSettingsBackup(themeMode = "DARK", themeStyle = style))

            assertEquals(SettingsImportResult.Success, SettingsBackupManager.import(context, SettingsBackupCodec.encode(backup)))
            assertEquals(ThemeStyle.NOTEBOOK, ThemePrefs.get(context).style)
            assertEquals(ThemeMode.DARK, ThemePrefs.get(context).mode)
        }
    }

    @Test
    fun `unknown theme style falls back safely`() {
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("theme_style", "FUTURE_STYLE").commit()

        assertEquals(ThemeStyle.DEFAULT, ThemePrefs.get(context).style)
    }

    @Test
    fun `custom accent seed round trips`() {
        ThemePrefs.save(
            context,
            AppTheme(
                mode = ThemeMode.DARK,
                preset = ThemePreset.CUSTOM,
                customSeed = 0xFF123456.toInt(),
            ),
        )

        val loaded = ThemePrefs.get(context)

        assertEquals(ThemeMode.DARK, loaded.mode)
        assertEquals(ThemePreset.CUSTOM, loaded.preset)
        assertEquals(0xFF123456.toInt(), loaded.customSeed)
    }
}
