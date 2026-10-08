package sh.lsx.untisx.data.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UntisRpcTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `429 maps to rate limited with retry-after`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "120"),
        )

        val error = assertThrows(UntisException.RateLimited::class.java) {
            runBlocking {
                UntisRpc.executeRpc(server.url("/jsonrpc.do").toString(), "getTimetable", buildJsonObject {})
            }
        }

        assertEquals(120L, error.retryAfterSeconds)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `429 without retry-after maps to rate limited without seconds`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val error = assertThrows(UntisException.RateLimited::class.java) {
            runBlocking {
                UntisRpc.executeRpc(server.url("/jsonrpc.do").toString(), "getTimetable", buildJsonObject {})
            }
        }

        assertNull(error.retryAfterSeconds)
    }
}
