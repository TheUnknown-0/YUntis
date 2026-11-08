package sh.lsx.untisx.ui.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookField
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookSearch
import sh.lsx.untisx.data.notebook.NotebookStatusFilter
import sh.lsx.untisx.data.notebook.SubjectRef
import sh.lsx.untisx.data.subjectShortLabel
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.timetable.rememberToday

@Composable
fun NotebookListScreen(
    vm: NotebookViewModel,
    subjects: List<SubjectRef>,
    accountLabel: String,
    focusNotice: NotebookFocusNotice?,
    onFocusNoticeShown: () -> Unit,
    onOpenItem: (String) -> Unit,
    onCreate: (NotebookKind, LessonRef?) -> Unit,
    onJumpToDate: (Int) -> Unit,
    subjectAliases: Map<String, SubjectAlias> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val state by vm.listState.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val undo by vm.pendingUndo.collectAsStateWithLifecycle()
    val conversion by vm.pendingConversion.collectAsStateWithLifecycle()

    val today = rememberToday()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchText by remember { mutableStateOf(vm.query.text) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NotebookItem?>(null) }

    LaunchedEffect(vm.query.text) {
        if (vm.query.text != searchText) searchText = vm.query.text
    }

    LaunchedEffect(searchText) {
        delay(250)
        vm.setSearchText(searchText)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.reconcileOnResume()
    }

    val noticeText = notice?.let { notebookNoticeText(it) }
    val undoLabel = stringResource(R.string.notebook_undo)
    val undoMessage = stringResource(R.string.notebook_updated)
    val focusNoticeText = when (focusNotice) {
        NotebookFocusNotice.ACCOUNT_MISSING ->
            stringResource(R.string.notebook_notification_account_missing)

        NotebookFocusNotice.ITEM_MISSING ->
            stringResource(R.string.notebook_notification_item_missing)

        null -> null
    }

    LaunchedEffect(notice, noticeText) {
        if (notice != null && noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            vm.clearNotice()
        }
    }

    LaunchedEffect(undo) {
        val current = undo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = undoMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )

        if (result == SnackbarResult.ActionPerformed) {
            vm.undoLastAction()
        } else {
            vm.clearPendingUndoIf(current)
        }
    }

    LaunchedEffect(focusNotice, focusNoticeText) {
        if (focusNotice != null && focusNoticeText != null) {
            snackbarHostState.showSnackbar(focusNoticeText)
            onFocusNoticeShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            NotebookListState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is NotebookListState.Failed -> {
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

                    OutlinedButton(onClick = vm::refresh) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is NotebookListState.Ready -> {
                NotebookListContent(
                    vm = vm,
                    state = current,
                    subjects = subjects,
                    subjectAliases = subjectAliases,
                    accountLabel = accountLabel,
                    today = today,
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    subjectMenuExpanded = subjectMenuExpanded,
                    onSubjectMenuExpandedChange = { subjectMenuExpanded = it },
                    statusMenuExpanded = statusMenuExpanded,
                    onStatusMenuExpandedChange = { statusMenuExpanded = it },
                    onOpenItem = onOpenItem,
                    onCreate = onCreate,
                    onDelete = { pendingDelete = it },
                    onJumpToDate = onJumpToDate,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 88.dp),
        )

        if (state is NotebookListState.Ready) {
            ExtendedFloatingActionButton(
                onClick = {
                    onCreate(
                        if (vm.selectedTab == NotebookTab.TASKS) NotebookKind.TASK else NotebookKind.NOTE,
                        null,
                    )
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = {
                    Text(
                        stringResource(
                            if (vm.selectedTab == NotebookTab.TASKS) {
                                R.string.notebook_new_task
                            } else {
                                R.string.notebook_new_note
                            },
                        ),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.notebook_item_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.notebook_delete_text,
                        item.displayTitle.ifBlank {
                            stringResource(R.string.notebook_untitled)
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(item.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.notebook_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    conversion?.let { request ->
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

        AlertDialog(
            onDismissRequest = vm::clearPendingConversion,
            title = { Text(stringResource(R.string.notebook_conversion_title)) },
            text = {
                Text(
                    if (lost.isBlank()) {
                        stringResource(R.string.notebook_conversion_text_none)
                    } else {
                        stringResource(R.string.notebook_conversion_text, lost)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.confirmConversion(request.itemId, request.target) },
                ) {
                    Text(stringResource(R.string.notebook_convert))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::clearPendingConversion) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookListContent(
    vm: NotebookViewModel,
    state: NotebookListState.Ready,
    subjects: List<SubjectRef>,
    subjectAliases: Map<String, SubjectAlias>,
    accountLabel: String,
    today: java.time.LocalDate,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    subjectMenuExpanded: Boolean,
    onSubjectMenuExpandedChange: (Boolean) -> Unit,
    statusMenuExpanded: Boolean,
    onStatusMenuExpandedChange: (Boolean) -> Unit,
    onOpenItem: (String) -> Unit,
    onCreate: (NotebookKind, LessonRef?) -> Unit,
    onDelete: (NotebookItem) -> Unit,
    onJumpToDate: (Int) -> Unit,
) {
    val tab = vm.selectedTab
    val taskListState = rememberLazyListState(initialFirstVisibleItemIndex = vm.taskScrollIndex)
    val noteListState = rememberLazyListState(initialFirstVisibleItemIndex = vm.noteScrollIndex)
    val listState = if (tab == NotebookTab.TASKS) taskListState else noteListState

    LaunchedEffect(tab, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            vm.setScrollIndex(tab, index)
        }
    }

    val filtered = remember(state.items, vm.query, vm.withoutSubject) {
        NotebookSearch.apply(state.items, vm.query).let { items ->
            if (vm.withoutSubject) notebookFilterWithoutSubject(items) else items
        }
    }

    val openTasks = remember(state.items) { notebookOpenTaskCount(state.items) }
    val activeNotes = remember(state.items) {
        state.items.count { !it.isTask && !it.isArchived }
    }

    val taskSections = remember(filtered, today) { notebookTaskSections(filtered, today) }
    val completedTasks = remember(filtered) {
        NotebookSearch.sort(filtered.filter { it.isTask && it.isCompleted })
    }
    val notes = remember(filtered) { notebookNotes(filtered) }

    Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (searchText.isNotBlank()) {
                    {
                        IconButton(onClick = { onSearchTextChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.notebook_clear),
                            )
                        }
                    }
                } else {
                    null
                },
                placeholder = { Text(stringResource(R.string.notebook_search_hint)) },
                shape = appCardShape(16.dp),
            )


        Spacer(Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = if (tab == NotebookTab.TASKS) 0 else 1,
        ) {
            Tab(
                selected = tab == NotebookTab.TASKS,
                onClick = { vm.setTab(NotebookTab.TASKS) },
                text = {
                    Text(stringResource(R.string.notebook_tasks_count, openTasks))
                },
            )

            Tab(
                selected = tab == NotebookTab.NOTES,
                onClick = { vm.setTab(NotebookTab.NOTES) },
                text = {
                    Text(stringResource(R.string.notebook_notes_count, activeNotes))
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statuses = if (tab == NotebookTab.TASKS) {
                listOf(NotebookStatusFilter.OPEN to R.string.notebook_filter_open,
                    NotebookStatusFilter.COMPLETED to R.string.notebook_filter_completed)
            } else {
                listOf(NotebookStatusFilter.OPEN to R.string.notebook_filter_active,
                    NotebookStatusFilter.ARCHIVED to R.string.notebook_filter_archived)
            }

            val selectedStatus = if (tab == NotebookTab.TASKS) vm.taskStatusFilter else vm.noteStatusFilter

            ExposedDropdownMenuBox(
                expanded = statusMenuExpanded,
                onExpandedChange = onStatusMenuExpandedChange,
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = stringResource(statuses.first { it.first == selectedStatus }.second),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.notebook_filter_status)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusMenuExpanded) },
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = MaterialTheme.shapes.medium,
                )

                ExposedDropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { onStatusMenuExpandedChange(false) },
                ) {
                    statuses.forEach { (status, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                if (tab == NotebookTab.TASKS) vm.updateTaskStatusFilter(status)
                                else vm.updateNoteStatusFilter(status)
                                onStatusMenuExpandedChange(false)
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = subjectMenuExpanded,
                onExpandedChange = onSubjectMenuExpandedChange,
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                        value = when {
                            vm.withoutSubject -> stringResource(R.string.notebook_filter_without_subject)
                            vm.subjectFilter != null -> subjectShortLabel(
                                subjectAliases,
                                vm.subjectFilter!!.displayName,
                            )
                            else -> stringResource(R.string.notebook_filter_subject_all)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.notebook_subject)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectMenuExpanded) },
                        modifier = Modifier.fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = MaterialTheme.shapes.medium,
                )

                ExposedDropdownMenu(
                    expanded = subjectMenuExpanded,
                    onDismissRequest = { onSubjectMenuExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notebook_filter_subject_all)) },
                        onClick = {
                            vm.clearSubjectFilter()
                            onSubjectMenuExpandedChange(false)
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notebook_filter_without_subject)) },
                        onClick = {
                            vm.setWithoutSubject()
                            onSubjectMenuExpandedChange(false)
                        },
                    )

                    subjects.forEach { subject ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = subjectShortLabel(subjectAliases, subject.displayName),
                                )
                            },
                            onClick = {
                                vm.updateSubjectFilter(subject)
                                onSubjectMenuExpandedChange(false)
                            },
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 4.dp,
                bottom = 96.dp,
            ),
        ) {
            val showEmpty = when (tab) {
                NotebookTab.TASKS ->
                    if (vm.taskStatusFilter == NotebookStatusFilter.COMPLETED) {
                        completedTasks.isEmpty()
                    } else {
                        taskSections.isEmpty()
                    }

                NotebookTab.NOTES -> notes.isEmpty()
            }

            if (showEmpty) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = stringResource(
                            if (state.items.isNotEmpty()) {
                                R.string.notebook_empty_filtered
                            } else {
                                when (tab) {
                                    NotebookTab.TASKS -> R.string.notebook_empty_tasks
                                    NotebookTab.NOTES -> R.string.notebook_empty_notes
                                }
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
            } else {
                when (tab) {
                    NotebookTab.TASKS -> {
                        if (vm.taskStatusFilter == NotebookStatusFilter.COMPLETED) {
                            item(key = "completed-header", contentType = "header") {
                                Text(
                                    text = stringResource(R.string.notebook_filter_completed),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            items(
                                items = completedTasks,
                                key = { item -> item.id },
                                contentType = { "task" },
                            ) { item ->
                                NotebookTaskRow(
                                    vm = vm,
                                    item = item,
                                    today = today,
                                    onOpenItem = onOpenItem,
                                    onDelete = onDelete,
                                    onJumpToDate = onJumpToDate,
                                )
                            }
                        } else {
                            taskSections.forEach { section ->
                                item(
                                    key = "header-${section.group}",
                                    contentType = "header",
                                ) {
                                    Text(
                                        text = stringResource(
                                            when (section.group) {
                                                NotebookTaskGroup.OVERDUE -> R.string.notebook_group_overdue
                                                NotebookTaskGroup.TODAY -> R.string.notebook_group_today
                                                NotebookTaskGroup.UPCOMING -> R.string.notebook_group_upcoming
                                                NotebookTaskGroup.NO_DATE -> R.string.notebook_group_no_date
                                            },
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }

                                items(
                                    items = section.items,
                                    key = { item -> item.id },
                                    contentType = { "task" },
                                ) { item ->
                                    NotebookTaskRow(
                                        vm = vm,
                                        item = item,
                                        today = today,
                                        onOpenItem = onOpenItem,
                                        onDelete = onDelete,
                                        onJumpToDate = onJumpToDate,
                                    )
                                }
                            }
                        }
                    }

                    NotebookTab.NOTES -> {
                        items(
                            items = notes,
                            key = { item -> item.id },
                            contentType = { "note" },
                        ) { item ->
                            NotebookItemRow(
                                item = item,
                                today = today,
                                onClick = { onOpenItem(item.id) },
                                onToggleCompleted = {},
                                onTogglePinned = { vm.togglePinned(item.id) },
                                onArchive = { vm.toggleArchived(item.id) },
                                onDelete = { onDelete(item) },
                                onConvert = { target -> vm.requestConversion(item.id, target) },
                                onJumpToDate = onJumpToDate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookTaskRow(
    vm: NotebookViewModel,
    item: NotebookItem,
    today: java.time.LocalDate,
    onOpenItem: (String) -> Unit,
    onDelete: (NotebookItem) -> Unit,
    onJumpToDate: (Int) -> Unit,
) {
    NotebookItemRow(
        item = item,
        today = today,
        onClick = { onOpenItem(item.id) },
        onToggleCompleted = { vm.toggleCompleted(item.id) },
        onTogglePinned = {},
        onArchive = {},
        onDelete = { onDelete(item) },
        onConvert = { target -> vm.requestConversion(item.id, target) },
        onJumpToDate = onJumpToDate,
    )
}
