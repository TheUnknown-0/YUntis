package sh.lsx.untisx

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File
import sh.lsx.untisx.data.api.UntisClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.api.LoginRequiredNotifier
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.net.Http
import sh.lsx.untisx.data.net.SchoolRequestGuard
import sh.lsx.untisx.data.notifications.Notifications

class UntisXApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AtomicJsonFile.cleanupTempFiles(filesDir)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { AtomicJsonFile.migrateCaches(filesDir) }

        Http.userAgent = buildUserAgent()
        SchoolRequestGuard.initialize(this)
        enableLocalTestServerIfRequested()

        SessionManager.loginRequiredNotifier = LoginRequiredNotifier {
            Notifications.notifyLoginRequired(it)
        }
    }

    /**
     * Debug builds only: if the file "test-server.txt" exists in the app's private files
     * folder (written with "adb shell run-as"), school search and login go to the URL inside it (e.g. http://127.0.0.1:8765 together with "adb reverse tcp:8765 tcp:8765").
     * Used to test the timetable with invented data instead of a real school account.
     */
    private fun enableLocalTestServerIfRequested() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

        val url = runCatching {
            File(filesDir, "test-server.txt").takeIf { it.isFile }?.readText()?.trim()
        }.getOrNull()

        if (url.isNullOrBlank()) return

        UntisClient.useLocalTestServer(url)
        Log.w("UntisXApp", "Using local test server $url")
    }

    private fun buildUserAgent(): String {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()

        return if (version.isNullOrBlank()) {
            "UntisX (+https://codeberg.org/Losterixx/UntisX)"
        } else {
            "UntisX/$version (+https://codeberg.org/Losterixx/UntisX)"
        }
    }

    companion object {
        const val PREFS_NAME = CorePrefs.PREFS_NAME
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
