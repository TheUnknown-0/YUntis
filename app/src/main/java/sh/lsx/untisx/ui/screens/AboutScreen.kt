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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.personTypeName
import sh.lsx.untisx.ui.util.uiTextString
import sh.lsx.untisx.ui.util.tapHaptic
import android.widget.Toast

private const val REPO_URL = "https://github.com/TheUnknown-0/YUntis"
private const val LICENSE_URL = "https://github.com/TheUnknown-0/YUntis/blob/main/LICENSE"
private const val ISSUES_URL = "https://github.com/TheUnknown-0/YUntis/issues"
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
private fun AppCard(versionName: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(20.dp),
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
                contentDescription = stringResource(R.string.untisx_logo),
                modifier = Modifier.size(72.dp),
            )

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = "YUntis",
                    style = MaterialTheme.typography.headlineMedium,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.about_by_lsx),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = stringResource(R.string.updates_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LinksCard(onOpen: (String) -> Unit) {
    AboutCard(stringResource(R.string.about_project)) {
        LinkRow(
            label = stringResource(R.string.about_source_code),
            value = "github.com/TheUnknown-0/YUntis",
            onClick = { onOpen(REPO_URL) },
        )

        Spacer(Modifier.height(4.dp))

        LinkRow(
            label = stringResource(R.string.about_report_issue),
            value = stringResource(R.string.about_report_issue_desc),
            onClick = { onOpen(ISSUES_URL) },
        )
    }
}

@Composable
private fun LibrariesCard() {
    AboutCard(stringResource(R.string.about_built_with)) {
        CreditRow("Jetpack Compose", stringResource(R.string.about_lib_compose_desc))
        CreditRow("OkHttp", stringResource(R.string.about_lib_okhttp_desc))
        CreditRow("kotlinx.serialization", stringResource(R.string.about_lib_serialization_desc))
        CreditRow("Material 3", stringResource(R.string.about_lib_material3_desc))
        CreditRow("Kalam", stringResource(R.string.about_kalam_credit))
        CreditRow("Permanent Marker", stringResource(R.string.about_marker_credit))
    }
}

@Composable
private fun LegalCard(onOpen: (String) -> Unit) {
    AboutCard(stringResource(R.string.about_license_title)) {
        Text(
            text = stringResource(R.string.about_license_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.about_unofficial_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        LinkRow(
            label = stringResource(R.string.about_view_license),
            value = "GPL-3.0",
            onClick = { onOpen(LICENSE_URL) },
        )
    }
}

@Composable
private fun CreditsCard(onOpen: (String) -> Unit) {
    AboutCard(stringResource(R.string.about_credits_title)) {
        Text(
            text = stringResource(R.string.about_credits_text),
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
        stringResource(R.string.about_debug_session_id) to auth?.session?.sessionId?.takeIf { it.isNotBlank() }?.let { "••••${it.takeLast(6)}..." },
        stringResource(R.string.about_debug_person_id) to auth?.session?.personId?.toString(),
        stringResource(R.string.about_debug_person_type) to auth?.session?.personType?.let { uiTextString(personTypeName(it)) },
        stringResource(R.string.about_debug_klasse_id) to auth?.session?.klasseId?.toString(),
        stringResource(R.string.about_debug_school_login) to auth?.loginName,
    )

    val revealedToastText = stringResource(R.string.about_debug_revealed_toast)

    AboutCard(stringResource(R.string.about_debug_title)) {
        rows.forEach { (label, value) ->
            val copiedToastText = stringResource(R.string.about_debug_copied_toast, label)

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
                                    revealedToastText,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }

                        value != null -> {
                            clipboard.setText(AnnotatedString(value))
                            Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(20.dp),
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
