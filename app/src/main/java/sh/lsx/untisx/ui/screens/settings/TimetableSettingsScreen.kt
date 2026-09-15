package sh.lsx.untisx.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import sh.lsx.untisx.ui.components.ColorPickerDialog
import sh.lsx.untisx.ui.components.SettingsColorRow
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
fun TimetableSettingsScreen(
    vm: TimetableViewModel,
    knownSubjects: List<String>,
    subjectColors: Map<String, Int>,
    onSubjectColor: (String, Int?) -> Unit,
    defaultSubjectColor: Int,
    onDefaultSubjectColor: (Int) -> Unit,
    lessonTextColor: Int,
    onLessonTextColor: (Int) -> Unit,
    cancelledStrike: Boolean,
    onCancelledStrike: (Boolean) -> Unit,
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
    }

    colorDialogFor?.let { subjectName ->
        ColorPickerDialog(
            title = subjectName,
            currentColor = subjectColors[subjectName],
            allowUseDefault = true,
            onPick = { argb ->
                onSubjectColor(subjectName, argb)
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
            onPick = { argb ->
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
            onPick = { argb ->
                argb?.let(onLessonTextColor)
                pickTextColor = false
            },
            onDismiss = { pickTextColor = false },
        )
    }
}
