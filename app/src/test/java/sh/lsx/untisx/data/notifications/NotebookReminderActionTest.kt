package sh.lsx.untisx.data.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.notebook.NotebookData
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookStore
import sh.lsx.untisx.data.notebook.NotebookWriteResult
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.FakeCryptor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookReminderActionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        context.getSharedPreferences(NotebookReminderLedger.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()

        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)

        NotebookReminderClock.reset()
    }

    @After
    fun tearDown() {
        NotebookReminderScheduler.cancelAll(context)
        context.getSharedPreferences(NotebookReminderLedger.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        AccountStore.resetForTests()
        NotebookReminderClock.reset()
    }

    private fun auth(user: String) = AuthData(
        server = "https://example.test/",
        loginName = "school",
        displayName = "School",
        user = user,
        session = AuthResult(sessionId = "session-$user"),
    )

    private fun item(id: String) = NotebookItem(
        id = id,
        kind = NotebookKind.TASK,
        title = "Task $id",
        createdAt = 1,
        updatedAt = 1,
    )

    private suspend fun write(accountId: String, itemId: String) {
        val result = NotebookStore.write(
            context,
            accountId,
            NotebookData(
                revision = 1,
                items = listOf(item(itemId)),
                updatedAt = 1,
            ),
        )
        assertTrue(result is NotebookWriteResult.Success)
    }

    private fun actionWorker(accountId: String, itemId: String) =
        TestListenableWorkerBuilder<NotebookReminderActionWorker>(context)
            .setInputData(
                workDataOf(
                    NotebookReminderScheduler.INPUT_ACCOUNT_ID to accountId,
                    NotebookReminderScheduler.INPUT_ITEM_ID to itemId,
                ),
            )
            .build()

    private fun accountTaggedInfos(accountId: String): List<WorkInfo> {
        val accountHash = NotebookReminderScheduler.accountHash(accountId)

        return WorkManager.getInstance(context)
            .getWorkInfosByTag(NotebookReminderScheduler.accountTag(accountHash))
            .get()
    }

    private suspend fun load(accountId: String): NotebookItem {
        val result = NotebookStore.load(context, accountId)
        assertTrue(result is NotebookReadResult.Loaded)
        return (result as NotebookReadResult.Loaded).data.items.single()
    }

    @Test
    fun `mark done uses the account from the intent not the active account`() = runBlocking {
        val authA = auth("student-a")
        val authB = auth("student-b")
        val idA = AccountPrefs.accountId(authA)
        val idB = AccountPrefs.accountId(authB)

        AccountStore.addAccount(context, authA, "secret-a")
        AccountStore.addAccount(context, authB, "secret-b")

        assertEquals(idB, AccountStore.activeId.value)

        write(idA, "item-a")
        write(idB, "item-b")

        val result = actionWorker(idA, "item-a").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNotNull(load(idA).completedAt)
        assertNull(load(idB).completedAt)
    }

    @Test
    fun `repeated mark done is idempotent`() = runBlocking {
        val authA = auth("student-a")
        val idA = AccountPrefs.accountId(authA)
        AccountStore.addAccount(context, authA, "secret-a")
        write(idA, "item-a")

        val first = actionWorker(idA, "item-a").doWork()
        val completedAt = load(idA).completedAt
        val second = actionWorker(idA, "item-a").doWork()

        assertTrue(first is ListenableWorker.Result.Success)
        assertTrue(second is ListenableWorker.Result.Success)
        assertNotNull(completedAt)
        assertEquals(completedAt, load(idA).completedAt)
    }

    @Test
    fun `action work is account tagged and cancelAccount cancels it`() = runBlocking {
        val authA = auth("student-a")
        val idA = AccountPrefs.accountId(authA)
        AccountStore.addAccount(context, authA, "secret-a")

        NotebookReminderActionReceiver().enqueue(
            context = context,
            accountId = idA,
            itemId = "item-a",
            constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build(),
        )

        assertTrue(accountTaggedInfos(idA).any { it.state == WorkInfo.State.ENQUEUED })

        NotebookReminderScheduler.cancelAccount(context, idA)

        assertFalse(
            accountTaggedInfos(idA).any {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.BLOCKED
            },
        )
    }

    @Test
    fun `mark done for removed account is a no-op and does not recreate data`() = runBlocking {
        val authA = auth("student-a")
        val idA = AccountPrefs.accountId(authA)

        AccountStore.addAccount(context, authA, "secret-a")
        write(idA, "item-a")
        AccountStore.removeAccount(context, idA)

        write(idA, "item-a")

        val result = actionWorker(idA, "item-a").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(load(idA).completedAt)
        assertFalse(AccountStore.snapshot.any { AccountPrefs.accountId(it) == idA })
    }
}
