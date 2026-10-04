package sh.lsx.untisx.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ExamReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<ExamReminderWorker>().build()

        WorkManager.getInstance(context).enqueue(request)
        NotificationScheduler.scheduleNextExamReminder(context)
    }
}
