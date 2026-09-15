package sh.lsx.untisx.ui.screens

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
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.AuthPrefs
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.personTypeName
import sh.lsx.untisx.ui.util.uiTextString

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf(AccountPrefs.loadAccounts(context)) }
    var activeId by remember { mutableStateOf(AccountPrefs.getActiveId(context)) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AuthData?>(null) }

    fun refresh() {
        accounts = AccountPrefs.loadAccounts(context)
        activeId = AccountPrefs.getActiveId(context)
    }

    fun removeAccountFully(account: AuthData) {
        scope.launch {
            val id = AccountPrefs.accountId(account)

            UntisClient.logout(account)
            TimetableCache.clear(context, id)
            AccountPrefs.removeAccount(context, id)
            AuthPrefs.clear(context)
            NotificationScheduler.onAccountRemoved(context, id)

            refresh()
        }
    }

    if (adding) {
        LoginScreen(
            modifier = modifier,
            onDone = {
                adding = false
                refresh()
            },
        )

        return
    }

    val active = accounts.firstOrNull { AccountPrefs.accountId(it) == activeId }

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
                    active?.let(::removeAccountFully)
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
                                    AccountPrefs.setActive(context, id)
                                    refresh()
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

                        IconButton(onClick = Haptics.haptic { removing = account }) {
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

    removing?.let { account ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.profile_remove_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_remove_dialog_text,
                        account.user,
                        account.displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = Haptics.haptic {
                    removing = null
                    removeAccountFully(account)
                }) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(stringResource(R.string.cancel))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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

