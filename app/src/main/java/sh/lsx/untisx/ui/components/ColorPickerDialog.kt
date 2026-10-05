package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.theme.SUBJECT_PALETTE
import sh.lsx.untisx.ui.util.Haptics

@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Int?,
    allowUseDefault: Boolean,
    onApply: (Int?) -> Unit,
    onDismiss: () -> Unit,
    defaultColor: Int? = null,
    previewSubject: String? = null,
    previewCardColor: Int? = null,
    previewTextColor: Int? = null,
) {
    val initial = currentColor ?: defaultColor ?: SUBJECT_PALETTE.first().toArgb()
    val editor = remember(title) { ColorEditorState(initial) }
    var useDefault by remember(title) { mutableStateOf(allowUseDefault && currentColor == null) }

    val effective = if (useDefault) (defaultColor ?: editor.color) else editor.color
    val cardColor = Color(previewCardColor ?: effective)
    val cardText = Color(previewTextColor ?: effective)
    val lowContrast = !useDefault && contrastRatio(cardColor, cardText) < 3f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ColorPreviewCard(
                    cardColor = cardColor,
                    textColor = cardText,
                    subject = previewSubject ?: "MA",
                )

                Spacer(Modifier.height(6.dp))

                ContrastWarning(visible = lowContrast)

                if (allowUseDefault) {
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.color_picker_use_default),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )

                        Switch(
                            checked = useDefault,
                            onCheckedChange = { useDefault = it },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                ColorEditor(state = editor, enabled = !useDefault)
            }
        },
        confirmButton = {
            TextButton(
                onClick = Haptics.haptic {
                    onApply(if (useDefault && allowUseDefault) null else editor.color)
                },
                enabled = useDefault || editor.hexValid,
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = Haptics.haptic(onDismiss)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
