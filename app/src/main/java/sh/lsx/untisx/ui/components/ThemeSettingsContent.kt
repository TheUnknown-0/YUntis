package sh.lsx.untisx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.ui.theme.NotebookHandwriting
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
private fun ThemeStyle.label(): String = stringResource(
    when (this) {
        ThemeStyle.DEFAULT -> R.string.theme_style_default
        ThemeStyle.NOTEBOOK -> R.string.theme_style_notebook
    },
)

@Composable
private fun ThemeStyle.description(): String = stringResource(
    when (this) {
        ThemeStyle.DEFAULT -> R.string.theme_style_default_description
        ThemeStyle.NOTEBOOK -> R.string.theme_style_notebook_description
    },
)

@Composable
fun ThemeModeSettingsContent(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsModeSelector(
        options = ThemeMode.entries,
        selected = theme.mode,
        onSelect = { onChange(theme.copy(mode = it)) },
        label = { it.label() },
        modifier = modifier,
    )
}

@Composable
fun <T> SettingsModeSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = Haptics.haptic { onSelect(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(mode))
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
        Text(stringResource(R.string.settings_theme_style), style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))

        ThemeStyleSettingsContent(theme = theme, onChange = onChange)

        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))

        ThemeModeSettingsContent(theme = theme, onChange = onChange)

        Spacer(Modifier.height(18.dp))

        Text(stringResource(R.string.settings_accent_color), style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))

        ThemeAccentSettingsContent(theme = theme, onChange = onChange)
    }
}

@Composable
fun ThemeStyleSettingsContent(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeStyle.entries.forEach { style ->
            val selected = theme.style == style

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp),
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = Haptics.haptic { onChange(theme.copy(style = style)) },
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ThemeStylePreview(style)

                Column(Modifier.weight(1f)) {
                    Text(
                        text = style.label(),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = style.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

@Composable
private fun ThemeStylePreview(style: ThemeStyle) {
    val notebook = when (style) {
        ThemeStyle.DEFAULT -> false
        ThemeStyle.NOTEBOOK -> true
    }

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(76.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(if (notebook) Color(0xFFF5EFDF) else Color(0xFFEEEAF4), cornerRadius = CornerRadius(3.dp.toPx()))

            if (notebook) {
                for (line in 1..6) {
                    val y = line * 11.dp.toPx()
                    drawLine(Color(0xFFB6CAD2), Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
                }
                drawLine(Color(0xFFCE9690), Offset(9.dp.toPx(), 0f), Offset(9.dp.toPx(), size.height), 0.6.dp.toPx())
            }

            val top = Offset(14.dp.toPx(), 16.dp.toPx())
            val noteSize = Size(size.width - 24.dp.toPx(), size.height - 32.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = 0.1f), top + Offset(0f, 2.dp.toPx()), noteSize, CornerRadius(if (notebook) 0f else 6.dp.toPx()))
            drawRoundRect(if (notebook) Color(0xFFF2D98D) else Color(0xFFD0BDEA), top, noteSize, CornerRadius(if (notebook) 0f else 6.dp.toPx()))

            if (notebook) {
                val fold = Path().apply {
                    moveTo(top.x + noteSize.width - 7.dp.toPx(), top.y + noteSize.height)
                    lineTo(top.x + noteSize.width - 7.dp.toPx(), top.y + noteSize.height - 7.dp.toPx())
                    lineTo(top.x + noteSize.width, top.y + noteSize.height - 7.dp.toPx())
                    close()
                }
                drawPath(fold, Color(0xFFFFF0BF))
            }
        }

        Text(
            text = "Aa",
            color = Color(0xFF302E29),
            fontSize = 20.sp,
            fontFamily = if (notebook) NotebookHandwriting else FontFamily.Default,
            fontWeight = FontWeight.Bold,
        )
    }
}
