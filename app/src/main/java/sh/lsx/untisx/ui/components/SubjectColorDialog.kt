package sh.lsx.untisx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import sh.lsx.untisx.data.SubjectColorResolver
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
    schoolColor: Int? = null,
    lockedBySchool: Boolean = false,
) {
    val cardEditor = remember(subject) { ColorEditorState(cardColor ?: defaultCardColor) }
    val textEditor = remember(subject) { ColorEditorState(textColor ?: defaultTextColor) }
    var cardUseDefault by remember(subject) { mutableStateOf(cardColor == null) }
    var textUseDefault by remember(subject) { mutableStateOf(textColor == null) }
    var editText by remember(subject) { mutableStateOf(false) }

    val effectiveCard = when {
        lockedBySchool && schoolColor != null -> schoolColor
        cardUseDefault -> defaultCardColor
        else -> cardEditor.color
    }
    val effectiveText = if (textUseDefault) defaultTextColor else textEditor.color
    val custom = !cardUseDefault || !textUseDefault
    val lowContrast = custom && contrastRatio(Color(effectiveCard), Color(effectiveText)) < 3f

    val editor = if (editText) textEditor else cardEditor
    val editorUseDefault = if (editText) textUseDefault else cardUseDefault
    val editorLocked = !editText && lockedBySchool
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

                if (editorLocked) {
                    Text(
                        text = stringResource(R.string.color_picker_locked_by_school),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!editText && !editorLocked) {
                    if (schoolColor != null) {
                        Text(
                            text = stringResource(R.string.color_picker_school_fallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(8.dp))
                    }

                    QuickSwatches(
                        selected = if (cardUseDefault) null else cardEditor.color,
                        onPick = { argb ->
                            cardUseDefault = false
                            cardEditor.select(argb)
                        },
                    )

                    Spacer(Modifier.height(12.dp))
                }

                if (!editorLocked) Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (editText) R.string.color_picker_use_default_text
                            else R.string.color_picker_no_own_color,
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

                if (!editorLocked) ColorEditor(state = editor, enabled = !editorUseDefault)
            }
        },
        confirmButton = {
            TextButton(
                onClick = Haptics.haptic {
                    onApply(
                        when {
                            lockedBySchool -> cardColor
                            cardUseDefault -> null
                            else -> cardEditor.color
                        },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSwatches(
    selected: Int?,
    onPick: (Int) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val ring = MaterialTheme.colorScheme.onSurface

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubjectColorResolver.AUTO_PALETTE.forEach { argb ->
            val isSelected = selected == argb

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) ring else outline, CircleShape)
                    .clickable(onClick = Haptics.haptic { onPick(argb) }),
            )
        }
    }
}
