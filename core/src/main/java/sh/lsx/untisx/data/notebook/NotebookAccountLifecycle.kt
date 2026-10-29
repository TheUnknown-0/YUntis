package sh.lsx.untisx.data.notebook

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.accountHash

internal object NotebookAccountLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun lock(accountId: String): Mutex = locks.getOrPut(accountId) { Mutex() }
}

internal object NotebookAccountLifecycle {
    private const val FILE_PREFIX = "notebook_lifecycle_"
    private const val PHASE_PREPARED = "PREPARED"
    private const val PHASE_DELETED = "DELETED"

    fun isBlocked(context: Context, accountId: String): Boolean =
        markerArtifacts(context, accountHash(accountId)).any { it.exists() }

    fun prepare(context: Context, accountId: String): Boolean =
        AtomicJsonFile.write(markerFile(context, accountId), PHASE_PREPARED)

    fun phase(context: Context, accountId: String): String? =
        readPhase(markerFile(context, accountId))

    fun restoreAfterAbortedRemoval(
        context: Context,
        accountId: String,
        previousBlocked: Boolean,
        previousPhase: String?,
    ): Boolean {
        if (!previousBlocked) {
            deleteMarkerArtifacts(markerFile(context, accountId))
            return !isBlocked(context, accountId)
        }

        val phase = previousPhase ?: PHASE_PREPARED
        return AtomicJsonFile.write(markerFile(context, accountId), phase)
    }

    fun activate(context: Context, accountId: String): Boolean {
        deleteMarkerArtifacts(markerFile(context, accountId))
        return !isBlocked(context, accountId)
    }

    fun finalizeForReadd(context: Context, accountId: String): Boolean {
        if (!isBlocked(context, accountId)) return true

        if (readPhase(markerFile(context, accountId)) != PHASE_DELETED) return false

        return clearNotebookData(context, accountId)
    }

    fun finalizeCommittedDeletion(context: Context, accountId: String): Boolean {
        if (!clearNotebookData(context, accountId)) return false

        return AtomicJsonFile.write(markerFile(context, accountId), PHASE_DELETED)
    }

    fun reconcile(
        context: Context,
        presentAccountIds: Set<String>,
        accountsReadable: Boolean,
    ): Boolean {
        val presentHashes = presentAccountIds.mapTo(mutableSetOf<String>()) { accountHash(it) }
        val hashes = context.filesDir
            .listFiles { file -> file.isFile && file.name.startsWith(FILE_PREFIX) }
            ?.mapNotNull { hashFromName(it.name) }
            ?.toSet()
            ?: return true

        var allDone = true

        hashes.forEach { hash ->
            val present = hash in presentHashes
            val file = markerFileByHash(context, hash)
            val phase = readPhase(file) ?: PHASE_PREPARED

            when (phase) {
                PHASE_PREPARED -> {
                    if (present) {
                        deleteMarkerArtifacts(file)
                        if (isBlockedByHash(context, hash)) allDone = false
                    } else if (accountsReadable) {
                        if (!clearNotebookDataByHash(context, hash)) {
                            allDone = false
                            return@forEach
                        }

                        if (!AtomicJsonFile.write(file, PHASE_DELETED)) allDone = false
                    } else {
                        allDone = false
                    }
                }

                PHASE_DELETED -> {
                    if (present) {
                        deleteMarkerArtifacts(file)
                        if (isBlockedByHash(context, hash)) allDone = false
                    }
                }
            }
        }

        return allDone
    }

    private fun clearNotebookData(context: Context, accountId: String): Boolean {
        val draftsCleared = NotebookDraftStore.clear(context, accountId)
        val notebookCleared = NotebookStore.clear(context, accountId)

        return draftsCleared && notebookCleared
    }

    private fun clearNotebookDataByHash(context: Context, hash: String): Boolean {
        val draftsCleared = NotebookDraftStore.clearByHash(context, hash)
        val notebookCleared = NotebookStore.clearByHash(context, hash)

        return draftsCleared && notebookCleared
    }

    private fun markerFile(context: Context, accountId: String): File =
        File(context.filesDir, FILE_PREFIX + accountHash(accountId) + ".json")

    private fun markerFileByHash(context: Context, hash: String): File =
        File(context.filesDir, FILE_PREFIX + hash + ".json")

    private fun markerArtifacts(context: Context, hash: String): List<File> {
        val base = markerFileByHash(context, hash)

        return listOf(base, File(base.path + ".bak"), File(base.path + ".new"))
    }

    private fun markerArtifacts(file: File): List<File> =
        listOf(file, File(file.path + ".bak"), File(file.path + ".new"))

    private fun deleteMarkerArtifacts(file: File) {
        markerArtifacts(file).forEach { artifact ->
            runCatching { artifact.delete() }
            Unit
        }
    }

    private fun isBlockedByHash(context: Context, hash: String): Boolean =
        markerArtifacts(context, hash).any { it.exists() }

    private fun readPhase(file: File): String? {
        if (!file.exists()) return null

        val text = AtomicJsonFile.read(file) ?: return null
        val phase = text.trim()

        return phase.takeIf { it == PHASE_PREPARED || it == PHASE_DELETED }
    }

    private fun hashFromName(name: String): String? {
        if (!name.startsWith(FILE_PREFIX)) return null

        var hash = name.removePrefix(FILE_PREFIX)

        if (hash.endsWith(".new")) hash = hash.removeSuffix(".new")
        if (hash.endsWith(".bak")) hash = hash.removeSuffix(".bak")
        if (hash.endsWith(".json")) hash = hash.removeSuffix(".json")

        return hash.takeIf { it.isNotBlank() }
    }
}
