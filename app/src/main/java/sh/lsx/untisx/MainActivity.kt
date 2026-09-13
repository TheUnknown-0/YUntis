package sh.lsx.untisx

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.LanguagePrefs
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.HapticsPrefs
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.ThemePrefs
import sh.lsx.untisx.ui.components.UpdateDialog
import sh.lsx.untisx.ui.screens.AboutScreen
import sh.lsx.untisx.ui.screens.LessonDetailsScreen
import sh.lsx.untisx.ui.screens.ProfileScreen
import sh.lsx.untisx.ui.screens.LoginScreen
import sh.lsx.untisx.ui.screens.OnboardingScreen
import sh.lsx.untisx.ui.screens.RoomsListScreen
import sh.lsx.untisx.ui.screens.SettingsScreen
import sh.lsx.untisx.ui.screens.TeachersListScreen
import sh.lsx.untisx.ui.timetable.ErrorBanner
import sh.lsx.untisx.ui.timetable.InfoBanner
import sh.lsx.untisx.ui.timetable.TimetableScreen
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.theme.UntisXTheme
import sh.lsx.untisx.ui.util.LocalHapticsEnabled
import sh.lsx.untisx.ui.util.tapHaptic
import sh.lsx.untisx.ui.util.uiTextString
import sh.lsx.untisx.update.UpdateViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LanguagePrefs.apply(LanguagePrefs.get(this))

        setContent {
            AppRoot()
        }
    }
}

private fun readOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(UntisXApp.KEY_ONBOARDING_DONE, false)

private fun prefsEdit(context: Context) =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE).edit()

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    var onboardingDone by remember { mutableStateOf(readOnboardingDone(context)) }
    var auth by remember { mutableStateOf(AccountPrefs.getActive(context)) }
    var hapticsEnabled by remember { mutableStateOf(HapticsPrefs.isEnabled(context)) }
    var subjectColors by remember { mutableStateOf(SubjectColorsPrefs.getColors(context)) }
    var knownSubjects by remember { mutableStateOf(SubjectColorsPrefs.getKnown(context)) }
    var defaultSubjectColor by remember { mutableStateOf(SubjectColorsPrefs.getDefaultColor(context)) }
    var lessonTextColor by remember { mutableStateOf(SubjectColorsPrefs.getTextColor(context)) }
    var cancelledStrike by remember { mutableStateOf(SubjectColorsPrefs.isCancelledStrike(context)) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in ThemePrefs.KEYS) {
                theme = ThemePrefs.get(context)
            }

            if (key == UntisXApp.KEY_ONBOARDING_DONE) {
                onboardingDone = readOnboardingDone(context)
            }

            if (key == AccountPrefs.KEY_ACCOUNTS || key == AccountPrefs.KEY_ACTIVE) {
                auth = AccountPrefs.getActive(context)
            }

            if (key == HapticsPrefs.KEY) {
                hapticsEnabled = HapticsPrefs.isEnabled(context)
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

    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled) {
        UntisXTheme(mode = theme.mode, preset = theme.preset) {
            val currentAuth = auth

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

            Box(modifier = Modifier.fillMaxSize()) {
                when {
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
                        subjectColors = subjectColors,
                        knownSubjects = knownSubjects,
                        onSubjectColor = { name, argb -> SubjectColorsPrefs.setColor(context, name, argb) },
                        defaultSubjectColor = defaultSubjectColor,
                        onDefaultSubjectColor = { SubjectColorsPrefs.setDefaultColor(context, it) },
                        lessonTextColor = lessonTextColor,
                        onLessonTextColor = { SubjectColorsPrefs.setTextColor(context, it) },
                        cancelledStrike = cancelledStrike,
                        onCancelledStrike = { SubjectColorsPrefs.setCancelledStrike(context, it) },
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

private enum class Screen {
    TIMETABLE,
    PROFILE,
    LESSON_DETAILS,
    TEACHERS,
    ROOMS,
    SETTINGS,
    ABOUT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    auth: AuthData,
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    subjectColors: Map<String, Int>,
    knownSubjects: List<String>,
    onSubjectColor: (String, Int?) -> Unit,
    defaultSubjectColor: Int,
    onDefaultSubjectColor: (Int) -> Unit,
    lessonTextColor: Int,
    onLessonTextColor: (Int) -> Unit,
    cancelledStrike: Boolean,
    onCancelledStrike: (Boolean) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val timetableVm: TimetableViewModel = viewModel(
        key = "${auth.loginName}:${auth.session.personId}",
    )
    val updateVm: UpdateViewModel = viewModel()

    var currentScreen by remember { mutableStateOf(Screen.TIMETABLE) }
    var selectedLesson by remember { mutableStateOf<TimetableEntry?>(null) }

    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.TIMETABLE) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            currentScreen = Screen.TIMETABLE
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
                    icon = Icons.Outlined.Person,
                    label = stringResource(R.string.teachers),
                    badge = allTeachers.size.toString(),
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.TEACHERS
                    },
                )

                SidebarItem(
                    icon = Icons.Outlined.DateRange,
                    label = stringResource(R.string.rooms),
                    badge = "...",
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.ROOMS
                    },
                )

                Spacer(Modifier.height(16.dp))
                SidebarSection(stringResource(R.string.nav_other))

                SidebarItem(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(R.string.settings),
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.SETTINGS
                    },
                )

                SidebarItem(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.about),
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = Screen.ABOUT
                    },
                )
            }
        },
    ) {
        val isTimetable = currentScreen == Screen.TIMETABLE
        val title = when (currentScreen) {
            Screen.PROFILE -> stringResource(R.string.profile)
            Screen.LESSON_DETAILS -> stringResource(R.string.lesson)
            Screen.TEACHERS -> stringResource(R.string.teachers)
            Screen.ROOMS -> stringResource(R.string.rooms)
            Screen.SETTINGS -> stringResource(R.string.settings)
            Screen.ABOUT -> stringResource(R.string.about)
            else -> stringResource(R.string.timetable)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .statusBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isTimetable) {
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
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .tapHaptic { currentScreen = Screen.TIMETABLE }
                                    .padding(8.dp),
                            )
                        }

                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )

                        if (isTimetable) {
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
                },
            ) { padding ->
                when (currentScreen) {
                    Screen.PROFILE -> ProfileScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        theme = theme,
                        onThemeChange = onThemeChange,
                        hapticsEnabled = hapticsEnabled,
                        onHapticsChange = onHapticsChange,
                        knownSubjects = knownSubjects,
                        subjectColors = subjectColors,
                        onSubjectColor = onSubjectColor,
                        defaultSubjectColor = defaultSubjectColor,
                        onDefaultSubjectColor = onDefaultSubjectColor,
                        lessonTextColor = lessonTextColor,
                        onLessonTextColor = onLessonTextColor,
                        cancelledStrike = cancelledStrike,
                        onCancelledStrike = onCancelledStrike,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )

                    Screen.TEACHERS -> TeachersListScreen(
                        teachers = allTeachers,
                        modifier = Modifier.padding(padding),
                    )

                    Screen.ROOMS -> RoomsListScreen(
                        modifier = Modifier.padding(padding),
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
                        onLessonClick = { entry ->
                            selectedLesson = entry
                            currentScreen = Screen.LESSON_DETAILS
                        },
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
                        onLessonClick = { entry ->
                            selectedLesson = entry
                            currentScreen = Screen.LESSON_DETAILS
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                updateVm.transientError?.let { message ->
                    ErrorBanner(text = uiTextString(message), onRetry = updateVm::check)
                }

                if (isTimetable) {
                    timetableVm.error?.let { loadError ->
                        ErrorBanner(text = uiTextString(loadError.text), onRetry = timetableVm::retry)
                    }

                    timetableVm.info?.let { message ->
                        InfoBanner(text = uiTextString(message), onRetry = timetableVm::retry)
                    }

                    timetableVm.timegridError?.let { message ->
                        InfoBanner(text = uiTextString(message), onRetry = timetableVm::retryTimegrid)
                    }
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
    onClick: () -> Unit,
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
