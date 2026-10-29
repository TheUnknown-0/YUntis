package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookReadFailure
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository

class NotebookReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Notifications.ensureChannels(context)

        val accountId = inputData.getString(NotebookReminderScheduler.INPUT_ACCOUNT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val itemId = inputData.getString(NotebookReminderScheduler.INPUT_ITEM_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val expectedTrigger = inputData.getLong(
            NotebookReminderScheduler.INPUT_TRIGGER_AT,
            Long.MIN_VALUE,
        )
        val expectedRevision = inputData.getLong(
            NotebookReminderScheduler.INPUT_TRIGGER_REVISION,
            Long.MIN_VALUE,
        )

        if (expectedTrigger == Long.MIN_VALUE || expectedRevision == Long.MIN_VALUE) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        val result = try {
            NotebookRepository(context, accountId).load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "notebook read failed: ${e.javaClass.simpleName}")
            return retryOrGiveUp(context, accountId, itemId, transient = true)
        }

        return when (result) {
            is NotebookReadResult.Loaded -> handleLoaded(
                context = context,
                accountId = accountId,
                itemId = itemId,
                items = result.data.items,
                expectedTrigger = expectedTrigger,
                expectedRevision = expectedRevision,
            )

            NotebookReadResult.Missing -> {
                NotebookReminderScheduler.clearState(context, accountId, itemId)
                Result.success()
            }

            is NotebookReadResult.Failed -> retryOrGiveUp(
                context = context,
                accountId = accountId,
                itemId = itemId,
                transient = result.reason == NotebookReadFailure.IO ||
                    result.reason == NotebookReadFailure.TRANSIENT,
            )

            else -> Result.success()
        }
    }

    private suspend fun handleLoaded(
        context: Context,
        accountId: String,
        itemId: String,
        items: List<NotebookItem>,
        expectedTrigger: Long,
        expectedRevision: Long,
    ): Result {
        val item = items.firstOrNull { it.id == itemId }

        if (item == null) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        val plan = NotebookReminderPlanner.plan(
            item = item,
            nowMillis = NotebookReminderClock.now(),
            zone = NotebookReminderClock.zone(),
        )

        if (plan == null) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        if (plan.triggerAt != expectedTrigger || plan.revision != expectedRevision) {
            NotebookReminderScheduler.scheduleNow(context, accountId, item)
            return Result.success()
        }

        val now = NotebookReminderClock.now()

        if (plan.triggerAt > now + NotebookReminderPlanner.EARLY_TOLERANCE_MILLIS) {
            NotebookReminderScheduler.scheduleNow(context, accountId, item)
            return Result.success()
        }

        val accountHash = NotebookReminderScheduler.accountHash(accountId)
        val workName = NotebookReminderScheduler.workName(accountId, itemId)

        if (!Notifications.canPost(context)) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        val latestResult = try {
            NotebookRepository(context, accountId).load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "notebook re-read failed: ${e.javaClass.simpleName}")
            return retryOrGiveUp(context, accountId, itemId, transient = true)
        }

        val latestItem = when (latestResult) {
            is NotebookReadResult.Loaded -> latestResult.data.items.firstOrNull { it.id == itemId }
            NotebookReadResult.Missing -> {
                NotebookReminderScheduler.clearState(context, accountId, itemId)
                return Result.success()
            }

            is NotebookReadResult.Failed -> return retryOrGiveUp(
                context = context,
                accountId = accountId,
                itemId = itemId,
                transient = latestResult.reason == NotebookReadFailure.IO ||
                    latestResult.reason == NotebookReadFailure.TRANSIENT,
            )

            else -> null
        }

        if (latestItem == null) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        val latestNow = NotebookReminderClock.now()
        val latestPlan = NotebookReminderPlanner.plan(
            item = latestItem,
            nowMillis = latestNow,
            zone = NotebookReminderClock.zone(),
        )

        if (latestPlan == null) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        if (latestPlan.triggerAt != expectedTrigger || latestPlan.revision != expectedRevision) {
            NotebookReminderScheduler.scheduleNow(context, accountId, latestItem)
            return Result.success()
        }

        if (latestPlan.triggerAt > latestNow + NotebookReminderPlanner.EARLY_TOLERANCE_MILLIS) {
            NotebookReminderScheduler.scheduleNow(context, accountId, latestItem)
            return Result.success()
        }

        if (NotebookReminderLedger.isDelivered(context, accountHash, workName, latestPlan.triggerAt)) {
            return Result.success()
        }

        if (!Notifications.canPost(context)) {
            NotebookReminderScheduler.clearState(context, accountId, itemId)
            return Result.success()
        }

        val posted = try {
            NotebookReminderNotifier.notify(context, accountId, latestItem, latestPlan.triggerAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        if (!posted) {
            return retryOrGiveUp(context, accountId, itemId, transient = true)
        }

        NotebookReminderLedger.markDelivered(context, accountHash, workName, latestPlan.triggerAt)
        return Result.success()
    }

    private fun retryOrGiveUp(
        context: Context,
        accountId: String,
        itemId: String,
        transient: Boolean,
    ): Result {
        if (transient && runAttemptCount < MAX_RETRIES) return Result.retry()

        NotebookReminderScheduler.clearState(context, accountId, itemId)
        return Result.success()
    }

    companion object {
        private const val TAG = "NotebookReminderWorker"
        private const val MAX_RETRIES = 2
    }
}
