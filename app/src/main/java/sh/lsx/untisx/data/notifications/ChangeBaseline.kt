package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.accountFile
import sh.lsx.untisx.data.AtomicJsonFile

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

    private fun file(context: Context, accountId: String) =
        accountFile(context, FILE_PREFIX, accountId)

    fun load(context: Context, accountId: String): Data? {
        val text = AtomicJsonFile.readEncrypted(file(context, accountId)) ?: return null

        return runCatching {
            val data = json.decodeFromString<Data>(text)
            if (data.version != VERSION) null else data
        }.getOrNull()
    }

    fun save(context: Context, accountId: String, weeks: Map<String, List<TimetableEntry>>) {
        val encoded = json.encodeToString(Data(weeks = weeks, version = VERSION))

        if (!AtomicJsonFile.writeEncrypted(file(context, accountId), encoded)) {
            Log.d(TAG, "baseline save failed")
        }
    }

    fun clear(context: Context, accountId: String) {
        AtomicJsonFile.delete(file(context, accountId))
    }
}
