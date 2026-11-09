package sh.lsx.untisx.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!isRescheduleAction(intent.action)) return

        val pendingResult = goAsync()

        try {
            NotificationScheduler.ensureAll(context)
            sh.lsx.untisx.data.widget.WidgetUpdater.updateAll(context)
            sh.lsx.untisx.data.automation.Automation.refresh(context)
        } catch (e: Exception) {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotebookReminderScheduler.rescheduleAllNow(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        internal fun isRescheduleAction(action: String?): Boolean = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> true

            else -> false
        }
    }
}
