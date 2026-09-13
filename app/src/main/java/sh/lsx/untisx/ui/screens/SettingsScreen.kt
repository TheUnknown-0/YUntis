package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppLanguage
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.LanguagePrefs
import sh.lsx.untisx.ui.components.ColorPickerDialog
import sh.lsx.untisx.ui.components.LanguageSettingsContent
import sh.lsx.untisx.ui.components.ThemeSettingsContent
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
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
    val context = LocalContext.current
    val timetableVm: TimetableViewModel = viewModel()

    var colorDialogFor by remember { mutableStateOf<String?>(null) }
    var pickDefaultColor by remember { mutableStateOf(false) }
    var pickTextColor by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(LanguagePrefs.get(context)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))

                LanguageSettingsContent(
                    language = language,
                    onChange = { newLanguage ->
                        language = newLanguage
                        LanguagePrefs.save(context, newLanguage)
                    },
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))

                ThemeSettingsContent(theme = theme, onChange = onThemeChange)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_haptics),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )

                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsChange,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.settings_timetable_colors), style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(6.dp))

                ColorRow(
                    label = stringResource(R.string.settings_default_subject_color),
                    color = Color(defaultSubjectColor),
                    onClick = { pickDefaultColor = true },
                )

                Spacer(Modifier.height(4.dp))

                ColorRow(
                    label = stringResource(R.string.settings_card_text_color),
                    color = Color(lessonTextColor),
                    onClick = { pickTextColor = true },
                )

                Spacer(Modifier.height(10.dp))

                if (knownSubjects.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_subjects),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = Haptics.haptic { timetableVm.rescanSubjects() }) {
                        Text(stringResource(R.string.settings_scan_subjects))
                    }
                } else {
                    knownSubjects.forEach { name ->
                        val custom = subjectColors.containsKey(name)
                        val color = subjectColors[name]?.let { Color(it) }
                            ?: Color(defaultSubjectColor)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .tapHaptic {
                                    colorDialogFor = name
                                }
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

                    TextButton(onClick = Haptics.haptic { timetableVm.rescanSubjects() }) {
                        Text(stringResource(R.string.settings_rescan_subjects))
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_strike_cancelled),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Text(
                            text = stringResource(R.string.settings_strike_cancelled_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = cancelledStrike,
                        onCheckedChange = onCancelledStrike,
                    )
                }
            }
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

@Composable
private fun ColorRow(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tapHaptic(onClick)
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
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
