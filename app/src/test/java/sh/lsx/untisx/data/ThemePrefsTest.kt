package sh.lsx.untisx.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
