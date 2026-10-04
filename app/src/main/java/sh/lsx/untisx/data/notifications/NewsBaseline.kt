package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.TimetableEntry
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object NewsBaseline {

    private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    private const val FILE_PREFIX = "notif_news_"
    private const val VERSION = 1
    private const val TAG = "NewsBaseline"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Data(
        val messages: Set<String>? = null,
        val events: Set<String>? = null,
        val announcements: Set<String>? = null,
        val messagesUpdatedAt: Long = 0L,
        val eventsUpdatedAt: Long = 0L,
        val announcementsUpdatedAt: Long = 0L,
        val version: Int = 0,
    )

    private fun fileName(accountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))

        return FILE_PREFIX + digest.joinToString("") { "%02x".format(it) } + ".json"
    }

    private fun file(context: Context, accountId: String) =
        File(context.filesDir, fileName(accountId))

    fun load(context: Context, accountId: String): Data? {
        val file = file(context, accountId)
        if (!file.exists()) return null

        return runCatching {
            val data = json.decodeFromString<Data>(file.readText())
            if (data.version != VERSION) null else data
        }.getOrNull()
    }

    fun save(context: Context, accountId: String, data: Data) {
        runCatching {
            val target = file(context, accountId)
            val temp = File(target.parentFile, "${target.name}.tmp")

            temp.writeText(json.encodeToString(data.copy(version = VERSION)))

            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not replace news baseline")
            }
        }.onFailure { Log.d(TAG, "baseline save failed: ${it.message}") }
    }

    fun clear(context: Context, accountId: String) {
        runCatching { file(context, accountId).delete() }
    }

    fun isStale(updatedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        updatedAt > 0 && now - updatedAt > STALE_AFTER_MS
}

object NewsSignatures {

    fun message(message: DirectMessage): String {
        if (message.id > 0) return "m:${message.id}"

        val fallback = "${message.sender?.displayName.orEmpty()}|${message.subject}|${message.sentDateTime}"

        return "m:${fallback.hashCode()}"
    }

    fun event(event: TimetableEntry): String {
        val roomId = event.room.firstOrNull()?.id ?: 0
        val fallback = "${event.date}|${event.startTime}|${event.endTime}|${event.eventTitle.orEmpty()}|$roomId"

        return "e:${fallback.hashCode()}"
    }

    fun announcement(message: DayMessage): String {
        if (message.id > 0) return "a:${message.id}"

        return "a:${message.body.hashCode()}"
    }
}

object NewsDiff {

    fun <T> newItems(items: List<T>, baseline: Set<String>, key: (T) -> String): List<T> {
        val unique = LinkedHashMap<String, T>()

        items.forEach { unique.putIfAbsent(key(it), it) }

        return unique.filterKeys { it !in baseline }.values.toList()
    }
}
