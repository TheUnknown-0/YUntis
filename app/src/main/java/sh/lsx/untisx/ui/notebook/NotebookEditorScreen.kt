package sh.lsx.untisx.ui.notebook

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.NotebookField
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookReminderTime
import sh.lsx.untisx.data.notebook.NotebookValidationIssue
import sh.lsx.untisx.data.notebook.NotebookValidator
import sh.lsx.untisx.data.notebook.ReminderSpec
import sh.lsx.untisx.ui.components.SettingsModeSelector
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape

private enum class NotebookDateTarget { DUE, REMINDER }

@Composable
fun NotebookEditorScreen(
    vm: NotebookViewModel,
    onBack: () -> Unit,
    onPickOriginLesson: () -> Unit,
    onPickDueLesson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.editorState.collectAsStateWithLifecycle()
    val exitRequest by vm.exitRequest.collectAsStateWithLifecycle()
    val conversion by vm.pendingConversion.collectAsStateWithLifecycle()
    val pendingDueLesson by vm.pendingDueLesson.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showNotFoundDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<NotebookDateTarget?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    val exitBaseline = remember { exitRequest }

    LaunchedEffect(state) {
        if (state == null) onBack()
    }

    LaunchedEffect(exitRequest) {
        if (exitRequest <= exitBaseline) return@LaunchedEffect

        val current = state

        if (current is NotebookEditorState.Ready && current.isDirty) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    LaunchedEffect(state) {
        val current = state as? NotebookEditorState.Ready ?: return@LaunchedEffect

        when (current.error) {
            NotebookEditorError.Conflict -> showConflictDialog = true
            NotebookEditorError.NotFound -> showNotFoundDialog = true
            else -> Unit
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.reconcileOnResume()
    }

    BackHandler {
        vm.requestEditorExit()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            null, NotebookEditorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is NotebookEditorState.Failed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.notebook_error_read),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = notebookReadFailureText(current.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.notebook_error_read_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                }
            }

            is NotebookEditorState.Ready -> {
                NotebookEditorContent(
                    vm = vm,
                    state = current,
                    showMenu = showMenu,
                    onShowMenuChange = { showMenu = it },
                    onPickOrigin = onPickOriginLesson,
                    onPickDueLesson = onPickDueLesson,
                    onPickDueDate = { dateTarget = NotebookDateTarget.DUE },
                    onPickReminderDate = { dateTarget = NotebookDateTarget.REMINDER },
                    onPickReminderTime = { showTimePicker = true },
                    onDelete = { showDeleteDialog = true },
                    onCancel = vm::requestEditorExit,
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.notebook_exit_title)) },
            text = { Text(stringResource(R.string.notebook_exit_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.notebook_discard))
                }
            },
            dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(R.string.notebook_continue_editing))
                    }
            },
        )
    }

    val ready = state as? NotebookEditorState.Ready
    val readyItemId = ready?.draft?.itemId

    if (showDeleteDialog && readyItemId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.notebook_item_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.notebook_delete_text,
                        ready?.draft?.title?.ifBlank {
                            stringResource(R.string.notebook_untitled)
                        }.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.delete(readyItemId)
                    },
                ) {
                    Text(stringResource(R.string.notebook_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text(stringResource(R.string.notebook_conflict_title)) },
            text = { Text(stringResource(R.string.notebook_conflict_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConflictDialog = false
                        vm.save(overwrite = true)
                    },
                ) {
                    Text(stringResource(R.string.notebook_overwrite))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showConflictDialog = false
                            vm.reloadEditorFromItem()
                        },
                    ) {
                        Text(stringResource(R.string.notebook_conflict_reload))
                    }

                    TextButton(
                        onClick = {
                            showConflictDialog = false
                            vm.clearEditorError()
                        },
                    ) {
                        Text(stringResource(R.string.notebook_continue_editing))
                    }
                }
            },
        )
    }

    if (showNotFoundDialog) {
        AlertDialog(
            onDismissRequest = { showNotFoundDialog = false },
            title = { Text(stringResource(R.string.notebook_not_found_title)) },
            text = { Text(stringResource(R.string.notebook_not_found_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotFoundDialog = false
                        vm.reviveDraftAsNew()
                    },
                ) {
                    Text(stringResource(R.string.notebook_create_as_new))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotFoundDialog = false
                        vm.discardDraftAndClose()
                    },
                ) {
                    Text(stringResource(R.string.notebook_discard))
                }
            },
        )
    }

    if (dateTarget != null && ready != null) {
        val due = ready.draft.due
        val initial = when (dateTarget) {
            NotebookDateTarget.DUE -> due?.date ?: AppClock.today().toUntisDate()
            NotebookDateTarget.REMINDER -> due?.reminder?.date ?: due?.date ?: AppClock.today().toUntisDate()
            null -> AppClock.today().toUntisDate()
        }

        NotebookDatePickerDialog(
            initialDate = initial,
            onSelect = { date ->
                when (dateTarget) {
                    NotebookDateTarget.DUE -> vm.updateDraft { draft ->
                        val currentDue = draft.due

                        draft.copy(
                            due = if (currentDue == null) {
                                DueRef(date = date)
                            } else {
                                currentDue.copy(date = date)
                            },
                        )
                    }

                    NotebookDateTarget.REMINDER -> vm.updateDraft { draft ->
                        val currentDue = draft.due ?: return@updateDraft draft
                        val reminder = currentDue.reminder
                            ?: ReminderSpec(date = date, minutesOfDay = NotebookReminderTime.DEFAULT_MINUTES_OF_DAY)

                        draft.copy(
                            due = currentDue.copy(reminder = reminder.copy(date = date)),
                        )
                    }

                    null -> Unit
                }

                dateTarget = null
            },
            onDismiss = { dateTarget = null },
        )
    }

    if (showTimePicker && ready != null) {
        val reminder = ready.draft.due?.reminder

        NotebookTimePickerDialog(
            initialMinutes = reminder?.minutesOfDay ?: NotebookReminderTime.DEFAULT_MINUTES_OF_DAY,
            onSelect = { minutes ->
                vm.updateDraft { draft ->
                    val currentDue = draft.due ?: return@updateDraft draft
                    val currentReminder = currentDue.reminder
                        ?: ReminderSpec(
                            date = currentDue.date,
                            minutesOfDay = NotebookReminderTime.DEFAULT_MINUTES_OF_DAY,
                        )

                    draft.copy(
                        due = currentDue.copy(
                            reminder = currentReminder.copy(minutesOfDay = minutes),
                        ),
                    )
                }

                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (pendingDueLesson != null && ready != null) {
        val lesson = pendingDueLesson!!
        val currentDate = ready.draft.due?.date ?: AppClock.today().toUntisDate()

        AlertDialog(
            onDismissRequest = vm::cancelPendingDueLesson,
            title = { Text(stringResource(R.string.notebook_due_lesson_date_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.notebook_due_lesson_date_text,
                        notebookDateText(lesson.date),
                        notebookDateText(currentDate),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmPendingDueLesson(useLessonDate = true) }) {
                    Text(stringResource(R.string.notebook_use_lesson_date))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.confirmPendingDueLesson(useLessonDate = false) }) {
                        Text(stringResource(R.string.notebook_keep_due_date))
                    }

                    TextButton(onClick = vm::cancelPendingDueLesson) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    conversion?.let { request ->
        NotebookConversionDialog(
            request = request,
            onConfirm = { vm.confirmConversion(request.itemId, request.target) },
            onSaveFirst = { vm.saveThenConvert(request.itemId, request.target) },
            onDiscardChanges = { vm.discardChangesAndConvert(request.itemId, request.target) },
            onDismiss = vm::clearPendingConversion,
        )
    }
}

@Composable
private fun NotebookEditorContent(
    vm: NotebookViewModel,
    state: NotebookEditorState.Ready,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    onPickOrigin: () -> Unit,
    onPickDueLesson: () -> Unit,
    onPickDueDate: () -> Unit,
    onPickReminderDate: () -> Unit,
    onPickReminderTime: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft = state.draft
    val readOnly = state.readOnly || state.saving
    val due = draft.due
    val item = state.item
    val title = draft.title
    val body = draft.body
    var showKindWarning by remember { mutableStateOf(false) }
    val nextLesson = remember(state.lessonCache, draft.subject, draft.origin, draft.due?.lesson) {
        notebookNextLesson(
            cache = state.lessonCache,
            now = AppClock.now(),
            subject = draft.subject,
            dueLesson = draft.due?.lesson,
            origin = draft.origin,
        )
    }
    val originWarning = remember(draft.origin, state.lessonCache) {
        draft.origin?.let { notebookLessonWarning(state.lessonCache, it) }
    }
    val dueLessonWarning = remember(draft.due?.lesson, state.lessonCache) {
        draft.due?.lesson?.let { notebookLessonWarning(state.lessonCache, it) }
    }
    val reminder = due?.reminder
    val reminderInPast = reminder?.let { spec ->
        NotebookReminderTime.triggerAt(spec, ZoneId.systemDefault())
            ?.let { it < System.currentTimeMillis() }
            ?: false
    } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        if (!state.isNew) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (draft.kind == NotebookKind.TASK) {
                        R.string.notebook_kind_task
                    } else {
                        R.string.notebook_kind_note
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            Box {
                if (item != null) {
                    IconButton(onClick = { onShowMenuChange(true) }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.notebook_more_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onShowMenuChange(false) },
                ) {
                    item?.let { existing ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (existing.isTask) {
                                            R.string.notebook_convert_to_note
                                        } else {
                                            R.string.notebook_convert_to_task
                                        },
                                    ),
                                )
                            },
                            onClick = {
                                onShowMenuChange(false)
                                vm.requestConversion(
                                    existing.id,
                                    if (existing.isTask) NotebookKind.NOTE else NotebookKind.TASK,
                                )
                            },
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notebook_delete)) },
                            onClick = {
                                onShowMenuChange(false)
                                onDelete()
                            },
                        )
                    }
                }
            }
        }

        if (state.readOnly) {
            Text(
                text = stringResource(R.string.notebook_error_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        state.error?.let { error ->
            Text(
                text = notebookEditorErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isNew) {
                SettingsModeSelector(
                    options = listOf(NotebookKind.TASK, NotebookKind.NOTE),
                    selected = draft.kind,
                    onSelect = { kind ->
                        if (kind == NotebookKind.NOTE && due != null) showKindWarning = true
                        else vm.updateDraft { it.copy(kind = kind) }
                    },
                    label = { kind -> stringResource(
                        if (kind == NotebookKind.TASK) R.string.notebook_kind_task
                        else R.string.notebook_kind_note,
                    ) },
                    enabled = !readOnly,
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { value ->
                    vm.updateDraft { it.copy(title = value.take(NotebookValidator.MAX_TITLE)) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !readOnly,
                singleLine = true,
                label = { Text(stringResource(R.string.notebook_title_label)) },
                shape = MaterialTheme.shapes.medium,
            )

            OutlinedTextField(
                value = body,
                onValueChange = { value ->
                    vm.updateDraft { it.copy(body = value.take(NotebookValidator.MAX_BODY)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                enabled = !readOnly,
                minLines = 8,
                label = { Text(stringResource(R.string.notebook_body_label)) },
                shape = MaterialTheme.shapes.medium,
            )

            if (!notebookHasContent(title, body)) {
                Text(
                    text = stringResource(R.string.notebook_body_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            EditorFieldRow(
                label = stringResource(R.string.notebook_origin),
                value = draft.origin?.let { notebookLessonTimeText(it) }
                    ?: stringResource(R.string.notebook_select_in_timetable),
                onClick = onPickOrigin,
                enabled = !readOnly,
                trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                onClear = if (draft.origin != null) {
                    { vm.updateDraft { it.copy(origin = null) } }
                } else {
                    null
                },
            )

            originWarning?.let { warning ->
                LessonWarningText(warning)
            }

            if (draft.kind == NotebookKind.TASK) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.notebook_due),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )

                            Switch(
                                checked = due != null,
                                onCheckedChange = { enabled ->
                                    vm.updateDraft { draft ->
                                        draft.copy(
                                            due = if (enabled) {
                                                DueRef(date = AppClock.today().toUntisDate())
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                },
                                enabled = !readOnly,
                            )
                        }

                        due?.let { currentDue ->
                            EditorFieldRow(
                                label = stringResource(R.string.notebook_due_date),
                                value = notebookDateText(currentDue.date),
                                onClick = onPickDueDate,
                                enabled = !readOnly,
                                trailingIcon = Icons.Outlined.DateRange,
                            )

                            EditorFieldRow(
                                label = stringResource(R.string.notebook_due_lesson),
                                value = currentDue.lesson?.let { notebookLessonTimeText(it) }
                                    ?: stringResource(R.string.notebook_select_in_timetable),
                                onClick = onPickDueLesson,
                                enabled = !readOnly,
                                trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                onClear = if (currentDue.lesson != null) {
                                    {
                                        vm.updateDraft { draft ->
                                            draft.copy(due = draft.due?.copy(lesson = null))
                                        }
                                    }
                                } else {
                                    null
                                },
                            )

                            dueLessonWarning?.let { warning ->
                                LessonWarningText(warning)
                            }

                            OutlinedButton(
                                onClick = {
                                    val lesson = nextLesson ?: return@OutlinedButton
                                    vm.applyDueLesson(lesson.toRef())
                                },
                                enabled = !readOnly && nextLesson != null,
                            ) {
                                Text(stringResource(R.string.notebook_next_lesson))
                            }

                            if (nextLesson == null) {
                                Text(
                                    text = stringResource(R.string.notebook_next_lesson_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.notebook_reminder),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )

                                Switch(
                                    checked = currentDue.reminder != null,
                                    onCheckedChange = { enabled ->
                                        vm.updateDraft { draft ->
                                            val target = draft.due ?: return@updateDraft draft

                                            draft.copy(
                                                due = target.copy(
                                                    reminder = if (enabled) {
                                                        ReminderSpec(
                                                            date = target.date,
                                                            minutesOfDay = NotebookReminderTime.DEFAULT_MINUTES_OF_DAY,
                                                        )
                                                    } else {
                                                        null
                                                    },
                                                ),
                                            )
                                        }
                                    },
                                    enabled = !readOnly,
                                )
                            }

                            currentDue.reminder?.let { currentReminder ->
                                EditorFieldRow(
                                    label = stringResource(R.string.notebook_reminder_date),
                                    value = notebookDateText(currentReminder.date),
                                    onClick = onPickReminderDate,
                                    enabled = !readOnly,
                                    trailingIcon = Icons.Outlined.DateRange,
                                )

                                EditorFieldRow(
                                    label = stringResource(R.string.notebook_reminder_time),
                                    value = notebookTimeText(
                                        currentReminder.minutesOfDay / 60 * 100 +
                                            currentReminder.minutesOfDay % 60,
                                    ),
                                    onClick = onPickReminderTime,
                                    enabled = !readOnly,
                                    trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                )

                                OutlinedButton(
                                    onClick = {
                                        vm.updateDraft { draft ->
                                            val target = draft.due ?: return@updateDraft draft
                                            val spec = target.reminder ?: return@updateDraft draft

                                            draft.copy(
                                                due = target.copy(
                                                    reminder = spec.copy(
                                                        date = notebookPreviousDay(target.date),
                                                    ),
                                                ),
                                            )
                                        }
                                    },
                                    enabled = !readOnly,
                                ) {
                                    Text(stringResource(R.string.notebook_reminder_previous_day))
                                }

                                if (reminderInPast) {
                                    Text(
                                        text = stringResource(R.string.notebook_reminder_past),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                }
            }

            item?.let { existing ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = appCardShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (existing.isTask) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.notebook_completed),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )

                                Switch(
                                    checked = existing.isCompleted,
                                    onCheckedChange = { checked -> vm.updateEditorFlags {
                                        it.copy(completedAt = if (checked) System.currentTimeMillis() else null)
                                    } },
                                    enabled = !readOnly,
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconToggleButton(
                                    checked = existing.isPinned,
                                    onCheckedChange = { checked -> vm.updateEditorFlags {
                                        it.copy(pinnedAt = if (checked) System.currentTimeMillis() else null)
                                    } },
                                    enabled = !readOnly,
                                ) {
                                    Icon(
                                        imageVector = notebookPinIcon(existing.isPinned),
                                        contentDescription = stringResource(
                                            if (existing.isPinned) R.string.notebook_unpin else R.string.notebook_pin,
                                        ),
                                        tint = if (existing.isPinned) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.notebook_archived),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )

                                Switch(
                                    checked = existing.isArchived,
                                    onCheckedChange = { checked -> vm.updateEditorFlags {
                                        it.copy(archivedAt = if (checked) System.currentTimeMillis() else null)
                                    } },
                                    enabled = !readOnly,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                enabled = !state.saving,
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = { vm.save() },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                enabled = notebookEditorCanSave(state),
            ) {
                Text(stringResource(R.string.notebook_save))
            }
        }
    }

    if (showKindWarning) {
        AlertDialog(
            onDismissRequest = { showKindWarning = false },
            title = { Text(stringResource(R.string.notebook_conversion_title)) },
            text = { Text(stringResource(R.string.notebook_new_note_remove_due)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateDraft { it.copy(kind = NotebookKind.NOTE, due = null) }
                    showKindWarning = false
                }) { Text(stringResource(R.string.notebook_convert)) }
            },
            dismissButton = {
                TextButton(onClick = { showKindWarning = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EditorFieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean,
    trailingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            trailingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        onClear?.let { clear ->
            IconButton(
                onClick = clear,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = stringResource(R.string.notebook_clear),
                )
            }
        }
    }
}

@Composable
private fun LessonWarningText(warning: NotebookLessonWarning) {
    Text(
        text = stringResource(
            when (warning) {
                NotebookLessonWarning.MISSING -> R.string.notebook_lesson_missing
                NotebookLessonWarning.AMBIGUOUS -> R.string.notebook_lesson_ambiguous
                NotebookLessonWarning.CANCELLED -> R.string.notebook_lesson_cancelled
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun notebookEditorErrorText(error: NotebookEditorError): String = when (error) {
    is NotebookEditorError.ReadFailed -> notebookReadFailureText(error.reason)
    is NotebookEditorError.StorageFailed -> notebookStorageFailureText(error.reason)
    is NotebookEditorError.Invalid -> error.issues.firstOrNull()
        ?.let { notebookValidationText(it) }
        ?: stringResource(R.string.notebook_error_invalid)

    NotebookEditorError.Conflict -> stringResource(R.string.notebook_conflict_text)
    NotebookEditorError.NotFound -> stringResource(R.string.notebook_not_found_text)
    NotebookEditorError.SaveFailed -> stringResource(R.string.notebook_save_failed)
}

@Composable
private fun NotebookConversionDialog(
    request: NotebookConversionRequest,
    onConfirm: () -> Unit,
    onSaveFirst: () -> Unit,
    onDiscardChanges: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lost = request.warning?.lostFields.orEmpty()
        .map { field ->
            stringResource(
                when (field) {
                    NotebookField.DUE -> R.string.notebook_conversion_due
                    NotebookField.REMINDER -> R.string.notebook_conversion_reminder
                    NotebookField.COMPLETION -> R.string.notebook_conversion_completion
                    NotebookField.PINNED -> R.string.notebook_conversion_pinned
                    NotebookField.ARCHIVE -> R.string.notebook_conversion_archive
                },
            )
        }
        .joinToString(", ")

    val warningText = if (lost.isBlank()) {
        null
    } else {
        stringResource(R.string.notebook_conversion_text, lost)
    }

    if (request.requiresEditorDecision) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.notebook_conversion_unsaved_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.notebook_conversion_unsaved_text))
                    warningText?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = onSaveFirst) {
                    Text(stringResource(R.string.notebook_conversion_save_first))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDiscardChanges) {
                        Text(stringResource(R.string.notebook_conversion_discard_convert))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.notebook_continue_editing))
                    }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.notebook_conversion_title)) },
            text = {
                Text(
                    warningText ?: stringResource(R.string.notebook_conversion_text_none),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.notebook_convert))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookDatePickerDialog(
    initialDate: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate.toUntisLocalDateOrNull()
        ?.atStartOfDay(ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis

                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toUntisDate()
                        onSelect(date)
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.notebook_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookTimePickerDialog(
    initialMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = (initialMinutes % 60).coerceIn(0, 59),
        is24Hour = DateFormat.is24HourFormat(context),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_reminder_time)) },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(state.hour * 60 + state.minute)
                },
            ) {
                Text(stringResource(R.string.notebook_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
