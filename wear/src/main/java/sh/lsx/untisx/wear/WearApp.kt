package sh.lsx.untisx.wear

import android.app.Application
import sh.lsx.untisx.data.AtomicJsonFile

class WearApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AtomicJsonFile.cleanupTempFiles(filesDir)
    }
}
