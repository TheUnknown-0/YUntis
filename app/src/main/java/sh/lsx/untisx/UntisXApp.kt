package sh.lsx.untisx

import android.app.Application

class UntisXApp : Application() {

    companion object {
        const val PREFS_NAME = "untisx_prefs"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
