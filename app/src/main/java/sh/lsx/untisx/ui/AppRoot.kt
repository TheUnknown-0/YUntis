package sh.lsx.untisx.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.SubjectColorResolver
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.ui.components.LocalSubjectColorState
import sh.lsx.untisx.ui.components.SubjectColorState
import sh.lsx.untisx.data.ThemePrefs
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.prefs.AccountLoadError
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AnimationPrefs
import sh.lsx.untisx.data.prefs.AppearancePrefs
import sh.lsx.untisx.data.prefs.HapticsPrefs
import sh.lsx.untisx.data.prefs.TimetablePrefs
import sh.lsx.untisx.ui.screens.LoginScreen
import sh.lsx.untisx.ui.screens.OnboardingScreen
import sh.lsx.untisx.ui.theme.UntisXTheme
import sh.lsx.untisx.ui.theme.appScreenContainerColor
import sh.lsx.untisx.ui.theme.appBackground
import sh.lsx.untisx.ui.timetable.LessonCardOptions
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.LocalHapticsEnabled

private fun readOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(UntisXApp.KEY_ONBOARDING_DONE, false)

private fun prefsEdit(context: Context) =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE).edit()

@Composable
internal fun AppRoot(
    focusDate: Int,
    focusScreen: String?,
    focusNonce: Int,
    focusNotebookItemId: String?,
    focusNotebookAccountId: String?,
    onFocusConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    var onboardingDone by remember { mutableStateOf(readOnboardingDone(context)) }
    var hapticsEnabled by remember { mutableStateOf(HapticsPrefs.isEnabled(context)) }
    var showLastRefreshed by remember { mutableStateOf(TimetablePrefs.showLastRefreshed(context)) }
    var animationsEnabled by remember { mutableStateOf(AnimationPrefs.isEnabled(context)) }
    var subjectAliases by remember { mutableStateOf(SubjectColorsPrefs.getAliases(context)) }
    var knownSubjects by remember { mutableStateOf(SubjectColorsPrefs.getKnown(context)) }
    var defaultSubjectColor by remember { mutableStateOf(SubjectColorsPrefs.getDefaultColor(context)) }
    var lessonTextColor by remember { mutableStateOf(SubjectColorsPrefs.getTextColor(context)) }
    var strikeColor by remember { mutableStateOf(SubjectColorsPrefs.getStrikeColor(context)) }
    var examColor by remember { mutableStateOf(SubjectColorsPrefs.getExamColor(context)) }
    var cancelledStrike by remember { mutableStateOf(SubjectColorsPrefs.isCancelledStrike(context)) }
    var colorSources by remember { mutableStateOf(SubjectColorsPrefs.sources(context)) }
    var cancelledDesaturation by remember { mutableStateOf(AppearancePrefs.getCancelledDesaturation(context)) }
    var cancelledBrightness by remember { mutableStateOf(AppearancePrefs.getCancelledBrightness(context)) }
    var pastDim by remember { mutableStateOf(AppearancePrefs.getPastDim(context)) }
    var showTeacher by remember { mutableStateOf(AppearancePrefs.showTeacher(context)) }
    var showRoom by remember { mutableStateOf(AppearancePrefs.showRoom(context)) }
    var showChanges by remember { mutableStateOf(AppearancePrefs.showChanges(context)) }
    var hideCancelled by remember { mutableStateOf(AppearancePrefs.hideCancelled(context)) }
    var showExamBadge by remember { mutableStateOf(AppearancePrefs.showExamBadge(context)) }
    var showFreePeriods by remember { mutableStateOf(AppearancePrefs.showFreePeriods(context)) }

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
                subjectAliases = SubjectColorsPrefs.getAliases(context)
                knownSubjects = SubjectColorsPrefs.getKnown(context)
                defaultSubjectColor = SubjectColorsPrefs.getDefaultColor(context)
                lessonTextColor = SubjectColorsPrefs.getTextColor(context)
                strikeColor = SubjectColorsPrefs.getStrikeColor(context)
                examColor = SubjectColorsPrefs.getExamColor(context)
                cancelledStrike = SubjectColorsPrefs.isCancelledStrike(context)
                colorSources = SubjectColorsPrefs.sources(context)
            }

            if (key in AppearancePrefs.KEYS) {
                cancelledDesaturation = AppearancePrefs.getCancelledDesaturation(context)
                cancelledBrightness = AppearancePrefs.getCancelledBrightness(context)
                pastDim = AppearancePrefs.getPastDim(context)
                showTeacher = AppearancePrefs.showTeacher(context)
                showRoom = AppearancePrefs.showRoom(context)
                showChanges = AppearancePrefs.showChanges(context)
                hideCancelled = AppearancePrefs.hideCancelled(context)
                showExamBadge = AppearancePrefs.showExamBadge(context)
                showFreePeriods = AppearancePrefs.showFreePeriods(context)
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveTheme(newTheme: AppTheme) = ThemePrefs.save(context, newTheme)

    val effectiveSubjectColors = remember(knownSubjects, colorSources) {
        SubjectColorResolver.cardColors(knownSubjects, colorSources)
    }
    val effectiveSubjectTextColors = remember(knownSubjects, colorSources) {
        SubjectColorResolver.textColors(knownSubjects, colorSources)
    }
    val subjectColorState = remember(colorSources, effectiveSubjectColors) {
        SubjectColorState(
            sources = colorSources,
            effective = effectiveSubjectColors,
            onPreferSchool = { SubjectColorsPrefs.setPreferSchoolColors(context, it) },
            onAutoColors = { SubjectColorsPrefs.setAutoColors(context, it) },
        )
    }

    CompositionLocalProvider(
        LocalSubjectColorState provides subjectColorState,
        LocalHapticsEnabled provides hapticsEnabled,
        LocalAnimationsEnabled provides animationsEnabled,
    ) {
        UntisXTheme(mode = theme.mode, preset = theme.preset, customSeed = theme.customSeed, style = theme.style) {
            LaunchedEffect(Unit) {
                Notifications.ensureChannels(context)
                NotificationScheduler.ensureAll(context)
                sh.lsx.untisx.data.calendar.CalendarExportWorker.ensure(context)
                sh.lsx.untisx.data.update.AppUpdater.ensure(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    sh.lsx.untisx.data.automation.Automation.refresh(context)
                }
            }

            LaunchedEffect(currentAuth?.let { AccountPrefs.accountId(it) }) {
                NotificationScheduler.ensureAll(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    sh.lsx.untisx.data.automation.Automation.refresh(context)
                }
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

                    currentAuth == null -> Scaffold(
                        modifier = Modifier.fillMaxSize().appBackground(),
                        containerColor = appScreenContainerColor(),
                    ) { padding ->
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
                        subjectColors = effectiveSubjectColors,
                        knownSubjects = knownSubjects,
                        onSubjectColor = { name, argb -> SubjectColorsPrefs.setColor(context, name, argb) },
                        subjectTextColors = effectiveSubjectTextColors,
                        onSubjectTextColor = { name, argb -> SubjectColorsPrefs.setSubjectTextColor(context, name, argb) },
                        subjectAliases = subjectAliases,
                        onSubjectAlias = { name, short, long -> SubjectColorsPrefs.setAlias(context, name, short, long) },
                        defaultSubjectColor = defaultSubjectColor,
                        onDefaultSubjectColor = { SubjectColorsPrefs.setDefaultColor(context, it) },
                        lessonTextColor = lessonTextColor,
                        onLessonTextColor = { SubjectColorsPrefs.setTextColor(context, it) },
                        strikeColor = strikeColor,
                        onStrikeColor = { SubjectColorsPrefs.setStrikeColor(context, it) },
                        examColor = examColor,
                        onExamColor = { SubjectColorsPrefs.setExamColor(context, it) },
                        cancelledStrike = cancelledStrike,
                        onCancelledStrike = { SubjectColorsPrefs.setCancelledStrike(context, it) },
                        cancelledDesaturation = cancelledDesaturation,
                        onCancelledDesaturation = { AppearancePrefs.setCancelledDesaturation(context, it) },
                        cancelledBrightness = cancelledBrightness,
                        onCancelledBrightness = { AppearancePrefs.setCancelledBrightness(context, it) },
                        pastDim = pastDim,
                        onPastDim = { AppearancePrefs.setPastDim(context, it) },
                        lessonOptions = LessonCardOptions(
                            showTeacher = showTeacher,
                            showRoom = showRoom,
                            showChanges = showChanges,
                            showExamBadge = showExamBadge,
                            showFreePeriods = showFreePeriods,
                            strikeColor = Color(strikeColor ?: SubjectColorsPrefs.DEFAULT_STRIKE_COLOR),
                            examColor = examColor?.let { Color(it) },
                        ),
                        onLessonOptionsChange = { updated ->
                            AppearancePrefs.setShowTeacher(context, updated.showTeacher)
                            AppearancePrefs.setShowRoom(context, updated.showRoom)
                            AppearancePrefs.setShowChanges(context, updated.showChanges)
                            AppearancePrefs.setShowExamBadge(context, updated.showExamBadge)
                            AppearancePrefs.setShowFreePeriods(context, updated.showFreePeriods)
                        },
                        hideCancelled = hideCancelled,
                        onHideCancelledChange = { AppearancePrefs.setHideCancelled(context, it) },
                        focusDate = focusDate,
                        focusScreen = focusScreen,
                        focusNonce = focusNonce,
                        focusNotebookItemId = focusNotebookItemId,
                        focusNotebookAccountId = focusNotebookAccountId,
                        onFocusConsumed = onFocusConsumed,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsUnavailableScreen(onRetry: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize().appBackground(),
        containerColor = appScreenContainerColor(),
    ) { padding ->
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
    Scaffold(
        modifier = Modifier.fillMaxSize().appBackground(),
        containerColor = appScreenContainerColor(),
    ) { padding ->
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
