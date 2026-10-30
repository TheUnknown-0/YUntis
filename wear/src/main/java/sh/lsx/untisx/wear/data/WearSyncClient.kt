package sh.lsx.untisx.wear.data

import android.content.Context
import com.google.android.gms.wearable.Wearable
import sh.lsx.untisx.data.wear.WearDataPaths
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object WearSyncClient {

    suspend fun requestSync(context: Context, requestId: String): Boolean = withContext(Dispatchers.IO) {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 5, TimeUnit.SECONDS)
        var sent = false

        nodes.forEach { node ->
            try {
                Tasks.await(Wearable.getMessageClient(context)
                    .sendMessage(node.id, WearDataPaths.REQUEST_SYNC, requestId.toByteArray(Charsets.UTF_8)), 5, TimeUnit.SECONDS)
                sent = true
            } catch (_: java.util.concurrent.ExecutionException) { }
        }

        sent
    }
}
