package sh.lsx.untisx.data.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.net.Http
import sh.lsx.untisx.data.notifications.Notifications
import java.io.File
import java.util.concurrent.TimeUnit

/** A release on GitHub that carries an APK. */
data class AppRelease(val version: String, val apkUrl: String, val notes: String, val page: String)

/**
 * Looks for new versions in the GitHub releases of the YUntis repository. Works without a token as
 * soon as the repository is public; while it is private GitHub answers 404 and nothing is offered.
 */
object AppUpdater {
    const val REPO = "TheUnknown-0/YUntis"
    const val FOCUS_ABOUT = "about"
    private const val TAG = "AppUpdater"
    private const val PREFS = "app_update"
    private const val WORK = "app_update_check"
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 710_001

    private val json = Json { ignoreUnknownKeys = true }

    /** GitHub's "latest release" endpoint; debug builds with a local test server point it there. */
    @Volatile
    var releaseApi: String = "https://api.github.com/repos/$REPO/releases/latest"

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** True when [candidate] is a higher version than [current] ("v0.10.0" > "0.9.1"). */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun parseRelease(body: String): AppRelease? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val apk = root["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true }
            ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ?: return null
        return AppRelease(
            version = tag.removePrefix("v"),
            apkUrl = apk,
            notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            page = root["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$REPO/releases",
        )
    }

    /** The latest release, or null if there is none or GitHub cannot be reached (also while the repo is private). */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(releaseApi)
                .header("Accept", "application/vnd.github+json")
                .build()
            Http.base.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "release check: HTTP ${response.code}")
                    null
                } else {
                    parseRelease(response.body?.string().orEmpty())
                }
            }
        }.onFailure { Log.d(TAG, "release check failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    suspend fun download(context: Context, release: AppRelease, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "YUntis-${release.version}.apk")

            Http.base.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            total?.let { onProgress(done.toFloat() / it) }
                        }
                    }
                }
            }
            target
        }.onFailure { Log.d(TAG, "download failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Hands the downloaded APK to the system installer, which asks the user once. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun autoCheck(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto", true)

    fun setAutoCheck(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto", on).apply()
        ensure(context)
    }

    fun lastCheck(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_check", 0L)

    fun markChecked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_check", System.currentTimeMillis()).apply()

    /** Daily background check, only with network. */
    fun ensure(context: Context) {
        val work = WorkManager.getInstance(context)
        if (!autoCheck(context)) {
            work.cancelUniqueWork(WORK)
            return
        }
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Notifies once per new version. */
    @SuppressLint("MissingPermission")
    fun notifyIfNew(context: Context, release: AppRelease) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("notified", null) == release.version) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Notifications.EXTRA_FOCUS_SCREEN, FOCUS_ABOUT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.update_available_title, release.version))
                .setContentText(context.getString(R.string.update_available_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        prefs.edit().putString("notified", release.version).apply()
    }
}

class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val release = AppUpdater.latest()
        AppUpdater.markChecked(context)
        if (release != null && AppUpdater.isNewer(release.version, AppUpdater.currentVersion(context))) {
            AppUpdater.notifyIfNew(context, release)
        }
        return Result.success()
    }
}
