package sh.lsx.untisx.ui.timetable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableFetchTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var fetch: TimetableFetch

    private val monday = LocalDate.of(2026, 10, 5)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()

        UntisClient.allowHttpForTests = true

        fetch = TimetableFetch(
            context = context,
            requireAuth = { auth() },
            withPassword = { "secret" },
            onFreshSession = {},
            resolveElementIds = { it },
        )
    }

    @After
    fun tearDown() {
        UntisClient.allowHttpForTests = false
        server.shutdown()
    }

    private fun auth() = AuthData(
        server = server.url("/").toString(),
        loginName = "school",
        displayName = "School",
        user = "student",
        session = AuthResult(sessionId = "session"),
    )

    @Test
    fun `rate limit stops the fallback chain`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))

        assertThrows(UntisException.RateLimited::class.java) {
            runBlocking { fetch.fetchWeek(1, monday, anchorMonday = monday) }
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `rest failure falls back to intern and legacy`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"result":[]}"""),
        )

        val result = runBlocking { fetch.fetchWeek(1, monday, anchorMonday = monday) }

        assertEquals(3, server.requestCount)
        assertTrue(result.entries.isEmpty())
        assertNull(result.fresh)
    }
}
