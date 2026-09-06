package sh.lsx.untisx.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import androidx.core.net.toUri

@Serializable
data class UpdateInfo(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("forced") val forced: Boolean = false,
    @SerialName("changelog") val changelog: String = "",
)

object UpdateClient {

    private const val VERSION_URL = "https://lsx.sh/api/projects/untisx/version"
    private const val APK_URL = "https://lsx.sh/api/projects/untisx/apk"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    fun installedVersionCode(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(VERSION_URL).build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Update server answered with ${response.code}")
            }

            val body = response.body.string()
            val info = json.decodeFromString<UpdateInfo>(body)

            if (info.versionCode > installedVersionCode(context)) info else null
        }
    }

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(APK_URL).build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Update server answered with ${response.code}")
            }

            val body = response.body
            val total = body.contentLength()
            val target = File(context.cacheDir, "updates/UntisX-v${info.versionName}.apk")
            target.parentFile?.mkdirs()

            var lastPercent = -1

            body.byteStream().use { input ->
                target.outputStream().use { output ->
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

            target
        }
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
