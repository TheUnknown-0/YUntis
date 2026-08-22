package sh.lsx.untisx.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.JsonRpcRequest
import sh.lsx.untisx.data.model.JsonRpcResponse
import sh.lsx.untisx.data.model.School
import sh.lsx.untisx.data.model.SchoolSearchResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed class UntisException(message: String) : Exception(message) {
    data object Network : UntisException("No connection to the Untis server.")
    data object BadCredentials : UntisException("Wrong username or password.")
    data object TooManyResults : UntisException("Too many schools match your search.")
    data class Api(val code: Int, override val message: String) : UntisException(message)
}

object UntisClient {

    private const val SCHOOL_SEARCH_URL = "https://mobile.webuntis.com/ms/schoolquery2"
    private const val CLIENT_NAME = "UntisX"
    private const val TAG = "UntisClient"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val nextId = AtomicInteger(1)

    suspend fun searchSchools(query: String): List<School> = withContext(Dispatchers.IO) {
        val params = buildJsonArray {
            add(
                buildJsonObject {
                    put("schoolid", 0)
                    put("search", query.trim())
                },
            )
        }

        try {
            val result = executeRpc(SCHOOL_SEARCH_URL, "searchSchool", params)
            val schools = json.decodeFromJsonElement<SchoolSearchResult>(result).schools
            Log.d(TAG, "search '$query' -> ${schools.size} schools")
            schools.take(20)
        } catch (e: UntisException.Api) {
            Log.d(TAG, "search '$query' -> Api error ${e.code}: ${e.message}")
            if (e.code == -6003) throw UntisException.TooManyResults
            emptyList()
        }
    }

    suspend fun authenticate(server: String, loginName: String, user: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val params = buildJsonObject {
                put("user", user)
                put("password", password)
                put("client", CLIENT_NAME)
            }

            val url = buildUrl(server, loginName)
            val result = executeRpc(url.toString(), "authenticate", params)
            json.decodeFromJsonElement<AuthResult>(result)
        }

    private fun buildUrl(server: String, loginName: String): HttpUrl {
        val host = server.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        return "https://$host/WebUntis/jsonrpc.do".toHttpUrl()
            .newBuilder()
            .addQueryParameter("school", loginName.trim())
            .build()
    }

    private fun executeRpc(url: String, method: String, params: JsonElement): JsonElement {
        val body = json.encodeToString(
            JsonRpcRequest(id = nextId.getAndIncrement(), method = method, params = params),
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder().url(url).post(body).build()

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw UntisException.Network

                val text = response.body?.string() ?: throw UntisException.Network
                val rpc = json.decodeFromString<JsonRpcResponse>(text)

                rpc.error?.let { err ->
                    throw when (err.code) {
                        -8500 -> UntisException.BadCredentials
                        else -> UntisException.Api(err.code, err.message)
                    }
                }

                rpc.result ?: throw UntisException.Api(-1, "Empty result.")
            }
        } catch (e: IOException) {
            throw UntisException.Network
        }
    }
}
