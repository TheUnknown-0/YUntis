package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset
import sh.lsx.untisx.ui.util.Haptics

@Composable
fun ThemeSettingsContent(
    theme: AppTheme,
    onChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Mode", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = theme.mode == mode,
                    onClick = Haptics.haptic { onChange(theme.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) {
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text("Accent Color", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))

        ThemePreset.entries.forEach { preset ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = theme.preset == preset,
                    onClick = Haptics.haptic { onChange(theme.copy(preset = preset)) },
                )

                Text(preset.label)
            }
        }
    }
}
