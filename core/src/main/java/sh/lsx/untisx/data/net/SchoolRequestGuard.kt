package sh.lsx.untisx.data.net

import android.content.Context
import android.content.SharedPreferences
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import sh.lsx.untisx.data.api.UntisException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

object SchoolRequestGuard : Interceptor {
    private val locks = ConcurrentHashMap<String, Any>()
    private val blockedUntil = ConcurrentHashMap<String, Long>()
    @Volatile private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("school_request_limits", Context.MODE_PRIVATE)
    }

    private fun key(url: HttpUrl) = "${url.scheme}://${url.host}:${url.port}"

    override fun intercept(chain: Interceptor.Chain): Response {
        val key = key(chain.request().url)

        return synchronized(locks.getOrPut(key) { Any() }) {
            val now = System.currentTimeMillis()
            val until = maxOf(blockedUntil[key] ?: 0L, prefs?.getLong(key, 0L) ?: 0L)
            if (until > now) throw UntisException.RateLimited((until - now + 999) / 1000)

            val response = chain.proceed(chain.request())
            if (response.code == 429) {
                val receivedAt = System.currentTimeMillis()
                val seconds = retryAfterSeconds(response.header("Retry-After"), receivedAt)
                val deadline = receivedAt + seconds * 1000
                blockedUntil[key] = deadline
                prefs?.edit()?.putLong(key, deadline)?.commit()
                response.close()
                throw UntisException.RateLimited(seconds)
            }

            response
        }
    }

    internal fun retryAfterSeconds(header: String?, now: Long): Long {
        val seconds = header?.trim()?.toLongOrNull() ?: runCatching {
            (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now + 999) / 1000
        }.getOrNull() ?: 5 * 60L
        return seconds.coerceIn(1, 365 * 24 * 60 * 60L)
    }
}

internal object SameOriginRedirects : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.call().request().url
        val target = chain.request().url

        if (original.scheme != target.scheme || original.host != target.host || original.port != target.port) {
            throw IOException("Cross-origin school redirect refused")
        }

        return chain.proceed(chain.request())
    }
}
