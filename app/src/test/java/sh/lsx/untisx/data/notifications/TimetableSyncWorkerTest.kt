package sh.lsx.untisx.data.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.CredentialsPrefs
import sh.lsx.untisx.data.prefs.FakeCryptor
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()

        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        UntisClient.allowHttpForTests = true

        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(true)

        NotificationPrefs.setEnabled(context, true)
        NotificationPrefs.setChangesEnabled(context, true)
    }

    @After
    fun tearDown() {
        UntisClient.allowHttpForTests = false
        AccountStore.resetForTests()
        AppClock.reset()
        server.shutdown()
    }

    private fun account() = AuthData(
        server = server.url("/").toString(),
        loginName = "school",
        displayName = "School",
        user = "student",
        session = AuthResult(sessionId = "old-session"),
    )

    @Test
    fun `bad credentials stop the sync and never retry on later runs`() = runBlocking {
        val data = account()

        AccountStore.addAccount(context, data, "secret")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-8500,"message":"denied"}}"""),
        )

        val first = TestListenableWorkerBuilder<TimetableSyncWorker>(context).build().doWork()

        assertTrue(first is ListenableWorker.Result.Success)
        assertEquals(1, server.requestCount)
        assertTrue(CredentialsPrefs.isInvalid(context, AccountPrefs.accountId(data)))

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).allNotifications.size)

        val requestCountAfterFirstRun = server.requestCount
        val second = TestListenableWorkerBuilder<TimetableSyncWorker>(context).build().doWork()

        assertTrue(second is ListenableWorker.Result.Success)
        assertEquals(requestCountAfterFirstRun, server.requestCount)
    }

    @Test
    fun `transient network failure retries`() = runBlocking {
        AccountStore.addAccount(context, account(), "secret")

        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = TestListenableWorkerBuilder<TimetableSyncWorker>(context).build().doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `network failure gives up after three attempts`() = runBlocking {
        AccountStore.addAccount(context, account(), "secret")

        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val result = TestListenableWorkerBuilder<TimetableSyncWorker>(context)
            .setRunAttemptCount(3)
            .build()
            .doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `changed room in a fetched week posts one notification`() = runBlocking {
        val data = account()
        AccountStore.addAccount(context, data, "secret")

        NotificationPrefs.setNotifyNewMessages(context, false)
        NotificationPrefs.setNotifyNewEvents(context, false)
        NotificationPrefs.setNotifyNewAnnouncements(context, false)

        val monday = LocalDate.now().toUntisWeekStart()
        val date = monday.toUntisDate()

        val previous = TimetableEntry(
            id = 1,
            date = date,
            startTime = 800,
            endTime = 845,
            klasse = listOf(TimetableElement(id = 10, name = "Klasse")),
            subject = listOf(TimetableElement(id = 1, name = "Mathe")),
            room = listOf(TimetableElement(id = 100, name = "A101")),
        )
        val fresh = previous.copy(room = listOf(TimetableElement(id = 101, name = "B202")))

        ChangeBaseline.save(
            context,
            AccountPrefs.accountId(data),
            mapOf(date.toString() to listOf(previous)),
        )

        val json = Json { encodeDefaults = true }

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"jsonrpc":"2.0","id":1,"result":[${json.encodeToString(TimetableEntry.serializer(), fresh)}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"jsonrpc":"2.0","id":2,"result":[]}"""),
        )

        val result = TestListenableWorkerBuilder<TimetableSyncWorker>(context).build().doWork()

        assertTrue(result is ListenableWorker.Result.Success)

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = shadowOf(manager).allNotifications

        assertEquals(1, notifications.size)
        assertEquals("schedule_changes", notifications.single().channelId)
    }
}
