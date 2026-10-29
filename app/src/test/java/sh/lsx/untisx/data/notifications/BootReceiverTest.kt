package sh.lsx.untisx.data.notifications

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BootReceiverTest {

    @Test
    fun `boot and clock changes trigger a reschedule`() {
        assertTrue(BootReceiver.isRescheduleAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootReceiver.isRescheduleAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(BootReceiver.isRescheduleAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(BootReceiver.isRescheduleAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(BootReceiver.isRescheduleAction(Intent.ACTION_DATE_CHANGED))
    }

    @Test
    fun `unrelated broadcasts do not trigger a reschedule`() {
        assertFalse(BootReceiver.isRescheduleAction(Intent.ACTION_SCREEN_ON))
        assertFalse(BootReceiver.isRescheduleAction(null))
    }
}
