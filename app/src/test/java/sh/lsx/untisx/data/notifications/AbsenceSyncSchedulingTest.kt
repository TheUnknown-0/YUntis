package sh.lsx.untisx.data.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.absences.AbsenceAlerts

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AbsenceSyncSchedulingTest {
    @Test
    fun `absence alerts alone keep periodic sync enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor()).build(),
        )
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        NotificationPrefs.setEnabled(context, true)
        NotificationPrefs.setChangesEnabled(context, false)
        NotificationPrefs.setNotifyNewMessages(context, false)
        NotificationPrefs.setNotifyNewEvents(context, false)
        NotificationPrefs.setNotifyNewAnnouncements(context, false)
        AbsenceAlerts.setEnabled(context, true)

        NotificationScheduler.ensureSyncWork(context)

        val manager = WorkManager.getInstance(context)
        assertTrue(manager.getWorkInfosForUniqueWork("untisx_timetable_sync").get()
            .any { it.state == WorkInfo.State.ENQUEUED })

        AbsenceAlerts.setEnabled(context, false)
        NotificationScheduler.ensureSyncWork(context)
        assertTrue(manager.getWorkInfosForUniqueWork("untisx_timetable_sync").get()
            .all { it.state.isFinished })
    }
}
