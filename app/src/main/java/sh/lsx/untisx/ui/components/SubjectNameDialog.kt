package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.ui.util.Haptics

@Composable
fun SubjectNameDialog(
    subject: String,
    alias: SubjectAlias?,
    onApply: (short: String?, long: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var shortText by remember(subject) { mutableStateOf(alias?.short.orEmpty()) }
    var longText by remember(subject) { mutableStateOf(alias?.long.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_subject_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shortText,
                    onValueChange = { shortText = it },
                    label = { Text(stringResource(R.string.settings_subject_short_name)) },
                    placeholder = { SubjectPlaceholder(subject) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = longText,
                    onValueChange = { longText = it },
                    label = { Text(stringResource(R.string.settings_subject_full_name)) },
                    placeholder = { SubjectPlaceholder(subject) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.settings_subject_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = Haptics.haptic {
                    onApply(
                        shortText.trim().takeIf { it.isNotBlank() },
                        longText.trim().takeIf { it.isNotBlank() },
                    )
                },
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = Haptics.haptic(onDismiss)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SubjectPlaceholder(subject: String) {
    Text(
        text = subject,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
