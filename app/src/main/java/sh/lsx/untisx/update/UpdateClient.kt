package sh.lsx.untisx.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class UpdateInfo(
    val versionName: String,
    val forced: Boolean,
    val changelog: String,
    val downloadUrl: String,
)

@Serializable
private data class CodebergRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("body") val body: String = "",
    @SerialName("assets") val assets: List<CodebergAsset> = emptyList(),
)

@Serializable
private data class CodebergAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

object UpdateClient {

    private const val RELEASES_URL =
        "https://codeberg.org/api/v1/repos/Losterixx/UntisX/releases"

    private const val KEEP_OLD_APKS = 2

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val releases = http.newCall(Request.Builder().url(RELEASES_URL).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Codeberg answered with ${response.code}")
                }

                json.decodeFromString<List<CodebergRelease>>(response.body.string())
            }

        val newest = releases.asSequence()
            .filter { !it.draft } // pre-releases allowed during alpha/beta
            .mapNotNull { release -> parseTag(release.tagName)?.let { release to it } }
            .maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
            ?: return@withContext null

        if (!isNewer(newest.second, installedVersionName(context))) return@withContext null

        val asset = newest.first.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: throw IllegalStateException("No APK attached to ${newest.first.tagName}.")

        UpdateInfo(
            versionName = newest.second.joinToString("."),
            forced = newest.first.tagName.endsWith("-forced", ignoreCase = true),
            changelog = newest.first.body.trim(),
            downloadUrl = asset.downloadUrl,
        )
    }

    internal fun parseTag(tag: String): List<Int>? =
        tag.trim()
            .removePrefix("v")
            .substringBefore("-")
            .split(".")
            .map { it.toIntOrNull() ?: return null }

    internal fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }

            if (left != right) return left - right
        }

        return 0
    }

    internal fun isNewer(candidate: List<Int>, installedName: String): Boolean {
        val installed = parseTag(installedName) ?: return true

        for (i in 0 until maxOf(candidate.size, installed.size)) {
            val c = candidate.getOrElse(i) { 0 }
            val current = installed.getOrElse(i) { 0 }

            if (c != current) return c > current
        }

        return false
    }

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val downloadUrl = info.downloadUrl.toHttpUrlOrNull() ?: throw IllegalStateException("Invalid update URL")
        require(downloadUrl.isHttps && downloadUrl.host == "codeberg.org") {
            "Untrusted update source"
        }

        val request = Request.Builder().url(downloadUrl).build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Codeberg answered with ${response.code}")
                }

                val body = response.body
                val total = body.contentLength()
                val target = File(context.cacheDir, "updates/UntisX-v${info.versionName}.apk")
                val temporary = File(target.parentFile, "${target.name}.part")

                target.parentFile?.mkdirs()
                temporary.delete()

                var lastPercent = -1

                try {
                    body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var read: Int

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                written += read

                                if (total > 0) {
                                    val percent = (written * 100 / total).toInt()

                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(written.toFloat() / total)
                                    }
                                }
                            }
                        }
                    }

                    if (temporary.length() == 0L) {
                        throw IllegalStateException("Downloaded update is empty")
                    }

                    if (!temporary.renameTo(target)) {
                        throw IllegalStateException("Could not finalize update download")
                    }
                } catch (e: Exception) {
                    temporary.delete()
                    throw e
                }

                target
            }
        } finally {
            pruneOldApks(File(context.cacheDir, "updates"))
        }
    }

    private fun pruneOldApks(dir: File?) {
        val files = dir?.listFiles { file -> file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".part")) }
            ?: return

        files.filter { it.name.endsWith(".part") }
            .forEach { runCatching { it.delete() } }

        files.filter { it.name.endsWith(".apk") }
            .sortedByDescending { it.lastModified() }
            .drop(KEEP_OLD_APKS + 1)
            .forEach { runCatching { it.delete() } }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }
}
