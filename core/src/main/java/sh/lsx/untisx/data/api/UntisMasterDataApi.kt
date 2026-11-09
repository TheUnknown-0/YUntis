package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.TimegridUnit
import sh.lsx.untisx.data.prefs.AuthData

internal object UntisMasterDataApi {

    suspend fun getSubjects(auth: AuthData): List<MasterDataItem> = masterData(auth, "getSubjects")

    suspend fun getTeachers(auth: AuthData): List<MasterDataItem> = masterData(auth, "getTeachers")

    suspend fun getRooms(auth: AuthData): List<MasterDataItem> = masterData(auth, "getRooms")

    suspend fun getKlassen(auth: AuthData): List<MasterDataItem> = masterData(auth, "getKlassen")

    suspend fun getStudents(auth: AuthData): List<MasterDataItem> = masterData(auth, "getStudents")

    suspend fun getHolidays(auth: AuthData): List<Holiday> = withContext(Dispatchers.IO) {
        UntisRpc.json.decodeFromJsonElement<List<Holiday>>(
            UntisRpc.rpcCall(auth, "getHolidays"),
        ).also { UntisRpc.log("getHolidays -> ${it.size} items") }
    }

    private suspend fun masterData(auth: AuthData, method: String): List<MasterDataItem> =
        withContext(Dispatchers.IO) {
            UntisRpc.json.decodeFromJsonElement<List<MasterDataItem>>(
                UntisRpc.rpcCall(auth, method),
            ).also { UntisRpc.log("$method -> ${it.size} items") }
        }

    suspend fun fetchMasterData(auth: AuthData, password: String): UntisClient.MasterData =
        withContext(Dispatchers.IO) {
            val result = userData(auth, password)
            val master = result.jsonObject["masterData"]?.jsonObject

            UntisClient.MasterData(
                subjects = parseMasterItems(master?.get("subjects")),
                teachers = parseUserDataTeachers(master?.get("teachers") as? JsonArray ?: JsonArray(emptyList())),
                rooms = parseMasterItems(master?.get("rooms")),
                holidays = parseUserDataHolidays(master?.get("holidays")),
                timegrid = parseTimegridResponse(result),
            ).also {
                UntisRpc.log(
                    "masterData -> subjects=${it.subjects.size} teachers=${it.teachers.size} " +
                        "rooms=${it.rooms.size} holidays=${it.holidays.size} timegrid=${it.timegrid.size}",
                )
            }
        }

    private fun userData(auth: AuthData, password: String): JsonElement =
        UntisRpc.internalRpc(auth, password, "getUserData2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("elementId", 0)
                    put("deviceOs", "A")
                    put("deviceOsVersion", "")
                    put("auth", credentials)
                })
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
                backColor = obj["backColor"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                foreColor = obj["foreColor"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
            )
        }
    }

    private fun parseUserDataHolidays(element: JsonElement?): List<Holiday> {
        val array = element as? JsonArray ?: return emptyList()

        return array.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null

            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val start = UntisRpc.parseIsoDate(obj["startDate"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val end = UntisRpc.parseIsoDate(obj["endDate"]?.jsonPrimitive?.contentOrNull.orEmpty())

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

    suspend fun fetchTimegridViaUserData(auth: AuthData, password: String): List<TimegridDay> =
        withContext(Dispatchers.IO) {
            parseTimegridResponse(userData(auth, password))
                .also { UntisRpc.log("timegrid via getUserData2017 -> ${it.size} days") }
        }

    suspend fun fetchTeachersViaUserData(auth: AuthData, password: String): List<MasterDataItem> =
        withContext(Dispatchers.IO) {
            val array = runCatching {
                userData(auth, password).jsonObject["masterData"]?.jsonObject?.get("teachers")?.jsonArray
            }.getOrNull() ?: return@withContext emptyList()

            parseUserDataTeachers(array).also { UntisRpc.log("teachers via getUserData2017 -> ${it.size}") }
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
}
