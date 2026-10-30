package sh.lsx.untisx.data.wear

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object WearPayloadCodec {

    const val MAX_PAYLOAD_BYTES = 90_000
    const val MAX_JSON_BYTES = 2_000_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun encode(payload: WearTimetablePayload): ByteArray {
        val raw = encodeJson(payload).toByteArray(Charsets.UTF_8)

        val buffer = ByteArrayOutputStream(raw.size / 2)
        GZIPOutputStream(buffer).use { it.write(raw) }

        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): WearTimetablePayload? = runCatching {
        require(bytes.size <= MAX_PAYLOAD_BYTES)
        val raw = GZIPInputStream(bytes.inputStream()).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_JSON_BYTES)
                output.write(buffer, 0, count)
            }

            output.toByteArray()
        }

        decodeJson(raw.toString(Charsets.UTF_8))
    }.getOrNull()

    fun encodeJson(payload: WearTimetablePayload): String = json.encodeToString(payload)

    fun decodeJson(text: String): WearTimetablePayload? = runCatching {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES)
        json.decodeFromString<WearTimetablePayload>(text).also {
            require(it.version == WearTimetablePayload.VERSION)
        }
    }.getOrNull()
}
