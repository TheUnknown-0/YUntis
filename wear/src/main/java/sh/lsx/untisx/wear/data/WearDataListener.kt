package sh.lsx.untisx.wear.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import sh.lsx.untisx.data.wear.WearDataPaths
import sh.lsx.untisx.data.wear.WearPayloadCodec

class WearDataListener : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue

            val item = event.dataItem
            if (item.uri.path != WearDataPaths.PAYLOAD) continue

            val bytes = DataMapItem.fromDataItem(item)
                .dataMap
                .getByteArray(WearDataPaths.KEY_PAYLOAD)
                ?: continue

            val payload = WearPayloadCodec.decode(bytes) ?: continue

            WearStore.save(applicationContext, payload)
        }
    }
}
