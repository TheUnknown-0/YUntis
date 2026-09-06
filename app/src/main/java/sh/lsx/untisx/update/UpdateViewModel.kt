package sh.lsx.untisx.update

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

        check()
    }

    fun check() {
        if (state is UpdateState.Downloading || state is UpdateState.Checking) return

        state = UpdateState.Checking

        viewModelScope.launch {
            try {
                val info = UpdateClient.check(getApplication())

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
