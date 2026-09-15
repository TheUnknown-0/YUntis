package sh.lsx.untisx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import sh.lsx.untisx.ui.components.LanguageSettingsContent
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSwitchRow

@Composable
fun GeneralSettingsScreen(
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(LanguagePrefs.get(context)) }

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
    }
}
