package sh.lsx.untisx.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.FakeCryptor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionManagerTest {

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
    }

    @After
    fun tearDown() {
        UntisClient.allowHttpForTests = false
        AccountStore.resetForTests()
        server.shutdown()
    }

    @Test
    fun `parallel expired sessions re-authenticate exactly once`() = runBlocking {
        val data = AuthData(
            server = server.url("/").toString(),
            loginName = "school",
            displayName = "School",
            user = "student",
            session = AuthResult(sessionId = "old-session"),
        )

        AccountStore.addAccount(context, data, "secret")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"jsonrpc":"2.0","id":1,"result":{"sessionId":"new-session","personType":5,"personId":1,"klasseId":0}}""",
                ),
        )

        val results = (1..5).map {
            async(Dispatchers.IO) {
                val (sessionId, _) = SessionManager.withValidSession(context, data) { current ->
                    if (current.session.sessionId == "old-session") {
                        throw UntisException.SessionExpired
                    }

                    current.session.sessionId
                }

                sessionId
            }
        }.awaitAll()

        assertEquals(List(5) { "new-session" }, results)
        assertEquals(1, server.requestCount)
    }
}
