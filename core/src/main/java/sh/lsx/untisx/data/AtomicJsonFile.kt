package sh.lsx.untisx.data

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.security.DecryptResult

enum class EncryptedReadFailure { IO, KEY_LOST, TRANSIENT, NOT_ENCRYPTED }

sealed interface EncryptedReadResult {
    data object Missing : EncryptedReadResult
    data class Text(val value: String) : EncryptedReadResult
    data class Failed(val reason: EncryptedReadFailure) : EncryptedReadResult
}

enum class EncryptedWriteFailure { IO, KEY_UNAVAILABLE }

sealed interface EncryptedWriteResult {
    data object Success : EncryptedWriteResult
    data class Failed(val reason: EncryptedWriteFailure) : EncryptedWriteResult
}

object AtomicJsonFile {

    private const val TAG = "AtomicJsonFile"

    private val locks = ConcurrentHashMap<String, Any>()
    private const val ENCRYPTED_PREFIX = "UntisX-cache-v1:"

    fun readEncryptedStrict(file: File): EncryptedReadResult = synchronized(lockFor(file)) {
        val backup = File(file.path + ".bak")

        if (!file.exists() && !backup.exists()) {
            return@synchronized EncryptedReadResult.Missing
        }

        val stored = read(file)
            ?: return@synchronized EncryptedReadResult.Failed(EncryptedReadFailure.IO)

        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            return@synchronized EncryptedReadResult.Failed(EncryptedReadFailure.NOT_ENCRYPTED)
        }

        when (val result = AccountStore.cryptor.decrypt(stored.removePrefix(ENCRYPTED_PREFIX))) {
            is DecryptResult.Value -> EncryptedReadResult.Text(result.plaintext)
            DecryptResult.KeyLost -> EncryptedReadResult.Failed(EncryptedReadFailure.KEY_LOST)
            DecryptResult.Transient -> EncryptedReadResult.Failed(EncryptedReadFailure.TRANSIENT)
            DecryptResult.Missing -> EncryptedReadResult.Failed(EncryptedReadFailure.IO)
        }
    }

    fun writeEncryptedStrict(file: File, text: String): EncryptedWriteResult = synchronized(lockFor(file)) {
        val encrypted = runCatching { AccountStore.cryptor.encrypt(text) }.getOrNull()
            ?: return@synchronized EncryptedWriteResult.Failed(EncryptedWriteFailure.KEY_UNAVAILABLE)

        if (encrypted.isBlank()) {
            return@synchronized EncryptedWriteResult.Failed(EncryptedWriteFailure.KEY_UNAVAILABLE)
        }

        if (write(file, ENCRYPTED_PREFIX + encrypted)) {
            EncryptedWriteResult.Success
        } else {
            EncryptedWriteResult.Failed(EncryptedWriteFailure.IO)
        }
    }

    fun readEncrypted(file: File): String? = synchronized(lockFor(file)) {
        val stored = read(file) ?: return null

        if (stored.startsWith(ENCRYPTED_PREFIX)) {
            return when (val result = AccountStore.cryptor.decrypt(stored.removePrefix(ENCRYPTED_PREFIX))) {
                is DecryptResult.Value -> result.plaintext
                else -> null
            }
        }

        if (writeEncrypted(file, stored)) stored else null
    }

    fun writeEncrypted(file: File, text: String): Boolean = synchronized(lockFor(file)) {
        try {
            write(file, ENCRYPTED_PREFIX + AccountStore.cryptor.encrypt(text))
        } catch (e: Exception) {
            Log.w(TAG, "cache encryption unavailable: ${e.javaClass.simpleName}")
            false
        }
    }

    fun migrateCaches(directory: File) {
        val prefixes = listOf("timetable_cache", "stats_", "exams_", "messages_", "notif_baseline_", "notif_news_")
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") && prefixes.any(file.name::startsWith) }
            ?.forEach { readEncrypted(it) }
    }

    private fun lockFor(file: File): Any = locks.getOrPut(file.absolutePath) { Any() }

    fun read(file: File): String? = synchronized(lockFor(file)) {
        if (!file.exists()) return null

        runCatching { AtomicFile(file).readFully().toString(Charsets.UTF_8) }
            .onFailure { Log.d(TAG, "read failed: ${it.message}") }
            .getOrNull()
    }

    fun write(file: File, text: String): Boolean = synchronized(lockFor(file)) {
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null

        try {
            stream = atomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            true
        } catch (e: IOException) {
            if (stream != null) atomic.failWrite(stream)
            Log.d(TAG, "write failed: ${e.message}")
            false
        }
    }

    fun delete(file: File): Boolean = synchronized(lockFor(file)) {
        runCatching { AtomicFile(file).delete() }.isSuccess
    }

    fun cleanupTempFiles(directory: File) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".tmp") }
            ?.forEach { runCatching { it.delete() } }
    }
}

fun accountHash(accountId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))

    return digest.joinToString("") { "%02x".format(it) }
}

fun accountFile(context: android.content.Context, prefix: String, accountId: String): File =
    File(context.filesDir, prefix + accountHash(accountId) + ".json")
