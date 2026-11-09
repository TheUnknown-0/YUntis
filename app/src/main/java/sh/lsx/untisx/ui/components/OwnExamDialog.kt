package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Color for exams the user entered, so they never look like the school's (red) exams. */
val OwnExamColor = Color(0xFF7E57C2)

/** A subject the user can pick for an own exam. */
data class ExamSubjectChoice(val id: Int, val name: String, val label: String)

/** One lesson of the chosen subject on the chosen day, offered as quick time choice. */
data class ExamLessonSlot(val startTime: Int, val endTime: Int, val label: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnExamDialog(
    initial: ExamInfo,
    isNew: Boolean,
    subjects: List<ExamSubjectChoice>,
    lessonsOn: (date: Int, subjectName: String) -> List<ExamLessonSlot>,
    onSave: (ExamInfo) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initial.examType) }
    var subject by remember {
        mutableStateOf(subjects.firstOrNull { it.id != 0 && it.id == initial.subjectId } ?: subjects.firstOrNull { it.name == initial.subjectName })
    }
    var date by remember { mutableIntStateOf(initial.date) }
    var start by remember { mutableIntStateOf(initial.startTime) }
    var end by remember { mutableIntStateOf(initial.endTime) }
    var note by remember { mutableStateOf(initial.text) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) }
    var subjectsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val slots = remember(date, subject) { subject?.let { lessonsOn(date, it.name) }.orEmpty() }
    val dateText = date.toUntisLocalDateOrNull()?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).orEmpty()
    val canSave = subject != null && date > 0 && (start == 0 || end == 0 || end > start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.own_exam_add else R.string.own_exam_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.own_exam_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnExamColor,
                )

                ExposedDropdownMenuBox(expanded = subjectsOpen, onExpandedChange = { subjectsOpen = it }) {
                    OutlinedTextField(
                        value = subject?.label.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.own_exam_subject)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectsOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = subjectsOpen, onDismissRequest = { subjectsOpen = false }) {
                        subjects.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice.label) },
                                onClick = {
                                    subject = choice
                                    subjectsOpen = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it.take(40) },
                    label = { Text(stringResource(R.string.own_exam_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dateText.ifBlank { stringResource(R.string.own_exam_pick_date) })
                }

                if (slots.isNotEmpty()) {
                    Text(stringResource(R.string.own_exam_lesson), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slots.forEach { slot ->
                            FilterChip(
                                selected = start == slot.startTime && end == slot.endTime,
                                onClick = {
                                    start = slot.startTime
                                    end = slot.endTime
                                },
                                label = { Text(slot.label) },
                            )
                        }
                    }
                } else if (subject != null) {
                    Text(
                        text = stringResource(R.string.own_exam_no_lesson),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.own_exam_from, timeText(start)))
                    }
                    OutlinedButton(onClick = { pickTime = false }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.own_exam_to, timeText(end)))
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text(stringResource(R.string.own_exam_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.own_exam_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val chosen = subject ?: return@TextButton
                    onSave(
                        initial.copy(
                            examType = type.trim(),
                            subjectId = chosen.id,
                            subjectName = chosen.name,
                            date = date,
                            startTime = start,
                            endTime = end,
                            text = note.trim(),
                            own = true,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickDate) {
        val initialMillis = (date.toUntisLocalDateOrNull() ?: AppClock.today())
            .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toUntisDate()
                        start = 0
                        end = 0
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    pickTime?.let { isStart ->
        val current = if (isStart) start else end
        val state = rememberTimePickerState(
            initialHour = if (current == 0) 8 else current / 100,
            initialMinute = if (current == 0) 0 else current % 100,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickTime = null },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val value = state.hour * 100 + state.minute
                    if (isStart) start = value else end = value
                    pickTime = null
                }) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.own_exam_delete_title)) },
            text = { Text(stringResource(R.string.own_exam_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.own_exam_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun timeText(time: Int): String =
    if (time == 0) "–" else "%02d:%02d".format(time / 100, time % 100)

/** A fresh own exam, optionally prefilled from a lesson. */
fun newOwnExam(
    date: Int = AppClock.today().toUntisDate(),
    subjectId: Int = 0,
    subjectName: String = "",
    startTime: Int = 0,
    endTime: Int = 0,
    type: String,
): ExamInfo = ExamInfo(
    id = 0,
    examType = type,
    date = date,
    startTime = startTime,
    endTime = endTime,
    subjectId = subjectId,
    subjectName = subjectName,
    own = true,
)
