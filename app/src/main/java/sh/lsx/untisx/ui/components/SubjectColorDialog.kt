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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.util.Haptics

@Composable
fun SubjectColorDialog(
    subject: String,
    cardColor: Int?,
    textColor: Int?,
    defaultCardColor: Int,
    defaultTextColor: Int,
    onApply: (cardColor: Int?, textColor: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val cardEditor = remember(subject) { ColorEditorState(cardColor ?: defaultCardColor) }
    val textEditor = remember(subject) { ColorEditorState(textColor ?: defaultTextColor) }
    var cardUseDefault by remember(subject) { mutableStateOf(cardColor == null) }
    var textUseDefault by remember(subject) { mutableStateOf(textColor == null) }
    var editText by remember(subject) { mutableStateOf(false) }

    val effectiveCard = if (cardUseDefault) defaultCardColor else cardEditor.color
    val effectiveText = if (textUseDefault) defaultTextColor else textEditor.color
    val custom = !cardUseDefault || !textUseDefault
    val lowContrast = custom && contrastRatio(Color(effectiveCard), Color(effectiveText)) < 3f

    val editor = if (editText) textEditor else cardEditor
    val editorUseDefault = if (editText) textUseDefault else cardUseDefault
    val applyEnabled = (cardUseDefault || cardEditor.hexValid) &&
        (textUseDefault || textEditor.hexValid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(subject, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ColorPreviewCard(
                    cardColor = Color(effectiveCard),
                    textColor = Color(effectiveText),
                    subject = subject,
                )

                Spacer(Modifier.height(6.dp))

                ContrastWarning(visible = lowContrast)

                Spacer(Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !editText,
                        onClick = Haptics.haptic { editText = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.color_picker_target_card))
                    }

                    SegmentedButton(
                        selected = editText,
                        onClick = Haptics.haptic { editText = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.color_picker_target_text))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (editText) R.string.color_picker_use_default_text
                            else R.string.color_picker_use_default,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )

                    Switch(
                        checked = editorUseDefault,
                        onCheckedChange = { checked ->
                            if (editText) textUseDefault = checked else cardUseDefault = checked
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                ColorEditor(state = editor, enabled = !editorUseDefault)
            }
        },
        confirmButton = {
            TextButton(
                onClick = Haptics.haptic {
                    onApply(
                        if (cardUseDefault) null else cardEditor.color,
                        if (textUseDefault) null else textEditor.color,
                    )
                },
                enabled = applyEnabled,
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
