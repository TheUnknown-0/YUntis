package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object UntisExamsApi {

    suspend fun fetchExams(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ExamInfo> = withContext(Dispatchers.IO) {
        val (elementId, elementType) = UntisRpc.internalElement(auth.session)

        val result = UntisRpc.internalRpc(auth, password, "getExams2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("id", elementId)
                    put("type", elementType)
                    put("startDate", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("endDate", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("auth", credentials)
                })
            }
        }

        parseExams(result).also { UntisRpc.log("getExams2017 -> ${it.size} exams") }
    }

    suspend fun fetchCurrentSchoolYear(auth: AuthData, password: String): SchoolYear? =
        withContext(Dispatchers.IO) {
            val body = UntisRpc.authenticatedGet(auth, password, UntisRpc.webuntisUrl(auth.server, "api/rest/view/v1/app/data"))

            if (body != null) {
                val parsed = runCatching { parseSchoolYear(body) }.getOrNull()

                if (parsed != null) {
                    UntisRpc.log("school year -> id=${parsed.id}")
                    return@withContext parsed
                }
            }

            try {
                UntisRpc.json.decodeFromJsonElement<SchoolYear>(UntisRpc.rpcCall(auth, "getCurrentSchoolyear"))
                    .takeIf { it.startDate > 0 }
                    .also { UntisRpc.log("school year via getCurrentSchoolyear -> ok=${it != null}") }
            } catch (e: Exception) {
                UntisRpc.log("school year via json-rpc failed: ${e.javaClass.simpleName}")
                null
            }
        }

    private fun parseExams(result: JsonElement): List<ExamInfo> {
        val exams = runCatching {
            when (result) {
                is JsonArray -> result
                else -> result.jsonObject["exams"]?.jsonArray.orEmpty()
            }
        }.getOrNull() ?: return emptyList()

        return exams.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null

            val start = obj["startDateTime"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val end = obj["endDateTime"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val date = UntisRpc.parseIsoDate(start)

            if (date == 0) return@mapNotNull null

            ExamInfo(
                id = obj["id"]?.jsonPrimitive?.intOrNull ?: 0,
                examType = obj["examType"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                date = date,
                startTime = UntisRpc.parseIsoTime(start),
                endTime = UntisRpc.parseIsoTime(end),
                subjectId = obj["subjectId"]?.jsonPrimitive?.intOrNull ?: 0,
                roomIds = intList(obj["roomIds"]),
                teacherIds = intList(obj["teacherIds"]),
                klasseIds = intList(obj["klasseIds"]),
            )
        }
    }

    private fun intList(element: JsonElement?): List<Int> =
        (element as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()

    private fun parseSchoolYear(body: String): SchoolYear? {
        val year = runCatching {
            UntisRpc.json.parseToJsonElement(body).jsonObject["currentSchoolYear"]?.jsonObject
        }.getOrNull() ?: return null

        val start = UntisRpc.parseIsoDate(year["dateRange"]?.jsonObject?.get("start")?.jsonPrimitive?.contentOrNull.orEmpty())
        val end = UntisRpc.parseIsoDate(year["dateRange"]?.jsonObject?.get("end")?.jsonPrimitive?.contentOrNull.orEmpty())

        if (start == 0 || end == 0) return null

        return SchoolYear(
            id = year["id"]?.jsonPrimitive?.intOrNull ?: 0,
            name = year["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            startDate = start,
            endDate = end,
        )
    }
}
