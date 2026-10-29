package sh.lsx.untisx.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimetableElement
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncScheduleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @After fun cleanup() { AppClock.reset() }

    @Test fun `boost includes exactly the two hours before the known start`() {
        val start = ZonedDateTime.parse("2026-10-29T08:00:00+01:00[Europe/Berlin]")
        assertFalse(SyncSchedule.inBoostWindow(start.minusHours(2).minusSeconds(1), 800))
        assertTrue(SyncSchedule.inBoostWindow(start.minusHours(2), 800))
        assertTrue(SyncSchedule.inBoostWindow(start.minusMinutes(1), 800))
        assertFalse(SyncSchedule.inBoostWindow(start, 800))
    }

    @Test fun `separate schedules do not turn boost into a news poll`() {
        val account = "schedule-test"
        SyncSchedule.clear(context, account)
        val now = 1_800_000_000_000L
        SyncSchedule.completed(context, account, "messages", now)
        SyncSchedule.completed(context, account, "changes", now)
        assertTrue(SyncSchedule.due(context, account, "changes", 15, now + 15 * 60_000))
        assertFalse(SyncSchedule.due(context, account, "messages", 180, now + 15 * 60_000))
        assertTrue(SyncSchedule.due(context, account, "messages", 180, now + 180 * 60_000))
    }

    @Test fun `cancelled first lesson cannot move the boost later`() {
        val account = "boost-test"
        val now = ZonedDateTime.parse("2026-10-29T06:15:00+01:00[Europe/Berlin]")
        AppClock.set(today = { now.toLocalDate() }, now = { now })
        SyncSchedule.clear(context, account)
        NotificationPrefs.setMorningBoost(context, true)
        NotificationPrefs.setChangesEnabled(context, true)

        val first = TimetableEntry(id = 1, date = 20261029, startTime = 800, endTime = 845, subject = listOf(TimetableElement(id = 1)))
        SyncSchedule.rememberStarts(context, account, listOf(first))
        SyncSchedule.rememberStarts(context, account, listOf(first.copy(startTime = 900)))
        assertTrue(SyncSchedule.isBoostWindow(context, account, now))
        NotificationPrefs.setMorningBoost(context, false)
        assertFalse(SyncSchedule.isBoostWindow(context, account, now))
    }
}
