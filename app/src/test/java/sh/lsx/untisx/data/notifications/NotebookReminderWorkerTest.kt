package sh.lsx.untisx.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.NotebookData
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookStore
import sh.lsx.untisx.data.notebook.NotebookWriteResult
import sh.lsx.untisx.data.notebook.ReminderSpec
import sh.lsx.untisx.data.notebook.SubjectRef
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.FakeCryptor
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookReminderWorkerTest {

    private lateinit var context: Context
    private lateinit var scheduledWorkDispatcher: TestDispatcher

    private val fixedNow = Instant.parse("2026-10-29T08:30:00Z").toEpochMilli()
    private val trigger = Instant.parse("2026-10-29T08:00:00Z").toEpochMilli()
    private val accountId = "account-A"

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        context.getSharedPreferences(NotebookReminderLedger.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()

        context.getSharedPreferences(NotebookNotificationFocus.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()

        scheduledWorkDispatcher = StandardTestDispatcher()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerCoroutineContext(scheduledWorkDispatcher)
                .build(),
        )

        shadowOf(manager).setNotificationsEnabled(true)

        NotebookNotificationFocus.resetForTests()
        NotebookReminderClock.now = { fixedNow }
        NotebookReminderClock.zone = { ZoneOffset.UTC }
    }

    @After
    fun tearDown() {
        NotebookReminderScheduler.cancelAll(context)
        scheduledWorkDispatcher.scheduler.runCurrent()
        WorkManagerTestInitHelper.closeWorkDatabase()
        context.getSharedPreferences(NotebookReminderLedger.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(NotebookNotificationFocus.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        AccountStore.resetForTests()
        NotebookNotificationFocus.resetForTests()
        NotebookReminderClock.reset()
    }

    private fun item(completedAt: Long? = null) = NotebookItem(
        id = "item-1",
        kind = NotebookKind.TASK,
        title = "Math homework",
        subject = SubjectRef(name = "Math"),
        due = DueRef(
            date = 20261029,
            reminder = ReminderSpec(date = 20261029, minutesOfDay = 8 * 60),
        ),
        createdAt = fixedNow - 1_000,
        updatedAt = 5,
        completedAt = completedAt,
    )

    private fun plan() = NotebookReminderPlan(
        triggerAt = trigger,
        revision = 5,
        reminderDate = 20261029,
        minutesOfDay = 8 * 60,
    )

    private suspend fun write(items: List<NotebookItem>) {
        val result = NotebookStore.write(
            context,
            accountId,
            NotebookData(
                revision = 1,
                items = items,
                updatedAt = fixedNow,
            ),
        )
        assertTrue(result is NotebookWriteResult.Success)
    }

    private fun worker(plan: NotebookReminderPlan = plan()) = TestListenableWorkerBuilder<NotebookReminderWorker>(context)
        .setInputData(NotebookReminderScheduler.inputData(accountId, "item-1", plan))
        .build()

    @Test
    fun `open task posts a notification and creates the channel`() = runBlocking {
        write(listOf(item()))

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertNotNull(manager.getNotificationChannel(NotebookReminderNotifier.CHANNEL_ID))
    }

    @Test
    fun `completed task does not notify`() = runBlocking {
        write(listOf(item(completedAt = fixedNow)))

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `missing item does not notify`() = runBlocking {
        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `stale reminder does not notify`() = runBlocking {
        write(listOf(item()))
        NotebookReminderClock.now = { fixedNow + 2 * 60 * 60 * 1000 }

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `modified task after enqueue does not notify`() = runBlocking {
        write(listOf(item().copy(updatedAt = 6)))

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, shadowOf(manager).allNotifications.size)

        val accountHash = NotebookReminderScheduler.accountHash(accountId)
        val workName = NotebookReminderScheduler.workName(accountId, "item-1")
        val replacement = NotebookReminderLedger.scheduleEntry(context, accountHash, workName)
        assertNotNull(replacement)
        assertEquals(6L, replacement?.revision)
        assertEquals(trigger, replacement?.triggerAt)
        assertFalse(NotebookReminderLedger.isDelivered(context, accountHash, workName, trigger))
    }

    @Test
    fun `replacement revision can still notify once after stale work is rejected`() = runBlocking {
        write(listOf(item().copy(updatedAt = 6)))

        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertEquals(0, shadowOf(manager).allNotifications.size)

        val replacement = worker(plan().copy(revision = 6))
        assertTrue(replacement.doWork() is ListenableWorker.Result.Success)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        replacement.doWork()
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `body-only task uses generic title and keeps body off notification`() = runBlocking {
        write(listOf(item().copy(title = "", body = "Secret body text")))

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)

        val notification = shadowOf(manager).allNotifications.single()
        val title = notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        assertEquals(context.getString(R.string.notebook_notification_default_title), title)
        assertFalse(title.contains("Secret body text"))
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)

        val publicTitle = notification.publicVersion
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        assertEquals(context.getString(R.string.notebook_notification_private), publicTitle)
    }

    @Test
    fun `notification focus token is issued and one-use bound`() = runBlocking {
        write(listOf(item()))

        worker().doWork()

        val notification = shadowOf(manager).allNotifications.single()
        val token = shadowOf(notification.contentIntent)
            .savedIntent
            ?.getStringExtra(NotebookNotificationFocus.EXTRA_FOCUS_TOKEN)

        assertNotNull(token)
        assertTrue(NotebookNotificationFocus.consume(context, token, accountId, "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, token, accountId, "item-1"))
    }

    @Test
    fun `repeated worker run does not duplicate delivery`() = runBlocking {
        write(listOf(item()))

        worker().doWork()
        worker().doWork()

        assertEquals(1, shadowOf(manager).allNotifications.size)

        val accountHash = NotebookReminderScheduler.accountHash(accountId)
        val workName = NotebookReminderScheduler.workName(accountId, "item-1")
        assertTrue(NotebookReminderLedger.isDelivered(context, accountHash, workName, trigger))
    }
}
