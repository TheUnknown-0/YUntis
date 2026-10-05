package sh.lsx.untisx.ui.timetable

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCardColorsTest {

    private val cardColor = Color(0xFF4B8AE2)
    private val textColor = Color(0xFF111111)

    private fun desaturatedGray(): Float =
        (0.299f * cardColor.red + 0.587f * cardColor.green + 0.114f * cardColor.blue) * 0.42f

    @Test
    fun `dim one matches the original cancelled look`() {
        val colors = cancelledCardColors(cardColor, textColor, 1f)
        val gray = desaturatedGray()
        val expected = Color(gray, gray, gray)

        assertEquals(expected.red, colors.container.red, 0.0001f)
        assertEquals(expected.green, colors.container.green, 0.0001f)
        assertEquals(expected.blue, colors.container.blue, 0.0001f)
        assertEquals(1f, colors.container.alpha, 0.0001f)
        assertEquals(0.7f, colors.title.alpha, 0.005f)
        assertEquals(0.55f, colors.body.alpha, 0.005f)
    }

    @Test
    fun `dim zero keeps the normal colors`() {
        val colors = cancelledCardColors(cardColor, textColor, 0f)

        assertEquals(cardColor.red, colors.container.red, 0.0001f)
        assertEquals(cardColor.green, colors.container.green, 0.0001f)
        assertEquals(cardColor.blue, colors.container.blue, 0.0001f)
        assertEquals(1f, colors.title.alpha, 0.0001f)
        assertEquals(0.8f, colors.body.alpha, 0.0001f)
    }

    @Test
    fun `dim is clamped to zero and one`() {
        val above = cancelledCardColors(cardColor, textColor, 2f)
        val one = cancelledCardColors(cardColor, textColor, 1f)
        val below = cancelledCardColors(cardColor, textColor, -1f)
        val zero = cancelledCardColors(cardColor, textColor, 0f)

        assertEquals(one.container.red, above.container.red, 0.0001f)
        assertEquals(one.title.alpha, above.title.alpha, 0.0001f)
        assertEquals(zero.container.red, below.container.red, 0.0001f)
        assertEquals(zero.title.alpha, below.title.alpha, 0.0001f)
    }

    @Test
    fun `dim blends between normal and cancelled`() {
        val colors = cancelledCardColors(cardColor, textColor, 0.5f)
        val gray = desaturatedGray()

        assertTrue(colors.container.red in (gray - 0.01f)..(cardColor.red + 0.01f))
        assertTrue(colors.container.green in (gray - 0.01f)..(cardColor.green + 0.01f))
        assertTrue(colors.container.blue in (gray - 0.01f)..(cardColor.blue + 0.01f))
        assertEquals(0.85f, colors.title.alpha, 0.005f)
        assertEquals(0.675f, colors.body.alpha, 0.005f)
    }
}
