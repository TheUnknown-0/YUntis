package sh.lsx.untisx.ui.screens

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.notebook.NotebookExport
import sh.lsx.untisx.data.notebook.NotebookExportCodec
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.data.notifications.NotebookReminderScheduler
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountRemovalResult
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.AuthPrefs
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.personTypeName
import sh.lsx.untisx.ui.util.uiTextString

private sealed interface RemovalInspection {
    data object Loading : RemovalInspection

    data class Ready(val snapshot: NotebookAccountDeletionSnapshot) : RemovalInspection

    data object Failed : RemovalInspection
}

private suspend fun notebookExportText(context: Context, accountId: String): String? =
    withContext(Dispatchers.IO) {
        val result = NotebookRepository(context, accountId).load()
        val items = when (result) {
            is NotebookReadResult.Loaded -> result.data.items
            NotebookReadResult.Missing -> emptyList()
            else -> return@withContext null
        }

        val export = NotebookExport(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion(context),
            items = items,
        )
        val text = NotebookExportCodec.encode(export)

        text.takeIf {
            it.toByteArray(Charsets.UTF_8).size <= NotebookExportCodec.MAX_BYTES
        }
    }

private fun indeterminateRemovalMessage(resources: Resources, accountId: String): String {
    val stillListed = AccountStore.snapshot.any {
        AccountPrefs.accountId(it) == accountId
    }

    return resources.getString(
        if (stillListed) {
            R.string.notebook_delete_indeterminate_listed
        } else {
            R.string.notebook_delete_indeterminate_not_listed
        },
    )
}

private fun exportFileName(account: AuthData): String {
    val safeUser = account.user
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(40)
        .ifBlank { "account" }

    return "untisx-notebook-${safeUser}.json"
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty()

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val scope = rememberCoroutineScope()

    val accounts by AccountStore.accounts.collectAsStateWithLifecycle()
    val activeId by AccountStore.activeId.collectAsStateWithLifecycle()

    var adding by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<AuthData?>(null) }
    var pendingIsLogout by remember { mutableStateOf(false) }
    var removalInspection by remember { mutableStateOf<RemovalInspection?>(null) }
    var removalState by remember { mutableStateOf<NotebookAccountDeletionState?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removalError by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var removalNotice by remember { mutableStateOf<String?>(null) }
    var showExportWarning by remember { mutableStateOf(false) }
    var pendingExportAccountId by remember { mutableStateOf<String?>(null) }
    var removalGeneration by remember { mutableStateOf(0) }

    fun resetRemoval() {
        removalGeneration++
        pendingRemoval = null
        pendingIsLogout = false
        removalInspection = null
        removalState = null
        exporting = false
        deleting = false
        removalError = null
        exportError = null
        removalNotice = null
        showExportWarning = false
        pendingExportAccountId = null
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val accountId = pendingExportAccountId
        pendingExportAccountId = null

        if (uri == null || accountId == null) {
            exporting = false
        } else {
            scope.launch {
                val text = try {
                    notebookExportText(context, accountId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

                val success = if (text == null) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                                stream.write(text.toByteArray(Charsets.UTF_8))
                            } ?: error("no output stream")
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        }
                    }
                }

                if (success) {
                    removalState = removalState?.copy(exported = true)
                    exportError = null
                } else {
                    exportError = resources.getString(R.string.notebook_delete_export_failed)
                }

                exporting = false
            }
        }
    }

    suspend fun removeAccountFully(account: AuthData) {
        if (deleting) return

        deleting = true
        removalError = null

        val id = AccountPrefs.accountId(account)

        try {
            try {
                UntisClient.logout(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { }

            UntisClient.clearSessionCache(id)

            NotebookReminderScheduler.cancelAccount(context, id)

            AccountStore.removeAccountTransactional(context, id)
                .let { result ->
                    when (notebookAccountRemovalOutcome(result)) {
                        NotebookAccountRemovalOutcome.REMOVED -> {
                            AuthPrefs.clear(context)
                            NotificationScheduler.onAccountRemoved(context, id)
                            resetRemoval()
                        }

                        NotebookAccountRemovalOutcome.CLEANUP_PENDING -> {
                            AuthPrefs.clear(context)
                            NotificationScheduler.onAccountRemoved(context, id)
                            resetRemoval()
                            removalNotice = resources.getString(
                                R.string.notebook_delete_cleanup_pending,
                            )
                        }

                        NotebookAccountRemovalOutcome.RETAINED -> {
                            NotebookReminderScheduler.ensureAll(context)
                            val failure = (result as AccountRemovalResult.Retained).failure
                            removalError = resources.getString(notebookRetainedMessage(failure))
                        }

                        NotebookAccountRemovalOutcome.INDETERMINATE -> {
                            NotebookReminderScheduler.ensureAll(context)
                            resetRemoval()
                            removalNotice = indeterminateRemovalMessage(resources, id)
                        }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NotebookReminderScheduler.ensureAll(context)
            resetRemoval()
            removalNotice = indeterminateRemovalMessage(resources, id)
        } finally {
            deleting = false
        }
    }

    fun beginRemoval(account: AuthData, logout: Boolean) {
        val generation = ++removalGeneration

        pendingRemoval = account
        pendingIsLogout = logout
        removalInspection = RemovalInspection.Loading
        removalState = null
        exporting = false
        deleting = false
        removalError = null
        exportError = null
        removalNotice = null
        showExportWarning = false
        pendingExportAccountId = null

        val id = AccountPrefs.accountId(account)

        scope.launch {
            val snapshot = try {
                inspectNotebookAccountDeletion(context, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (generation != removalGeneration) return@launch

            if (snapshot == null) {
                removalInspection = RemovalInspection.Failed
                return@launch
            }

            removalInspection = RemovalInspection.Ready(snapshot)
            removalState = NotebookAccountDeletionState(
                notebookCount = snapshot.notebookCount,
                notebookReadFailure = snapshot.notebookReadFailure,
            )

            val noWarning = snapshot.readFailure == null &&
                snapshot.notebookCount == 0

            if (logout && noWarning) {
                removeAccountFully(account)
            }
        }
    }

    fun requestExport() {
        if (removalState?.exportAvailable != true) return
        showExportWarning = true
    }

    fun continueExport() {
        val account = pendingRemoval ?: return

        showExportWarning = false
        exporting = true
        exportError = null
        pendingExportAccountId = AccountPrefs.accountId(account)
        exportLauncher.launch(exportFileName(account))
    }

    fun selectAccount(id: String) {
        scope.launch { AccountStore.setActive(context, id) }
    }

    if (adding) {
        LoginScreen(
            modifier = modifier,
            onDone = { adding = false },
        )

        return
    }

    val active = accounts.firstOrNull { AccountPrefs.accountId(it) == activeId }
        ?: accounts.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileCard(stringResource(R.string.profile_signed_in_title)) {
            if (active == null) {
                Text(
                    text = stringResource(R.string.profile_not_signed_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ProfileRow(stringResource(R.string.profile_user), active.user)
                ProfileRow(stringResource(R.string.profile_school), active.displayName)
                ProfileRow(stringResource(R.string.profile_server), active.server)
                ProfileRow(
                    stringResource(R.string.profile_person),
                    uiTextString(personTypeName(active.session.personType)),
                )

                Spacer(Modifier.height(12.dp))

                Button(onClick = Haptics.haptic {
                    active?.let { beginRemoval(it, logout = true) }
                }) {
                    Text(stringResource(R.string.profile_logout))
                }
            }
        }

        ProfileCard(stringResource(R.string.profile_accounts_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                accounts.forEach { account ->
                    val id = AccountPrefs.accountId(account)
                    val isActive = id == activeId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = Haptics.haptic {
                                if (!isActive) {
                                    selectAccount(id)
                                }
                            })
                            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isActive, onClick = null)

                        Spacer(Modifier.size(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.user,
                                style = MaterialTheme.typography.bodyLarge,
                            )

                            Text(
                                text = account.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(onClick = Haptics.haptic {
                            beginRemoval(account, logout = false)
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.profile_remove_account),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = Haptics.haptic { adding = true }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(Modifier.size(6.dp))

                Text(stringResource(R.string.profile_add_account))
            }
        }
    }

    when (val inspection = removalInspection) {
        RemovalInspection.Loading -> NotebookAccountDeletionLoadingDialog(
            onDismiss = { resetRemoval() },
        )

        RemovalInspection.Failed -> AlertDialog(
            onDismissRequest = { resetRemoval() },
            title = { Text(stringResource(R.string.notebook_delete_inspect_failed_title)) },
            text = { Text(stringResource(R.string.notebook_delete_inspect_failed_text)) },
            confirmButton = {
                TextButton(onClick = { resetRemoval() }) {
                    Text(stringResource(R.string.close))
                }
            },
        )

        is RemovalInspection.Ready -> {
            val account = pendingRemoval
            val state = removalState

            if (account != null && state != null) {
                if (state.requiresWarning) {
                    NotebookAccountDeletionDialog(
                        state = state,
                        exporting = exporting,
                        deleting = deleting,
                        error = removalError ?: exportError,
                        onDismiss = { resetRemoval() },
                        onExport = { requestExport() },
                        onToggleDeleteWithoutExport = { checked ->
                            removalState = state.copy(allowDeleteWithoutExport = checked)
                        },
                        onConfirmDelete = {
                            scope.launch { removeAccountFully(account) }
                        },
                    )
                } else if (!pendingIsLogout) {
                    AlertDialog(
                        onDismissRequest = { resetRemoval() },
                        title = { Text(stringResource(R.string.profile_remove_dialog_title)) },
                        text = {
                            Column {
                                Text(
                                    stringResource(
                                        R.string.profile_remove_dialog_text,
                                        account.user,
                                        account.displayName,
                                    ),
                                )

                                removalError?.let {
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
                            TextButton(onClick = Haptics.haptic {
                                scope.launch { removeAccountFully(account) }
                            }) {
                                Text(stringResource(R.string.remove))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { resetRemoval() }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }
            }
        }

        null -> Unit
    }

    if (showExportWarning) {
        NotebookExportWarningDialog(
            onDismiss = { showExportWarning = false },
            onContinue = { continueExport() },
        )
    }

    val logoutError = removalError
    if (logoutError != null && pendingIsLogout && removalState?.requiresWarning != true) {
        AlertDialog(
            onDismissRequest = { resetRemoval() },
            title = { Text(stringResource(R.string.notebook_delete_notice_title)) },
            text = { Text(logoutError) },
            confirmButton = {
                TextButton(onClick = { resetRemoval() }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    removalNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { removalNotice = null },
            title = { Text(stringResource(R.string.notebook_delete_notice_title)) },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { removalNotice = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    title: String,
    content: @Composable () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )

        Text(
             text = value,
             style = MaterialTheme.typography.bodyMedium,
             modifier = Modifier.weight(0.6f),
         )
     }
 }
