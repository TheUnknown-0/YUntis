package sh.lsx.untisx.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.ExamInfo

object ExamsStore {

    private const val FILE_PREFIX = "exams_"
    private const val VERSION = 1
    private const val TAG = "ExamsStore"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Data(
        val exams: List<ExamInfo> = emptyList(),
        val fetchedAt: Long = 0,
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

    fun save(context: Context, accountId: String, data: Data) {
        val encoded = json.encodeToString(data.copy(version = VERSION))

        if (!AtomicJsonFile.writeEncrypted(file(context, accountId), encoded)) {
            Log.d(TAG, "exams save failed")
        }
    }

    fun clear(context: Context, accountId: String) {
        AtomicJsonFile.delete(file(context, accountId))
    }
}
