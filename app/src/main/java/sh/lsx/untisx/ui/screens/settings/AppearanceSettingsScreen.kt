package sh.lsx.untisx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import sh.lsx.untisx.ui.components.ThemeSettingsContent
import sh.lsx.untisx.ui.components.WidgetStyleSettingsCard

@Composable
fun AppearanceSettingsScreen(
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
            ThemeSettingsContent(theme = theme, onChange = onThemeChange)
        }

        SettingsSectionCard {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_animations),
                description = stringResource(R.string.settings_animations_desc),
                checked = animationsEnabled,
                onCheckedChange = onAnimationsChange,
            )
        }

        WidgetStyleSettingsCard()
    }
}
