package sh.lsx.untisx.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.ui.timetable.cancelledCardColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookThemeTest {
    @Test
    fun `all styles retain accents and readable screen colors in both modes`() {
        for (dark in listOf(false, true)) {
            val base = if (dark) darkColorScheme(primary = Color.Cyan) else lightColorScheme(primary = Color.Blue)

            for (style in ThemeStyle.entries) {
                val colors = themeAppearance(style, dark, base).colors
                val contrast = (maxOf(colors.background.luminance(), colors.onBackground.luminance()) + 0.05f) /
                    (minOf(colors.background.luminance(), colors.onBackground.luminance()) + 0.05f)

                assertEquals(base.primary, colors.primary)
                assertEquals(base.error, colors.error)
                assertTrue("$style / dark=$dark: $contrast", contrast >= 4.5f)
            }
        }
    }

    @Test
    fun `cancelled notebook stock is visibly dark and grey even with weak dimming settings`() {
        for (dark in listOf(false, true)) {
            for (subject in listOf(Color.Blue, Color.Red, Color.Yellow, Color(0xFF81B8E0))) {
                val paper = notebookNoteColor(subject, dark)

                for (brightness in listOf(0f, 1f)) {
                    val cancelled = cancelledCardColors(paper, Color.Black, 0f, brightness, notebook = true).container
                    assertTrue(cancelled.luminance() < paper.luminance() * 0.5f)
                    assertTrue(maxOf(cancelled.red, cancelled.green, cancelled.blue) -
                        minOf(cancelled.red, cancelled.green, cancelled.blue) < 0.08f)
                }
            }
        }
    }

    @Test
    fun `marker inscription has readable ink on light and dark stock`() {
        for (paper in listOf(Color(0xFFFFF5DF), Color(0xFFE5B7AF), Color(0xFF333128))) {
            val ink = notebookMarkerInk(paper)
            val contrast = (maxOf(paper.luminance(), ink.luminance()) + 0.05f) /
                (minOf(paper.luminance(), ink.luminance()) + 0.05f)

            assertTrue(contrast >= 4.5f)
            assertTrue("Marker must read as red", ink.red > ink.green && ink.red > ink.blue)
        }
    }

    @Test
    fun `notes remain light pastel paper even on dark backgrounds`() {
        for (dark in listOf(false, true)) {
            for (subject in listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Yellow)) {
                val paper = notebookNoteColor(subject, dark)
                assertTrue("Paper must not turn into a dark tile: $paper", paper.luminance() > 0.40f)
            }
        }
    }

    @Test
    fun `pastel notes preserve recognizable subject hues`() {
        for (dark in listOf(false, true)) {
            val blue = notebookNoteColor(Color(0xFF81B8E0), dark)
            val red = notebookNoteColor(Color(0xFFF08E91), dark)
            assertTrue(blue.blue > blue.red)
            assertTrue(red.red > red.blue)
        }
    }

    @Test
    fun `ink stays readable for light and dark notes including custom extremes`() {
        for (dark in listOf(false, true)) {
            for (subject in listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Yellow, Color(0xFF81D4FA))) {
                val paper = notebookNoteColor(subject, dark)
                for (preferred in listOf(Color.Black, Color.White, Color.Yellow, Color.Transparent)) {
                    val ink = notebookInk(paper, preferred)
                    val contrast = (maxOf(paper.luminance(), ink.luminance()) + 0.05f) /
                        (minOf(paper.luminance(), ink.luminance()) + 0.05f)

                    assertTrue("$paper / $ink: $contrast", contrast >= 4.5f)
                }
            }
        }
    }

    @Test
    fun `legible custom ink is retained`() {
        val preferred = Color(0xFF143A55)
        assertEquals(preferred, notebookInk(Color(0xFFFFF3CC), preferred))
    }
}
