package sh.lsx.untisx.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notebook.NotebookReadFailure
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.prefs.AccountRemovalFailure
import sh.lsx.untisx.data.prefs.AccountRemovalResult

data class NotebookAccountDeletionSnapshot(
    val notebookCount: Int = 0,
    val notebookReadFailure: NotebookReadFailure? = null,
) {
    val readFailure: NotebookReadFailure? get() = notebookReadFailure
}

data class NotebookAccountDeletionState(
    val notebookCount: Int = 0,
    val notebookReadFailure: NotebookReadFailure? = null,
    val exported: Boolean = false,
    val allowDeleteWithoutExport: Boolean = false,
) {
    val readFailure: NotebookReadFailure? get() = notebookReadFailure

    val hasContent: Boolean get() = notebookCount > 0

    val requiresWarning: Boolean get() = hasContent || readFailure != null

    val exportAvailable: Boolean get() = notebookReadFailure == null

    val exportCoversAllData: Boolean
        get() = exported &&
            notebookReadFailure == null

    val canDelete: Boolean
        get() = !requiresWarning || exportCoversAllData || allowDeleteWithoutExport
}

internal enum class NotebookAccountRemovalOutcome {
    REMOVED,
    CLEANUP_PENDING,
    RETAINED,
    INDETERMINATE,
}

internal fun notebookAccountRemovalOutcome(result: AccountRemovalResult): NotebookAccountRemovalOutcome =
    when (result) {
        AccountRemovalResult.Removed -> NotebookAccountRemovalOutcome.REMOVED
        is AccountRemovalResult.RemovedCleanupPending -> NotebookAccountRemovalOutcome.CLEANUP_PENDING
        is AccountRemovalResult.Retained -> NotebookAccountRemovalOutcome.RETAINED
        is AccountRemovalResult.Indeterminate -> NotebookAccountRemovalOutcome.INDETERMINATE
    }

internal fun notebookRetainedMessage(failure: AccountRemovalFailure): Int = when (failure) {
    AccountRemovalFailure.ACCOUNTS_UNAVAILABLE ->
        R.string.notebook_delete_retained_accounts_unavailable

    AccountRemovalFailure.DELETION_MARKER_FAILED ->
        R.string.notebook_delete_retained_marker_failed

    AccountRemovalFailure.ACCOUNT_PERSISTENCE_FAILED ->
        R.string.notebook_delete_retained_persistence_failed
}

suspend fun inspectNotebookAccountDeletion(
    context: Context,
    accountId: String,
): NotebookAccountDeletionSnapshot {
    val notebook = NotebookRepository(context, accountId).load()

    val notebookCount = when (notebook) {
        is NotebookReadResult.Loaded -> notebook.data.items.size
        else -> 0
    }
    val notebookFailure = (notebook as? NotebookReadResult.Failed)?.reason

    return NotebookAccountDeletionSnapshot(
        notebookCount = notebookCount,
        notebookReadFailure = notebookFailure,
    )
}

@Composable
fun NotebookAccountDeletionDialog(
    state: NotebookAccountDeletionState,
    exporting: Boolean,
    deleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onToggleDeleteWithoutExport: (Boolean) -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val acknowledgement = if (state.readFailure != null) {
        stringResource(R.string.notebook_delete_ack_unreadable)
    } else {
        stringResource(R.string.notebook_delete_without_export)
    }

    AlertDialog(
        onDismissRequest = { if (!deleting && !exporting) onDismiss() },
        title = { Text(stringResource(R.string.notebook_delete_title)) },
        text = {
            Column {
                if (state.hasContent) {
                    Text(
                        stringResource(
                            R.string.notebook_delete_message_counts,
                            state.notebookCount,
                        ),
                    )

                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(R.string.notebook_delete_device_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.readFailure != null) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.notebook_delete_unreadable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))

                when {
                    !state.exportAvailable -> Text(
                        text = stringResource(R.string.notebook_delete_export_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    state.exported -> Text(
                        text = stringResource(R.string.notebook_delete_export_success),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    else -> TextButton(
                        onClick = onExport,
                        enabled = !exporting && !deleting,
                    ) {
                        Text(stringResource(R.string.notebook_delete_export))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = state.allowDeleteWithoutExport,
                        onCheckedChange = onToggleDeleteWithoutExport,
                        enabled = !deleting && !exporting,
                    )

                    Text(
                        text = acknowledgement,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                enabled = state.canDelete && !deleting && !exporting,
            ) {
                Text(stringResource(R.string.notebook_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !deleting && !exporting,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun NotebookExportWarningDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_delete_export_warning_title)) },
        text = { Text(stringResource(R.string.notebook_delete_export_warning_text)) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.notebook_delete_export_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun NotebookAccountDeletionLoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_delete_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.notebook_delete_loading))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
