package sh.lsx.untisx.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookNotificationContract
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.components.AppDrawer
import sh.lsx.untisx.ui.components.AppTopBar
import sh.lsx.untisx.ui.messages.MessagesViewModel
import sh.lsx.untisx.ui.nav.AnimatedScreenHost
import sh.lsx.untisx.ui.nav.Screen
import sh.lsx.untisx.ui.nav.ScreenHost
import sh.lsx.untisx.ui.notebook.NotebookEditorSession
import sh.lsx.untisx.ui.notebook.NotebookEditorSessionSaver
import sh.lsx.untisx.ui.notebook.NotebookEditorState
import sh.lsx.untisx.ui.notebook.NotebookFocusNotice
import sh.lsx.untisx.ui.notebook.NotebookLessonTarget
import sh.lsx.untisx.ui.notebook.NotebookListState
import sh.lsx.untisx.ui.notebook.NotebookViewModel
import sh.lsx.untisx.ui.screens.settings.SettingsCategory
import sh.lsx.untisx.ui.timetable.ErrorBanner
import sh.lsx.untisx.ui.timetable.InfoBanner
import sh.lsx.untisx.ui.timetable.LessonCardOptions
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.uiTextString
import sh.lsx.untisx.update.UpdateViewModel

private val entryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val timetableEntrySaver: Saver<TimetableEntry?, String> = Saver(
    save = { entry ->
        entry?.let { runCatching { entryJson.encodeToString(TimetableEntry.serializer(), it) }.getOrNull() }
    },
    restore = { text ->
        runCatching { entryJson.decodeFromString(TimetableEntry.serializer(), text) }.getOrNull()
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainApp(
    auth: AuthData,
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    showLastRefreshed: Boolean,
    onShowLastRefreshedChange: (Boolean) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsChange: (Boolean) -> Unit,
    subjectColors: Map<String, Int>,
    knownSubjects: List<String>,
    onSubjectColor: (String, Int?) -> Unit,
    subjectTextColors: Map<String, Int>,
    onSubjectTextColor: (String, Int?) -> Unit,
    subjectAliases: Map<String, SubjectAlias>,
    onSubjectAlias: (String, String?, String?) -> Unit,
    defaultSubjectColor: Int,
    onDefaultSubjectColor: (Int) -> Unit,
    lessonTextColor: Int,
    onLessonTextColor: (Int) -> Unit,
    strikeColor: Int?,
    onStrikeColor: (Int?) -> Unit,
    examColor: Int?,
    onExamColor: (Int?) -> Unit,
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
    hideCancelled: Boolean,
    onHideCancelledChange: (Boolean) -> Unit,
    focusDate: Int,
    focusScreen: String?,
    focusNonce: Int,
    focusNotebookItemId: String?,
    focusNotebookAccountId: String?,
    onFocusConsumed: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerOpen = drawerState.targetValue == DrawerValue.Open
    val scope = rememberCoroutineScope()
    val timetableVm: TimetableViewModel = viewModel(
        key = AccountPrefs.accountId(auth),
    )
    val messagesVm: MessagesViewModel = viewModel(
        key = "messages:${AccountPrefs.accountId(auth)}",
    )
    val notebookVm: NotebookViewModel = viewModel(
        key = "notebook:${AccountPrefs.accountId(auth)}",
    )
    val updateVm: UpdateViewModel = viewModel()

    val notebookListState by notebookVm.listState.collectAsStateWithLifecycle()
    val notebookEditorState by notebookVm.editorState.collectAsStateWithLifecycle()
    val notebookEditorNonce by notebookVm.openEditorNonce.collectAsStateWithLifecycle()
    var notebookFocusNotice by remember { mutableStateOf<NotebookFocusNotice?>(null) }
    val notebookItems = (notebookListState as? NotebookListState.Ready)?.items.orEmpty()
    val notebookOpenTasks = remember(notebookItems) {
        notebookItems.count { it.isTask && !it.isCompleted }
    }
    val context = LocalContext.current

    DisposableEffect(timetableVm) {
        onDispose { timetableVm.cancelBackgroundWork() }
    }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.TIMETABLE) }
    var selectedLesson by rememberSaveable(stateSaver = timetableEntrySaver) {
        mutableStateOf<TimetableEntry?>(null)
    }
    var selectedEvent by rememberSaveable(stateSaver = timetableEntrySaver) {
        mutableStateOf<TimetableEntry?>(null)
    }
    var previousScreen by rememberSaveable { mutableStateOf(Screen.TIMETABLE) }
    var settingsCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var examFocusDate by rememberSaveable { mutableStateOf(0) }
    var examFocusNonce by rememberSaveable { mutableStateOf(0) }
    var notebookSession by rememberSaveable(stateSaver = NotebookEditorSessionSaver) {
        mutableStateOf(NotebookEditorSession())
    }
    var notebookSessionAccount by rememberSaveable {
        mutableStateOf(AccountPrefs.accountId(auth))
    }
    val notebookSelectionTarget = notebookSession.pickerTarget
    val notebookSelectionActive = notebookSelectionTarget != null
    var previousNotebookVm by remember { mutableStateOf(notebookVm) }
    LaunchedEffect(notebookVm) {
        if (previousNotebookVm !== notebookVm) {
            previousNotebookVm.closeEditor()
            previousNotebookVm = notebookVm
        }
    }
    var pendingNotebookLeave by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestNotebookLeave(action: () -> Unit) {
        val editor = notebookVm.editorState.value as? NotebookEditorState.Ready

        if (editor?.saving == true) return

        val leave = {
            notebookSession = notebookSession.pickerClosed()
            notebookVm.closeEditor()
            action()
        }

        if (editor?.isDirty == true) pendingNotebookLeave = leave else leave()
    }

    if (pendingNotebookLeave != null) {
        AlertDialog(
            onDismissRequest = { pendingNotebookLeave = null; onFocusConsumed() },
            title = { Text(stringResource(R.string.notebook_exit_title)) },
            text = { Text(stringResource(R.string.notebook_exit_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val leave = pendingNotebookLeave
                    pendingNotebookLeave = null
                    leave?.invoke()
                }) { Text(stringResource(R.string.notebook_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingNotebookLeave = null; onFocusConsumed() }) {
                    Text(stringResource(R.string.notebook_continue_editing))
                }
            },
        )
    }

    LaunchedEffect(focusNonce) {
        val focus = focusScreen

        when (focus) {
            Notifications.FOCUS_MESSAGES -> {
                requestNotebookLeave {
                messagesVm.refresh()
                currentScreen = Screen.MESSAGES
                onFocusConsumed()
                }
            }

            Notifications.FOCUS_EVENTS -> {
                requestNotebookLeave {
                timetableVm.ensureEvents()
                currentScreen = Screen.EVENTS
                onFocusConsumed()
                }
            }

            Notifications.FOCUS_ACCOUNT -> {
                requestNotebookLeave {
                currentScreen = Screen.PROFILE
                onFocusConsumed()
                }
            }
        }
    }

    LaunchedEffect(notebookEditorNonce) {
        if (notebookEditorNonce > 0 && notebookEditorState != null && notebookSession.pickerTarget == null) {
            if (currentScreen == Screen.NOTEBOOK) {
                notebookSession = notebookSession.openedFrom(Screen.NOTEBOOK)
            }

            currentScreen = Screen.NOTEBOOK_EDITOR
        }
    }

    LaunchedEffect(AccountPrefs.accountId(auth)) {
        val account = AccountPrefs.accountId(auth)

        if (account != notebookSessionAccount) {
            notebookSessionAccount = account
            notebookSession = NotebookEditorSession()
            pendingNotebookLeave = null

            if (currentScreen == Screen.NOTEBOOK_EDITOR || notebookSelectionActive) {
                notebookVm.closeEditor()
                currentScreen = Screen.NOTEBOOK
            }
        }
    }

    LaunchedEffect(notebookSession.pickerTarget, notebookEditorState) {
        if (notebookSession.pickerTarget != null && notebookEditorState == null) {
            val returnScreen = notebookSession.returnScreen

            notebookSession = notebookSession.pickerClosed()
            currentScreen = returnScreen
        }
    }

    LaunchedEffect(
        focusNonce,
        focusNotebookItemId,
        focusNotebookAccountId,
        AccountPrefs.accountId(auth),
    ) {
        if (focusScreen != NotebookNotificationContract.FOCUS_NOTEBOOK) return@LaunchedEffect

        requestNotebookLeave {
            val targetAccount = focusNotebookAccountId
            val currentAccount = AccountPrefs.accountId(auth)

            if (!targetAccount.isNullOrBlank() && targetAccount != currentAccount) {
                val known = AccountStore.snapshot.any { AccountPrefs.accountId(it) == targetAccount }

                if (known) {
                    scope.launch { AccountStore.setActive(context, targetAccount) }
                } else {
                    notebookFocusNotice = NotebookFocusNotice.ACCOUNT_MISSING
                    notebookSession = notebookSession.openedFrom(Screen.NOTEBOOK)
                    currentScreen = Screen.NOTEBOOK
                    onFocusConsumed()
                }
            } else {
                notebookSession = notebookSession.openedFrom(Screen.NOTEBOOK)
                currentScreen = Screen.NOTEBOOK

                if (!focusNotebookItemId.isNullOrBlank()) {
                    notebookVm.openItem(focusNotebookItemId)
                } else {
                    notebookVm.refresh()
                }

                onFocusConsumed()
            }
        }
    }

    fun openEvent(entry: TimetableEntry, from: Screen) {
        selectedEvent = entry
        timetableVm.loadLessonNotes(entry)
        previousScreen = from
        currentScreen = Screen.EVENT_DETAILS
    }

    fun openNotebookNew(kind: NotebookKind, origin: LessonRef?, from: Screen) {
        notebookSession = notebookSession.openedFrom(from)
        notebookVm.openNew(kind, origin)
    }

    fun openNotebookItem(itemId: String, from: Screen) {
        notebookSession = notebookSession.openedFrom(from)
        notebookVm.openItem(itemId)
    }

    fun startNotebookLessonPicker(target: NotebookLessonTarget) {
        if (notebookVm.editorState.value is NotebookEditorState.Ready) {
            notebookSession = notebookSession.picker(target)
            currentScreen = Screen.TIMETABLE
        }
    }

    fun cancelNotebookLessonPicker() {
        notebookSession = notebookSession.pickerClosed()
        currentScreen = Screen.NOTEBOOK_EDITOR
    }

    fun completeNotebookLessonPicker(entry: TimetableEntry) {
        val target = notebookSession.pickerTarget ?: return

        notebookVm.applyTimetableLesson(target, entry)
        notebookSession = notebookSession.pickerClosed()
        currentScreen = Screen.NOTEBOOK_EDITOR
    }

    fun goBack() {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            notebookSelectionActive -> cancelNotebookLessonPicker()
            currentScreen == Screen.SETTINGS && settingsCategory != null -> settingsCategory = null
            currentScreen == Screen.NOTEBOOK_EDITOR -> notebookVm.requestEditorExit()
            currentScreen == Screen.NOTEBOOK -> currentScreen = Screen.TIMETABLE
            currentScreen == Screen.EVENT_DETAILS -> currentScreen = previousScreen
            currentScreen == Screen.MESSAGE_DETAILS -> currentScreen = Screen.MESSAGES
            else -> currentScreen = Screen.TIMETABLE
        }
    }

    val backProgress = remember { Animatable(0f) }

    BackHandler(
        enabled = drawerState.isOpen ||
            (notebookSelectionActive && !animationsEnabled) ||
            (
                currentScreen != Screen.TIMETABLE &&
                    currentScreen != Screen.NOTEBOOK_EDITOR &&
                    !animationsEnabled
                ),
    ) {
        goBack()
    }

    PredictiveBackHandler(
        enabled = (
            notebookSelectionActive ||
                (
                    currentScreen != Screen.TIMETABLE &&
                        currentScreen != Screen.NOTEBOOK_EDITOR
                    )
                ) &&
            !drawerState.isOpen &&
            animationsEnabled,
    ) { progress ->
        try {
            progress.collect { event -> backProgress.snapTo(event.progress) }
            goBack()
            backProgress.snapTo(0f)
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, AppMotion.spatial<Float>()) }
        }
    }

    val allTeachers = remember {
        derivedStateOf {
            timetableVm.teachersById.values
                .filter { it.name.isNotBlank() }
                .sortedBy { it.longName.ifBlank { it.name } }
        }
    }.value

    fun selectDrawerTarget(target: Screen) {
        when (target) {
            Screen.MESSAGES -> {
                messagesVm.refresh()
                currentScreen = Screen.MESSAGES
            }

            Screen.EVENTS -> {
                timetableVm.ensureEvents()
                currentScreen = Screen.EVENTS
            }

            Screen.NOTEBOOK -> {
                notebookVm.refresh()
                currentScreen = Screen.NOTEBOOK
            }

            Screen.SETTINGS -> {
                settingsCategory = null
                currentScreen = Screen.SETTINGS
            }

            else -> currentScreen = target
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen != Screen.NOTEBOOK_EDITOR,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                open = drawerOpen,
                messagesBadge = messagesVm.messageCount.takeIf { it > 0 }?.toString(),
                eventsBadge = timetableVm.dayMessages.size.takeIf { it > 0 }?.toString(),
                notebookBadge = notebookOpenTasks.takeIf { it > 0 }?.toString(),
                teachersBadge = allTeachers.size.toString(),
                statsBadge = timetableVm.currentYearLessonCount().takeIf { it > 0 }?.toString(),
                onSelect = { target ->
                    requestNotebookLeave { selectDrawerTarget(target) }
                },
            )
        },
    ) {
        val isTimetable = currentScreen == Screen.TIMETABLE
        val selectionPrompt = when (notebookSelectionTarget) {
            NotebookLessonTarget.ORIGIN -> stringResource(R.string.notebook_select_source_lesson)
            NotebookLessonTarget.DUE -> stringResource(R.string.notebook_select_due_lesson)
            null -> null
        }
        val settingsTitle = settingsCategory?.let { stringResource(it.titleRes) }
            ?: stringResource(R.string.settings)

        val title = when (currentScreen) {
            Screen.PROFILE -> stringResource(R.string.profile)
            Screen.LESSON_DETAILS -> stringResource(R.string.lesson)
            Screen.EVENT_DETAILS -> stringResource(R.string.event)
            Screen.EVENTS -> stringResource(R.string.events)
            Screen.MESSAGES -> stringResource(R.string.messages)
            Screen.MESSAGE_DETAILS -> stringResource(R.string.messages)
            Screen.NOTEBOOK -> stringResource(R.string.notebook_title)
            Screen.NOTEBOOK_EDITOR -> when (
                (notebookEditorState as? NotebookEditorState.Ready)?.draft?.kind
            ) {
                NotebookKind.TASK -> stringResource(R.string.notebook_kind_task)
                NotebookKind.NOTE -> stringResource(R.string.notebook_kind_note)
                null -> stringResource(R.string.notebook_title)
            }

            Screen.TEACHERS -> stringResource(R.string.teachers)
            Screen.ROOMS -> stringResource(R.string.rooms)
            Screen.STATS -> stringResource(R.string.stats)
            Screen.SETTINGS -> settingsTitle
            Screen.ABOUT -> stringResource(R.string.about)
            else -> stringResource(R.string.timetable)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = backProgress.value

                        if (progress > 0f) {
                            translationX = size.width * 0.12f * progress
                            scaleX = 1f - 0.04f * progress
                            scaleY = 1f - 0.04f * progress
                        }
                    },
                topBar = {
                    if (notebookSelectionActive) {
                        NotebookSelectionTopBar(
                            prompt = selectionPrompt.orEmpty(),
                            onCancel = { cancelNotebookLessonPicker() },
                        )
                    } else {
                        AppTopBar(
                            title = title,
                            isTimetable = isTimetable,
                            onMenuToggle = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                    else drawerState.close()
                                }
                            },
                            onBack = { goBack() },
                            onProfile = { currentScreen = Screen.PROFILE },
                        )
                    }
                },
            ) { padding ->
                AnimatedScreenHost(
                    screen = currentScreen,
                ) { screen ->
                    ScreenHost(
                        screen = screen,
                        padding = padding,
                        auth = auth,
                        timetableVm = timetableVm,
                        messagesVm = messagesVm,
                        notebookVm = notebookVm,
                        notebookItems = notebookItems,
                        notebookFocusNotice = notebookFocusNotice,
                        onNotebookFocusNoticeShown = { notebookFocusNotice = null },
                        onNotebookJumpToDate = { date ->
                            examFocusDate = date
                            examFocusNonce++
                            currentScreen = Screen.TIMETABLE
                        },
                        notebookEditorReturn = notebookSession.returnScreen,
                        onNotebookEditorClosed = {
                            if (currentScreen == Screen.NOTEBOOK_EDITOR) {
                                currentScreen = notebookSession.returnScreen
                            }
                        },
                        notebookLessonTarget = notebookSelectionTarget,
                        onNotebookOpenItem = { itemId, from -> openNotebookItem(itemId, from) },
                        onNotebookCreate = { kind, origin, from ->
                            openNotebookNew(kind, origin, from)
                        },
                        onNotebookPickLesson = { target -> startNotebookLessonPicker(target) },
                        onNotebookLessonSelected = { entry -> completeNotebookLessonPicker(entry) },
                        teachers = allTeachers,
                        settingsCategory = settingsCategory,
                        onSettingsCategoryChange = { settingsCategory = it },
                        selectedLesson = selectedLesson,
                        selectedEvent = selectedEvent,
                        onLessonClick = { entry ->
                            selectedLesson = entry
                            timetableVm.loadLessonNotes(entry)
                            currentScreen = Screen.LESSON_DETAILS
                        },
                        onOpenEvent = { entry, from -> openEvent(entry, from) },
                        onScreenChange = { currentScreen = it },
                        onExamClick = { date ->
                            examFocusDate = date
                            examFocusNonce++
                            currentScreen = Screen.TIMETABLE
                        },
                        focusDate = if (examFocusDate > 0) examFocusDate else focusDate,
                        focusNonce = focusNonce + examFocusNonce,
                        onFocusConsumed = {
                            if (examFocusDate > 0) examFocusDate = 0 else onFocusConsumed()
                        },
                        theme = theme,
                        onThemeChange = onThemeChange,
                        hapticsEnabled = hapticsEnabled,
                        onHapticsChange = onHapticsChange,
                        showLastRefreshed = showLastRefreshed,
                        onShowLastRefreshedChange = onShowLastRefreshedChange,
                        animationsEnabled = animationsEnabled,
                        onAnimationsChange = onAnimationsChange,
                        knownSubjects = knownSubjects,
                        subjectColors = subjectColors,
                        onSubjectColor = onSubjectColor,
                        subjectTextColors = subjectTextColors,
                        onSubjectTextColor = onSubjectTextColor,
                        subjectAliases = subjectAliases,
                        onSubjectAlias = onSubjectAlias,
                        defaultSubjectColor = defaultSubjectColor,
                        onDefaultSubjectColor = onDefaultSubjectColor,
                        lessonTextColor = lessonTextColor,
                        onLessonTextColor = onLessonTextColor,
                        strikeColor = strikeColor,
                        onStrikeColor = onStrikeColor,
                        examColor = examColor,
                        onExamColor = onExamColor,
                        cancelledStrike = cancelledStrike,
                        onCancelledStrike = onCancelledStrike,
                        cancelledDesaturation = cancelledDesaturation,
                        onCancelledDesaturation = onCancelledDesaturation,
                        cancelledBrightness = cancelledBrightness,
                        onCancelledBrightness = onCancelledBrightness,
                        pastDim = pastDim,
                        onPastDim = onPastDim,
                        lessonOptions = lessonOptions,
                        onLessonOptionsChange = onLessonOptionsChange,
                        hideCancelled = hideCancelled,
                        onHideCancelledChange = onHideCancelledChange,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.22f * backProgress.value }
                    .background(Color.Black),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ErrorBanner(
                    text = updateVm.transientError?.let { uiTextString(it) },
                    onRetry = updateVm::check,
                )

                if (isTimetable) {
                    ErrorBanner(
                        text = timetableVm.error?.let { uiTextString(it.text) },
                        onRetry = timetableVm::retry,
                    )

                    InfoBanner(
                        text = timetableVm.info?.let { uiTextString(it) },
                        onRetry = timetableVm::retry,
                    )

                    InfoBanner(
                        text = timetableVm.timegridError?.let { uiTextString(it) },
                        onRetry = timetableVm::retryTimegrid,
                    )
                }

                if (currentScreen == Screen.EVENTS) {
                    ErrorBanner(
                        text = timetableVm.eventsError?.let { uiTextString(it) },
                        onRetry = timetableVm::refreshEvents,
                    )
                }

                if (currentScreen == Screen.STATS) {
                    ErrorBanner(
                        text = timetableVm.statsError?.let { uiTextString(it) },
                        onRetry = timetableVm::loadHistoricalStats,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookSelectionTopBar(
    prompt: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = prompt,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
    }
}
