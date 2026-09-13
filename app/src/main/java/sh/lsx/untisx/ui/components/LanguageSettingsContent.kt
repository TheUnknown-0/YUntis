package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppLanguage
import sh.lsx.untisx.ui.util.Haptics

@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.GERMAN -> "Deutsch"
}

@Composable
fun LanguageSettingsContent(
    language: AppLanguage,
    onChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AppLanguage.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = language == option,
                    onClick = Haptics.haptic { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                ) {
                    Text(option.label())
                }
            }
        }
    }
}
