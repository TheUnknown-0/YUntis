package sh.lsx.untisx

import android.app.Application
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.api.LoginRequiredNotifier
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.notifications.Notifications

class UntisXApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AtomicJsonFile.cleanupTempFiles(filesDir)

        SessionManager.loginRequiredNotifier = LoginRequiredNotifier {
            Notifications.notifyLoginRequired(it)
        }
    }

    companion object {
        const val PREFS_NAME = CorePrefs.PREFS_NAME
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
