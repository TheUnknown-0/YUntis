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
import java.security.MessageDigest

object TimetableCache {

    private const val LEGACY_FILE_NAME = "timetable_cache.json"
    private const val FILE_PREFIX = "timetable_cache_"
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

    private fun fileName(accountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))

        return FILE_PREFIX + digest.joinToString("") { "%02x".format(it) } + ".json"
    }

    private fun file(context: Context, accountId: String) =
        File(context.filesDir, fileName(accountId))

    private fun legacyFile(context: Context) = File(context.filesDir, LEGACY_FILE_NAME)

    fun load(context: Context, accountId: String): Data? {
        migrateLegacy(context, accountId)

        val file = file(context, accountId)

        if (!file.exists()) return null

        return runCatching {
            val data = json.decodeFromString<Data>(file.readText())

            if (data.version != VERSION) null else data
        }.getOrNull()
    }

    fun save(context: Context, accountId: String, data: Data) {
        runCatching {
            file(context, accountId).writeText(json.encodeToString(data.copy(version = VERSION)))
        }
    }

    fun clear(context: Context, accountId: String) {
        runCatching { file(context, accountId).delete() }
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
