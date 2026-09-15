package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.TimetableEntry
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object ChangeBaseline {

    private const val FILE_PREFIX = "notif_baseline_"
    private const val VERSION = 1
    private const val TAG = "ChangeBaseline"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Data(
        val weeks: Map<String, List<TimetableEntry>> = emptyMap(),
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

    fun save(context: Context, accountId: String, weeks: Map<String, List<TimetableEntry>>) {
        runCatching {
            val target = file(context, accountId)
            val temp = File(target.parentFile, "${target.name}.tmp")

            temp.writeText(json.encodeToString(Data(weeks = weeks, version = VERSION)))

            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not replace notification baseline")
            }
        }.onFailure { Log.d(TAG, "baseline save failed: ${it.message}") }
    }

    fun clear(context: Context, accountId: String) {
        runCatching { file(context, accountId).delete() }
    }
}
