package sh.lsx.untisx

import android.app.Application
import sh.lsx.untisx.data.AtomicJsonFile

class UntisXApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AtomicJsonFile.cleanupTempFiles(filesDir)
    }

    companion object {
        const val PREFS_NAME = "untisx_prefs"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
