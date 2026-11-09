package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import sh.lsx.untisx.data.model.Absence
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The account's own absences, as far as the school shares them with the student. */
internal object UntisAbsencesApi {

    suspend fun fetchAbsences(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<Absence> = withContext(Dispatchers.IO) {
        val result = UntisRpc.internalRpc(auth, password, "getStudentAbsences2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("startDate", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("endDate", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("includeExcused", true)
                    put("includeUnExcused", true)
                    put("auth", credentials)
                })
            }
        }

        parseAbsences(result).also { UntisRpc.log("getStudentAbsences2017 -> ${it.size} absences") }
    }

    internal fun parseAbsences(result: JsonElement): List<Absence> {
        val array = (result as? JsonObject)?.get("absences") as? JsonArray ?: result as? JsonArray ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val start = obj.text("startDateTime") ?: return@mapNotNull null
            val end = obj.text("endDateTime") ?: start
            val excuse = obj["excuse"] as? JsonObject

            Absence(
                id = (obj["id"] as? JsonPrimitive)?.intOrNull ?: 0,
                date = UntisRpc.parseIsoDate(start),
                startTime = UntisRpc.parseIsoTime(start),
                endDate = UntisRpc.parseIsoDate(end),
                endTime = UntisRpc.parseIsoTime(end),
                reason = obj.text("absenceReason") ?: obj.text("reason").orEmpty(),
                text = obj.text("text").orEmpty(),
                excused = (obj["excused"] as? JsonPrimitive)?.booleanOrNull
                    ?: excuse?.let { e -> (e["excused"] as? JsonPrimitive)?.booleanOrNull } ?: false,
                excuseStatus = excuse?.text("excuseStatus") ?: excuse?.text("text").orEmpty(),
            )
        }.filter { it.date != 0 }.sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() && it != "null" }
}
