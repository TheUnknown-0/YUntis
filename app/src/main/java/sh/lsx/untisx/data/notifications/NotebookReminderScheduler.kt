package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object NotebookReminderScheduler {

    const val WORK_TAG = "notebook_reminder"

    internal const val INPUT_ACCOUNT_ID = "account_id"
    internal const val INPUT_ITEM_ID = "item_id"
    internal const val INPUT_TRIGGER_AT = "trigger_at"
    internal const val INPUT_TRIGGER_REVISION = "trigger_revision"

    private const val TAG = "NotebookReminders"
    private const val UNIQUE_PREFIX = "notebook_reminder_"
    private const val ACCOUNT_TAG_PREFIX = "notebook_account_"
    private const val ITEM_TAG_PREFIX = "notebook_item_"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun ensureAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch { ensureAllNow(appContext) }
    }

    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch { rescheduleAllNow(appContext) }
    }

    suspend fun ensureAllNow(context: Context) {
        val appContext = context.applicationContext
        mutex.withLock { reconcile(appContext, force = false) }
    }

    suspend fun rescheduleAllNow(context: Context) {
        val appContext = context.applicationContext
        mutex.withLock { reconcile(appContext, force = true) }
    }

    fun schedule(context: Context, accountId: String, item: NotebookItem) {
        if (item.id.isBlank()) return

        val plan = NotebookReminderPlanner.plan(
            item = item,
            nowMillis = NotebookReminderClock.now(),
            zone = NotebookReminderClock.zone(),
        )

        if (plan == null) {
            cancel(context, accountId, item.id)
            return
        }

        val accountHash = accountHash(accountId)
        val name = workName(accountId, item.id)
        val existing = NotebookReminderLedger.scheduleEntry(context, accountHash, name)

        if (existing?.triggerAt == plan.triggerAt && existing.revision == plan.revision) return

        enqueue(context, accountId, item.id, plan)
    }

    internal fun scheduleNow(context: Context, accountId: String, item: NotebookItem) {
        if (item.id.isBlank()) return

        val plan = NotebookReminderPlanner.plan(
            item = item,
            nowMillis = NotebookReminderClock.now(),
            zone = NotebookReminderClock.zone(),
        )

        if (plan == null) {
            cancel(context, accountId, item.id)
            return
        }

        enqueue(context, accountId, item.id, plan)
    }

    fun cancel(context: Context, accountId: String, itemId: String) {
        val accountHash = accountHash(accountId)
        val name = workName(accountId, itemId)

        WorkManager.getInstance(context).cancelUniqueWork(name)
        NotebookReminderNotifier.cancel(context, accountHash, name)
        NotebookReminderLedger.clearSchedule(context, accountHash, name)
    }

    fun cancelAccount(context: Context, accountId: String) {
        cancelAccountByHash(context, accountHash(accountId))
    }

    fun cancelAll(context: Context) {
        cancelAllInternal(context.applicationContext)
    }

    internal fun clearState(context: Context, accountId: String, itemId: String) {
        val accountHash = accountHash(accountId)
        val name = workName(accountId, itemId)

        NotebookReminderNotifier.cancel(context, accountHash, name)
        NotebookReminderLedger.clearSchedule(context, accountHash, name)
    }

    internal fun accountHash(accountId: String): String = sha256(accountId).take(16)

    internal fun itemHash(itemId: String): String = sha256(itemId).take(16)

    internal fun workName(accountId: String, itemId: String): String =
        "$UNIQUE_PREFIX${accountHash(accountId)}_${itemHash(itemId)}"

    internal fun accountTag(accountHash: String): String = "$ACCOUNT_TAG_PREFIX$accountHash"

    internal fun itemTag(workName: String): String = "$ITEM_TAG_PREFIX$workName"

    internal fun inputData(
        accountId: String,
        itemId: String,
        plan: NotebookReminderPlan,
    ): Data = workDataOf(
        INPUT_ACCOUNT_ID to accountId,
        INPUT_ITEM_ID to itemId,
        INPUT_TRIGGER_AT to plan.triggerAt,
        INPUT_TRIGGER_REVISION to plan.revision,
    )

    private suspend fun reconcile(context: Context, force: Boolean) {
        AccountStore.ensureLoaded(context)

        if (AccountStore.error.value != null) return
        if (!Notifications.canPost(context)) return

        val accountIds = AccountStore.snapshot
            .map { AccountPrefs.accountId(it) }
            .distinct()

        accountIds.forEach { accountId ->
            val result = try {
                NotebookRepository(context, accountId).load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "notebook read failed: ${e.javaClass.simpleName}")
                null
            }

            when (result) {
                is NotebookReadResult.Loaded -> reconcileAccount(
                    context = context,
                    accountId = accountId,
                    items = result.data.items,
                    force = force,
                )

                NotebookReadResult.Missing -> cancelAccountByHash(context, accountHash(accountId))
                is NotebookReadResult.Failed -> Unit
                else -> Unit
            }
        }

        val currentHashes = accountIds.map { accountHash(it) }.toSet()
        (NotebookReminderLedger.accountHashes(context) - currentHashes)
            .forEach { cancelAccountByHash(context, it) }
    }

    private suspend fun reconcileAccount(
        context: Context,
        accountId: String,
        items: List<NotebookItem>,
        force: Boolean,
    ) {
        val workManager = WorkManager.getInstance(context)
        val accountHash = accountHash(accountId)
        val tag = accountTag(accountHash)

        if (force) {
            workManager.cancelAllWorkByTag(tag)
            NotebookReminderLedger.clearSchedules(context, accountHash)
        }

        val now = NotebookReminderClock.now()
        val zone = NotebookReminderClock.zone()

        val desired = LinkedHashMap<String, Pair<NotebookItem, NotebookReminderPlan>>()
        items.forEach { item ->
            val plan = NotebookReminderPlanner.plan(item, now, zone) ?: return@forEach
            desired[workName(accountId, item.id)] = item to plan
        }

        val activeNames = withContext(Dispatchers.IO) {
            runCatching { workManager.getWorkInfosByTag(tag).get() }
                .getOrDefault(emptyList<WorkInfo>())
                .filter {
                    it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
                }
                .mapNotNull { info ->
                    info.tags
                        .firstOrNull { it.startsWith(ITEM_TAG_PREFIX) }
                        ?.removePrefix(ITEM_TAG_PREFIX)
                }
                .toSet()
        }

        val ledgerNames = NotebookReminderLedger.scheduledWorkNames(context, accountHash)

        desired.forEach { (name, itemPlan) ->
            val (item, plan) = itemPlan
            val entry = NotebookReminderLedger.scheduleEntry(context, accountHash, name)
            val unchanged = entry?.triggerAt == plan.triggerAt && entry.revision == plan.revision
            val delivered = NotebookReminderLedger.isDelivered(
                context,
                accountHash,
                name,
                plan.triggerAt,
            )

            if (delivered) return@forEach
            if (!force && unchanged && name in activeNames) return@forEach

            enqueue(context, accountId, item.id, plan)
        }

        (ledgerNames + activeNames).forEach { name ->
            if (name in desired) return@forEach

            workManager.cancelUniqueWork(name)
            NotebookReminderNotifier.cancel(context, accountHash, name)
            NotebookReminderLedger.clearSchedule(context, accountHash, name)
        }

        if (desired.isEmpty()) {
            NotebookReminderNotifier.cancelAccount(context, accountHash)
            NotebookReminderLedger.clearAccount(context, accountHash)
        }
    }

    private fun enqueue(
        context: Context,
        accountId: String,
        itemId: String,
        plan: NotebookReminderPlan,
    ) {
        val workManager = WorkManager.getInstance(context)
        val accountHash = accountHash(accountId)
        val name = workName(accountId, itemId)
        val delay = (plan.triggerAt - NotebookReminderClock.now()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<NotebookReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(inputData(accountId, itemId, plan))
            .addTag(WORK_TAG)
            .addTag(accountTag(accountHash))
            .addTag(itemTag(name))
            .build()

        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        NotebookReminderLedger.recordSchedule(
            context = context,
            accountHash = accountHash,
            workName = name,
            triggerAt = plan.triggerAt,
            revision = plan.revision,
        )
    }

    private fun cancelAccountByHash(context: Context, accountHash: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(accountTag(accountHash))
        NotebookReminderNotifier.cancelAccount(context, accountHash)
        NotebookReminderLedger.clearAccount(context, accountHash)
    }

    private fun cancelAllInternal(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        NotebookReminderNotifier.cancelAll(context)
        NotebookReminderLedger.clearAll(context)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
