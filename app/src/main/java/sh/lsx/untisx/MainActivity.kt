package sh.lsx.untisx

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.LanguagePrefs
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.AccountLoadError
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AnimationPrefs
import sh.lsx.untisx.data.prefs.HapticsPrefs
import sh.lsx.untisx.data.prefs.TimetablePrefs
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.ThemePrefs
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.ui.components.UpdateDialog
import sh.lsx.untisx.ui.messages.MessageDetailsScreen
import sh.lsx.untisx.ui.messages.MessagesScreen
import sh.lsx.untisx.ui.messages.MessagesViewModel
import sh.lsx.untisx.ui.screens.AboutScreen
import sh.lsx.untisx.ui.screens.EventDetailsScreen
import sh.lsx.untisx.ui.screens.EventsScreen
import sh.lsx.untisx.ui.screens.LessonDetailsScreen
import sh.lsx.untisx.ui.screens.ProfileScreen
import sh.lsx.untisx.ui.screens.LoginScreen
import sh.lsx.untisx.ui.screens.OnboardingScreen
import sh.lsx.untisx.ui.screens.RoomsListScreen
import sh.lsx.untisx.ui.screens.StatsScreen
import sh.lsx.untisx.ui.screens.TeachersListScreen
import sh.lsx.untisx.ui.screens.settings.AppearanceSettingsScreen
import sh.lsx.untisx.ui.screens.settings.GeneralSettingsScreen
import sh.lsx.untisx.ui.screens.settings.NotificationSettingsScreen
import sh.lsx.untisx.ui.screens.settings.SettingsCategory
import sh.lsx.untisx.ui.screens.settings.SettingsOverviewScreen
import sh.lsx.untisx.ui.screens.settings.TimetableSettingsScreen
import sh.lsx.untisx.ui.timetable.ErrorBanner
import sh.lsx.untisx.ui.timetable.InfoBanner
import sh.lsx.untisx.ui.timetable.TimetableScreen
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.theme.UntisXTheme
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.LocalAnimatedContentScope
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.LocalHapticsEnabled
import sh.lsx.untisx.ui.util.LocalSharedTransitionScope
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.motionSpatial
import sh.lsx.untisx.ui.util.tapHaptic
import sh.lsx.untisx.ui.util.uiTextString
import sh.lsx.untisx.update.UpdateViewModel

class MainActivity : AppCompatActivity() {

    private var focusDate by mutableIntStateOf(0)
    private var focusScreen by mutableStateOf<String?>(null)
    private var focusNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LanguagePrefs.apply(LanguagePrefs.get(this))
        handleIntent(intent)

        setContent {
            AppRoot(
                focusDate = focusDate,
                focusScreen = focusScreen,
                focusNonce = focusNonce,
                onFocusConsumed = ::consumeFocus,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val date = intent?.getIntExtra(Notifications.EXTRA_FOCUS_DATE, 0) ?: 0
        val screen = intent?.getStringExtra(Notifications.EXTRA_FOCUS_SCREEN)

        if (date > 0) {
            focusDate = date
            focusNonce++
        }

        if (!screen.isNullOrBlank()) {
            focusScreen = screen
            focusNonce++
        }
    }

    private fun consumeFocus() {
        focusDate = 0
        focusScreen = null
    }
}

private fun readOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(UntisXApp.KEY_ONBOARDING_DONE, false)

private fun prefsEdit(context: Context) =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE).edit()

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

@Composable
private fun AppRoot(
    focusDate: Int,
    focusScreen: String?,
    focusNonce: Int,
    onFocusConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    var onboardingDone by remember { mutableStateOf(readOnboardingDone(context)) }
    var hapticsEnabled by remember { mutableStateOf(HapticsPrefs.isEnabled(context)) }
    var showLastRefreshed by remember { mutableStateOf(TimetablePrefs.showLastRefreshed(context)) }
    var animationsEnabled by remember { mutableStateOf(AnimationPrefs.isEnabled(context)) }
    var subjectColors by remember { mutableStateOf(SubjectColorsPrefs.getColors(context)) }
    var knownSubjects by remember { mutableStateOf(SubjectColorsPrefs.getKnown(context)) }
    var defaultSubjectColor by remember { mutableStateOf(SubjectColorsPrefs.getDefaultColor(context)) }
    var lessonTextColor by remember { mutableStateOf(SubjectColorsPrefs.getTextColor(context)) }
    var cancelledStrike by remember { mutableStateOf(SubjectColorsPrefs.isCancelledStrike(context)) }

    val accounts by AccountStore.accounts.collectAsStateWithLifecycle()
    val activeId by AccountStore.activeId.collectAsStateWithLifecycle()
    val accountsLoaded by AccountStore.loaded.collectAsStateWithLifecycle()
    val accountError by AccountStore.error.collectAsStateWithLifecycle()
    val currentAuth = remember(accounts, activeId) {
        accounts.firstOrNull { AccountPrefs.accountId(it) == activeId } ?: accounts.firstOrNull()
    }

    LaunchedEffect(Unit) {
        AccountStore.refresh(context)
    }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in ThemePrefs.KEYS) {
                theme = ThemePrefs.get(context)
            }

            if (key == UntisXApp.KEY_ONBOARDING_DONE) {
                onboardingDone = readOnboardingDone(context)
            }

            if (key == HapticsPrefs.KEY) {
                hapticsEnabled = HapticsPrefs.isEnabled(context)
            }

            if (key == TimetablePrefs.KEY_SHOW_LAST_REFRESHED) {
                showLastRefreshed = TimetablePrefs.showLastRefreshed(context)
            }

            if (key == AnimationPrefs.KEY) {
                animationsEnabled = AnimationPrefs.isEnabled(context)
            }

            if (key in SubjectColorsPrefs.KEYS) {
                subjectColors = SubjectColorsPrefs.getColors(context)
                knownSubjects = SubjectColorsPrefs.getKnown(context)
                defaultSubjectColor = SubjectColorsPrefs.getDefaultColor(context)
                lessonTextColor = SubjectColorsPrefs.getTextColor(context)
                cancelledStrike = SubjectColorsPrefs.isCancelledStrike(context)
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveTheme(newTheme: AppTheme) = ThemePrefs.save(context, newTheme)

    CompositionLocalProvider(
        LocalHapticsEnabled provides hapticsEnabled,
        LocalAnimationsEnabled provides animationsEnabled,
    ) {
        UntisXTheme(mode = theme.mode, preset = theme.preset) {
            val updateVm: UpdateViewModel = viewModel()

            LaunchedEffect(Unit) {
                updateVm.checkOnStartup()
            }

            LaunchedEffect(updateVm.transientError) {
                if (updateVm.transientError != null) {
                    delay(3000)
                    updateVm.clearTransientError()
                }
            }

            LaunchedEffect(Unit) {
                Notifications.ensureChannels(context)
                NotificationScheduler.ensureAll(context)
            }

            LaunchedEffect(currentAuth?.let { AccountPrefs.accountId(it) }) {
                NotificationScheduler.ensureAll(context)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    accountError is AccountLoadError.Transient -> AccountsUnavailableScreen {
                        scope.launch { AccountStore.refresh(context) }
                    }

                    accountError is AccountLoadError.KeyLost -> AccountsUnreadableScreen {
                        scope.launch { AccountStore.discardUnreadable(context) }
                    }

                    !accountsLoaded -> Unit

                    !onboardingDone -> OnboardingScreen(
                        theme = theme,
                        onThemeChange = ::saveTheme,
                        loggedIn = currentAuth != null,
                        onFinish = {
                            prefsEdit(context).putBoolean(UntisXApp.KEY_ONBOARDING_DONE, true).apply()
                        },
                    )

                    currentAuth == null -> Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                        LoginScreen(modifier = Modifier.padding(padding))
                    }

                    else -> MainApp(
                        auth = currentAuth,
                        theme = theme,
                        onThemeChange = ::saveTheme,
                        hapticsEnabled = hapticsEnabled,
                        onHapticsChange = { HapticsPrefs.setEnabled(context, it) },
                        showLastRefreshed = showLastRefreshed,
                        onShowLastRefreshedChange = { TimetablePrefs.setShowLastRefreshed(context, it) },
                        animationsEnabled = animationsEnabled,
                        onAnimationsChange = { AnimationPrefs.setEnabled(context, it) },
                        subjectColors = subjectColors,
                        knownSubjects = knownSubjects,
                        onSubjectColor = { name, argb -> SubjectColorsPrefs.setColor(context, name, argb) },
                        defaultSubjectColor = defaultSubjectColor,
                        onDefaultSubjectColor = { SubjectColorsPrefs.setDefaultColor(context, it) },
                        lessonTextColor = lessonTextColor,
                        onLessonTextColor = { SubjectColorsPrefs.setTextColor(context, it) },
                        cancelledStrike = cancelledStrike,
                        onCancelledStrike = { SubjectColorsPrefs.setCancelledStrike(context, it) },
                        focusDate = focusDate,
                        focusScreen = focusScreen,
                        focusNonce = focusNonce,
                        onFocusConsumed = onFocusConsumed,
                    )
                }

                UpdateDialog(
                    state = updateVm.state,
                    onDownload = updateVm::download,
                    onInstall = updateVm::install,
                    onDismiss = updateVm::dismiss,
                )
            }
        }
    }
}

@Composable
private fun AccountsUnavailableScreen(onRetry: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.accounts_transient),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = onRetry) {
                Text(stringResource(R.string.accounts_retry))
            }
        }
    }
}

@Composable
private fun AccountsUnreadableScreen(onStartOver: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.accounts_unreadable),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = onStartOver) {
                Text(stringResource(R.string.accounts_start_over))
            }
        }
    }
}

private enum class Screen {
    TIMETABLE,
    PROFILE,
    LESSON_DETAILS,
    EVENT_DETAILS,
    EVENTS,
    MESSAGES,
    MESSAGE_DETAILS,
    TEACHERS,
    ROOMS,
    STATS,
    SETTINGS,
    ABOUT,
}

private fun Screen.depth(): Int = when (this) {
    Screen.TIMETABLE -> 0

    Screen.PROFILE,
    Screen.EVENTS,
    Screen.MESSAGES,
    Screen.TEACHERS,
    Screen.ROOMS,
    Screen.STATS,
    Screen.SETTINGS,
    Screen.ABOUT,
    -> 1

    Screen.LESSON_DETAILS,
    Screen.EVENT_DETAILS,
    Screen.MESSAGE_DETAILS,
    -> 2
}

@Composable
private fun AnimatedScreenHost(
    screen: Screen,
    content: @Composable (Screen) -> Unit,
) {
    val spatial = motionSpatial<IntOffset>()
    val effects = motionEffects<Float>()

    SharedTransitionLayout {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { screenTransition(initialState, targetState, spatial, effects) },
            label = "screen",
        ) { target ->
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedContentScope provides this@AnimatedContent,
            ) {
                content(target)
            }
        }
    }
}

private fun screenTransition(
    initial: Screen,
    target: Screen,
    spatial: FiniteAnimationSpec<IntOffset>,
    effects: FiniteAnimationSpec<Float>,
): ContentTransform {
    val forward = target.depth() >= initial.depth()

    val enter = slideInHorizontally(animationSpec = spatial) { width ->
        if (forward) width / 5 else -width / 8
    } + fadeIn(animationSpec = effects)

    val exit = slideOutHorizontally(animationSpec = spatial) { width ->
        if (forward) -width / 8 else width / 5
    } + fadeOut(animationSpec = effects)

    return enter togetherWith exit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
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
    defaultSubjectColor: Int,
    onDefaultSubjectColor: (Int) -> Unit,
    lessonTextColor: Int,
    onLessonTextColor: (Int) -> Unit,
    cancelledStrike: Boolean,
    onCancelledStrike: (Boolean) -> Unit,
    focusDate: Int,
    focusScreen: String?,
    focusNonce: Int,
    onFocusConsumed: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerOpen = drawerState.targetValue == DrawerValue.Open
    val effectsSpec = motionEffects<Float>()
    val spatialSpec = motionSpatial<IntOffset>()
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

    val allRooms = remember {
        derivedStateOf {
            timetableVm.roomsById.values
                .filter { it.name.isNotBlank() }
                .sortedBy { it.name }
        }
    }.value

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = stringResource(R.string.untisx_logo),
                        modifier = Modifier.size(40.dp),
                    )

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "UntisX",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .tapHaptic {
                                scope.launch { drawerState.close() }
                            }
                            .padding(2.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                SidebarSection(stringResource(R.string.nav_information))

                SidebarItem(
                    icon = Icons.Outlined.Email,
                    label = stringResource(R.string.messages),
                    badge = messagesVm.messageCount.takeIf { it > 0 }?.toString(),
                    visible = drawerOpen,
                    index = 0,
                    onClick = {
                        scope.launch { drawerState.close() }
                        messagesVm.refresh()
                        currentScreen = Screen.MESSAGES
                    },
                )

                SidebarItem(
                    icon = Icons.Outlined.DateRange,
                    label = stringResource(R.string.events),
                    badge = timetableVm.dayMessages.size.takeIf { it > 0 }?.toString(),
                    visible = drawerOpen,
                    index = 1,
                    onClick = {
                        scope.launch { drawerState.close() }
                        timetableVm.ensureEvents()
                        currentScreen = Screen.EVENTS
                    },
                )

                SidebarItem(
                    icon = Icons.Outlined.Person,
                    label = stringResource(R.string.teachers),
                    badge = allTeachers.size.toString(),
                    visible = drawerOpen,
                    index = 2,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.TEACHERS
                    },
                )

                SidebarItem(
                    icon = Icons.Outlined.Place,
                    label = stringResource(R.string.rooms),
                    badge = "...",
                    visible = drawerOpen,
                    index = 3,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.ROOMS
                    },
                )

                SidebarItem(
                    icon = Icons.AutoMirrored.Outlined.List,
                    label = stringResource(R.string.stats),
                    badge = timetableVm.currentYearLessonCount().takeIf { it > 0 }?.toString(),
                    visible = drawerOpen,
                    index = 4,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.STATS
                    },
                )

                Spacer(Modifier.height(16.dp))
                SidebarSection(stringResource(R.string.nav_other))

                SidebarItem(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(R.string.settings),
                    visible = drawerOpen,
                    index = 5,
                    onClick = {
                        scope.launch { drawerState.close() }
                        settingsCategory = null
                        currentScreen = Screen.SETTINGS
                    },
                )

                SidebarItem(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.about),
                    visible = drawerOpen,
                    index = 6,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.ABOUT
                    },
                )
            }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .statusBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState = isTimetable,
                            transitionSpec = {
                                fadeIn(animationSpec = effectsSpec) togetherWith
                                    fadeOut(animationSpec = effectsSpec)
                            },
                            label = "topBarLeading",
                        ) { timetable ->
                            if (timetable) {
                                Icon(
                                    imageVector = Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.menu),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .tapHaptic {
                                            scope.launch {
                                                if (drawerState.isClosed) drawerState.open()
                                                else drawerState.close()
                                            }
                                        }
                                        .padding(8.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .tapHaptic { goBack() }
                                        .padding(8.dp),
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = title,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                fadeIn(animationSpec = effectsSpec) togetherWith
                                    fadeOut(animationSpec = effectsSpec)
                            },
                            label = "topBarTitle",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }

                        AnimatedContent(
                            targetState = isTimetable,
                            transitionSpec = {
                                fadeIn(animationSpec = effectsSpec) togetherWith
                                    fadeOut(animationSpec = effectsSpec)
                            },
                            label = "topBarTrailing",
                        ) { timetable ->
                            if (timetable) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = stringResource(R.string.profile),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .tapHaptic { currentScreen = Screen.PROFILE }
                                        .padding(8.dp),
                                )
                            } else {
                                Spacer(Modifier.size(40.dp))
                            }
                        }
                    }
                },
            ) { padding ->
                AnimatedScreenHost(
                    screen = currentScreen,
                ) { screen ->
                    when (screen) {
                        Screen.PROFILE -> ProfileScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.SETTINGS -> {
                            val settingsModifier = Modifier
                                .fillMaxSize()
                                .padding(padding)

                            AnimatedContent(
                                targetState = settingsCategory,
                                transitionSpec = {
                                    val forward = targetState != null

                                    val enter = slideInHorizontally(animationSpec = spatialSpec) { width ->
                                        if (forward) width / 4 else -width / 8
                                    } + fadeIn(animationSpec = effectsSpec)
                                    val exit = slideOutHorizontally(animationSpec = spatialSpec) { width ->
                                        if (forward) -width / 8 else width / 4
                                    } + fadeOut(animationSpec = effectsSpec)

                                    enter togetherWith exit
                                },
                                label = "settingsCategory",
                            ) { category ->
                                if (category == null) {
                                    SettingsOverviewScreen(
                                        onOpenCategory = { settingsCategory = it },
                                        modifier = settingsModifier,
                                    )
                                } else {
                                    when (category) {
                                        SettingsCategory.GENERAL -> GeneralSettingsScreen(
                                            hapticsEnabled = hapticsEnabled,
                                            onHapticsChange = onHapticsChange,
                                            showLastRefreshed = showLastRefreshed,
                                            onShowLastRefreshedChange = onShowLastRefreshedChange,
                                            modifier = settingsModifier,
                                        )

                                        SettingsCategory.APPEARANCE -> AppearanceSettingsScreen(
                                            theme = theme,
                                            onThemeChange = onThemeChange,
                                            animationsEnabled = animationsEnabled,
                                            onAnimationsChange = onAnimationsChange,
                                            modifier = settingsModifier,
                                        )

                                        SettingsCategory.NOTIFICATIONS -> NotificationSettingsScreen(
                                            modifier = settingsModifier,
                                        )

                                        SettingsCategory.TIMETABLE -> TimetableSettingsScreen(
                                            vm = timetableVm,
                                            knownSubjects = knownSubjects,
                                            subjectColors = subjectColors,
                                            onSubjectColor = onSubjectColor,
                                            defaultSubjectColor = defaultSubjectColor,
                                            onDefaultSubjectColor = onDefaultSubjectColor,
                                            lessonTextColor = lessonTextColor,
                                            onLessonTextColor = onLessonTextColor,
                                            cancelledStrike = cancelledStrike,
                                            onCancelledStrike = onCancelledStrike,
                                            modifier = settingsModifier,
                                        )
                                    }
                                }
                            }
                        }

                        Screen.TEACHERS -> TeachersListScreen(
                            teachers = allTeachers,
                            modifier = Modifier.padding(padding),
                        )

                        Screen.EVENTS -> EventsScreen(
                            events = timetableVm.events,
                            holidays = timetableVm.holidays,
                            messages = timetableVm.dayMessages,
                            roomsById = timetableVm.roomsById,
                            loading = timetableVm.eventsLoading,
                            onRefresh = timetableVm::refreshEvents,
                            onEventClick = { openEvent(it, Screen.EVENTS) },
                            exams = timetableVm.exams,
                            subjectsById = timetableVm.subjectsById,
                            teachersById = timetableVm.teachersById,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.EVENT_DETAILS -> selectedEvent?.let { event ->
                            EventDetailsScreen(
                                entry = event,
                                teachersById = timetableVm.teachersById,
                                roomsById = timetableVm.roomsById,
                                notes = timetableVm.lessonNotes,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                            )
                        } ?: TimetableScreen(
                            vm = timetableVm,
                            subjectColors = subjectColors.mapValues { Color(it.value) },
                            defaultColor = Color(defaultSubjectColor),
                            textColor = Color(lessonTextColor),
                            cancelledStrike = cancelledStrike,
                            showLastRefreshed = showLastRefreshed,
                            focusDate = focusDate,
                            focusNonce = focusNonce,
                            onFocusConsumed = onFocusConsumed,
                            onLessonClick = { entry ->
                                selectedLesson = entry
                                timetableVm.loadLessonNotes(entry)
                                currentScreen = Screen.LESSON_DETAILS
                            },
                            onEventClick = { openEvent(it, Screen.TIMETABLE) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.MESSAGES -> MessagesScreen(
                            vm = messagesVm,
                            onMessageClick = { currentScreen = Screen.MESSAGE_DETAILS },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.MESSAGE_DETAILS -> MessageDetailsScreen(
                            vm = messagesVm,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.ROOMS -> RoomsListScreen(
                            modifier = Modifier.padding(padding),
                        )

                        Screen.STATS -> StatsScreen(
                            yearStats = timetableVm.currentYearStats(),
                            schoolYear = timetableVm.statsSchoolYear(),
                            subjectsById = timetableVm.subjectsById,
                            loading = timetableVm.statsLoading,
                            progress = timetableVm.statsProgress,
                            upToDate = timetableVm.statsUpToDate,
                            onLoadHistory = timetableVm::loadHistoricalStats,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.ABOUT -> AboutScreen(
                            auth = auth,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        Screen.LESSON_DETAILS -> selectedLesson?.let { lesson ->
                            LessonDetailsScreen(
                                entry = lesson,
                                subjectsById = timetableVm.subjectsById,
                                teachersById = timetableVm.teachersById,
                                roomsById = timetableVm.roomsById,
                                color = subjectColors[
                                    lesson.subject.firstOrNull()
                                        ?.let { timetableVm.subjectsById[it.id]?.name?.takeIf { name -> name.isNotBlank() } ?: it.name }
                                ]?.let { Color(it) } ?: Color(defaultSubjectColor),
                                textColor = Color(lessonTextColor),
                                notes = timetableVm.lessonNotes,
                                exam = timetableVm.examFor(lesson),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                            )
                        } ?: TimetableScreen(
                            vm = timetableVm,
                            subjectColors = subjectColors.mapValues { Color(it.value) },
                            defaultColor = Color(defaultSubjectColor),
                            textColor = Color(lessonTextColor),
                            cancelledStrike = cancelledStrike,
                            showLastRefreshed = showLastRefreshed,
                            focusDate = focusDate,
                            focusNonce = focusNonce,
                            onFocusConsumed = onFocusConsumed,
                            onLessonClick = { entry ->
                                selectedLesson = entry
                                timetableVm.loadLessonNotes(entry)
                                currentScreen = Screen.LESSON_DETAILS
                            },
                            onEventClick = { openEvent(it, Screen.TIMETABLE) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )

                        else -> TimetableScreen(
                            vm = timetableVm,
                            subjectColors = subjectColors.mapValues { Color(it.value) },
                            defaultColor = Color(defaultSubjectColor),
                            textColor = Color(lessonTextColor),
                            cancelledStrike = cancelledStrike,
                            showLastRefreshed = showLastRefreshed,
                            focusDate = focusDate,
                            focusNonce = focusNonce,
                            onFocusConsumed = onFocusConsumed,
                            onLessonClick = { entry ->
                                selectedLesson = entry
                                timetableVm.loadLessonNotes(entry)
                                currentScreen = Screen.LESSON_DETAILS
                            },
                            onEventClick = { openEvent(it, Screen.TIMETABLE) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )
                    }
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
private fun SidebarSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    visible: Boolean = true,
    index: Int = 0,
    onClick: () -> Unit,
) {
    val animations = LocalAnimationsEnabled.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = if (animations) {
                tween(durationMillis = 180, delayMillis = index * 35)
            } else {
                snap()
            },
        ) + slideInHorizontally(
            animationSpec = if (animations) {
                tween(durationMillis = 220, delayMillis = index * 35)
            } else {
                snap()
            },
        ) { width -> -width / 4 },
        exit = fadeOut(
            animationSpec = if (animations) tween(durationMillis = 90) else snap(),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .tapHaptic(onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(20.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
