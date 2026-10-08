package sh.lsx.untisx

import android.app.Application
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.api.LoginRequiredNotifier
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.net.Http
import sh.lsx.untisx.data.notifications.Notifications

class UntisXApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AtomicJsonFile.cleanupTempFiles(filesDir)

        Http.userAgent = buildUserAgent()

        SessionManager.loginRequiredNotifier = LoginRequiredNotifier {
            Notifications.notifyLoginRequired(it)
        }
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
