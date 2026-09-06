package sh.lsx.untisx.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimegridDay
import java.io.File

object TimetableCache {

    private const val FILE_NAME = "timetable_cache.json"

    // Bump when the meaning of stored data changes, so stale caches are
    // discarded instead of served (e.g. when per-day restriction tracking
    // was introduced, old caches had no restriction info at all).
    private const val VERSION = 2

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
        val lastFetch: Long = 0,
        val version: Int = 0,
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): Data? {
        val file = file(context)

        if (!file.exists()) return null

        return runCatching {
            val data = json.decodeFromString<Data>(file.readText())

            if (data.version != VERSION) null else data
        }.getOrNull()
    }

    fun save(context: Context, data: Data) {
        runCatching {
            file(context).writeText(json.encodeToString(data.copy(version = VERSION)))
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
