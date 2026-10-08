package sh.lsx.untisx.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.DirectMessage

object MessagesStore {

    private const val FILE_PREFIX = "messages_"
    private const val VERSION = 1
    private const val TAG = "MessagesStore"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Data(
        val incoming: List<DirectMessage> = emptyList(),
        val readConfirmations: List<DirectMessage> = emptyList(),
        val fetchedAt: Long = 0,
        val version: Int = 0,
    )

    private fun file(context: Context, accountId: String) =
        accountFile(context, FILE_PREFIX, accountId)

    fun load(context: Context, accountId: String): Data? {
        val text = AtomicJsonFile.read(file(context, accountId)) ?: return null

        return runCatching {
            val data = json.decodeFromString<Data>(text)
            if (data.version != VERSION) null else data
        }.getOrNull()
    }

    fun save(context: Context, accountId: String, data: Data) {
        val encoded = json.encodeToString(data.copy(version = VERSION))

        if (!AtomicJsonFile.write(file(context, accountId), encoded)) {
            Log.d(TAG, "messages save failed")
        }
    }

    fun clear(context: Context, accountId: String) {
        AtomicJsonFile.delete(file(context, accountId))
    }
}
