package sh.lsx.untisx.data.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearSyncService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearDataPaths.REQUEST_SYNC) return
        if (event.data.size > 64) return

        WearSync.enqueueRefresh(applicationContext, event.data.toString(Charsets.UTF_8))
    }
}
