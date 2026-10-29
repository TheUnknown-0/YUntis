package sh.lsx.untisx.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import sh.lsx.untisx.data.notebook.NotebookNotificationContract
import java.util.concurrent.TimeUnit

class NotebookReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotebookNotificationContract.ACTION_MARK_DONE) return

        val accountId = intent.getStringExtra(
            NotebookNotificationContract.EXTRA_NOTEBOOK_ACCOUNT_ID,
        )?.takeIf { it.isNotBlank() } ?: return
        val itemId = intent.getStringExtra(
            NotebookNotificationContract.EXTRA_NOTEBOOK_ITEM_ID,
        )?.takeIf { it.isNotBlank() } ?: return

        enqueue(context, accountId, itemId)
    }

    internal fun enqueue(
        context: Context,
        accountId: String,
        itemId: String,
        constraints: Constraints = Constraints.NONE,
    ) {
        val accountHash = NotebookReminderScheduler.accountHash(accountId)
        val workName = "notebook_action_${accountHash}_" +
            NotebookReminderScheduler.itemHash(itemId)

        val request = OneTimeWorkRequestBuilder<NotebookReminderActionWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    NotebookReminderScheduler.INPUT_ACCOUNT_ID to accountId,
                    NotebookReminderScheduler.INPUT_ITEM_ID to itemId,
                ),
            )
            .addTag(NotebookReminderScheduler.WORK_TAG)
            .addTag(NotebookReminderScheduler.accountTag(accountHash))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
