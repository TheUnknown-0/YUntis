package sh.lsx.untisx.ui.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDateFormatsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val date = LocalDate.of(2026, 10, 4)

    @Test
    @Config(qualifiers = "de-rDE")
    fun `german locale uses german month names`() {
        assertEquals("4. Okt.", AppDateFormats.formatter(context, "dMMM").format(date))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `us locale puts the month first`() {
        assertEquals("Oct 4", AppDateFormats.formatter(context, "dMMM").format(date))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `us locale uses the 12 hour clock`() {
        val time = AppDateFormats.time(context).format(java.time.LocalTime.of(15, 30))

        assertEquals("3:30 PM", time)
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `time respects 24h display`() {
        assertEquals("15:30", AppDateFormats.time(context).format(java.time.LocalTime.of(15, 30)))
    }
}
