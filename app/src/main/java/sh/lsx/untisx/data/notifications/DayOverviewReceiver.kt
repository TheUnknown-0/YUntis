package sh.lsx.untisx.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class DayOverviewReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationPrefs.isEnabled(context) ||
            !NotificationPrefs.isOverviewEnabled(context) ||
            !Notifications.canPost(context)
        ) {
            return
        }

        val request = OneTimeWorkRequestBuilder<DayOverviewWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            NotificationScheduler.OVERVIEW_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )

        NotificationScheduler.scheduleNextOverview(context)
    }
}
