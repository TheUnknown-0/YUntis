package sh.lsx.untisx.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubjectColorsPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `subject text colors round trip and reset`() {
        SubjectColorsPrefs.setSubjectTextColor(context, "MA", 0xFF112233.toInt())
        SubjectColorsPrefs.setSubjectTextColor(context, "EN", 0xFF445566.toInt())

        val colors = SubjectColorsPrefs.getSubjectTextColors(context)

        assertEquals(0xFF112233.toInt(), colors["MA"])
        assertEquals(0xFF445566.toInt(), colors["EN"])

        SubjectColorsPrefs.setSubjectTextColor(context, "MA", null)

        val afterReset = SubjectColorsPrefs.getSubjectTextColors(context)

        assertNull(afterReset["MA"])
        assertEquals(0xFF445566.toInt(), afterReset["EN"])
    }

    @Test
    fun `strike and exam colors round trip and reset`() {
        SubjectColorsPrefs.setStrikeColor(context, 0xFF00AA00.toInt())
        SubjectColorsPrefs.setExamColor(context, 0xFF0000AA.toInt())

        assertEquals(0xFF00AA00.toInt(), SubjectColorsPrefs.getStrikeColor(context))
        assertEquals(0xFF0000AA.toInt(), SubjectColorsPrefs.getExamColor(context))

        SubjectColorsPrefs.setStrikeColor(context, null)
        SubjectColorsPrefs.setExamColor(context, null)

        assertNull(SubjectColorsPrefs.getStrikeColor(context))
        assertNull(SubjectColorsPrefs.getExamColor(context))
    }
}
