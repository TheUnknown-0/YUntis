package sh.lsx.untisx.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class ExamReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationPrefs.isEnabled(context) ||
            !NotificationPrefs.examRemindersEnabled(context) ||
            !Notifications.canPost(context)
        ) {
            return
        }

        val request = OneTimeWorkRequestBuilder<ExamReminderWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            NotificationScheduler.EXAM_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )

        NotificationScheduler.scheduleNextExamReminder(context)
    }
}
