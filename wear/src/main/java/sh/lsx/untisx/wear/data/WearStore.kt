package sh.lsx.untisx.wear.data

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.security.Crypto
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult
import sh.lsx.untisx.data.wear.WearPayloadCodec
import sh.lsx.untisx.data.wear.WearTimetablePayload
import java.io.File

object WearStore {

    private const val FILE_NAME = "wear_payload.enc"
    private const val TAG = "WearStore"

    private val state = MutableStateFlow<WearTimetablePayload?>(null)
    val payload: StateFlow<WearTimetablePayload?> = state.asStateFlow()

    @Volatile
    private var cryptor: Cryptor = Crypto

    @Synchronized
    fun load(context: Context): WearTimetablePayload? {
        val text = AtomicJsonFile.read(file(context)) ?: return state.value

        when (val result = cryptor.decrypt(text)) {
            is DecryptResult.Value -> state.value = WearPayloadCodec.decodeJson(result.plaintext)

            DecryptResult.KeyLost -> {
                Log.d(TAG, "payload key lost, clearing")
                AtomicJsonFile.delete(file(context))
                state.value = null
            }

            DecryptResult.Missing -> Unit
            DecryptResult.Transient -> Log.d(TAG, "payload decrypt failed")
        }

        return state.value
    }

    @Synchronized
    fun save(context: Context, payload: WearTimetablePayload): Boolean {
        if (payload.version != WearTimetablePayload.VERSION) return false

        val previous = load(context)
        if (previous != null && previous.sentAt > 0L && payload.sentAt <= previous.sentAt) return false

        val retained = if (previous?.accountId == payload.accountId && payload.accountId.isNotBlank()) {
            (payload.weeks.keys + payload.weekFetchedAt.keys).filter { key ->
                (previous.weekFetchedAt[key] ?: 0L) > (payload.weekFetchedAt[key] ?: 0L)
            }.toSet()
        } else emptySet()

        val retainedDates = retained.flatMap { key ->
            key.toIntOrNull()?.toUntisLocalDateOrNull()?.let { monday ->
                (0..6).map { monday.plusDays(it.toLong()).toUntisDate() }
            }.orEmpty()
        }.toSet()

        val accepted = if (retained.isEmpty()) payload else payload.copy(
            weeks = payload.weeks + previous!!.weeks.filterKeys { it in retained },
            weekFetchedAt = payload.weekFetchedAt + previous.weekFetchedAt.filterKeys { it in retained },
            fetchedAt = maxOf(payload.fetchedAt, previous.fetchedAt),
            restrictedDays = (payload.restrictedDays - retainedDates) +
                previous.restrictedDays.filter { it in retainedDates },
        )

        val saved = runCatching {
            AtomicJsonFile.write(file(context), cryptor.encrypt(WearPayloadCodec.encodeJson(accepted)))
        }.getOrDefault(false)

        if (!saved) {
            Log.d(TAG, "payload save failed")
            return false
        }

        state.value = accepted
        return true
    }

    @Synchronized
    fun clear(context: Context) {
        AtomicJsonFile.delete(file(context))
        state.value = null
    }

    @VisibleForTesting
    fun setCryptor(cryptor: Cryptor) {
        this.cryptor = cryptor
    }

    @VisibleForTesting
    fun reset() {
        state.value = null
        cryptor = Crypto
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
