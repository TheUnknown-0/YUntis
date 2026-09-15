package sh.lsx.untisx.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.JsonRpcRequest
import sh.lsx.untisx.data.model.JsonRpcResponse
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.School
import sh.lsx.untisx.data.model.SchoolSearchResult
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.TimegridUnit
import sh.lsx.untisx.data.security.generateTotp
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed class UntisException(message: String) : Exception(message) {
    data object Network : UntisException("No connection to the Untis server.")
    data object BadCredentials : UntisException("Wrong username or password.")
    data object TooManyResults : UntisException("Too many schools match your search.")
    data object SessionExpired : UntisException("Session expired.")
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

            Log.d(TAG, "school search -> ${schools.size} schools")
            schools.take(20)
        } catch (e: UntisException.Api) {
            Log.d(TAG, "school search API error ${e.code}")
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

    suspend fun logout(auth: AuthData) = withContext(Dispatchers.IO) {
        runCatching { rpcCall(auth, "logout") }
            .onFailure { Log.d(TAG, "server logout failed") }
        Unit
    }

    suspend fun getTimetable(
        auth: AuthData,
        startDate: Int,
        endDate: Int,
    ): List<TimetableEntry> = withContext(Dispatchers.IO) {
        val session = auth.session

        val elementId: Int
        val elementType: Int

        if (session.personType == 5 && session.personId != 0) {
            elementId = session.personId
            elementType = 5
        } else if (session.klasseId != 0) {
            elementId = session.klasseId
            elementType = 1
        } else {
            elementId = session.personId
            elementType = 5
        }

        val params = buildJsonObject {
            put("id", elementId)
            put("type", elementType)
            put("startDate", startDate)
            put("endDate", endDate)
        }

        val url = buildUrl(auth.server, auth.loginName).toString()

        json.decodeFromJsonElement<List<TimetableEntry>>(
            executeRpc(url, "getTimetable", params, session.sessionId),
        ).also { Log.d(TAG, "getTimetable $startDate-$endDate -> ${it.size} lessons") }
    }

    suspend fun getSubjects(auth: AuthData): List<MasterDataItem> = masterData(auth, "getSubjects")

    suspend fun getTeachers(auth: AuthData): List<MasterDataItem> = masterData(auth, "getTeachers")

    suspend fun getRooms(auth: AuthData): List<MasterDataItem> = masterData(auth, "getRooms")

    suspend fun getHolidays(auth: AuthData): List<Holiday> = withContext(Dispatchers.IO) {
        json.decodeFromJsonElement<List<Holiday>>(
            rpcCall(auth, "getHolidays"),
        ).also { Log.d(TAG, "getHolidays -> ${it.size} items") }
    }

    private suspend fun masterData(auth: AuthData, method: String): List<MasterDataItem> =
        withContext(Dispatchers.IO) {
            json.decodeFromJsonElement<List<MasterDataItem>>(
                rpcCall(auth, method),
            ).also { Log.d(TAG, "$method -> ${it.size} items") }
        }

    private fun rpcCall(
        auth: AuthData,
        method: String,
        params: JsonElement = buildJsonObject {},
    ): JsonElement = executeRpc(
        buildUrl(auth.server, auth.loginName).toString(),
        method,
        params,
        auth.session.sessionId,
    )

    private class InternTotp(
        val url: String,
        val user: String,
        val otp: Long,
        val clientTime: Long,
    ) {
        fun credentials(): JsonObject = buildJsonObject {
            put("user", user)
            put("otp", otp)
            put("clientTime", clientTime)
        }
    }

    private fun internTotp(auth: AuthData, password: String): InternTotp {
        val url = buildInternalRpcUrl(auth.server, auth.loginName).toString()

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
            ?: throw UntisException.Api(-1, "No app shared secret.")

        val now = System.currentTimeMillis()

        return InternTotp(
            url = url,
            user = auth.user,
            otp = generateTotp(secret, now),
            clientTime = now,
        )
    }

    private fun userData(auth: AuthData, password: String): JsonElement {
        val totp = internTotp(auth, password)

        return executeRpc(
            totp.url,
            "getUserData2017",
            buildJsonArray {
                add(buildJsonObject {
                    put("elementId", 0)
                    put("deviceOs", "A")
                    put("deviceOsVersion", "")
                    put("auth", totp.credentials())
                })
            },
        )
    }

    suspend fun fetchTimegridViaUserData(auth: AuthData, password: String): List<TimegridDay> = withContext(Dispatchers.IO) {
        parseTimegridResponse(userData(auth, password))
            .also { Log.d(TAG, "timegrid via getUserData2017 -> ${it.size} days") }
    }

    suspend fun fetchTeachersViaUserData(auth: AuthData, password: String): List<MasterDataItem> = withContext(Dispatchers.IO) {
        val array = runCatching {
            userData(auth, password).jsonObject["masterData"]?.jsonObject?.get("teachers")?.jsonArray
        }.getOrNull() ?: return@withContext emptyList()

        parseUserDataTeachers(array).also { Log.d(TAG, "teachers via getUserData2017 -> ${it.size}") }
    }

    private fun parseUserDataTeachers(array: JsonArray): List<MasterDataItem> =
        array.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (id <= 0) return@mapNotNull null

            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()

            MasterDataItem(
                id = id,
                name = name,
                longName = obj["lastName"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: name,
                foreName = obj["firstName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

    private fun parseTimegridResponse(result: JsonElement): List<TimegridDay> {
        val daysElement = runCatching {
            when {
                result.jsonObject.containsKey("masterData") ->
                    result.jsonObject["masterData"]?.jsonObject?.get("timeGrid")?.jsonObject?.get("days")

                result.jsonObject.containsKey("timeGrid") ->
                    result.jsonObject["timeGrid"]?.jsonObject?.get("days")

                result.jsonObject.containsKey("days") -> result.jsonObject["days"]

                else -> null
            }?.jsonArray
        }.getOrNull() ?: return emptyList()

        return daysElement.mapNotNull { day ->
            val dayObj = runCatching { day.jsonObject }.getOrNull() ?: return@mapNotNull null

            val units = dayObj["units"]?.jsonArray?.mapNotNull { unit ->
                val unitObj = runCatching { unit.jsonObject }.getOrNull() ?: return@mapNotNull null

                TimegridUnit(
                    label = unitObj["label"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                    startTime = parseUntisTime(unitObj["startTime"]) ?: return@mapNotNull null,
                    endTime = parseUntisTime(unitObj["endTime"]) ?: return@mapNotNull null,
                    canLesson = unitObj["canLesson"]?.jsonPrimitive?.booleanOrNull,
                )
            }.orEmpty()

            TimegridDay(day = dayObj["day"]?.jsonPrimitive?.intOrNull ?: 0, timeUnits = units)
        }
    }

    private fun parseUntisTime(element: JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null

        primitive.intOrNull?.let { return it }

        val digits = primitive.contentOrNull
            ?.removePrefix("T")
            ?.replace(":", "")
            ?: return null

        return digits.toIntOrNull()
    }

    suspend fun getTimetableIntern(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry> = withContext(Dispatchers.IO) {
        val totp = internTotp(auth, password)
        val session = auth.session

        val (elementId, elementType) = when {
            session.personType == 5 && session.personId != 0 -> session.personId to "STUDENT"
            session.klasseId != 0 -> session.klasseId to "CLASS"
            else -> session.personId to "STUDENT"
        }

        val result = executeRpc(
            totp.url,
            "getTimetable2017",
            buildJsonArray {
                add(buildJsonObject {
                    put("id", elementId)
                    put("type", elementType)
                    put("startDate", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("endDate", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("masterDataTimestamp", 0)
                    put("timetableTimestamp", 0)
                    put("timetableTimestamps", buildJsonArray {})
                    put("auth", totp.credentials())
                })
            },
        )

        parseInternTimetable(result).also { Log.d(TAG, "getTimetable2017 $from-$to -> ${it.size} periods") }
    }

    private fun parseInternTimetable(result: JsonElement): List<TimetableEntry> {
        val periods = runCatching {
            result.jsonObject["timetable"]?.jsonObject?.get("periods")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return periods.mapNotNull { period ->
            runCatching { parseInternPeriod(period.jsonObject) }.getOrNull()
        }
    }

    private fun parseInternPeriod(period: JsonObject): TimetableEntry {
        val start = period["startDateTime"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val end = period["endDateTime"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val elements = period["elements"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val typeId = internElementTypeId(obj["type"]?.jsonPrimitive?.contentOrNull)
                ?: return@mapNotNull null

            typeId to TimetableElement(
                id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0,
                orgId = obj["orgId"]?.jsonPrimitive?.intOrNull,
            )
        }

        val states = period["is"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

        val text = runCatching { period["text"]?.jsonObject }.getOrNull()
        val lstext = text?.get("substitution")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: text?.get("info")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)

        return TimetableEntry(
            id = period["id"]?.jsonPrimitive?.intOrNull ?: 0,
            date = parseIsoDate(start),
            startTime = parseIsoTime(start),
            endTime = parseIsoTime(end),
            klasse = elements.filter { it.first == 1 }.map { it.second },
            teacher = elements.filter { it.first == 2 }.map { it.second },
            subject = elements.filter { it.first == 3 }.map { it.second },
            room = elements.filter { it.first == 4 }.map { it.second },
            code = when {
                states.any { it.equals("CANCELLED", ignoreCase = true) } -> "cancelled"
                states.any { it.equals("IRREGULAR", ignoreCase = true) } -> "irregular"
                else -> null
            },
            lstext = lstext,
        )
    }

    private fun internElementTypeId(name: String?): Int? = when (name?.uppercase()) {
        "CLASS", "KLASSE" -> 1
        "TEACHER" -> 2
        "SUBJECT" -> 3
        "ROOM" -> 4
        "STUDENT" -> 5
        else -> null
    }

    private fun parseIsoDate(iso: String): Int =
        iso.take(10).replace("-", "").toIntOrNull() ?: 0

    private fun parseIsoTime(iso: String): Int =
        runCatching { iso.drop(11).take(5).replace(":", "").toInt() }.getOrDefault(0)

    suspend fun fetchTimegridFromInternalRpc(auth: AuthData): List<TimegridDay> = withContext(Dispatchers.IO) {
        val url = buildInternalRpcUrl(auth.server, auth.loginName).toString()

        json.decodeFromJsonElement<List<TimegridDay>>(
            executeRpc(url, "getTimegrid", buildJsonObject {}, auth.session.sessionId),
        ).also { Log.d(TAG, "timegrid via internal getTimegrid -> ${it.size} days") }
    }

    suspend fun fetchTimegridRpc(auth: AuthData): List<TimegridDay> = withContext(Dispatchers.IO) {
        val url = buildUrl(auth.server, auth.loginName).toString()

        val result = executeRpc(url, "getTimegrid", buildJsonObject {}, auth.session.sessionId)

        val days: JsonArray = when {
            result is JsonArray -> result

            result.jsonObject.containsKey("timeGrid") -> when (val grid = result.jsonObject["timeGrid"]) {
                is JsonArray -> grid
                else -> grid?.jsonObject?.get("days")?.jsonArray ?: JsonArray(emptyList())
            }

            result.jsonObject.containsKey("days") ->
                result.jsonObject["days"]?.jsonArray ?: JsonArray(emptyList())

            else -> JsonArray(emptyList())
        }

        json.decodeFromJsonElement<List<TimegridDay>>(days)
            .also { Log.d(TAG, "timegrid via getTimegrid -> ${it.size} days") }
    }

    private fun buildUrl(server: String, loginName: String): HttpUrl =
        buildUrl(server, loginName, "jsonrpc.do")

    private fun buildInternalRpcUrl(server: String, loginName: String): HttpUrl =
        buildUrl(server, loginName, "jsonrpc_intern.do")

    private fun buildUrl(server: String, loginName: String, rpcPath: String): HttpUrl {
        val trimmed = server.trim()
        val raw = buildString {
            if (!trimmed.contains("://")) append("https://")
            append(trimmed.removePrefix("https://").removePrefix("http://").trimEnd('/'))
        }

        val base = raw.toHttpUrlOrNull() ?: throw UntisException.Api(-1, "Invalid server URL: $trimmed")

        if (!base.isHttps) {
            throw UntisException.Api(-1, "Only HTTPS servers are supported: $trimmed")
        }

        return base.newBuilder()
            .addPathSegments("WebUntis/$rpcPath")
            .addQueryParameter("school", loginName.trim())
            .build()
    }

    private fun executeRpc(
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
                val rpc = response.body?.string()?.let { runCatching { json.decodeFromString<JsonRpcResponse>(it) }.getOrNull() }

                rpc?.error?.let { err ->
                    Log.d(TAG, "rpc '$method' error ${err.code}")

                    throw when (err.code) {
                        -8500 -> UntisException.BadCredentials
                        -8520 -> UntisException.SessionExpired
                        else -> UntisException.Api(err.code, err.message)
                    }
                }

                if (!response.isSuccessful) {
                    Log.d(TAG, "rpc '$method' http ${response.code}")
                    throw UntisException.Network
                }

                if (rpc == null) {
                    Log.d(TAG, "rpc '$method' returned an unparseable response")
                    throw UntisException.Network
                }

                rpc.result ?: throw UntisException.Api(-1, "Empty result.")
            }
        } catch (e: IOException) {
            Log.d(TAG, "rpc '$method' network failure")
            throw UntisException.Network
        }
    }
}
