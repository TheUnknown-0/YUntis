package sh.lsx.untisx.data.notebook

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.EncryptedReadFailure
import sh.lsx.untisx.data.EncryptedReadResult
import sh.lsx.untisx.data.EncryptedWriteFailure
import sh.lsx.untisx.data.EncryptedWriteResult
import sh.lsx.untisx.data.accountFile

enum class NotebookReadFailure {
    IO, KEY_LOST, TRANSIENT, NOT_ENCRYPTED, CORRUPT, UNSUPPORTED_VERSION,
}

enum class NotebookStorageFailure { IO, KEY_UNAVAILABLE }

sealed interface NotebookReadResult {
    data class Loaded(val data: NotebookData) : NotebookReadResult
    data object Missing : NotebookReadResult
    data class Failed(val reason: NotebookReadFailure) : NotebookReadResult
}

sealed interface NotebookWriteResult {
    data class Success(
        val data: NotebookData,
        val draftCleanupFailed: Boolean = false,
    ) : NotebookWriteResult

    data class ReadFailed(val reason: NotebookReadFailure) : NotebookWriteResult
    data class StorageFailed(val reason: NotebookStorageFailure) : NotebookWriteResult
    data class Conflict(val current: NotebookData) : NotebookWriteResult
    data class ConfirmationRequired(val warning: NotebookConversionWarning) : NotebookWriteResult
    data class NotFound(val itemId: String) : NotebookWriteResult
    data class Invalid(val issues: List<NotebookValidationIssue>) : NotebookWriteResult
}

sealed interface NotebookDraftReadResult {
    data class Loaded(val data: NotebookDraftData) : NotebookDraftReadResult
    data object Missing : NotebookDraftReadResult
    data class Failed(val reason: NotebookReadFailure) : NotebookDraftReadResult
}

sealed interface NotebookDraftWriteResult {
    data class Success(val data: NotebookDraftData) : NotebookDraftWriteResult
    data class ReadFailed(val reason: NotebookReadFailure) : NotebookDraftWriteResult
    data class StorageFailed(val reason: NotebookStorageFailure) : NotebookDraftWriteResult
    data class Invalid(val issues: List<NotebookValidationIssue>) : NotebookDraftWriteResult
}

object NotebookStore {
    const val FILE_PREFIX = "notebook_"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun file(context: Context, accountId: String): File =
        accountFile(context, FILE_PREFIX, accountId)

    suspend fun load(context: Context, accountId: String): NotebookReadResult = withContext(Dispatchers.IO) {
        when (val read = AtomicJsonFile.readEncryptedStrict(file(context, accountId))) {
            EncryptedReadResult.Missing -> NotebookReadResult.Missing
            is EncryptedReadResult.Text -> decode(read.value)
            is EncryptedReadResult.Failed -> NotebookReadResult.Failed(read.reason.toNotebookFailure())
        }
    }

    suspend fun write(
        context: Context,
        accountId: String,
        data: NotebookData,
    ): NotebookWriteResult = withContext(Dispatchers.IO) {
        val target = file(context, accountId)

        when (val existing = AtomicJsonFile.readEncryptedStrict(target)) {
            is EncryptedReadResult.Failed ->
                return@withContext NotebookWriteResult.ReadFailed(existing.reason.toNotebookFailure())

            is EncryptedReadResult.Text -> {
                when (val decoded = decode(existing.value)) {
                    is NotebookReadResult.Failed ->
                        return@withContext NotebookWriteResult.ReadFailed(decoded.reason)

                    else -> Unit
                }
            }

            EncryptedReadResult.Missing -> Unit
        }

        val normalized = data.copy(items = data.items.map { NotebookValidator.normalize(it) })
        val issues = NotebookValidator.validate(normalized)

        if (issues.isNotEmpty()) return@withContext NotebookWriteResult.Invalid(issues)

        when (val written = AtomicJsonFile.writeEncryptedStrict(target, json.encodeToString(normalized))) {
            EncryptedWriteResult.Success -> NotebookWriteResult.Success(normalized)
            is EncryptedWriteResult.Failed ->
                NotebookWriteResult.StorageFailed(written.reason.toNotebookStorageFailure())
        }
    }

    fun clear(context: Context, accountId: String): Boolean = clearFile(file(context, accountId))

    internal fun clearByHash(context: Context, hash: String): Boolean =
        clearFile(File(context.filesDir, FILE_PREFIX + hash + ".json"))

    private fun decode(text: String): NotebookReadResult {
        val data = runCatching { json.decodeFromString<NotebookData>(text) }.getOrNull()
            ?: return NotebookReadResult.Failed(NotebookReadFailure.CORRUPT)

        if (data.schema != NotebookSchema.CURRENT) {
            return NotebookReadResult.Failed(NotebookReadFailure.UNSUPPORTED_VERSION)
        }

        val issues = NotebookValidator.validate(data)

        return if (issues.isEmpty()) {
            NotebookReadResult.Loaded(data)
        } else {
            NotebookReadResult.Failed(NotebookReadFailure.CORRUPT)
        }
    }
}

object NotebookDraftStore {
    const val FILE_PREFIX = "notebook_drafts_"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun file(context: Context, accountId: String): File =
        accountFile(context, FILE_PREFIX, accountId)

    suspend fun load(context: Context, accountId: String): NotebookDraftReadResult = withContext(Dispatchers.IO) {
        when (val read = AtomicJsonFile.readEncryptedStrict(file(context, accountId))) {
            EncryptedReadResult.Missing -> NotebookDraftReadResult.Missing
            is EncryptedReadResult.Text -> decode(read.value)
            is EncryptedReadResult.Failed -> NotebookDraftReadResult.Failed(read.reason.toNotebookFailure())
        }
    }

    suspend fun write(
        context: Context,
        accountId: String,
        data: NotebookDraftData,
    ): NotebookDraftWriteResult = withContext(Dispatchers.IO) {
        val target = file(context, accountId)

        when (val existing = AtomicJsonFile.readEncryptedStrict(target)) {
            is EncryptedReadResult.Failed ->
                return@withContext NotebookDraftWriteResult.ReadFailed(existing.reason.toNotebookFailure())

            is EncryptedReadResult.Text -> {
                when (val decoded = decode(existing.value)) {
                    is NotebookDraftReadResult.Failed ->
                        return@withContext NotebookDraftWriteResult.ReadFailed(decoded.reason)

                    else -> Unit
                }
            }

            EncryptedReadResult.Missing -> Unit
        }

        val normalized = data.copy(
            drafts = data.drafts.mapValues { (_, draft) -> NotebookValidator.normalizeDraft(draft) },
        )
        val issues = NotebookValidator.validateDrafts(normalized)

        if (issues.isNotEmpty()) return@withContext NotebookDraftWriteResult.Invalid(issues)

        when (val written = AtomicJsonFile.writeEncryptedStrict(target, json.encodeToString(normalized))) {
            EncryptedWriteResult.Success -> NotebookDraftWriteResult.Success(normalized)
            is EncryptedWriteResult.Failed ->
                NotebookDraftWriteResult.StorageFailed(written.reason.toNotebookStorageFailure())
        }
    }

    fun clear(context: Context, accountId: String): Boolean = clearFile(file(context, accountId))

    internal fun clearByHash(context: Context, hash: String): Boolean =
        clearFile(File(context.filesDir, FILE_PREFIX + hash + ".json"))

    private fun decode(text: String): NotebookDraftReadResult {
        val data = runCatching { json.decodeFromString<NotebookDraftData>(text) }.getOrNull()
            ?: return NotebookDraftReadResult.Failed(NotebookReadFailure.CORRUPT)

        if (data.schema != NotebookSchema.CURRENT) {
            return NotebookDraftReadResult.Failed(NotebookReadFailure.UNSUPPORTED_VERSION)
        }

        val issues = NotebookValidator.validateDrafts(data)

        return if (issues.isEmpty()) {
            NotebookDraftReadResult.Loaded(data)
        } else {
            NotebookDraftReadResult.Failed(NotebookReadFailure.CORRUPT)
        }
    }
}

private fun clearFile(target: File): Boolean {
    AtomicJsonFile.delete(target)
    File(target.path + ".new").delete()

    return !target.exists() &&
        !File(target.path + ".bak").exists() &&
        !File(target.path + ".new").exists()
}

private fun EncryptedReadFailure.toNotebookFailure(): NotebookReadFailure = when (this) {
    EncryptedReadFailure.IO -> NotebookReadFailure.IO
    EncryptedReadFailure.KEY_LOST -> NotebookReadFailure.KEY_LOST
    EncryptedReadFailure.TRANSIENT -> NotebookReadFailure.TRANSIENT
    EncryptedReadFailure.NOT_ENCRYPTED -> NotebookReadFailure.NOT_ENCRYPTED
}

private fun EncryptedWriteFailure.toNotebookStorageFailure(): NotebookStorageFailure = when (this) {
    EncryptedWriteFailure.IO -> NotebookStorageFailure.IO
    EncryptedWriteFailure.KEY_UNAVAILABLE -> NotebookStorageFailure.KEY_UNAVAILABLE
}
