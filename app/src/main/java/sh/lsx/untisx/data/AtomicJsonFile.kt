package sh.lsx.untisx.data

import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal object AtomicJsonFile {

    private const val TAG = "AtomicJsonFile"

    private val locks = ConcurrentHashMap<String, Any>()

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

internal fun accountFile(context: android.content.Context, prefix: String, accountId: String): File {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))

    return File(context.filesDir, prefix + digest.joinToString("") { "%02x".format(it) } + ".json")
}
