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
}
