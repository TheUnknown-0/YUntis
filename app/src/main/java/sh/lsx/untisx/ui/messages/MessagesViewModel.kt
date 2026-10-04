package sh.lsx.untisx.ui.messages

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.MessagesStore
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.ui.util.UiText

class MessagesViewModel(app: Application) : AndroidViewModel(app) {

    var incoming by mutableStateOf<List<DirectMessage>>(emptyList())
        private set
    var readConfirmations by mutableStateOf<List<DirectMessage>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var detail by mutableStateOf<DirectMessage?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set

    private var detailRequestId = 0

    val messageCount: Int
        get() = incoming.size + readConfirmations.size

    init {
        viewModelScope.launch {
            AccountStore.ensureLoaded(getApplication())

            val account = AccountPrefs.getActive() ?: return@launch

            val cached = withContext(Dispatchers.IO) {
                MessagesStore.load(getApplication(), AccountPrefs.accountId(account))
            }

            if (cached != null) {
                incoming = cached.incoming
                readConfirmations = cached.readConfirmations
            }

            refresh()
        }
    }

    fun refresh() {
        if (loading) return

        loading = true
        error = null

        viewModelScope.launch {
            try {
                AccountStore.ensureLoaded(getApplication())

                val account = AccountPrefs.getActive()
                    ?: throw UntisException.BadCredentials

                val password = withContext(Dispatchers.IO) {
                    PasswordVault.retrieve(getApplication(), AccountPrefs.accountId(account))
                } ?: throw UntisException.BadCredentials

                val data = UntisClient.fetchMessages(account, password)
                    ?: throw IllegalStateException("Messages endpoint returned no usable response.")

                incoming = data.incoming
                readConfirmations = data.readConfirmations

                withContext(Dispatchers.IO) {
                    MessagesStore.save(
                        getApplication(),
                        AccountPrefs.accountId(account),
                        MessagesStore.Data(
                            incoming = data.incoming,
                            readConfirmations = data.readConfirmations,
                            fetchedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "messages load failed: ${e.javaClass.simpleName}")
                error = UiText.Res(R.string.error_messages_load)
            } finally {
                loading = false
            }
        }
    }

    fun open(message: DirectMessage) {
        val requestId = ++detailRequestId

        detail = message
        detailLoading = message.id > 0

        if (message.id <= 0) return

        viewModelScope.launch {
            try {
                AccountStore.ensureLoaded(getApplication())

                val account = AccountPrefs.getActive() ?: return@launch

                val password = withContext(Dispatchers.IO) {
                    PasswordVault.retrieve(getApplication(), AccountPrefs.accountId(account))
                } ?: return@launch

                val full = UntisClient.fetchMessage(account, password, message.id)

                if (requestId != detailRequestId) return@launch

                if (full != null) {
                    detail = full
                    incoming = incoming.map { if (it.id == full.id) full else it }
                    readConfirmations = readConfirmations.map { if (it.id == full.id) full else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "message detail failed: ${e.javaClass.simpleName}")
            } finally {
                if (requestId == detailRequestId) {
                    detailLoading = false
                }
            }
        }
    }

    fun clearError() {
        error = null
    }

    companion object {
        private const val TAG = "MessagesViewModel"
    }
}
