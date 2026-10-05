package sh.lsx.untisx.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.prefs.AppearancePrefs
import sh.lsx.untisx.ui.components.ColorPickerDialog
import sh.lsx.untisx.ui.components.SettingsColorRow
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSliderRow
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import sh.lsx.untisx.ui.components.SubjectColorDialog
import sh.lsx.untisx.ui.timetable.LessonCard
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.timetable.previewLesson
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic
import kotlin.math.roundToInt

@Composable
fun TimetableSettingsScreen(
    vm: TimetableViewModel,
    knownSubjects: List<String>,
    subjectColors: Map<String, Int>,
    onSubjectColor: (String, Int?) -> Unit,
    subjectTextColors: Map<String, Int>,
    onSubjectTextColor: (String, Int?) -> Unit,
    defaultSubjectColor: Int,
    onDefaultSubjectColor: (Int) -> Unit,
    lessonTextColor: Int,
    onLessonTextColor: (Int) -> Unit,
    cancelledStrike: Boolean,
    onCancelledStrike: (Boolean) -> Unit,
    cancelledDim: Float,
    onCancelledDim: (Float) -> Unit,
    pastDim: Float,
    onPastDim: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {

    var colorDialogFor by remember { mutableStateOf<String?>(null) }
    var pickDefaultColor by remember { mutableStateOf(false) }
    var pickTextColor by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard(title = stringResource(R.string.settings_timetable_colors)) {
            SettingsColorRow(
                label = stringResource(R.string.settings_default_subject_color),
                color = Color(defaultSubjectColor),
                onClick = { pickDefaultColor = true },
            )

            SettingsColorRow(
                label = stringResource(R.string.settings_card_text_color),
                color = Color(lessonTextColor),
                onClick = { pickTextColor = true },
            )

            Spacer(Modifier.size(10.dp))

            if (knownSubjects.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_subjects),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.size(8.dp))

                TextButton(onClick = Haptics.haptic { vm.rescanSubjects() }) {
                    Text(stringResource(R.string.settings_scan_subjects))
                }
            } else {
                knownSubjects.forEach { name ->
                    val custom = subjectColors.containsKey(name)
                    val color = subjectColors[name]?.let { Color(it) } ?: Color(defaultSubjectColor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .tapHaptic { colorDialogFor = name }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(color, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )

                        subjectTextColors[name]?.let { textOverride ->
                            Spacer(Modifier.size(6.dp))

                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(textOverride), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            )
                        }

                        Spacer(Modifier.size(10.dp))

                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        if (custom) {
                            Text(
                                text = stringResource(R.string.settings_custom),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(Modifier.size(4.dp))
                }

                TextButton(onClick = Haptics.haptic { vm.rescanSubjects() }) {
                    Text(stringResource(R.string.settings_rescan_subjects))
                }
            }

            Spacer(Modifier.size(10.dp))

            SettingsSwitchRow(
                title = stringResource(R.string.settings_strike_cancelled),
                description = stringResource(R.string.settings_strike_cancelled_desc),
                checked = cancelledStrike,
                onCheckedChange = onCancelledStrike,
            )
        }

        SettingsSectionCard(title = stringResource(R.string.settings_display)) {
            SettingsSliderRow(
                title = stringResource(R.string.settings_cancelled_dim),
                description = stringResource(R.string.settings_cancelled_dim_desc),
                value = cancelledDim,
                onValueChange = onCancelledDim,
                valueLabel = percentLabel(cancelledDim),
            )

            SettingsSliderRow(
                title = stringResource(R.string.settings_past_dim),
                description = stringResource(R.string.settings_past_dim_desc),
                value = pastDim,
                onValueChange = onPastDim,
                valueRange = 0f..AppearancePrefs.MAX_PAST_DIM,
                valueLabel = percentLabel(pastDim),
            )

            Spacer(Modifier.size(4.dp))

            LessonPreview(
                cancelledDim = cancelledDim,
                pastDim = pastDim,
                cancelledStrike = cancelledStrike,
                color = Color(defaultSubjectColor),
                textColor = Color(lessonTextColor),
            )
        }
    }

    colorDialogFor?.let { subjectName ->
        SubjectColorDialog(
            subject = subjectName,
            cardColor = subjectColors[subjectName],
            textColor = subjectTextColors[subjectName],
            defaultCardColor = defaultSubjectColor,
            defaultTextColor = lessonTextColor,
            onApply = { card, text ->
                onSubjectColor(subjectName, card)
                onSubjectTextColor(subjectName, text)
                colorDialogFor = null
            },
            onDismiss = { colorDialogFor = null },
        )
    }

    if (pickDefaultColor) {
        ColorPickerDialog(
            title = stringResource(R.string.settings_default_subject_color),
            currentColor = defaultSubjectColor,
            allowUseDefault = false,
            previewTextColor = lessonTextColor,
            onApply = { argb ->
                argb?.let(onDefaultSubjectColor)
                pickDefaultColor = false
            },
            onDismiss = { pickDefaultColor = false },
        )
    }

    if (pickTextColor) {
        ColorPickerDialog(
            title = stringResource(R.string.settings_card_text_color),
            currentColor = lessonTextColor,
            allowUseDefault = false,
            previewCardColor = defaultSubjectColor,
            onApply = { argb ->
                argb?.let(onLessonTextColor)
                pickTextColor = false
            },
            onDismiss = { pickTextColor = false },
        )
    }
}

@Composable
private fun LessonPreview(
    cancelledDim: Float,
    pastDim: Float,
    cancelledStrike: Boolean,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PreviewLesson(
            label = stringResource(R.string.settings_preview_past),
            cancelled = false,
            pastDim = pastDim,
            strike = true,
            color = color,
            textColor = textColor,
            cancelledDim = cancelledDim,
            modifier = Modifier.weight(1f),
        )

        PreviewLesson(
            label = stringResource(R.string.settings_preview_cancelled),
            cancelled = true,
            pastDim = null,
            strike = cancelledStrike,
            color = color,
            textColor = textColor,
            cancelledDim = cancelledDim,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreviewLesson(
    label: String,
    cancelled: Boolean,
    pastDim: Float?,
    strike: Boolean,
    color: Color,
    textColor: Color,
    cancelledDim: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            LessonCard(
                lesson = previewLesson(cancelled = cancelled),
                color = color,
                textColor = textColor,
                strike = strike,
                cancelledDim = cancelledDim,
            )

            if (pastDim != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = pastDim)),
                )
            }
        }

        Spacer(Modifier.size(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun percentLabel(value: Float): String = "${(value * 100).roundToInt()} %"
