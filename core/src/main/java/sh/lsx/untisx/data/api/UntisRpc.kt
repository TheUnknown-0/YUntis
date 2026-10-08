package sh.lsx.untisx.data.api

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.JsonRpcRequest
import sh.lsx.untisx.data.model.JsonRpcResponse
import sh.lsx.untisx.data.net.Http
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.security.generateTotp
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

internal object UntisRpc {

    private const val TAG = "UntisClient"
    private const val SECRET_TTL_MS = 6 * 60 * 60 * 1000L
    private const val JWT_TTL_MS = 10 * 60 * 1000L

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val http = Http.base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val nextId = AtomicInteger(1)

    private val sharedSecrets = ConcurrentHashMap<String, CachedSecret>()
    private val jwts = ConcurrentHashMap<String, CachedJwt>()

    private data class CachedSecret(val secret: String, val at: Long)
    private data class CachedJwt(val jwt: String, val at: Long)

    @Volatile
    var allowHttpForTests = false

    internal fun log(message: String) {
        Log.d(TAG, message)
    }

    internal fun warn(message: String) {
        Log.w(TAG, message)
    }

    internal fun clearSessionCache(accountId: String) {
        sharedSecrets.remove(accountId)
        jwts.remove(accountId)
    }

    internal fun rpcCall(
        auth: AuthData,
        method: String,
        params: JsonElement = buildJsonObject {},
    ): JsonElement = executeRpc(
        buildUrl(auth.server, auth.loginName).toString(),
        method,
        params,
        auth.session.sessionId,
    )

    internal fun internalRpc(
        auth: AuthData,
        password: String,
        method: String,
        params: (credentials: JsonObject) -> JsonArray,
    ): JsonElement {
        val url = buildInternalRpcUrl(auth.server, auth.loginName).toString()
        val key = AccountPrefs.accountId(auth)

        fun call(secret: String): JsonElement {
            val now = System.currentTimeMillis()
            val otp = generateTotp(secret, now)
                ?: throw UntisException.Api(UntisClient.RPC_INVALID, "Invalid app shared secret.")

            val credentials = buildJsonObject {
                put("user", auth.user)
                put("otp", otp)
                put("clientTime", now)
            }

            return executeRpc(url, method, params(credentials))
        }

        val cached = sharedSecrets[key]?.takeIf { System.currentTimeMillis() - it.at < SECRET_TTL_MS }

        if (cached == null) return call(fetchSharedSecret(auth, password, url, key))

        return try {
            call(cached.secret)
        } catch (e: UntisException.Network) {
            throw e
        } catch (e: UntisException.BadCredentials) {
            throw e
        } catch (e: UntisException.RateLimited) {
            throw e
        } catch (e: UntisException) {
            log("'$method' rejected the cached secret -> requesting a new one")

            sharedSecrets.remove(key)
            call(fetchSharedSecret(auth, password, url, key))
        }
    }

    private fun fetchSharedSecret(
        auth: AuthData,
        password: String,
        url: String,
        key: String,
    ): String {
        val secret = executeRpc(
            url,
            "getAppSharedSecret",
            buildJsonArray {
                add(buildJsonObject {
                    put("userName", auth.user)
                    put("password", password)
                })
            },
        ).jsonPrimitive.contentOrNull
            ?: throw UntisException.Api(UntisClient.RPC_INVALID, "No app shared secret.")

        sharedSecrets[key] = CachedSecret(secret, System.currentTimeMillis())

        return secret
    }

    internal fun authenticatedGet(auth: AuthData, password: String, url: String): String? {
        repeat(2) { attempt ->
            val jwt = mobileJwt(auth, password) ?: return null

            val response = try {
                http.newCall(
                    Request.Builder().url(url)
                        .header("Authorization", "Bearer $jwt")
                        .header("Accept", "application/json")
                        .get()
                        .build(),
                ).execute().use { result ->
                    if (result.code == 429) {
                        throw UntisException.RateLimited(result.header("Retry-After")?.toLongOrNull())
                    }

                    val body = if (result.isSuccessful) result.body?.string() else null

                    body to result.code
                }
            } catch (e: UntisException.RateLimited) {
                throw e
            } catch (e: Exception) {
                warn("bearer GET failed: ${e.javaClass.simpleName}")
                return null
            }

            val (body, code) = response

            if (body != null) return body

            warn("bearer GET $code")

            if (code == 401 || code == 403) {
                jwts.remove(AccountPrefs.accountId(auth))
            }

            if (attempt == 1 || (code != 401 && code != 403)) return null
        }

        return null
    }

    private fun mobileJwt(auth: AuthData, password: String): String? {
        val key = AccountPrefs.accountId(auth)
        val now = System.currentTimeMillis()

        jwts[key]?.let { if (now - it.at < JWT_TTL_MS) return it.jwt }

        return try {
            val url = serverBase(auth.server).newBuilder()
                .addPathSegment("WebUntis")
                .addPathSegments("api/mobile/v2")
                .addPathSegment(auth.loginName)
                .addPathSegment("authentication")
                .build()
                .toString()
            val payload = buildJsonObject {
                put("username", auth.user)
                put("password", password)
            }

            http.newCall(
                Request.Builder().url(url)
                    .post(json.encodeToString(payload).toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().use { response ->
                if (response.code == 429) {
                    throw UntisException.RateLimited(response.header("Retry-After")?.toLongOrNull())
                }

                if (!response.isSuccessful) {
                    warn("mobile jwt http ${response.code}")
                    return null
                }

                val jwt = runCatching {
                    json.parseToJsonElement(response.body?.string().orEmpty())
                        .jsonObject["jwt"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()

                if (jwt != null) {
                    jwts[key] = CachedJwt(jwt, now)
                }

                jwt
            }
        } catch (e: UntisException.RateLimited) {
            throw e
        } catch (e: Exception) {
            warn("mobile jwt failed: ${e.javaClass.simpleName}")
            null
        }
    }

    internal fun webuntisUrl(server: String, path: String): String =
        serverBase(server).newBuilder()
            .addPathSegment("WebUntis")
            .addPathSegments(path)
            .build()
            .toString()

    internal fun internalElement(session: AuthResult): Pair<Int, String> = when {
        session.personType == 5 && session.personId != 0 -> session.personId to "STUDENT"
        session.klasseId != 0 -> session.klasseId to "CLASS"
        else -> session.personId to "STUDENT"
    }

    internal fun parseIsoDate(iso: String): Int =
        iso.take(10).replace("-", "").toIntOrNull() ?: 0

    internal fun parseIsoTime(iso: String): Int =
        runCatching { iso.drop(11).take(5).replace(":", "").toInt() }.getOrDefault(0)

    internal fun buildUrl(server: String, loginName: String): HttpUrl =
        buildUrl(server, loginName, "jsonrpc.do")

    internal fun buildInternalRpcUrl(server: String, loginName: String): HttpUrl =
        buildUrl(server, loginName, "jsonrpc_intern.do")

    private fun buildUrl(server: String, loginName: String, rpcPath: String): HttpUrl =
        serverBase(server).newBuilder()
            .addPathSegments("WebUntis/$rpcPath")
            .addQueryParameter("school", loginName.trim())
            .build()

    private fun serverBase(server: String): HttpUrl {
        val trimmed = server.trim().trimEnd('/')

        if (trimmed.isBlank()) throw UntisException.Api(UntisClient.RPC_INVALID, "Empty server URL")

        val raw = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val base = raw.toHttpUrlOrNull() ?: throw UntisException.Api(UntisClient.RPC_INVALID, "Invalid server URL: $trimmed")

        if (!base.isHttps && !allowHttpForTests) {
            throw UntisException.Api(UntisClient.RPC_INVALID, "Only HTTPS servers are supported: $trimmed")
        }

        return base
    }

    internal fun executeRpc(
        url: String,
        method: String,
        params: JsonElement,
        sessionId: String? = null,
    ): JsonElement {
        val body = json.encodeToString(
            JsonRpcRequest(id = nextId.getAndIncrement(), method = method, params = params),
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder().url(url).post(body).apply {
            if (!sessionId.isNullOrBlank()) header("Cookie", "JSESSIONID=$sessionId")
        }.build()

        return try {
            http.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    log("rpc '$method' rate limited")
                    throw UntisException.RateLimited(response.header("Retry-After")?.toLongOrNull())
                }

                val rpc = response.body?.string()?.let { runCatching { json.decodeFromString<JsonRpcResponse>(it) }.getOrNull() }

                rpc?.error?.let { err ->
                    log("rpc '$method' error ${err.code}")

                    throw when (err.code) {
                        UntisClient.RPC_BAD_CREDENTIALS -> UntisException.BadCredentials
                        UntisClient.RPC_SESSION_EXPIRED -> UntisException.SessionExpired
                        else -> UntisException.Api(err.code, err.message)
                    }
                }

                if (!response.isSuccessful) {
                    log("rpc '$method' http ${response.code}")
                    throw UntisException.Network
                }

                if (rpc == null) {
                    log("rpc '$method' returned an unparseable response")
                    throw UntisException.Network
                }

                rpc.result ?: throw UntisException.Api(UntisClient.RPC_INVALID, "Empty result.")
            }
        } catch (e: SSLException) {
            log("rpc '$method' failed TLS verification")
            throw UntisException.Certificate
        } catch (e: IOException) {
            log("rpc '$method' network failure")
            throw UntisException.Network
        }
    }
}
