package sh.lsx.untisx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
    ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
}

@Composable
private fun ThemePreset.label(): String = when (this) {
    ThemePreset.DEVICE -> stringResource(R.string.theme_preset_device)
    ThemePreset.ORANGE -> stringResource(R.string.theme_preset_orange)
    ThemePreset.BLUE -> stringResource(R.string.theme_preset_blue)
    ThemePreset.GREEN -> stringResource(R.string.theme_preset_green)
    ThemePreset.PURPLE -> stringResource(R.string.theme_preset_purple)
    ThemePreset.RED -> stringResource(R.string.theme_preset_red)
    ThemePreset.CUSTOM -> stringResource(R.string.theme_preset_custom)
}

@Composable
fun ThemeModeSettingsContent(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = theme.mode == mode,
                onClick = Haptics.haptic { onChange(theme.copy(mode = mode)) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(mode.label())
            }
        }
    }
}

@Composable
fun ThemeAccentSettingsContent(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickCustom by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ThemePreset.entries.forEach { preset ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = theme.preset == preset,
                    onClick = Haptics.haptic { onChange(theme.copy(preset = preset)) },
                )

                Text(preset.label())

                if (preset == ThemePreset.CUSTOM) {
                    Spacer(Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(theme.customSeed), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .tapHaptic { pickCustom = true },
                    )
                }
            }
        }
    }

    if (pickCustom) {
        ColorPickerDialog(
            title = stringResource(R.string.settings_accent_color),
            currentColor = theme.customSeed,
            allowUseDefault = false,
            onApply = { argb ->
                argb?.let { onChange(theme.copy(preset = ThemePreset.CUSTOM, customSeed = it)) }
                pickCustom = false
            },
            onDismiss = { pickCustom = false },
        )
    }
}

@Composable
fun ThemeSettingsContent(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))

        ThemeModeSettingsContent(theme = theme, onChange = onChange)

        Spacer(Modifier.height(18.dp))

        Text(stringResource(R.string.settings_accent_color), style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))

        ThemeAccentSettingsContent(theme = theme, onChange = onChange)
    }
}
