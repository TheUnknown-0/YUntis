package sh.lsx.untisx.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.api.UntisException
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchoolRequestGuardTest {
    @Test fun `cooldown blocks a different endpoint before hitting the server`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))

            val http = OkHttpClient.Builder().addInterceptor(SchoolRequestGuard).build()

            assertThrows(UntisException.RateLimited::class.java) {
                http.newCall(Request.Builder().url(server.url("/timetable")).build()).execute()
            }

            assertThrows(UntisException.RateLimited::class.java) {
                http.newCall(Request.Builder().url(server.url("/messages")).build()).execute()
            }

            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `retry-after supports HTTP dates and invalid values have a safe fallback`() {
        val now = Instant.parse("2026-10-29T06:00:00Z").toEpochMilli()
        assertEquals(600L, SchoolRequestGuard.retryAfterSeconds("Thu, 29 Oct 2026 06:10:00 GMT", now))
        assertEquals(300L, SchoolRequestGuard.retryAfterSeconds("nonsense", now))
        assertEquals(1L, SchoolRequestGuard.retryAfterSeconds("-10", now))
    }

    @Test fun `redirect cannot send a school cookie to another origin`() {
        MockWebServer().use { source -> MockWebServer().use { target ->
            source.start()
            target.start()
            source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", target.url("/steal")))

            val http = OkHttpClient.Builder().addNetworkInterceptor(SameOriginRedirects).build()
            assertThrows(IOException::class.java) {
                http.newCall(Request.Builder().url(source.url("/school")).header("Cookie", "JSESSIONID=secret").build()).execute()
            }

            assertEquals(0, target.requestCount)
        } }
    }

    @Test fun `same origin redirect still works`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/new"))
            server.enqueue(MockResponse().setBody("ok"))

            val http = OkHttpClient.Builder().addNetworkInterceptor(SameOriginRedirects).build()
            http.newCall(Request.Builder().url(server.url("/old")).build()).execute().use {
                assertEquals("ok", it.body!!.string())
            }

            assertEquals(2, server.requestCount)
        }
    }
}
