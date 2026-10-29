package sh.lsx.untisx.data.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
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
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.ReminderSpec
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookReminderSchedulerTest {

    private lateinit var context: Context

    private val fixedNow = Instant.parse("2026-10-29T07:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        context.getSharedPreferences(NotebookReminderLedger.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)

        NotebookReminderClock.now = { fixedNow }
        NotebookReminderClock.zone = { ZoneOffset.UTC }
    }

    @After
    fun tearDown() {
        NotebookReminderScheduler.cancelAll(context)
        context.getSharedPreferences(NotebookReminderLedger.PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotebookReminderClock.reset()
    }

    private fun item(
        id: String = "item-1",
        updatedAt: Long = 1,
        title: String = "Secret title",
        body: String = "Secret body",
    ) = NotebookItem(
        id = id,
        kind = NotebookKind.TASK,
        title = title,
        body = body,
        due = DueRef(
            date = 20261030,
            reminder = ReminderSpec(date = 20261030, minutesOfDay = 8 * 60),
        ),
        createdAt = fixedNow,
        updatedAt = updatedAt,
    )

    private fun workInfos(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosByTag(NotebookReminderScheduler.WORK_TAG).get()

    private fun uniqueWorkInfos(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private fun planFor(item: NotebookItem): NotebookReminderPlan =
        requireNotNull(NotebookReminderPlanner.plan(item, fixedNow, ZoneOffset.UTC))

    private fun dataFor(item: NotebookItem, accountId: String = "account-A") =
        NotebookReminderScheduler.inputData(accountId, item.id, planFor(item))

    @Test
    fun `work input contains IDs and non-sensitive trigger metadata only`() {
        val item = item()
        NotebookReminderScheduler.schedule(context, "account-A", item)

        assertEquals(1, workInfos().count { it.state == WorkInfo.State.ENQUEUED })

        val data = dataFor(item)

        assertEquals(
            setOf(
                NotebookReminderScheduler.INPUT_ACCOUNT_ID,
                NotebookReminderScheduler.INPUT_ITEM_ID,
                NotebookReminderScheduler.INPUT_TRIGGER_AT,
                NotebookReminderScheduler.INPUT_TRIGGER_REVISION,
            ),
            data.keyValueMap.keys,
        )

        val values = data.keyValueMap.values.joinToString(" ")
        assertFalse(values.contains("Secret title"))
        assertFalse(values.contains("Secret body"))
        assertEquals("account-A", data.getString(NotebookReminderScheduler.INPUT_ACCOUNT_ID))
        assertEquals("item-1", data.getString(NotebookReminderScheduler.INPUT_ITEM_ID))
        assertTrue(data.getLong(NotebookReminderScheduler.INPUT_TRIGGER_AT, -1L) > 0L)
        assertEquals(
            1L,
            data.getLong(NotebookReminderScheduler.INPUT_TRIGGER_REVISION, -1L),
        )
    }

    @Test
    fun `unchanged item is not re-enqueued`() {
        NotebookReminderScheduler.schedule(context, "account-A", item())
        val first = workInfos().single { it.state == WorkInfo.State.ENQUEUED }

        NotebookReminderScheduler.schedule(context, "account-A", item())
        val active = workInfos().filter { it.state == WorkInfo.State.ENQUEUED }

        assertEquals(1, active.size)
        assertEquals(first.id, active.single().id)
    }

    @Test
    fun `changed item revision replaces the work`() {
        NotebookReminderScheduler.schedule(context, "account-A", item())

        NotebookReminderScheduler.schedule(context, "account-A", item(updatedAt = 2))
        val active = workInfos().filter { it.state == WorkInfo.State.ENQUEUED }

        assertEquals(1, active.size)

        val accountHash = NotebookReminderScheduler.accountHash("account-A")
        val workName = NotebookReminderScheduler.workName("account-A", "item-1")
        assertEquals(
            2L,
            NotebookReminderLedger.scheduleEntry(context, accountHash, workName)?.revision,
        )
    }

    @Test
    fun `cancelAccount leaves other accounts untouched`() {
        NotebookReminderScheduler.schedule(context, "account-A", item(id = "a"))
        NotebookReminderScheduler.schedule(context, "account-B", item(id = "b"))

        NotebookReminderScheduler.cancelAccount(context, "account-A")

        val accountAWork = NotebookReminderScheduler.workName("account-A", "a")
        val accountBWork = NotebookReminderScheduler.workName("account-B", "b")

        assertTrue(
            uniqueWorkInfos(accountAWork).none {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.BLOCKED
            },
        )
        assertTrue(uniqueWorkInfos(accountBWork).any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `scheduler class does not use exact alarms`() {
        val resource = NotebookReminderScheduler::class.java
            .getResourceAsStream("NotebookReminderScheduler.class")
        assertNotNull(resource)

        val bytecode = String(resource!!.readBytes(), Charsets.ISO_8859_1)

        assertTrue(bytecode.contains("WorkManager"))
        assertFalse(bytecode.contains("AlarmManager"))
        assertFalse(bytecode.contains("canScheduleExactAlarms"))
    }
}
