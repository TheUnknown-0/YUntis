package sh.lsx.untisx.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.wear.WearRefreshResult
import sh.lsx.untisx.data.wear.WearTimetablePayload
import sh.lsx.untisx.wear.data.WearStore
import sh.lsx.untisx.wear.data.WearSyncClient
import java.time.LocalDate
import java.util.UUID

sealed interface WearUiState {
    data object Loading : WearUiState
    data object NoData : WearUiState
    data class Content(val payload: WearTimetablePayload) : WearUiState
}

enum class WearSyncState { IDLE, WAITING, DELAYED, CACHE, UPDATED, FAILED }

class WearViewModel internal constructor(
    app: Application,
    private val payloads: StateFlow<WearTimetablePayload?>,
    private val load: suspend () -> WearTimetablePayload?,
    private val request: suspend (String) -> Boolean,
    private val now: () -> Long,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(
        app, WearStore.payload,
        { withContext(Dispatchers.IO) { WearStore.load(app) } },
        { WearSyncClient.requestSync(app, it) }, System::currentTimeMillis,
    )

    private val _state = MutableStateFlow<WearUiState>(WearUiState.Loading)
    val state = _state.asStateFlow()
    private val _syncState = MutableStateFlow(WearSyncState.IDLE)
    val syncState = _syncState.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _today: MutableStateFlow<LocalDate> = MutableStateFlow(AppClock.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()
    private var pendingId: String? = null
    private var latestRequestId: String? = null
    private var lastAttempt: Long? = null
    private var loaded = false
    private var foreground = false

    init {
        viewModelScope.launch {
            show(load())
            loaded = true
            launch { payloads.collect { show(it) } }
            if (foreground) onForegroundTick()
        }
    }

    private fun show(payload: WearTimetablePayload?) {
        _state.value = payload?.takeIf { it.accountId.isNotBlank() }
            ?.let { WearUiState.Content(it) } ?: WearUiState.NoData

        if (payload != null && latestRequestId != null && payload.requestId == latestRequestId) {
            _syncState.value = when (payload.refreshResult) {
                WearRefreshResult.CACHE -> WearSyncState.CACHE
                WearRefreshResult.UPDATED -> WearSyncState.UPDATED
                WearRefreshResult.FAILED -> WearSyncState.FAILED
            }

            pendingId = null
        }
    }

    fun onForegroundTick() {
        foreground = true
        _today.value = AppClock.today()

        if (!loaded || _refreshing.value) return

        val time = now()
        val elapsed = lastAttempt?.let { time - it }
        if (elapsed != null && elapsed in 0 until if (pendingId == null) 30_000L else FRESH_WINDOW_MS) return

        val payload = (_state.value as? WearUiState.Content)?.payload
        if (!isFresh(payload, _today.value, time)) refresh()
    }

    fun onBackground() { foreground = false }

    fun refresh() {
        if (_refreshing.value) return

        _refreshing.value = true
        _syncState.value = WearSyncState.WAITING

        val id = UUID.randomUUID().toString()
        pendingId = id
        latestRequestId = id
        lastAttempt = now()

        viewModelScope.launch {
            try {
                if (!request(id)) {
                    pendingId = null
                    _syncState.value = WearSyncState.FAILED
                    return@launch
                }

                val response = withTimeoutOrNull(120_000L) {
                    payloads.filterNotNull().first { it.requestId == id }
                }

                if (response != null) show(response)
                else if (pendingId == id) _syncState.value = WearSyncState.DELAYED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                pendingId = null
                _syncState.value = WearSyncState.FAILED
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        const val FRESH_WINDOW_MS = 10 * 60 * 1000L

        internal fun isFresh(payload: WearTimetablePayload?, today: LocalDate, now: Long): Boolean {
            if (payload == null || payload.accountId.isBlank()) return false

            val monday = today.toUntisWeekStart()
            return listOf(monday, monday.plusWeeks(1)).all {
                val key = it.toUntisDate().toString()
                key in payload.weeks && now - (payload.weekFetchedAt[key] ?: 0L) in 0..FRESH_WINDOW_MS
            }
        }
    }
}
