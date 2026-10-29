package sh.lsx.untisx.ui.notebook

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.ui.components.SettingsActionRow
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.data.notebook.NotebookExportCodec
import sh.lsx.untisx.data.notebook.NotebookImportChoice
import sh.lsx.untisx.data.notebook.NotebookImportConflict
import sh.lsx.untisx.data.notebook.NotebookImportConflictReason
import sh.lsx.untisx.data.notebook.NotebookImportPreview

sealed interface NotebookFileReadResult {
    data class Text(val text: String) : NotebookFileReadResult

    data object TooLarge : NotebookFileReadResult

    data object Failed : NotebookFileReadResult
}

@Composable
internal fun NotebookDataTransferRows(onExport: () -> Unit, onImport: () -> Unit) {
    SettingsSectionCard(
        title = stringResource(R.string.notebook_data_title),
        subtitle = stringResource(R.string.notebook_data_description),
    ) {
        SettingsActionRow(title = stringResource(R.string.notebook_export), onClick = onExport)
        SettingsActionRow(title = stringResource(R.string.notebook_import), onClick = onImport)
    }
}

fun readNotebookText(
    input: InputStream,
    maxBytes: Int = NotebookExportCodec.MAX_BYTES,
): NotebookFileReadResult {
    return try {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0

        while (true) {
            val read = input.read(buffer)

            if (read < 0) break

            total += read

            if (total > maxBytes) return NotebookFileReadResult.TooLarge

            output.write(buffer, 0, read)
        }

        NotebookFileReadResult.Text(output.toString(Charsets.UTF_8.name()))
    } catch (_: Exception) {
        NotebookFileReadResult.Failed
    }
}

private suspend fun readNotebookUri(context: Context, uri: Uri): NotebookFileReadResult =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { readNotebookText(it) }
                ?: NotebookFileReadResult.Failed
        }.getOrDefault(NotebookFileReadResult.Failed)
    }

private suspend fun writeNotebookUri(context: Context, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                true
            } ?: false
        }.getOrDefault(false)
    }

@Composable
internal fun NotebookExportImportAction(
    vm: NotebookViewModel,
    accountLabel: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val noticeText = notice?.let { notebookNoticeText(it) }
    LaunchedEffect(notice, noticeText) {
        if (noticeText != null) {
            Toast.makeText(context, noticeText, Toast.LENGTH_SHORT).show()
            vm.clearNotice()
        }
    }

    fun isCurrentAccount(): Boolean = vm.accountId.isNotBlank() &&
        AccountStore.active?.let { AccountPrefs.accountId(it) } == vm.accountId

    var showExportWarning by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val text = pendingExportText
        pendingExportText = null

        if (uri != null && text != null && isCurrentAccount()) {
            scope.launch {
                val written = writeNotebookUri(context, uri, text)

                vm.postNotice(
                    if (written) NotebookNotice.EXPORT_DONE else NotebookNotice.EXPORT_FAILED,
                )
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && isCurrentAccount()) {
            scope.launch {
                when (val read = readNotebookUri(context, uri)) {
                    is NotebookFileReadResult.Text -> if (isCurrentAccount()) vm.previewImport(read.text)
                    NotebookFileReadResult.TooLarge -> vm.postNotice(NotebookNotice.IMPORT_TOO_LARGE)
                    NotebookFileReadResult.Failed -> vm.postNotice(NotebookNotice.IMPORT_READ_FAILED)
                }
            }
        }
    }

    NotebookDataTransferRows(
                onExport = {
                    if (!isCurrentAccount()) return@NotebookDataTransferRows
                    if (vm.exportItemCount() == 0) {
                        vm.postNotice(NotebookNotice.EXPORT_EMPTY)
                    } else {
                        showExportWarning = true
                    }
                },
                onImport = {
                    if (!isCurrentAccount()) return@NotebookDataTransferRows
                    openDocument.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                },
    )

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text(stringResource(R.string.notebook_export_warning_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.notebook_export_warning_text))

                    Text(
                        text = stringResource(R.string.notebook_import_account, accountLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    Text(
                        text = stringResource(
                            R.string.notebook_export_count,
                            vm.exportItemCount(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isCurrentAccount()) return@TextButton
                        val text = vm.exportText()

                        when {
                            text == null -> {
                                showExportWarning = false
                                vm.postNotice(NotebookNotice.EXPORT_EMPTY)
                            }

                            text.toByteArray(Charsets.UTF_8).size > NotebookExportCodec.MAX_BYTES -> {
                                showExportWarning = false
                                vm.postNotice(NotebookNotice.EXPORT_TOO_LARGE)
                            }

                            else -> {
                                pendingExportText = text
                                showExportWarning = false
                                val name = "untisx-notebook-" +
                                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                                    ".json"
                                createDocument.launch(name)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.notebook_export_choose))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val importState by vm.importPreview.collectAsStateWithLifecycle()

    when (val state = importState) {
        is NotebookImportPreviewResult.Ready -> NotebookImportPreviewDialog(
            preview = state.preview,
            accountLabel = accountLabel,
            onApply = { choices -> if (isCurrentAccount()) vm.applyImport(state.preview, choices) },
            onDismiss = vm::clearImportPreview,
        )

        is NotebookImportPreviewResult.Invalid -> AlertDialog(
            onDismissRequest = vm::clearImportPreview,
            title = { Text(stringResource(R.string.notebook_import_invalid_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.notebook_import_invalid))

                    state.issues.take(4).forEach { issue ->
                        Text(
                            text = notebookValidationText(issue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::clearImportPreview) {
                    Text(stringResource(R.string.close))
                }
            },
        )

        NotebookImportPreviewResult.UnsupportedVersion -> AlertDialog(
            onDismissRequest = vm::clearImportPreview,
            title = { Text(stringResource(R.string.notebook_import_invalid_title)) },
            text = { Text(stringResource(R.string.notebook_import_unsupported)) },
            confirmButton = {
                TextButton(onClick = vm::clearImportPreview) {
                    Text(stringResource(R.string.close))
                }
            },
        )

        NotebookImportPreviewResult.TooLarge -> AlertDialog(
            onDismissRequest = vm::clearImportPreview,
            title = { Text(stringResource(R.string.notebook_import_invalid_title)) },
            text = { Text(stringResource(R.string.notebook_import_too_large)) },
            confirmButton = {
                TextButton(onClick = vm::clearImportPreview) {
                    Text(stringResource(R.string.close))
                }
            },
        )

        null -> Unit
    }
}

@Composable
private fun NotebookImportPreviewDialog(
    preview: NotebookImportPreview,
    accountLabel: String,
    onApply: (Map<String, NotebookImportChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(preview) { mutableStateMapOf<String, NotebookImportChoice>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_import_preview_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.notebook_import_account, accountLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = stringResource(
                        R.string.notebook_import_counts,
                        preview.newItems.size,
                        preview.identical.size,
                        preview.conflicts.size,
                        preview.invalid.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.notebook_import_choice_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (preview.conflicts.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = preview.conflicts,
                            key = { conflict -> conflict.incoming.id },
                            contentType = { "import-conflict" },
                        ) { conflict ->
                            NotebookImportConflictRow(
                                conflict = conflict,
                                choice = choices[conflict.incoming.id] ?: NotebookImportChoice.SKIP,
                                onChoice = { choice -> choices[conflict.incoming.id] = choice },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(choices.toMap()) }) {
                Text(stringResource(R.string.notebook_import_apply))
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
private fun NotebookImportConflictRow(
    conflict: NotebookImportConflict,
    choice: NotebookImportChoice,
    onChoice: (NotebookImportChoice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = conflict.incoming.displayTitle.ifBlank {
                stringResource(R.string.notebook_untitled)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = conflict.existing.displayTitle.ifBlank {
                stringResource(R.string.notebook_untitled)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = stringResource(
                when (conflict.reason) {
                    NotebookImportConflictReason.SAME_ID_DIFFERENT_CONTENT ->
                        R.string.notebook_import_conflict_reason_same_id

                    NotebookImportConflictReason.SAME_CONTENT_DIFFERENT_ID ->
                        R.string.notebook_import_conflict_reason_same_content

                    NotebookImportConflictReason.SAME_ID_OLDER ->
                        R.string.notebook_import_conflict_reason_older
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ImportChoiceChip(
                label = stringResource(R.string.notebook_import_choice_skip),
                selected = choice == NotebookImportChoice.SKIP,
                onClick = { onChoice(NotebookImportChoice.SKIP) },
            )

            if (conflict.reason != NotebookImportConflictReason.SAME_CONTENT_DIFFERENT_ID) {
                ImportChoiceChip(
                    label = stringResource(R.string.notebook_import_choice_replace),
                    selected = choice == NotebookImportChoice.REPLACE,
                    onClick = { onChoice(NotebookImportChoice.REPLACE) },
                )
            }

            ImportChoiceChip(
                label = stringResource(R.string.notebook_import_choice_keep_both),
                selected = choice == NotebookImportChoice.KEEP_BOTH,
                onClick = { onChoice(NotebookImportChoice.KEEP_BOTH) },
            )
        }
    }
}

@Composable
private fun ImportChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
