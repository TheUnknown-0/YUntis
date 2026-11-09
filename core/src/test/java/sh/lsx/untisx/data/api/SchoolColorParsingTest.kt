package sh.lsx.untisx.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate

/** School colors must survive parsing on all three ways the app loads a timetable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchoolColorParsingTest {

    private lateinit var server: MockWebServer
    private val monday = LocalDate.of(2026, 11, 9)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    path.contains("/api/mobile/v2/") -> json("""{"jwt":"test-jwt"}""")
                    path.contains("/api/rest/view/v1/timetable/entries") -> json(REST_ENTRIES)
                    body.contains("getAppSharedSecret") -> rpc("\"JBSWY3DPEHPK3PXP\"")
                    body.contains("getTimetable2017") -> rpc(INTERN_TIMETABLE)
                    body.contains("getUserData2017") -> rpc(USER_DATA)
                    body.contains("getSubjects") -> rpc(SUBJECTS)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        UntisClient.allowHttpForTests = true
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
        session = AuthResult(sessionId = "session", personType = 5, personId = 42),
    )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun rpc(result: String) = json("""{"jsonrpc":"2.0","id":1,"result":$result}""")

    @Test
    fun `REST timetable keeps the lesson color`() = runBlocking {
        val entries = UntisClient.fetchTimetableEntries(auth(), "pw", monday, monday.plusDays(6))!!
        assertEquals(2, entries.size)
        assertEquals("#F49F25", entries[0].backColor)
        assertEquals("#000000", entries[0].foreColor)
        assertNull(entries[1].backColor)
    }

    @Test
    fun `internal timetable keeps period colors`() = runBlocking {
        val entries = UntisClient.getTimetableIntern(auth(), "pw", monday, monday.plusDays(6))
        assertEquals(1, entries.size)
        assertEquals("#4f8df5", entries[0].backColor)
        assertEquals("#ffffff", entries[0].foreColor)
    }

    @Test
    fun `master data keeps subject colors`() = runBlocking {
        val master = UntisClient.fetchMasterData(auth(), "pw")
        val math = master.subjects.first { it.name == "M" }
        assertEquals("4F8DF5", math.backColor)
        assertEquals("FFFFFF", math.foreColor)
        assertNull(master.subjects.first { it.name == "Ph" }.backColor)
    }

    @Test
    fun `classic subject list keeps colors`() = runBlocking {
        val subjects = UntisClient.getSubjects(auth())
        assertEquals("F49F25", subjects.first { it.name == "D" }.backColor)
        assertNull(subjects.first { it.name == "Ph" }.backColor)
    }

    private companion object {
        val REST_ENTRIES = """
            {"days":[{"date":"2026-11-09","gridEntries":[
              {"ids":[1],"duration":{"start":"2026-11-09T08:00","end":"2026-11-09T08:45"},"type":"NORMAL_TEACHING_PERIOD","status":"REGULAR",
               "color":"#F49F25","foreColor":"#000000",
               "position1":[{"current":{"type":"SUBJECT","id":1,"shortName":"D","longName":"Deutsch"}}]},
              {"ids":[2],"duration":{"start":"2026-11-09T08:50","end":"2026-11-09T09:35"},"type":"NORMAL_TEACHING_PERIOD","status":"REGULAR",
               "position1":[{"current":{"type":"SUBJECT","id":4,"shortName":"Ph","longName":"Physik"}}]}
            ]}]}
        """.trimIndent()

        val INTERN_TIMETABLE = """
            {"timetable":{"periods":[
              {"id":7,"startDateTime":"2026-11-09T08:00","endDateTime":"2026-11-09T08:45",
               "foreColor":"#ffffff","backColor":"#4f8df5","elements":[{"type":"SUBJECT","id":2}]}
            ]}}
        """.trimIndent()

        val USER_DATA = """
            {"masterData":{"subjects":[
              {"id":2,"name":"M","longName":"Mathematik","backColor":"4F8DF5","foreColor":"FFFFFF"},
              {"id":4,"name":"Ph","longName":"Physik"}
            ]}}
        """.trimIndent()

        val SUBJECTS = """
            [{"id":1,"name":"D","longName":"Deutsch","backColor":"F49F25","foreColor":"000000"},
             {"id":4,"name":"Ph","longName":"Physik"}]
        """.trimIndent()
    }
}
