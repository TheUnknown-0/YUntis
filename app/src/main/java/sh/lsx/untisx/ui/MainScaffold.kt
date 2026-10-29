package sh.lsx.untisx.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.components.AppDrawer
import sh.lsx.untisx.ui.components.AppTopBar
import sh.lsx.untisx.ui.messages.MessagesViewModel
import sh.lsx.untisx.ui.nav.AnimatedScreenHost
import sh.lsx.untisx.ui.nav.Screen
import sh.lsx.untisx.ui.nav.ScreenHost
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
    val updateVm: UpdateViewModel = viewModel()

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

    LaunchedEffect(focusNonce) {
        when (focusScreen) {
            Notifications.FOCUS_MESSAGES -> {
                messagesVm.refresh()
                currentScreen = Screen.MESSAGES
                onFocusConsumed()
            }

            Notifications.FOCUS_EVENTS -> {
                timetableVm.ensureEvents()
                currentScreen = Screen.EVENTS
                onFocusConsumed()
            }

            Notifications.FOCUS_ACCOUNT -> {
                currentScreen = Screen.PROFILE
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

    fun goBack() {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            currentScreen == Screen.SETTINGS && settingsCategory != null -> settingsCategory = null
            currentScreen == Screen.EVENT_DETAILS -> currentScreen = previousScreen
            currentScreen == Screen.MESSAGE_DETAILS -> currentScreen = Screen.MESSAGES
            else -> currentScreen = Screen.TIMETABLE
        }
    }

    val backProgress = remember { Animatable(0f) }

    BackHandler(
        enabled = drawerState.isOpen || (currentScreen != Screen.TIMETABLE && !animationsEnabled),
    ) {
        goBack()
    }

    PredictiveBackHandler(
        enabled = currentScreen != Screen.TIMETABLE && !drawerState.isOpen && animationsEnabled,
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                open = drawerOpen,
                messagesBadge = messagesVm.messageCount.takeIf { it > 0 }?.toString(),
                eventsBadge = timetableVm.dayMessages.size.takeIf { it > 0 }?.toString(),
                teachersBadge = allTeachers.size.toString(),
                statsBadge = timetableVm.currentYearLessonCount().takeIf { it > 0 }?.toString(),
                onSelect = { target ->
                    when (target) {
                        Screen.MESSAGES -> {
                            messagesVm.refresh()
                            currentScreen = Screen.MESSAGES
                        }

                        Screen.EVENTS -> {
                            timetableVm.ensureEvents()
                            currentScreen = Screen.EVENTS
                        }

                        Screen.SETTINGS -> {
                            settingsCategory = null
                            currentScreen = Screen.SETTINGS
                        }

                        else -> currentScreen = target
                    }
                },
            )
        },
    ) {
        val isTimetable = currentScreen == Screen.TIMETABLE
        val settingsTitle = settingsCategory?.let { stringResource(it.titleRes) }
            ?: stringResource(R.string.settings)

        val title = when (currentScreen) {
            Screen.PROFILE -> stringResource(R.string.profile)
            Screen.LESSON_DETAILS -> stringResource(R.string.lesson)
            Screen.EVENT_DETAILS -> stringResource(R.string.event)
            Screen.EVENTS -> stringResource(R.string.events)
            Screen.MESSAGES -> stringResource(R.string.messages)
            Screen.MESSAGE_DETAILS -> stringResource(R.string.messages)
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
