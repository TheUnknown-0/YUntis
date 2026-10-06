package sh.lsx.untisx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.colorToHex
import sh.lsx.untisx.data.hexToColor
import sh.lsx.untisx.ui.theme.SUBJECT_PALETTE
import sh.lsx.untisx.ui.timetable.LessonCard
import sh.lsx.untisx.ui.timetable.LessonCardOptions
import sh.lsx.untisx.ui.timetable.previewLesson
import sh.lsx.untisx.ui.util.tapHaptic

internal fun hexText(argb: Int): String = colorToHex(argb).removePrefix("#")

@Stable
internal class ColorEditorState(initialColor: Int) {
    var color by mutableStateOf(initialColor)
        private set

    var hsv by mutableStateOf(colorToHsv(Color(initialColor)))
        private set

    var hex by mutableStateOf(hexText(initialColor))
        private set

    var hexError by mutableStateOf(false)
        private set

    val hexValid: Boolean get() = hex.isEmpty() || hexToColor(hex) != null

    fun select(argb: Int) {
        color = argb
        hsv = colorToHsv(Color(argb))
        hex = hexText(argb)
        hexError = false
    }

    fun updateHsv(updated: Hsv) {
        hsv = updated
        color = hsvToColor(updated).toArgb()
        hex = hexText(color)
        hexError = false
    }

    fun updateHex(raw: String) {
        val cleaned = raw.trim().removePrefix("#").take(8).uppercase()
        hex = cleaned

        val parsed = if (cleaned.isEmpty()) null else hexToColor(cleaned)

        if (parsed != null) {
            color = parsed
            hsv = colorToHsv(Color(parsed))
            hexError = false
        } else {
            hexError = cleaned.length >= 6
        }
    }
}

@Composable
internal fun ColorPreviewCard(
    cardColor: Color,
    textColor: Color,
    subject: String,
    cancelled: Boolean = false,
    exam: Boolean = false,
    options: LessonCardOptions = LessonCardOptions(),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        LessonCard(
            lesson = previewLesson(subject = subject, cancelled = cancelled, exam = exam),
            color = cardColor,
            textColor = textColor,
            sharedElement = false,
            options = options,
        )
    }
}

@Composable
internal fun ContrastWarning(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.color_picker_low_contrast),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 2,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (visible) 1f else 0f)
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    )
}

@Composable
internal fun ColorEditor(
    state: ColorEditorState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.alpha(if (enabled) 1f else 0.35f)) {
        HsvColorPicker(
            hsv = state.hsv,
            enabled = enabled,
            onHsvChange = state::updateHsv,
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SUBJECT_PALETTE) { color ->
                val argb = color.toArgb()
                val selected = enabled && state.color == argb

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .tapHaptic(enabled) { state.select(argb) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.hex,
            onValueChange = state::updateHex,
            label = { Text(stringResource(R.string.color_picker_custom_hex)) },
            placeholder = { Text("RRGGBB") },
            prefix = { Text("#") },
            singleLine = true,
            isError = state.hexError,
            enabled = enabled,
            supportingText = if (state.hexError) {
                { Text(stringResource(R.string.color_picker_invalid_hex)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
