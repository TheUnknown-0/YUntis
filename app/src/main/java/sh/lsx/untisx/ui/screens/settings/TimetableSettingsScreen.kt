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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.prefs.AppearancePrefs
import sh.lsx.untisx.ui.components.ColorPickerDialog
import sh.lsx.untisx.ui.components.SettingsColorRow
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSliderRow
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import sh.lsx.untisx.ui.components.SubjectColorDialog
import sh.lsx.untisx.ui.timetable.LessonCard
import sh.lsx.untisx.ui.timetable.LessonCardOptions
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
    cancelledDesaturation: Float,
    onCancelledDesaturation: (Float) -> Unit,
    cancelledBrightness: Float,
    onCancelledBrightness: (Float) -> Unit,
    pastDim: Float,
    onPastDim: (Float) -> Unit,
    lessonOptions: LessonCardOptions,
    onLessonOptionsChange: (LessonCardOptions) -> Unit,
    strikeColor: Int?,
    onStrikeColor: (Int?) -> Unit,
    examColor: Int?,
    onExamColor: (Int?) -> Unit,
    hideCancelled: Boolean,
    onHideCancelledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {

    var colorDialogFor by remember { mutableStateOf<String?>(null) }
    var pickDefaultColor by remember { mutableStateOf(false) }
    var pickTextColor by remember { mutableStateOf(false) }
    var pickStrikeColor by remember { mutableStateOf(false) }
    var pickExamColor by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard(title = stringResource(R.string.settings_default_colors)) {
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
        }

        SettingsSectionCard(title = stringResource(R.string.settings_subjects)) {
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
        }

        SettingsSectionCard(title = stringResource(R.string.settings_extras)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_strike_cancelled),
                description = stringResource(R.string.settings_strike_cancelled_desc),
                checked = cancelledStrike,
                onCheckedChange = onCancelledStrike,
            )

            SettingsColorRow(
                label = stringResource(R.string.settings_strike_color),
                color = Color(strikeColor ?: SubjectColorsPrefs.DEFAULT_STRIKE_COLOR),
                onClick = { pickStrikeColor = true },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_exam_badge),
                checked = lessonOptions.showExamBadge,
                onCheckedChange = { onLessonOptionsChange(lessonOptions.copy(showExamBadge = it)) },
            )

            SettingsColorRow(
                label = stringResource(R.string.settings_exam_color),
                color = Color(examColor ?: MaterialTheme.colorScheme.error.toArgb()),
                onClick = { pickExamColor = true },
            )
        }

        SettingsSectionCard(title = stringResource(R.string.settings_cards)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_teacher),
                checked = lessonOptions.showTeacher,
                onCheckedChange = { onLessonOptionsChange(lessonOptions.copy(showTeacher = it)) },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_room),
                checked = lessonOptions.showRoom,
                onCheckedChange = { onLessonOptionsChange(lessonOptions.copy(showRoom = it)) },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_changes),
                description = stringResource(R.string.settings_show_changes_desc),
                checked = lessonOptions.showChanges,
                onCheckedChange = { onLessonOptionsChange(lessonOptions.copy(showChanges = it)) },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_hide_cancelled),
                checked = hideCancelled,
                onCheckedChange = onHideCancelledChange,
            )
        }

        SettingsSectionCard(title = stringResource(R.string.settings_display)) {
            SettingsSliderRow(
                title = stringResource(R.string.settings_cancelled_desaturation),
                description = stringResource(R.string.settings_cancelled_desaturation_desc),
                value = cancelledDesaturation,
                onValueChange = onCancelledDesaturation,
                valueLabel = percentLabel(cancelledDesaturation),
            )

            SettingsSliderRow(
                title = stringResource(R.string.settings_cancelled_darken),
                description = stringResource(R.string.settings_cancelled_darken_desc),
                value = cancelledBrightness,
                onValueChange = onCancelledBrightness,
                valueLabel = percentLabel(cancelledBrightness),
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
                cancelledDesaturation = cancelledDesaturation,
                cancelledBrightness = cancelledBrightness,
                pastDim = pastDim,
                cancelledStrike = cancelledStrike,
                options = lessonOptions,
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

    if (pickStrikeColor) {
        ColorPickerDialog(
            title = stringResource(R.string.settings_strike_color),
            currentColor = strikeColor,
            allowUseDefault = true,
            defaultColor = SubjectColorsPrefs.DEFAULT_STRIKE_COLOR,
            previewCardColor = defaultSubjectColor,
            previewTextColor = lessonTextColor,
            previewCancelled = true,
            onApply = { argb ->
                onStrikeColor(argb)
                pickStrikeColor = false
            },
            onDismiss = { pickStrikeColor = false },
        )
    }

    if (pickExamColor) {
        ColorPickerDialog(
            title = stringResource(R.string.settings_exam_color),
            currentColor = examColor,
            allowUseDefault = true,
            defaultColor = MaterialTheme.colorScheme.error.toArgb(),
            previewCardColor = defaultSubjectColor,
            previewTextColor = lessonTextColor,
            previewExam = true,
            onApply = { argb ->
                onExamColor(argb)
                pickExamColor = false
            },
            onDismiss = { pickExamColor = false },
        )
    }
}

@Composable
private fun LessonPreview(
    cancelledDesaturation: Float,
    cancelledBrightness: Float,
    pastDim: Float,
    cancelledStrike: Boolean,
    options: LessonCardOptions,
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
            options = options,
            color = color,
            textColor = textColor,
            cancelledDesaturation = cancelledDesaturation,
            cancelledBrightness = cancelledBrightness,
            modifier = Modifier.weight(1f),
        )

        PreviewLesson(
            label = stringResource(R.string.settings_preview_cancelled),
            cancelled = true,
            pastDim = null,
            strike = cancelledStrike,
            options = options,
            color = color,
            textColor = textColor,
            cancelledDesaturation = cancelledDesaturation,
            cancelledBrightness = cancelledBrightness,
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
    options: LessonCardOptions,
    color: Color,
    textColor: Color,
    cancelledDesaturation: Float,
    cancelledBrightness: Float,
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
                cancelledDesaturation = cancelledDesaturation,
                cancelledBrightness = cancelledBrightness,
                options = options,
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
