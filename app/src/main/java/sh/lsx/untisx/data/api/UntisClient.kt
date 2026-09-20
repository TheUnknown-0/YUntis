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
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.JsonRpcRequest
import sh.lsx.untisx.data.model.JsonRpcResponse
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.School
import sh.lsx.untisx.data.model.SchoolSearchResult
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.TimegridUnit
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.security.generateTotp
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
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

    private const val SECRET_TTL_MS = 6 * 60 * 60 * 1000L
    private const val JWT_TTL_MS = 10 * 60 * 1000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val nextId = AtomicInteger(1)

    private val sharedSecrets = ConcurrentHashMap<String, CachedSecret>()
    private val jwts = ConcurrentHashMap<String, CachedJwt>()

    private data class CachedSecret(val secret: String, val at: Long)
    private data class CachedJwt(val jwt: String, val at: Long)

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    fun clearSessionCache(accountId: String) {
        sharedSecrets.remove(accountId)
        jwts.remove(accountId)
    }

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

            val schools = runCatching {
                json.decodeFromJsonElement<SchoolSearchResult>(result).schools
            }.getOrElse {
                log("school search returned an unparseable response")
                return@withContext emptyList()
            }

            log("school search -> ${schools.size} schools")
            schools.take(20)
        } catch (e: UntisException.Api) {
            log("school search API error ${e.code}")
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
            .onFailure { log("server logout failed") }
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

        val entries = json.decodeFromJsonElement<List<TimetableEntry>>(
            executeRpc(url, "getTimetable", params, session.sessionId),
        ).filter { it.date != 0 }

        log("getTimetable -> ${entries.size} lessons")

        entries
    }

    suspend fun getSubjects(auth: AuthData): List<MasterDataItem> = masterData(auth, "getSubjects")

    suspend fun getTeachers(auth: AuthData): List<MasterDataItem> = masterData(auth, "getTeachers")

    suspend fun getRooms(auth: AuthData): List<MasterDataItem> = masterData(auth, "getRooms")

    suspend fun getHolidays(auth: AuthData): List<Holiday> = withContext(Dispatchers.IO) {
        json.decodeFromJsonElement<List<Holiday>>(
            rpcCall(auth, "getHolidays"),
        ).also { log("getHolidays -> ${it.size} items") }
    }

    private suspend fun masterData(auth: AuthData, method: String): List<MasterDataItem> =
        withContext(Dispatchers.IO) {
            json.decodeFromJsonElement<List<MasterDataItem>>(
                rpcCall(auth, method),
            ).also { log("$method -> ${it.size} items") }
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

    private fun internalRpc(
        auth: AuthData,
        password: String,
        method: String,
        params: (credentials: JsonObject) -> JsonArray,
    ): JsonElement {
        val url = buildInternalRpcUrl(auth.server, auth.loginName).toString()
        val key = AccountPrefs.accountId(auth)

        fun call(secret: String): JsonElement {
            val now = System.currentTimeMillis()

            val credentials = buildJsonObject {
                put("user", auth.user)
                put("otp", generateTotp(secret, now))
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
            ?: throw UntisException.Api(-1, "No app shared secret.")

        sharedSecrets[key] = CachedSecret(secret, System.currentTimeMillis())

        return secret
    }

    private fun userData(auth: AuthData, password: String): JsonElement =
        internalRpc(auth, password, "getUserData2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("elementId", 0)
                    put("deviceOs", "A")
                    put("deviceOsVersion", "")
                    put("auth", credentials)
                })
            }
        }

    data class MasterData(
        val subjects: List<MasterDataItem>,
        val teachers: List<MasterDataItem>,
        val rooms: List<MasterDataItem>,
        val holidays: List<Holiday>,
        val timegrid: List<TimegridDay>,
    )

    suspend fun fetchMasterData(auth: AuthData, password: String): MasterData = withContext(Dispatchers.IO) {
        val result = userData(auth, password)
        val master = result.jsonObject["masterData"]?.jsonObject

        MasterData(
            subjects = parseMasterItems(master?.get("subjects")),
            teachers = parseUserDataTeachers(master?.get("teachers") as? JsonArray ?: JsonArray(emptyList())),
            rooms = parseMasterItems(master?.get("rooms")),
            holidays = parseUserDataHolidays(master?.get("holidays")),
            timegrid = parseTimegridResponse(result),
        ).also {
            log("masterData -> subjects=${it.subjects.size} teachers=${it.teachers.size} " +
                    "rooms=${it.rooms.size} holidays=${it.holidays.size} timegrid=${it.timegrid.size}",)
        }
    }

    private fun parseMasterItems(element: JsonElement?): List<MasterDataItem> {
        val array = element as? JsonArray ?: return emptyList()

        return array.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            if (id <= 0) return@mapNotNull null

            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()

            MasterDataItem(
                id = id,
                name = name,
                longName = obj["longName"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: name,
                foreName = obj["foreName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    private fun parseUserDataHolidays(element: JsonElement?): List<Holiday> {
        val array = element as? JsonArray ?: return emptyList()

        return array.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null

            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val start = parseIsoDate(obj["startDate"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val end = parseIsoDate(obj["endDate"]?.jsonPrimitive?.contentOrNull.orEmpty())

            if (start == 0 || end == 0) return@mapNotNull null

            Holiday(
                id = id,
                name = name,
                longName = obj["longName"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: name,
                startDate = start,
                endDate = end,
            )
        }
    }

    suspend fun fetchTimegridViaUserData(auth: AuthData, password: String): List<TimegridDay> = withContext(Dispatchers.IO) {
        parseTimegridResponse(userData(auth, password))
            .also { log("timegrid via getUserData2017 -> ${it.size} days") }
    }

    suspend fun fetchTeachersViaUserData(auth: AuthData, password: String): List<MasterDataItem> = withContext(Dispatchers.IO) {
        val array = runCatching {
            userData(auth, password).jsonObject["masterData"]?.jsonObject?.get("teachers")?.jsonArray
        }.getOrNull() ?: return@withContext emptyList()

        parseUserDataTeachers(array).also { log("teachers via getUserData2017 -> ${it.size}") }
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
        val session = auth.session

        val (elementId, elementType) = when {
            session.personType == 5 && session.personId != 0 -> session.personId to "STUDENT"
            session.klasseId != 0 -> session.klasseId to "CLASS"
            else -> session.personId to "STUDENT"
        }

        val result = internalRpc(auth, password, "getTimetable2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("id", elementId)
                    put("type", elementType)
                    put("startDate", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("endDate", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("masterDataTimestamp", 0)
                    put("timetableTimestamp", 0)
                    put("timetableTimestamps", buildJsonArray {})
                    put("auth", credentials)
                })
            }
        }

        parseInternTimetable(result).also { log("getTimetable2017 -> ${it.size} periods") }
    }

    data class PeriodNotes(
        val topic: String?,
        val notes: List<String>,
    )

    data class LessonDetail(
        val info: String?,
        val substitution: String?,
        val topic: String?,
        val notes: List<String>,
    )

    suspend fun fetchCurrentSchoolYear(auth: AuthData, password: String): SchoolYear? = withContext(Dispatchers.IO) {
        val jwt = mobileJwt(auth, password)

        if (jwt != null) {
            val host = auth.server.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
            val url = "https://$host/WebUntis/api/rest/view/v1/app/data"

            try {
                http.newCall(
                    Request.Builder().url(url)
                        .header("Authorization", "Bearer $jwt")
                        .header("Accept", "application/json")
                        .get()
                        .build(),
                ).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val parsed = runCatching { parseSchoolYear(body) }.getOrNull()

                        if (parsed != null) {
                            log("school year -> id=${parsed.id}")
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                log("school year via app data failed: ${e.javaClass.simpleName}")
            }
        }

        try {
            json.decodeFromJsonElement<SchoolYear>(rpcCall(auth, "getCurrentSchoolyear"))
                .takeIf { it.startDate > 0 }
                .also { log("school year via getCurrentSchoolyear -> ok=${it != null}") }
        } catch (e: Exception) {
            log("school year via json-rpc failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun parseSchoolYear(body: String): SchoolYear? {
        val year = runCatching {
            json.parseToJsonElement(body).jsonObject["currentSchoolYear"]?.jsonObject
        }.getOrNull() ?: return null

        val start = parseIsoDate(year["dateRange"]?.jsonObject?.get("start")?.jsonPrimitive?.contentOrNull.orEmpty())
        val end = parseIsoDate(year["dateRange"]?.jsonObject?.get("end")?.jsonPrimitive?.contentOrNull.orEmpty())

        if (start == 0 || end == 0) return null

        return SchoolYear(
            id = year["id"]?.jsonPrimitive?.intOrNull ?: 0,
            name = year["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            startDate = start,
            endDate = end,
        )
    }

    private fun mobileJwt(auth: AuthData, password: String): String? {
        val key = AccountPrefs.accountId(auth)
        val now = System.currentTimeMillis()

        jwts[key]?.let { if (now - it.at < JWT_TTL_MS) return it.jwt }

        return try {
            val host = auth.server.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
            val url = "https://$host/WebUntis/api/mobile/v2/${auth.loginName}/authentication"
            val payload = buildJsonObject {
                put("username", auth.user)
                put("password", password)
            }

            http.newCall(
                Request.Builder().url(url)
                    .post(json.encodeToString(payload).toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) return null

                val jwt = runCatching {
                    json.parseToJsonElement(response.body?.string().orEmpty())
                        .jsonObject["jwt"]?.jsonPrimitive?.contentOrNull
                }.getOrNull()

                if (jwt != null) {
                    jwts[key] = CachedJwt(jwt, now)
                }

                jwt
            }
        } catch (e: Exception) {
            log("mobile jwt failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private suspend fun fallbackLessonDetail(
        auth: AuthData,
        password: String,
        entryId: Int,
    ): LessonDetail? =
        fetchPeriodNotes(auth, password, entryId)
            ?.let { LessonDetail(info = null, substitution = null, topic = it.topic, notes = it.notes) }

    suspend fun fetchLessonDetails(
        auth: AuthData,
        password: String,
        entryId: Int,
        date: LocalDate,
        startTime: Int,
        endTime: Int,
    ): LessonDetail? = withContext(Dispatchers.IO) {
        val jwt = mobileJwt(auth, password) ?: return@withContext fallbackLessonDetail(auth, password, entryId)

        val session = auth.session

        val (elementId, elementType) = when {
            session.personType == 5 && session.personId != 0 -> session.personId to 5
            session.klasseId != 0 -> session.klasseId to 1
            else -> session.personId to 5
        }

        val start = date.atTime(startTime.toUntisLocalTime()).format(DATE_TIME_FORMAT)
        val end = date.atTime(endTime.toUntisLocalTime()).format(DATE_TIME_FORMAT)
        val host = auth.server.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

        val url = "https://$host/WebUntis/api/rest/view/v2/calendar-entry/detail" +
            "?elementId=$elementId&elementType=$elementType" +
            "&startDateTime=$start&endDateTime=$end" +
            "&initialSelectedPeriodId=$entryId&initialIsBlockSelected=false"

        val body = try {
            http.newCall(
                Request.Builder().url(url)
                    .header("Authorization", "Bearer $jwt")
                    .header("Accept", "application/json")
                    .get()
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    jwts.remove(AccountPrefs.accountId(auth))
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            log("lesson detail failed: ${e.javaClass.simpleName}")
            null
        }

        val detail = body?.let { parseLessonDetail(it, entryId) }

        detail?.also { log("lesson detail -> info=${it.info != null} topic=${it.topic != null} notes=${it.notes.size}") }
            ?: fallbackLessonDetail(auth, password, entryId)
    }

    private fun parseLessonDetail(body: String, entryId: Int): LessonDetail? {
        val entries = runCatching {
            json.parseToJsonElement(body).jsonObject["calendarEntries"]?.jsonArray.orEmpty()
        }.getOrNull() ?: return null

        if (entries.isEmpty()) return null

        val entry = entries
            .firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == entryId }
            ?.jsonObject
            ?: entries.first().jsonObject

        val notes = listOfNotNull(
            entry["notesAll"]?.jsonPrimitive?.contentOrNull,
            entry["notesStaff"]?.jsonPrimitive?.contentOrNull,
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return LessonDetail(
            info = entry["lessonInfo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            substitution = entry["substText"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            topic = entry["teachingContent"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            notes = notes,
        )
    }

    suspend fun fetchMessagesOfDay(
        auth: AuthData,
        password: String,
        date: LocalDate,
    ): List<DayMessage> = withContext(Dispatchers.IO) {
        val result = internalRpc(auth, password, "getMessagesOfDay2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("auth", credentials)
                })
            }
        }

        parseMessagesOfDay(result).also { log("getMessagesOfDay2017 -> ${it.size} messages") }
    }

    private fun parseMessagesOfDay(result: JsonElement): List<DayMessage> =
        runCatching {
            result.jsonObject["messages"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val body = obj["body"]?.jsonPrimitive?.contentOrNull?.let(::stripHtml).orEmpty()

                if (body.isBlank()) return@mapNotNull null

                DayMessage(
                    id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0,
                    subject = obj["subject"]?.jsonPrimitive?.contentOrNull?.let(::stripHtml).orEmpty(),
                    body = body,
                )
            }
        }.getOrDefault(emptyList())

    private fun stripHtml(text: String): String =
        text
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .trim()

    suspend fun fetchPeriodNotes(
        auth: AuthData,
        password: String,
        ttId: Int,
    ): PeriodNotes? = withContext(Dispatchers.IO) {
        val result = internalRpc(auth, password, "getPeriodData2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("ttIds", buildJsonArray { add(JsonPrimitive(ttId)) })
                    put("auth", credentials)
                })
            }
        }

        parsePeriodNotes(result, ttId).also { log("getPeriodData2017 -> topic=${it?.topic != null} notes=${it?.notes?.size}") }
    }

    private fun parsePeriodNotes(result: JsonElement, ttId: Int): PeriodNotes? {
        val data = runCatching {
            result.jsonObject["dataByTTId"]?.jsonObject?.get(ttId.toString())?.jsonObject
        }.getOrNull() ?: return null

        val topic = runCatching {
            data["topic"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        }.getOrNull()

        val notes = runCatching {
            data["classRegEvents"]?.jsonArray.orEmpty().mapNotNull { element ->
                runCatching { element.jsonObject }.getOrNull()
                    ?.get("text")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            }
        }.getOrDefault(emptyList())

        if (topic == null && notes.isEmpty()) return null

        return PeriodNotes(topic = topic, notes = notes)
    }

    private fun parseInternTimetable(result: JsonElement): List<TimetableEntry> {
        val periods = runCatching {
            result.jsonObject["timetable"]?.jsonObject?.get("periods")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return periods.mapNotNull { period ->
            runCatching { parseInternPeriod(period.jsonObject) }.getOrNull()
        }.filter { it.date != 0 }
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
        val substitution = text?.get("substitution")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        val info = text?.get("info")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        val lessonText = text?.get("lesson")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)

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
            lstext = substitution ?: info,
            info = info,
            substText = substitution,
            lessonText = lessonText,
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
        ).also { log("timegrid via internal getTimegrid -> ${it.size} days") }
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
            .also { log("timegrid via getTimegrid -> ${it.size} days") }
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
                    log("rpc '$method' error ${err.code}")

                    throw when (err.code) {
                        -8500 -> UntisException.BadCredentials
                        -8520 -> UntisException.SessionExpired
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

                rpc.result ?: throw UntisException.Api(-1, "Empty result.")
            }
        } catch (e: IOException) {
            log("rpc '$method' network failure")
            throw UntisException.Network
        }
    }
}
