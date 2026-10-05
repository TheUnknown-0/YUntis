package sh.lsx.untisx.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class HsvColorTest {

    @Test
    fun `primary colors map to expected hues`() {
        assertEquals(0f, colorToHsv(Color.Red).hue, 0.001f)
        assertEquals(120f, colorToHsv(Color.Green).hue, 0.001f)
        assertEquals(240f, colorToHsv(Color.Blue).hue, 0.001f)
        assertEquals(1f, colorToHsv(Color.Red).saturation, 0.001f)
        assertEquals(1f, colorToHsv(Color.Red).value, 0.001f)
    }

    @Test
    fun `grayscale colors have no saturation`() {
        assertEquals(0f, colorToHsv(Color.White).saturation, 0.001f)
        assertEquals(0f, colorToHsv(Color.Black).value, 0.001f)
        assertEquals(0f, colorToHsv(Color(0xFF808080)).saturation, 0.001f)
        assertEquals(1f, colorToHsv(Color.White).value, 0.001f)
    }

    @Test
    fun `hsv round trips through rgb`() {
        val samples = listOf(
            Color(0xFF4B8AE2),
            Color(0xFFEF5353),
            Color(0xFF8ECD52),
            Color(0xFFD94996),
            Color.White,
            Color.Black,
            Color(0xFF808080),
        )

        samples.forEach { color ->
            val roundTrip = hsvToColor(colorToHsv(color))

            assertEquals(color.red, roundTrip.red, 0.005f)
            assertEquals(color.green, roundTrip.green, 0.005f)
            assertEquals(color.blue, roundTrip.blue, 0.005f)
        }
    }

    @Test
    fun `hsv conversion clamps and wraps`() {
        assertEquals(1f, hsvToColor(Hsv(360f, 1f, 1f)).red, 0.001f)
        assertEquals(1f, hsvToColor(Hsv(-120f, 1f, 1f)).blue, 0.001f)

        val gray = hsvToColor(Hsv(200f, 0f, 0.5f))
        assertEquals(0.5f, gray.red, 0.005f)
        assertEquals(0.5f, gray.green, 0.005f)
        assertEquals(0.5f, gray.blue, 0.005f)
    }

    @Test
    fun `contrast ratio covers extremes`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.Red, Color.Red), 0.001f)
    }
}
