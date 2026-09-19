package sh.lsx.untisx.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.LanguagePrefs
import sh.lsx.untisx.data.SettingsBackupManager
import sh.lsx.untisx.data.SettingsImportResult
import sh.lsx.untisx.ui.components.LanguageSettingsContent
import sh.lsx.untisx.ui.components.SettingsActionRow
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import java.time.LocalDate

@Composable
fun GeneralSettingsScreen(
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(LanguagePrefs.get(context)) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val success = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(SettingsBackupManager.export(context).toByteArray(Charsets.UTF_8))
            } ?: error("Could not open output stream")
        }.isSuccess

        Toast.makeText(
            context,
            if (success) R.string.settings_backup_export_success else R.string.settings_backup_export_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard(title = stringResource(R.string.settings_language)) {
            LanguageSettingsContent(
                language = language,
                onChange = { newLanguage ->
                    language = newLanguage
                    LanguagePrefs.save(context, newLanguage)
                },
            )
        }

        SettingsSectionCard {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_haptics),
                checked = hapticsEnabled,
                onCheckedChange = onHapticsChange,
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.settings_backup),
            subtitle = stringResource(R.string.settings_backup_desc),
        ) {
            SettingsActionRow(
                title = stringResource(R.string.settings_backup_export),
                description = stringResource(R.string.settings_backup_export_desc),
                onClick = { exportLauncher.launch(defaultBackupFileName()) },
            )

            SettingsActionRow(
                title = stringResource(R.string.settings_backup_import),
                description = stringResource(R.string.settings_backup_import_desc),
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val result = runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        }.getOrNull()?.let { SettingsBackupManager.import(context, it) }
                            ?: SettingsImportResult.InvalidFile

                        Toast.makeText(context, importMessage(result), Toast.LENGTH_SHORT).show()
                        pendingImport = null
                    },
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun defaultBackupFileName(): String = "untisx-settings-${LocalDate.now()}.json"

@StringRes
private fun importMessage(result: SettingsImportResult): Int = when (result) {
    is SettingsImportResult.Success -> R.string.settings_backup_import_success
    is SettingsImportResult.UnsupportedVersion -> R.string.settings_backup_import_version
    else -> R.string.settings_backup_import_failed
}
