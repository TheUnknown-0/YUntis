package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.data.update.AppRelease
import sh.lsx.untisx.data.update.AppUpdater
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import java.io.File

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NotFound : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data object Failed : UpdateState
}

/** Checks the GitHub releases for a newer YUntis and installs it on request. */
@Composable
internal fun UpdateCard() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    var auto by remember { mutableStateOf(AppUpdater.autoCheck(context)) }
    val current = remember { AppUpdater.currentVersion(context) }
    // the install permission is granted in the system settings; read it again when coming back
    var canInstall by remember { mutableStateOf(AppUpdater.canInstall(context)) }
    LifecycleResumeEffect(Unit) {
        canInstall = AppUpdater.canInstall(context)
        onPauseOrDispose { }
    }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            val release = AppUpdater.latest()
            AppUpdater.markChecked(context)
            state = when {
                release == null -> UpdateState.NotFound
                AppUpdater.isNewer(release.version, current) -> UpdateState.Available(release)
                else -> UpdateState.UpToDate
            }
        }
    }

    fun download(release: AppRelease) {
        progress = 0f
        state = UpdateState.Downloading(release)
        scope.launch {
            val file = AppUpdater.download(context, release) { progress = it }
            state = if (file == null) UpdateState.Failed else UpdateState.Ready(release, file)
        }
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)

            val message = when (val s = state) {
                UpdateState.Idle, UpdateState.Checking -> null
                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, current)
                UpdateState.NotFound -> stringResource(R.string.update_not_found)
                is UpdateState.Available -> stringResource(R.string.update_available_title, s.release.version)
                is UpdateState.Downloading -> stringResource(R.string.update_downloading, s.release.version)
                is UpdateState.Ready -> stringResource(R.string.update_ready, s.release.version)
                UpdateState.Failed -> stringResource(R.string.update_failed)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            (state as? UpdateState.Available)?.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                ReleaseNotes(notes, maxBlocks = 16)
            }

            if (state is UpdateState.Downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val s = state) {
                    is UpdateState.Available -> Button(onClick = { download(s.release) }) { Text(stringResource(R.string.update_download)) }
                    is UpdateState.Ready -> Button(onClick = {
                        if (canInstall) AppUpdater.install(context, s.file) else AppUpdater.openInstallPermission(context)
                    }) {
                        Text(stringResource(if (canInstall) R.string.update_install else R.string.update_allow_install))
                    }
                    is UpdateState.Downloading, UpdateState.Checking -> Unit
                    else -> Button(onClick = { check() }) { Text(stringResource(R.string.update_check)) }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { uriHandler.openUri("https://github.com/${AppUpdater.REPO}/releases") }) {
                    Text(stringResource(R.string.update_open_releases))
                }
            }

            SettingsSwitchRow(
                title = stringResource(R.string.update_auto),
                description = stringResource(R.string.update_auto_desc),
                checked = auto,
                onCheckedChange = {
                    auto = it
                    AppUpdater.setAutoCheck(context, it)
                },
            )
        }
    }
}
