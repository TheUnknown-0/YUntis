package sh.lsx.untisx.data.wear

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.ExamsStore
import java.security.MessageDigest
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import java.util.concurrent.TimeUnit

object WearSync {

    const val FRESH_WINDOW_MS = 10 * 60 * 1000L
    private const val WORK_NAME = "wear_sync_refresh"
    private const val TAG = "WearSync"
    private val pushLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: Job? = null

    @Synchronized
    fun observeAccounts(context: Context) {
        if (observer?.isActive == true) return
        observer = scope.launch {
            AccountStore.ensureLoaded(context)
            AccountStore.activeId.collect {
                val auth = AccountStore.active
                val data = auth?.let { TimetableCache.load(context, AccountPrefs.accountId(it)) }
                push(context, data ?: TimetableCache.Data(), auth)
            }
        }
    }

    suspend fun push(
        context: Context,
        data: TimetableCache.Data,
        auth: AuthData?,
        requestId: String = "",
        refreshResult: WearRefreshResult = WearRefreshResult.CACHE,
    ): Boolean =
        withContext(Dispatchers.IO) {
            pushLock.withLock {
                try {
                    val accountId = auth?.let(AccountPrefs::accountId)
                    if (AccountStore.activeId.value != accountId || AccountStore.error.value != null) {
                        return@withLock false
                    }

                    val prefs = context.getSharedPreferences("wear_sync", Context.MODE_PRIVATE)
                    val sentAt = maxOf(System.currentTimeMillis(), prefs.getLong("sent_at", 0L) + 1)
                    val encoded = WearPayloadBuilder.buildFitting(
                        data = data,
                        accountId = accountId.orEmpty(),
                        displayName = auth?.displayName.orEmpty(),
                        server = auth?.server.orEmpty(),
                        subjectColors = if (auth == null) emptyMap() else SubjectColorsPrefs.getColors(context),
                        subjectAliases = if (auth == null) emptyMap() else SubjectColorsPrefs.getAliases(context),
                        defaultColor = SubjectColorsPrefs.getDefaultColor(context),
                        sentAt = sentAt,
                        exams = accountId?.let { ExamsStore.load(context, it)?.exams }.orEmpty(),
                        requestId = requestId,
                        refreshResult = refreshResult,
                    )

                    val payload = encoded.payload
                    val digest = MessageDigest.getInstance("SHA-256").digest(
                        WearPayloadCodec.encode(payload.copy(sentAt = 0L)),
                    ).joinToString("") { "%02x".format(it) }
                    if (requestId.isEmpty() && prefs.getString("digest", null) == digest) return@withLock true
                    if (AccountStore.activeId.value != accountId) return@withLock false

                    val request = PutDataMapRequest.create(WearDataPaths.PAYLOAD)
                        .apply { dataMap.putByteArray(WearDataPaths.KEY_PAYLOAD, encoded.bytes) }
                        .asPutDataRequest()

                    if (requestId.isNotEmpty() || accountId.orEmpty() != prefs.getString("account", null)) request.setUrgent()

                    Tasks.await(Wearable.getDataClient(context).putDataItem(request), 30, TimeUnit.SECONDS)
                    prefs.edit().putLong("sent_at", sentAt).putString("digest", digest).putString("account", accountId.orEmpty()).commit()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "push failed: ${e.javaClass.simpleName}")
                    false
                }
            }
        }

    fun enqueueRefresh(context: Context, requestId: String = "") {
        val request = OneTimeWorkRequestBuilder<WearSyncWorker>()
            .setInputData(workDataOf("request_id" to requestId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
