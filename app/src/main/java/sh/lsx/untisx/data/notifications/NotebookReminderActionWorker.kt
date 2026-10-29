package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.notebook.NotebookReadFailure
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.notebook.NotebookStorageFailure
import sh.lsx.untisx.data.notebook.NotebookWriteResult
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore

class NotebookReminderActionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val accountId = inputData.getString(NotebookReminderScheduler.INPUT_ACCOUNT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val itemId = inputData.getString(NotebookReminderScheduler.INPUT_ITEM_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()

        AccountStore.ensureLoaded(context)
        if (AccountStore.error.value != null) {
            return retryOrKeepNotification(transient = true)
        }

        val accountExists = AccountStore.snapshot.any {
            AccountPrefs.accountId(it) == accountId
        }

        if (!accountExists) {
            NotebookReminderScheduler.cancel(context, accountId, itemId)
            return Result.success()
        }

        val result = try {
            NotebookRepository(context, accountId).setCompleted(itemId, completed = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "mark done failed: ${e.javaClass.simpleName}")
            return retryOrKeepNotification()
        }

        return when (result) {
            is NotebookWriteResult.Success -> {
                NotebookReminderScheduler.cancel(context, accountId, itemId)
                Result.success()
            }

            is NotebookWriteResult.NotFound -> {
                NotebookReminderScheduler.cancel(context, accountId, itemId)
                Result.success()
            }

            is NotebookWriteResult.Invalid -> {
                NotebookReminderScheduler.cancel(context, accountId, itemId)
                Result.success()
            }

            is NotebookWriteResult.ReadFailed -> {
                val transient = result.reason == NotebookReadFailure.IO ||
                    result.reason == NotebookReadFailure.TRANSIENT
                retryOrKeepNotification(transient)
            }

            is NotebookWriteResult.StorageFailed -> {
                retryOrKeepNotification(result.reason == NotebookStorageFailure.IO)
            }

            else -> Result.success()
        }
    }

    private fun retryOrKeepNotification(transient: Boolean = true): Result {
        if (transient && runAttemptCount < MAX_RETRIES) return Result.retry()

        return Result.success()
    }

    companion object {
        private const val TAG = "NotebookActionWorker"
        private const val MAX_RETRIES = 2
    }
}
