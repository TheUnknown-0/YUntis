package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.PeriodExam
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object UntisTimetableApi {

    suspend fun getTimetable(
        auth: AuthData,
        startDate: Int,
        endDate: Int,
        target: TimetableTarget? = null,
    ): List<TimetableEntry> = withContext(Dispatchers.IO) {
        val session = auth.session

        val elementId: Int
        val elementType: Int

        if (target != null) {
            elementId = target.id
            elementType = target.type
        } else if (session.personType == 5 && session.personId != 0) {
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

        val url = UntisRpc.buildUrl(auth.server, auth.loginName).toString()

        val entries = UntisRpc.json.decodeFromJsonElement<List<TimetableEntry>>(
            UntisRpc.executeRpc(url, "getTimetable", params, session.sessionId),
        ).filter { it.date != 0 }

        UntisRpc.log("getTimetable -> ${entries.size} lessons")

        entries
    }

    suspend fun getTimetableIntern(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
        target: TimetableTarget? = null,
    ): List<TimetableEntry> = withContext(Dispatchers.IO) {
        val (elementId, elementType) = target?.let { it.id to internalTypeName(it.type) }
            ?: UntisRpc.internalElement(auth.session)

        val result = UntisRpc.internalRpc(auth, password, "getTimetable2017") { credentials ->
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

        parseInternTimetable(result).also { UntisRpc.log("getTimetable2017 -> ${it.size} periods") }
    }

    suspend fun fetchTimetableEntries(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
        target: TimetableTarget? = null,
    ): List<TimetableEntry>? = withContext(Dispatchers.IO) {
        val (resourceType, resourceId) = target?.let { internalTypeName(it.type) to it.id } ?: restResource(auth.session)
        val timetableType = if (target == null) "MY_TIMETABLE" else "STANDARD"

        val url = UntisRpc.webuntisUrl(auth.server, "api/rest/view/v1/timetable/entries") +
            "?start=${from.format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
            "&end=${to.format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
            "&resourceType=$resourceType&resources=$resourceId&timetableType=$timetableType"

        val body = UntisRpc.authenticatedGet(auth, password, url) ?: return@withContext null

        val parsed = runCatching { UntisRpc.json.parseToJsonElement(body) }.getOrNull()

        // the school may release days later on this API ("NOT_ALLOWED") while another API has them
        val refused = refusedDaysV1(parsed)
        if (refused.isNotEmpty()) {
            UntisRpc.log("timetable entries refused for $refused -> trying another API")
            return@withContext null
        }

        runCatching {
            parseTimetableEntriesV1(requireNotNull(parsed))
        }.onFailure {
            UntisRpc.warn("timetable entries returned an unparseable response")
        }.getOrNull()
    }

    /**
     * Week of another class or student through the endpoint the WebUntis web page uses.
     * Works for accounts that may view class timetables in the browser even when the
     * app APIs refuse them. Returns null when the server refuses the request.
     */
    suspend fun fetchWebWeek(
        auth: AuthData,
        monday: LocalDate,
        target: TimetableTarget,
    ): List<TimetableEntry>? = withContext(Dispatchers.IO) {
        val url = UntisRpc.webuntisUrl(auth.server, "api/public/timetable/weekly/data") +
            "?elementType=${target.type}&elementId=${target.id}" +
            "&date=${monday.format(DateTimeFormatter.ISO_LOCAL_DATE)}&formatId=1"

        val body = UntisRpc.webGet(auth, url) ?: return@withContext null

        runCatching { parseWebWeek(UntisRpc.json.parseToJsonElement(body), target.id) }
            .onFailure { UntisRpc.warn("web week returned an unparseable response") }
            .getOrNull()
            ?.also { UntisRpc.log("web week -> ${it.size} periods") }
    }

    /** Parses `data.result.data` of the web endpoint. Null if the answer holds no timetable. */
    internal fun parseWebWeek(root: JsonElement, elementId: Int): List<TimetableEntry>? {
        val data = runCatching {
            root.jsonObject["data"]?.jsonObject?.get("result")?.jsonObject?.get("data")?.jsonObject
        }.getOrNull() ?: return null

        val names = HashMap<Pair<Int, Int>, JsonObject>()
        (data["elements"] as? JsonArray).orEmpty().forEach { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val type = obj["type"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
            names[type to id] = obj
        }

        val periodsByElement = runCatching { data["elementPeriods"]?.jsonObject }.getOrNull()
            ?: return emptyList()
        val periods = (periodsByElement[elementId.toString()] as? JsonArray)
            ?: periodsByElement.values.firstOrNull() as? JsonArray
            ?: return emptyList()

        return periods.mapNotNull { period ->
            runCatching { parseWebPeriod(period.jsonObject, names) }.getOrNull()
        }.filter { it.date != 0 }
    }

    private fun parseWebPeriod(period: JsonObject, names: Map<Pair<Int, Int>, JsonObject>): TimetableEntry {
        fun text(key: String) = (period[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        fun nameOf(type: Int, id: Int, key: String) =
            (names[type to id]?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

        val elements = (period["elements"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0
            val orgId = obj["orgId"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 && it != id }

            type to TimetableElement(
                id = id,
                name = nameOf(type, id, "name").orEmpty(),
                longName = nameOf(type, id, "longName"),
                orgId = orgId,
                orgName = orgId?.let { nameOf(type, it, "name") },
            )
        }

        val state = text("cellState").orEmpty().uppercase()
        val flags = runCatching { period["is"]?.jsonObject }.getOrNull()
        fun flag(key: String) = (flags?.get(key) as? JsonPrimitive)?.contentOrNull == "true"

        val cancelled = state == "CANCEL" || flag("cancelled")
        val irregular = !cancelled && (
            state in setOf("SUBSTITUTION", "ROOMSUBSTITUTION", "ADDITIONAL", "SHIFT") ||
                flag("substitution") || flag("roomSubstitution") || flag("additional") || flag("shift")
            )

        val id = period["id"]?.jsonPrimitive?.intOrNull ?: 0
        val periodText = text("periodText")
        val lessonText = text("lessonText")
        val substText = text("substText")
        val info = text("periodInfo")

        return TimetableEntry(
            id = id,
            date = period["date"]?.jsonPrimitive?.intOrNull ?: 0,
            startTime = period["startTime"]?.jsonPrimitive?.intOrNull ?: 0,
            endTime = period["endTime"]?.jsonPrimitive?.intOrNull ?: 0,
            klasse = elements.filter { it.first == 1 }.map { it.second },
            teacher = elements.filter { it.first == 2 }.map { it.second },
            subject = elements.filter { it.first == 3 }.map { it.second },
            room = elements.filter { it.first == 4 }.map { it.second },
            code = when {
                cancelled -> "cancelled"
                irregular -> "irregular"
                else -> null
            },
            lstext = substText ?: lessonText,
            info = info ?: periodText,
            substText = substText,
            lessonText = lessonText,
            exam = if (state == "EXAM" || flag("exam")) {
                PeriodExam(id = id, name = periodText, text = info)
            } else {
                null
            },
        )
    }

    suspend fun fetchTimegridFromInternalRpc(auth: AuthData): List<TimegridDay> = withContext(Dispatchers.IO) {
        val url = UntisRpc.buildInternalRpcUrl(auth.server, auth.loginName).toString()

        UntisRpc.json.decodeFromJsonElement<List<TimegridDay>>(
            UntisRpc.executeRpc(url, "getTimegrid", buildJsonObject {}, auth.session.sessionId),
        ).also { UntisRpc.log("timegrid via internal getTimegrid -> ${it.size} days") }
    }

    suspend fun fetchTimegridRpc(auth: AuthData): List<TimegridDay> = withContext(Dispatchers.IO) {
        val url = UntisRpc.buildUrl(auth.server, auth.loginName).toString()

        val result = UntisRpc.executeRpc(url, "getTimegrid", buildJsonObject {}, auth.session.sessionId)

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

        UntisRpc.json.decodeFromJsonElement<List<TimegridDay>>(days)
            .also { UntisRpc.log("timegrid via getTimegrid -> ${it.size} days") }
    }

    private fun restResource(session: AuthResult): Pair<String, Int> = when {
        session.personType == 5 && session.personId != 0 -> "STUDENT" to session.personId
        session.personType == 2 && session.personId != 0 -> "TEACHER" to session.personId
        session.klasseId != 0 -> "CLASS" to session.klasseId
        else -> "STUDENT" to session.personId
    }

    /** Dates whose day the server refused to show ("NOT_ALLOWED" and similar). */
    internal fun refusedDaysV1(result: JsonElement?): List<String> =
        (runCatching { result?.jsonObject?.get("days") as? JsonArray }.getOrNull()).orEmpty().mapNotNull { day ->
            val obj = runCatching { day.jsonObject }.getOrNull() ?: return@mapNotNull null
            val status = (obj["status"] as? JsonPrimitive)?.contentOrNull.orEmpty().uppercase()

            if ("NOT_ALLOWED" in status || "FORBIDDEN" in status || "NO_RIGHT" in status) {
                (obj["date"] as? JsonPrimitive)?.contentOrNull ?: "?"
            } else {
                null
            }
        }

    private fun parseTimetableEntriesV1(result: JsonElement): List<TimetableEntry> {
        val days = requireNotNull(result.jsonObject["days"] as? JsonArray) { "Missing timetable days" }
        val entries = ArrayList<TimetableEntry>()

        for (day in days) {
            val dayObj = runCatching { day.jsonObject }.getOrNull() ?: continue
            val date = UntisRpc.parseIsoDate(dayObj["date"]?.jsonPrimitive?.contentOrNull.orEmpty())

            if (date == 0) continue

            for (key in listOf("gridEntries", "dayEntries")) {
                (dayObj[key] as? JsonArray).orEmpty().forEach { element ->
                    runCatching { parseV1Entry(element.jsonObject, date) }
                        .getOrNull()
                        ?.let(entries::add)
                }
            }
        }

        return entries
    }

    private fun parseV1Entry(obj: JsonObject, date: Int): TimetableEntry {
        val duration = runCatching { obj["duration"]?.jsonObject }.getOrNull()
        val start = duration?.get("start")?.jsonPrimitive?.contentOrNull.orEmpty()
        val end = duration?.get("end")?.jsonPrimitive?.contentOrNull.orEmpty()

        val klasse = ArrayList<TimetableElement>()
        val teacher = ArrayList<TimetableElement>()
        val subject = ArrayList<TimetableElement>()
        val room = ArrayList<TimetableElement>()

        for (index in 1..7) {
            (obj["position$index"] as? JsonArray).orEmpty().forEach { position ->
                val pos = runCatching { position.jsonObject }.getOrNull() ?: return@forEach
                val current = runCatching { pos["current"]?.jsonObject }.getOrNull() ?: return@forEach
                val removed = runCatching { pos["removed"]?.jsonObject }.getOrNull()

                val element = TimetableElement(
                    id = current["id"]?.jsonPrimitive?.intOrNull ?: 0,
                    name = current["shortName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    longName = current["longName"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                    orgId = removed?.get("id")?.jsonPrimitive?.intOrNull,
                    orgName = removed?.get("shortName")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                )

                when (current["type"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                    "CLASS", "KLASSE" -> klasse += element
                    "TEACHER" -> teacher += element
                    "SUBJECT" -> subject += element
                    "ROOM" -> room += element
                }
            }
        }

        val ids = (obj["ids"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.intOrNull }
        val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val statusDetail = obj["statusDetail"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val layoutStart = (obj["layoutStartPosition"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 1000)
        val layoutWidth = (obj["layoutWidth"]?.jsonPrimitive?.intOrNull ?: 1000).coerceIn(1, 1000)

        val lessonInfo = obj["lessonInfo"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        val lessonText = obj["lessonText"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        val substitution = obj["substitutionText"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

        val cancelled = listOf(type, status, statusDetail).any { it.contains("CANCEL", ignoreCase = true) }
        val irregular = !cancelled && listOf(type, status, statusDetail).any { it.contains("IRREGULAR", ignoreCase = true) }

        val exam = if (type.equals("EXAM", ignoreCase = true)) {
            PeriodExam(
                id = ids.firstOrNull() ?: 0,
                examtype = null,
                name = name ?: lessonInfo,
                text = lessonInfo,
            )
        } else {
            null
        }

        return TimetableEntry(
            id = ids.firstOrNull() ?: 0,
            date = date,
            startTime = UntisRpc.parseIsoTime(start),
            endTime = UntisRpc.parseIsoTime(end),
            klasse = klasse,
            teacher = teacher,
            subject = subject,
            room = room,
            code = when {
                cancelled -> "cancelled"
                irregular -> "irregular"
                else -> null
            },
            lstext = substitution ?: lessonInfo,
            info = lessonInfo,
            substText = substitution,
            lessonText = lessonText,
            exam = exam,
            layoutStartPosition = layoutStart,
            layoutWidth = layoutWidth,
            backColor = colorField(obj, "backColor", "color", "backgroundColor"),
            foreColor = colorField(obj, "foreColor", "textColor", "foregroundColor"),
        )
    }

    private fun parseInternTimetable(result: JsonElement): List<TimetableEntry> {
        val periods = runCatching {
            result.jsonObject["timetable"]?.jsonObject?.get("periods")?.jsonArray
        }.getOrNull() ?: throw UntisException.Api(UntisClient.RPC_INVALID, "Missing timetable periods")

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

        val exam = runCatching { period["exam"]?.jsonObject }.getOrNull()?.let { obj ->
            PeriodExam(
                id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0,
                examtype = obj["examtype"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                text = obj["text"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
            )
        }

        return TimetableEntry(
            id = period["id"]?.jsonPrimitive?.intOrNull ?: 0,
            date = UntisRpc.parseIsoDate(start),
            startTime = UntisRpc.parseIsoTime(start),
            endTime = UntisRpc.parseIsoTime(end),
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
            exam = exam,
            backColor = colorField(period, "backColor"),
            foreColor = colorField(period, "foreColor"),
        )
    }

    private fun colorField(obj: JsonObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }

    private fun internalTypeName(type: Int): String = when (type) {
        TimetableTarget.CLASS -> "CLASS"
        2 -> "TEACHER"
        3 -> "SUBJECT"
        4 -> "ROOM"
        else -> "STUDENT"
    }

    private fun internElementTypeId(name: String?): Int? = when (name?.uppercase()) {
        "CLASS", "KLASSE" -> 1
        "TEACHER" -> 2
        "SUBJECT" -> 3
        "ROOM" -> 4
        "STUDENT" -> 5
        else -> null
    }
}
