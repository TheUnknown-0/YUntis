package sh.lsx.untisx.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.model.ExamInfo
import kotlin.random.Random

/** Exams the user entered, stored encrypted per account like the school's exams. */
object OwnExamsStore {

    private const val FILE_PREFIX = "own_exams_"
    private const val VERSION = 1
    private const val TAG = "OwnExamsStore"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Data(
        val exams: List<ExamInfo> = emptyList(),
        val version: Int = VERSION,
    )

    private fun file(context: Context, accountId: String) = accountFile(context, FILE_PREFIX, accountId)

    fun load(context: Context, accountId: String): List<ExamInfo> {
        val text = AtomicJsonFile.readEncrypted(file(context, accountId)) ?: return emptyList()

        return runCatching { json.decodeFromString<Data>(text).exams.map { it.copy(own = true) } }
            .getOrDefault(emptyList())
    }

    private fun save(context: Context, accountId: String, exams: List<ExamInfo>): Boolean {
        val ok = AtomicJsonFile.writeEncrypted(file(context, accountId), json.encodeToString(Data(exams)))
        if (!ok) Log.d(TAG, "own exams save failed")
        return ok
    }

    /** Adds or replaces an exam (matched by id). Returns the stored list. */
    fun upsert(context: Context, accountId: String, exam: ExamInfo): List<ExamInfo> {
        val stored = exam.copy(own = true, id = if (exam.id < 0) exam.id else newId())
        val updated = load(context, accountId).filterNot { it.id == stored.id } + stored
        val sorted = updated.sortedWith(compareBy({ it.date }, { it.startTime }))
        save(context, accountId, sorted)
        return sorted
    }

    fun delete(context: Context, accountId: String, id: Int): List<ExamInfo> {
        val updated = load(context, accountId).filterNot { it.id == id }
        save(context, accountId, updated)
        return updated
    }

    fun clear(context: Context, accountId: String) {
        AtomicJsonFile.delete(file(context, accountId))
    }

    /** Own exams get negative ids so they never collide with ids from Untis. */
    fun newId(): Int = -(Random.nextInt(1, Int.MAX_VALUE))
}
