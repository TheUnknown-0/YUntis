package sh.lsx.untisx.ui.timetable

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonCardColorsTest {

    private val cardColor = Color(0xFF4B8AE2)
    private val textColor = Color(0xFF111111)

    private val luma: Float
        get() = 0.299f * cardColor.red + 0.587f * cardColor.green + 0.114f * cardColor.blue

    private fun expectedFullLook(): Color {
        val gray = Color(luma, luma, luma)

        return Color(gray.red * 0.42f, gray.green * 0.42f, gray.blue * 0.42f)
    }

    @Test
    fun `full desaturation and darkening reproduce the original look`() {
        val colors = cancelledCardColors(cardColor, textColor, desaturation = 1f, brightness = 1f)
        val expected = expectedFullLook()

        assertEquals(expected.red, colors.container.red, 0.0001f)
        assertEquals(expected.green, colors.container.green, 0.0001f)
        assertEquals(expected.blue, colors.container.blue, 0.0001f)
        assertEquals(0.7f, colors.title.alpha, 0.005f)
        assertEquals(0.55f, colors.body.alpha, 0.005f)
    }

    @Test
    fun `zero desaturation and darkening keep the normal colors`() {
        val colors = cancelledCardColors(cardColor, textColor, desaturation = 0f, brightness = 0f)

        assertEquals(cardColor.red, colors.container.red, 0.0001f)
        assertEquals(cardColor.green, colors.container.green, 0.0001f)
        assertEquals(cardColor.blue, colors.container.blue, 0.0001f)
        assertEquals(1f, colors.title.alpha, 0.0001f)
        assertEquals(0.8f, colors.body.alpha, 0.0001f)
    }

    @Test
    fun `desaturation removes color without darkening`() {
        val colors = cancelledCardColors(cardColor, textColor, desaturation = 1f, brightness = 0f)
        val expectedGray = Color(luma, luma, luma)

        assertEquals(expectedGray.red, colors.container.red, 0.0001f)
        assertEquals(expectedGray.green, colors.container.green, 0.0001f)
        assertEquals(expectedGray.blue, colors.container.blue, 0.0001f)
        assertEquals(0.85f, colors.title.alpha, 0.005f)
    }

    @Test
    fun `darkening scales channels while darkening without desaturating`() {
        val colors = cancelledCardColors(cardColor, textColor, desaturation = 0f, brightness = 1f)

        assertEquals(cardColor.red * 0.42f, colors.container.red, 0.005f)
        assertEquals(cardColor.green * 0.42f, colors.container.green, 0.005f)
        assertEquals(cardColor.blue * 0.42f, colors.container.blue, 0.005f)
        assertEquals(0.85f, colors.title.alpha, 0.005f)
    }

    @Test
    fun `values are clamped`() {
        val above = cancelledCardColors(cardColor, textColor, desaturation = 2f, brightness = 2f)
        val one = cancelledCardColors(cardColor, textColor, desaturation = 1f, brightness = 1f)
        val below = cancelledCardColors(cardColor, textColor, desaturation = -1f, brightness = -1f)
        val zero = cancelledCardColors(cardColor, textColor, desaturation = 0f, brightness = 0f)

        assertEquals(one.container.red, above.container.red, 0.0001f)
        assertEquals(one.title.alpha, above.title.alpha, 0.0001f)
        assertEquals(zero.container.red, below.container.red, 0.0001f)
        assertEquals(zero.title.alpha, below.title.alpha, 0.0001f)
    }
}
