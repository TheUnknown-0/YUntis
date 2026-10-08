package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.MessagesData
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.stripHtml
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object UntisMessagesApi {

    suspend fun fetchMessages(auth: AuthData, password: String): MessagesData? = withContext(Dispatchers.IO) {
        val body = UntisRpc.authenticatedGet(auth, password, UntisRpc.webuntisUrl(auth.server, "api/rest/view/v1/messages"))
            ?: return@withContext null

        runCatching {
            val response = UntisRpc.json.decodeFromString<MessagesRestResponse>(body)

            MessagesData(
                incoming = response.incomingMessages.orEmpty(),
                readConfirmations = response.readConfirmationMessages.orEmpty(),
            )
        }.onFailure {
            UntisRpc.warn("messages returned an unparseable response")
        }.getOrNull()
    }

    suspend fun fetchMessage(auth: AuthData, password: String, id: Long): DirectMessage? =
        withContext(Dispatchers.IO) {
            if (id <= 0) return@withContext null

            val body = UntisRpc.authenticatedGet(auth, password, UntisRpc.webuntisUrl(auth.server, "api/rest/view/v1/messages/$id"))
                ?: return@withContext null

            runCatching {
                UntisRpc.json.decodeFromString<DirectMessage>(body)
            }.onFailure {
                UntisRpc.warn("message detail returned an unparseable response")
            }.getOrNull()
        }

    suspend fun fetchMessagesOfDay(
        auth: AuthData,
        password: String,
        date: LocalDate,
    ): List<DayMessage> = withContext(Dispatchers.IO) {
        val result = UntisRpc.internalRpc(auth, password, "getMessagesOfDay2017") { credentials ->
            buildJsonArray {
                add(buildJsonObject {
                    put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    put("auth", credentials)
                })
            }
        }

        parseMessagesOfDay(result).also { UntisRpc.log("getMessagesOfDay2017 -> ${it.size} messages") }
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

    @Serializable
    private data class MessagesRestResponse(
        val incomingMessages: List<DirectMessage>? = null,
        val readConfirmationMessages: List<DirectMessage>? = null,
    )
}
