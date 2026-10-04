package sh.lsx.untisx.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import java.io.File

object TimetableCache {

    private const val LEGACY_FILE_NAME = "timetable_cache.json"
    private const val FILE_PREFIX = "timetable_cache_"
    private const val VERSION = 6
    private const val TAG = "TimetableCache"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Data(
        val weeks: Map<String, List<TimetableEntry>> = emptyMap(),
        val subjects: List<MasterDataItem> = emptyList(),
        val teachers: List<MasterDataItem> = emptyList(),
        val rooms: List<MasterDataItem> = emptyList(),
        val holidays: List<Holiday> = emptyList(),
        val timegrid: List<TimegridDay> = emptyList(),
        val restrictedDays: Set<Int> = emptySet(),
        val events: List<TimetableEntry> = emptyList(),
        val eventsFetchedAt: Long = 0,
        val messages: List<DayMessage> = emptyList(),
        val messagesFetchedAt: Long = 0,
        val lastFetch: Long = 0,
        val version: Int = 0,
    )

    private fun file(context: Context, accountId: String) =
        accountFile(context, FILE_PREFIX, accountId)

    private fun legacyFile(context: Context) = File(context.filesDir, LEGACY_FILE_NAME)

    fun load(context: Context, accountId: String): Data? {
        migrateLegacy(context, accountId)

        val text = AtomicJsonFile.read(file(context, accountId)) ?: return null

        return runCatching {
            val data = json.decodeFromString<Data>(text)

            if (data.version != VERSION) null else data
        }.getOrNull()
    }

    fun save(context: Context, accountId: String, data: Data) {
        val encoded = json.encodeToString(data.copy(version = VERSION))

        if (!AtomicJsonFile.write(file(context, accountId), encoded)) {
            Log.d(TAG, "cache save failed")
        }
    }

    fun clear(context: Context, accountId: String) {
        AtomicJsonFile.delete(file(context, accountId))
    }

    private fun migrateLegacy(context: Context, accountId: String) {
        val legacy = legacyFile(context)

        if (!legacy.exists()) return

        val hasAccountCaches = context.filesDir.listFiles()
            ?.any { it.name.startsWith(FILE_PREFIX) } == true

        if (!hasAccountCaches) {
            runCatching { legacy.renameTo(file(context, accountId)) }.onFailure { legacy.delete() }
        } else {
            legacy.delete()
        }
    }
}
