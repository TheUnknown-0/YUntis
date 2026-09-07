package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic
import sh.lsx.untisx.update.UpdateState
import sh.lsx.untisx.update.UpdateViewModel
import android.widget.Toast

private const val REPO_URL = "https://codeberg.org/Losterixx/UntisX"
private const val LICENSE_URL = "https://codeberg.org/Losterixx/UntisX/src/branch/main/LICENSE"
private const val ISSUES_URL = "https://codeberg.org/Losterixx/UntisX/issues"
private const val BETTERUNTIS_URL = "https://github.com/Sapuseven/BetterUntis"

@Composable
fun AboutScreen(
    auth: AuthData?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppCard(versionName)

        UpdatesCard()

        LinksCard(
            onOpen = uriHandler::openUri,
        )

        LibrariesCard()

        LegalCard(
            onOpen = uriHandler::openUri,
        )

        CreditsCard(
            onOpen = uriHandler::openUri,
        )

        DebugCard(auth)
    }
}

@Composable
private fun UpdatesCard() {
    val updateVm: UpdateViewModel = viewModel()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Updates", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(10.dp))

            val status = when (val state = updateVm.state) {
                is UpdateState.Checking -> "Checking for updates…"
                is UpdateState.UpToDate -> "You are on the latest version."
                is UpdateState.Available -> "Version ${state.info.versionName} is available!"
                is UpdateState.Downloading -> "Downloading ${state.info.versionName}... ${(state.progress * 100).toInt()}%"
                is UpdateState.Downloaded -> "${state.info.versionName} downloaded and ready to install."
                is UpdateState.Idle ->
                    updateVm.transientError
                        ?: if (updateVm.pendingInstall != null) {
                            "Update downloaded and ready to install."
                        } else {
                            "Check for updates manually."
                        }
            }

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (updateVm.pendingInstall != null) {
                    Button(onClick = Haptics.haptic(updateVm::installPending)) {
                        Text("Install")
                    }
                }

                Button(
                    onClick = Haptics.haptic(updateVm::check),
                    enabled = updateVm.state !is UpdateState.Checking &&
                        updateVm.state !is UpdateState.Downloading,
                ) {
                    Text("Check for updates")
                }
            }
        }
    }
}

@Composable
private fun AppCard(versionName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(sh.lsx.untisx.R.drawable.app_logo),
                contentDescription = "UntisX logo",
                modifier = Modifier.size(72.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = "UntisX",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "by LSX Studios",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LinksCard(onOpen: (String) -> Unit) {
    AboutCard("Project") {
        LinkRow(
            label = "Source code",
            value = "codeberg.org/Losterixx/UntisX",
            onClick = { onOpen(REPO_URL) },
        )

        Spacer(Modifier.height(4.dp))

        LinkRow(
            label = "Report an issue",
            value = "Bugs & feature requests",
            onClick = { onOpen(ISSUES_URL) },
        )
    }
}

@Composable
private fun LibrariesCard() {
    AboutCard("Built with") {
        CreditRow("Jetpack Compose", "UI framework")
        CreditRow("OkHttp", "HTTP client for the Untis API")
        CreditRow("kotlinx.serialization", "JSON parsing")
        CreditRow("Material 3", "Theming with dynamic colors")
    }
}

@Composable
private fun LegalCard(onOpen: (String) -> Unit) {
    AboutCard("License") {
        Text(
            text = "UntisX is free and open-source software licensed under the GNU GPL-3.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "UntisX is an unofficial client and is not affiliated with Untis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        LinkRow(
            label = "View license",
            value = "GPL-3.0",
            onClick = { onOpen(LICENSE_URL) },
        )
    }
}

@Composable
private fun CreditsCard(onOpen: (String) -> Unit) {
    AboutCard("Credits") {
        Text(
            text = "UntisX is heavenly inspired by BetterUntis, the open-source Untis client for Android.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        LinkRow(
            label = "BetterUntis",
            value = "github.com/Sapuseven/BetterUntis",
            onClick = { onOpen(BETTERUNTIS_URL) },
        )
    }
}

@Composable
private fun DebugCard(auth: AuthData?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var taps by remember { mutableIntStateOf(0) }
    val revealed = auth != null && taps >= 3

    val rows = listOf(
        "Session ID" to auth?.session?.sessionId?.takeIf { it.isNotBlank() },
        "Person ID" to auth?.session?.personId?.toString(),
        "Person type" to auth?.session?.personType?.let(::personTypeName),
        "Klasse ID" to auth?.session?.klasseId?.toString(),
        "School login name" to auth?.loginName,
    )

    AboutCard("Debug") {
        rows.forEach { (label, value) ->
            DebugRow(
                label = label,
                value = value,
                revealed = revealed,
                onTap = {
                    when {
                        !revealed -> {
                            taps++
                            if (taps == 3) {
                                Toast.makeText(
                                    context,
                                    "Debug values revealed. Tap a value to copy it.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }

                        value != null -> {
                            clipboard.setText(AnnotatedString(value))
                            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DebugRow(
    label: String,
    value: String?,
    revealed: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tapHaptic(onTap)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )

        Text(
            text = when {
                value == null || value == "-" -> "-"
                revealed -> value
                else -> "•".repeat(16)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun AboutCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
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

            Spacer(Modifier.height(10.dp))

            content()
        }
    }
}

@Composable
private fun LinkRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tapHaptic(onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreditRow(name: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f),
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private fun personTypeName(type: Int): String = when (type) {
    2 -> "Teacher"
    5 -> "Student"
    else -> type.toString()
}
