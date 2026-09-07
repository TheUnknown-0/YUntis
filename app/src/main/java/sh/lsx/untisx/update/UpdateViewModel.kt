package sh.lsx.untisx.update

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import sh.lsx.untisx.UntisXApp
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Downloaded(val info: UpdateInfo, val file: File) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val LAST_CHECK_KEY = "last_update_check"
        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var pendingInstall by mutableStateOf<UpdateState.Downloaded?>(null)
        private set
    var transientError by mutableStateOf<String?>(null)
        private set

    fun clearTransientError() {
        transientError = null
    }

    fun checkOnStartup() {
        if (state !is UpdateState.Idle) return

        val prefs = getApplication<Application>().getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK_KEY, 0) < CHECK_INTERVAL_MS) return

        check(recordTimestamp = true)
    }

    fun check(recordTimestamp: Boolean = false) {
        if (state is UpdateState.Downloading || state is UpdateState.Checking) return

        state = UpdateState.Checking

        viewModelScope.launch {
            try {
                val info = UpdateClient.check(getApplication())

                if (recordTimestamp) {
                    getApplication<Application>()
                        .getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit { putLong(LAST_CHECK_KEY, System.currentTimeMillis()) }
                }

                state = if (info != null) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                state = UpdateState.Idle
                transientError = "Update check failed: ${e.message}"
            }
        }
    }

    fun download() {
        val info = when (val current = state) {
            is UpdateState.Available -> current.info
            else -> return
        }

        state = UpdateState.Downloading(info, 0f)

        viewModelScope.launch {
            try {
                val file = UpdateClient.download(getApplication(), info) { progress ->
                    if (state is UpdateState.Downloading) {
                        state = UpdateState.Downloading(info, progress)
                    }
                }

                state = UpdateState.Downloaded(info, file)
            } catch (e: Exception) {
                state = UpdateState.Available(info)
                transientError = "Download failed: ${e.message}"
            }
        }
    }

    fun install() {
        val current = state as? UpdateState.Downloaded ?: return

        if (!UpdateClient.canRequestInstalls(getApplication())) {
            UpdateClient.openInstallPermissionSettings(getApplication())
            return
        }

        UpdateClient.install(getApplication(), current.file)
    }

    fun installPending() {
        val pending = pendingInstall ?: return

        if (!UpdateClient.canRequestInstalls(getApplication())) {
            UpdateClient.openInstallPermissionSettings(getApplication())
            return
        }

        UpdateClient.install(getApplication(), pending.file)
    }

    fun dismiss() {
        val current = state

        if (current is UpdateState.Downloaded) {
            pendingInstall = current
        }

        state = UpdateState.Idle
    }
}
