package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import sh.lsx.untisx.R
import sh.lsx.untisx.update.UpdateState
import sh.lsx.untisx.ui.util.Haptics

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Downloaded -> state.info
        else -> return
    }

    val forced = info.forced

    AlertDialog(
        onDismissRequest = { if (!forced) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !forced,
            dismissOnClickOutside = !forced,
        ),
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column {
                Text(
                    text = info.versionName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.update_changelog),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = info.changelog.ifBlank { stringResource(R.string.update_no_changelog) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                )

                Spacer(Modifier.height(16.dp))

                when (state) {
                    is UpdateState.Downloading -> {
                        Text(
                            text = stringResource(R.string.update_downloading, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is UpdateState.Downloaded -> {
                        Text(
                            text = stringResource(R.string.update_download_complete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> Button(onClick = Haptics.haptic(onDownload)) {
                    Text(stringResource(R.string.update_download))
                }

                is UpdateState.Downloaded -> Button(onClick = Haptics.haptic(onInstall)) {
                    Text(stringResource(R.string.install))
                }

                else -> {}
            }
        },
        dismissButton = {
            if (!forced) {
                TextButton(onClick = Haptics.haptic(onDismiss)) {
                    Text(
                        text = when (state) {
                            is UpdateState.Downloaded -> stringResource(R.string.update_later)
                            else -> stringResource(R.string.update_not_now)
                        },
                    )
                }
            }
        },
    )
}
