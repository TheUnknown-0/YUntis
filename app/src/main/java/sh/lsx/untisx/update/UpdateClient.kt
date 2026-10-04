package sh.lsx.untisx.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.net.Http
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class UpdateInfo(
    val versionName: String,
    val forced: Boolean,
    val changelog: String,
    val downloadUrl: String,
)

internal data class Version(
    val numbers: List<Int>,
    val preRelease: List<String>,
) {
    val normalized: String = numbers.joinToString(".") + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")
}

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
    private val http = Http.base

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
            versionName = newest.second.normalized,
            forced = newest.first.tagName.endsWith("-forced", ignoreCase = true),
            changelog = newest.first.body.trim(),
            downloadUrl = asset.downloadUrl,
        )
    }

    internal fun parseTag(tag: String): Version? {
        var value = tag.trim().removePrefix("v")

        if (value.endsWith("-forced", ignoreCase = true)) {
            value = value.dropLast("-forced".length)
        }

        val separator = value.indexOf('-')
        val core = if (separator >= 0) value.substring(0, separator) else value
        val preRelease = if (separator >= 0) value.substring(separator + 1) else ""

        return Version(
            numbers = core.split(".").map { it.toIntOrNull() ?: return null },
            preRelease = preRelease.split('.').filter { it.isNotEmpty() },
        )
    }

    internal fun compareVersions(a: Version, b: Version): Int {
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val left = a.numbers.getOrElse(i) { 0 }
            val right = b.numbers.getOrElse(i) { 0 }

            if (left != right) return left.compareTo(right)
        }

        if (a.preRelease.isEmpty() && b.preRelease.isEmpty()) return 0
        if (a.preRelease.isEmpty()) return 1
        if (b.preRelease.isEmpty()) return -1

        for (i in 0 until minOf(a.preRelease.size, b.preRelease.size)) {
            val result = comparePreReleaseIdentifier(a.preRelease[i], b.preRelease[i])

            if (result != 0) return result
        }

        return a.preRelease.size.compareTo(b.preRelease.size)
    }

    internal fun isNewer(candidate: Version, installedName: String): Boolean {
        val installed = parseTag(installedName) ?: return true

        return compareVersions(candidate, installed) > 0
    }

    private fun comparePreReleaseIdentifier(a: String, b: String): Int {
        val aNumeric = a.toBigIntegerOrNull()
        val bNumeric = b.toBigIntegerOrNull()

        return when {
            aNumeric != null && bNumeric != null -> aNumeric.compareTo(bNumeric)
            aNumeric != null -> -1
            bNumeric != null -> 1
            else -> a.compareTo(b)
        }
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

                    if (!verifySignature(context, target)) {
                        target.delete()
                        throw IllegalStateException("Update signature does not match the installed app")
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

    private fun verifySignature(context: Context, apk: File): Boolean {
        val expected = installedCertificateSha256(context) ?: return false
        val actual = archiveCertificateSha256(context, apk) ?: return false

        return expected.isNotEmpty() && actual.isNotEmpty() && expected == actual
    }

    private fun installedCertificateSha256(context: Context): Set<String>? =
        context.packageManager.getPackageInfo(context.packageName, signatureFlags)
            .signerCertificatesSha256()

    private fun archiveCertificateSha256(context: Context, apk: File): Set<String>? {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags)
            ?: return null

        if (info.packageName != context.packageName) return null

        return info.signerCertificatesSha256()
    }

    @Suppress("DEPRECATION")
    private val signatureFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerCertificatesSha256(): Set<String>? {
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        } ?: return null

        if (signers.isEmpty()) return null

        return signers.mapTo(mutableSetOf()) { signer ->
            MessageDigest.getInstance("SHA-256")
                .digest(signer.toByteArray())
                .joinToString("") { "%02x".format(it) }
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
