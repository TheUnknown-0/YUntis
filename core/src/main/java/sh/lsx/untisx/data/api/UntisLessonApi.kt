package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object UntisLessonApi {

    private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    suspend fun fetchLessonDetails(
        auth: AuthData,
        password: String,
        entryId: Int,
        date: LocalDate,
        startTime: Int,
        endTime: Int,
    ): UntisClient.LessonDetail? = withContext(Dispatchers.IO) {
        val session = auth.session

        val (elementId, elementType) = when {
            session.personType == 5 && session.personId != 0 -> session.personId to 5
            session.klasseId != 0 -> session.klasseId to 1
            else -> session.personId to 5
        }

        val start = date.atTime(startTime.toUntisLocalTime()).format(DATE_TIME_FORMAT)
        val end = date.atTime(endTime.toUntisLocalTime()).format(DATE_TIME_FORMAT)

        val url = UntisRpc.webuntisUrl(auth.server, "api/rest/view/v2/calendar-entry/detail") +
            "?elementId=$elementId&elementType=$elementType" +
            "&startDateTime=$start&endDateTime=$end" +
            "&initialSelectedPeriodId=$entryId&initialIsBlockSelected=false"

        val body = UntisRpc.authenticatedGet(auth, password, url)

        val detail = body?.let { parseLessonDetail(it, entryId) }

        detail?.also { UntisRpc.log("lesson detail -> info=${it.info != null} topic=${it.topic != null} notes=${it.notes.size}") }
            ?: fallbackLessonDetail(auth, password, entryId)
    }

    private suspend fun fallbackLessonDetail(
        auth: AuthData,
        password: String,
        entryId: Int,
    ): UntisClient.LessonDetail? =
        fetchPeriodNotes(auth, password, entryId)
            ?.let {
                UntisClient.LessonDetail(
                    info = null,
                    substitution = null,
                    topic = it.topic,
                    notes = it.notes,
                    homeworks = it.homeworks,
                    attendanceChecked = it.attendanceChecked,
                    absent = it.absent,
                )
            }

    private fun text(element: JsonElement?, vararg keys: String): String? {
        val obj = runCatching { element?.jsonObject }.getOrNull() ?: return null
        return keys.firstNotNullOfOrNull { key -> obj[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } }
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun array(element: JsonElement?, vararg keys: String) = runCatching {
        val obj = element?.jsonObject ?: return@runCatching emptyList()
        keys.firstNotNullOfOrNull { obj[it]?.let { value -> runCatching { value.jsonArray }.getOrNull() } }.orEmpty()
    }.getOrDefault(emptyList())

    /** Teachers or rooms with a status (REGULAR, SUBSTITUTED/ADDED, REMOVED): what replaced what. */
    internal fun swapsOf(entry: JsonElement, key: String, kind: String): List<UntisClient.Swap> {
        val items = array(entry, key).mapNotNull { item ->
            val name = text(item, "displayName", "longName", "shortName", "name") ?: return@mapNotNull null
            name to (text(item, "status")?.uppercase() ?: "REGULAR")
        }
        val removed = items.filter { it.second == "REMOVED" }.map { it.first }
        val added = items.filter { it.second == "SUBSTITUTED" || it.second == "ADDED" || it.second == "CHANGED" }.map { it.first }
        if (removed.isEmpty() && added.isEmpty()) return emptyList()
        return listOf(UntisClient.Swap(kind, added.joinToString(", "), removed.joinToString(", ")))
    }

    private fun parseLessonDetail(body: String, entryId: Int): UntisClient.LessonDetail? {
        val entries = runCatching {
            UntisRpc.json.parseToJsonElement(body).jsonObject["calendarEntries"]?.jsonArray.orEmpty()
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

        val homeworks = array(entry, "homeworks", "homeWorks").mapNotNull { text(it, "text", "remark") }
        val exam = entry["exam"]?.let { exam ->
            listOfNotNull(text(exam, "examType", "type"), text(exam, "name"), text(exam, "text"))
                .distinct().joinToString(" · ").takeIf { it.isNotBlank() }
        }

        return UntisClient.LessonDetail(
            info = entry["lessonInfo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            substitution = entry["substText"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            topic = entry["teachingContent"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            notes = notes,
            homeworks = homeworks,
            swaps = swapsOf(entry, "teachers", "teacher") + swapsOf(entry, "rooms", "room"),
            exam = exam,
        )
    }

    suspend fun fetchPeriodNotes(
        auth: AuthData,
        password: String,
        ttId: Int,
    ): UntisClient.PeriodNotes? = withContext(Dispatchers.IO) {
        val result = UntisRpc.internalRpc(auth, password, "getPeriodData2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("ttIds", buildJsonArray { add(JsonPrimitive(ttId)) })
                    put("auth", credentials)
                })
            }
        }

        parsePeriodNotes(result, ttId).also { UntisRpc.log("getPeriodData2017 -> topic=${it?.topic != null} notes=${it?.notes?.size}") }
    }

    private fun parsePeriodNotes(result: JsonElement, ttId: Int): UntisClient.PeriodNotes? {
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

        val homeworks = array(data, "homeWorks", "homeworks").mapNotNull { text(it, "text", "remark") }
        val checked = runCatching { data["absenceChecked"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() }.getOrNull()
        val absent = array(data, "absences").isNotEmpty()

        if (topic == null && notes.isEmpty() && homeworks.isEmpty() && checked == null && !absent) return null

        return UntisClient.PeriodNotes(topic = topic, notes = notes, homeworks = homeworks, attendanceChecked = checked, absent = absent)
    }
}
