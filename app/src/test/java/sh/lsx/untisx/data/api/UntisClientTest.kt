package sh.lsx.untisx.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UntisClientTest {

    @Suppress("unused")
    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        AccountStore.resetForTests()
        UntisClient.allowHttpForTests = true

        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        UntisClient.allowHttpForTests = false
        AccountStore.resetForTests()
        server.shutdown()
    }

    private fun auth(): AuthData {
        val data = AuthData(
            server = server.url("/").toString(),
            loginName = "school",
            displayName = "School",
            user = "student",
            session = AuthResult(sessionId = "session", personType = 5, personId = 5),
        )

        UntisClient.clearSessionCache(AccountPrefs.accountId(data))

        return data
    }

    @Test
    fun `lesson details request keeps the query string out of the path`() = runBlocking {
        val data = auth()

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"jwt":"test-jwt"}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"calendarEntries":[{"id":42,"lessonInfo":"Info","substText":"Sub","teachingContent":"Topic","notesAll":"Notes"}]}""",
                ),
        )

        val detail = UntisClient.fetchLessonDetails(
            auth = data,
            password = "secret",
            entryId = 42,
            date = LocalDate.of(2026, 10, 5),
            startTime = 955,
            endTime = 1040,
        )

        assertEquals("Info", detail?.info)
        assertEquals("Sub", detail?.substitution)

        server.takeRequest()
        val detailRequest = server.takeRequest()

        val path = detailRequest.path.orEmpty()
        assertTrue("query lost in path: $path", path.startsWith("/WebUntis/api/rest/view/v2/calendar-entry/detail?"))
        assertTrue("elementId missing: $path", path.contains("elementId=5"))
        assertTrue("start missing: $path", path.contains("startDateTime=2026-10-05T09:55:00"))
        assertTrue("encoded query marker: $path", !path.contains("%3F"))
    }

    @Test
    fun `bad credentials error is mapped`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-8500,"message":"denied"}}"""),
        )

        try {
            UntisClient.authenticate(
                server = server.url("/").toString(),
                loginName = "school",
                user = "student",
                password = "wrong",
            )
            fail("expected BadCredentials")
        } catch (expected: UntisException.BadCredentials) {
            // expected
        }
    }

    @Test
    fun `expired session error is mapped`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-8520,"message":"expired"}}"""),
        )

        try {
            UntisClient.getTimetable(auth(), startDate = 20261005, endDate = 20261011)
            fail("expected SessionExpired")
        } catch (expected: UntisException.SessionExpired) {
            // expected
        }
    }

    @Test
    fun `other api errors keep their code`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":-7004,"message":"no timetable"}}"""),
        )

        try {
            UntisClient.getTimetable(auth(), startDate = 20261005, endDate = 20261011)
            fail("expected Api error")
        } catch (expected: UntisException.Api) {
            assertEquals(UntisClient.RPC_NO_TIMETABLE, expected.code)
        }
    }

    @Test
    fun `student timetable request asks for the person id`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"result":[]}"""),
        )

        UntisClient.getTimetable(auth(), startDate = 20261005, endDate = 20261011)

        val body = server.takeRequest().body.readUtf8()
        val params = Json.parseToJsonElement(body).jsonObject["params"]!!.jsonObject

        assertEquals(5, params["id"]!!.jsonPrimitive.intOrNull)
        assertEquals(5, params["type"]!!.jsonPrimitive.intOrNull)
        assertEquals(20261005, params["startDate"]!!.jsonPrimitive.intOrNull)
        assertEquals(20261011, params["endDate"]!!.jsonPrimitive.intOrNull)
    }

    @Test
    fun `plain http servers are rejected outside tests`() = runBlocking {
        UntisClient.allowHttpForTests = false

        try {
            UntisClient.authenticate(
                server = server.url("/").toString(),
                loginName = "school",
                user = "student",
                password = "secret",
            )
            fail("expected Api error")
        } catch (expected: UntisException.Api) {
            assertEquals(UntisClient.RPC_INVALID, expected.code)
        } finally {
            UntisClient.allowHttpForTests = true
        }
    }
}
