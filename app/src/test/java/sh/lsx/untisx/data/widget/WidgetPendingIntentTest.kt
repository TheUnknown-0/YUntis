package sh.lsx.untisx.data.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.notifications.Notifications
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetPendingIntentTest {
    @Test
    fun `widgets showing different days keep their own navigation target`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = WidgetUpdater.openApp(context, LocalDate.of(2026, 11, 16))
        val second = WidgetUpdater.openApp(context, LocalDate.of(2026, 11, 17))

        assertNotEquals(first, second)
        assertEquals(20261116, shadowOf(first).savedIntent.getIntExtra(Notifications.EXTRA_FOCUS_DATE, 0))
        assertEquals(20261117, shadowOf(second).savedIntent.getIntExtra(Notifications.EXTRA_FOCUS_DATE, 0))
    }
}
