package sh.lsx.untisx.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.messages.MessageDetailsScreen
import sh.lsx.untisx.ui.messages.MessagesScreen
import sh.lsx.untisx.ui.messages.MessagesViewModel
import sh.lsx.untisx.ui.screens.AboutScreen
import sh.lsx.untisx.ui.screens.EventDetailsScreen
import sh.lsx.untisx.ui.screens.EventsScreen
import sh.lsx.untisx.ui.screens.LessonDetailsScreen
import sh.lsx.untisx.ui.screens.ProfileScreen
import sh.lsx.untisx.ui.screens.RoomsListScreen
import sh.lsx.untisx.ui.screens.StatsScreen
import sh.lsx.untisx.ui.screens.TeachersListScreen
import sh.lsx.untisx.ui.screens.settings.AppearanceSettingsScreen
import sh.lsx.untisx.ui.screens.settings.GeneralSettingsScreen
import sh.lsx.untisx.ui.screens.settings.NotificationSettingsScreen
import sh.lsx.untisx.ui.screens.settings.SettingsCategory
import sh.lsx.untisx.ui.screens.settings.SettingsOverviewScreen
import sh.lsx.untisx.ui.screens.settings.TimetableSettingsScreen
import sh.lsx.untisx.ui.timetable.LessonCardOptions
import sh.lsx.untisx.ui.timetable.TimetableScreen
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.motionSpatial

@Composable
internal fun ScreenHost(
    screen: Screen,
    padding: PaddingValues,
    auth: AuthData,
    timetableVm: TimetableViewModel,
    messagesVm: MessagesViewModel,
    teachers: List<MasterDataItem>,
    settingsCategory: SettingsCategory?,
    onSettingsCategoryChange: (SettingsCategory?) -> Unit,
    selectedLesson: TimetableEntry?,
    selectedEvent: TimetableEntry?,
    onLessonClick: (TimetableEntry) -> Unit,
    onOpenEvent: (TimetableEntry, Screen) -> Unit,
    onScreenChange: (Screen) -> Unit,
    onExamClick: (Int) -> Unit,
    focusDate: Int,
    focusNonce: Int,
    onFocusConsumed: () -> Unit,
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    showLastRefreshed: Boolean,
    onShowLastRefreshedChange: (Boolean) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsChange: (Boolean) -> Unit,
    knownSubjects: List<String>,
    subjectColors: Map<String, Int>,
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
) {
    val spatialSpec = motionSpatial<IntOffset>()
    val effectsSpec = motionEffects<Float>()

    val timetableContent: @Composable () -> Unit = {
        TimetableScreen(
            vm = timetableVm,
            subjectColors = subjectColors.mapValues { Color(it.value) },
            defaultColor = Color(defaultSubjectColor),
            textColor = Color(lessonTextColor),
            subjectTextColors = subjectTextColors.mapValues { Color(it.value) },
            subjectAliases = subjectAliases,
            cancelledStrike = cancelledStrike,
            cancelledDesaturation = cancelledDesaturation,
            cancelledBrightness = cancelledBrightness,
            pastDim = pastDim,
            lessonOptions = lessonOptions,
            hideCancelled = hideCancelled,
            showLastRefreshed = showLastRefreshed,
            focusDate = focusDate,
            focusNonce = focusNonce,
            onFocusConsumed = onFocusConsumed,
            onLessonClick = onLessonClick,
            onEventClick = { onOpenEvent(it, Screen.TIMETABLE) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

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
                        onOpenCategory = onSettingsCategoryChange,
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
                            subjectTextColors = subjectTextColors,
                            onSubjectTextColor = onSubjectTextColor,
                            subjectAliases = subjectAliases,
                            onSubjectAlias = onSubjectAlias,
                            defaultSubjectColor = defaultSubjectColor,
                            onDefaultSubjectColor = onDefaultSubjectColor,
                            lessonTextColor = lessonTextColor,
                            onLessonTextColor = onLessonTextColor,
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
                            strikeColor = strikeColor,
                            onStrikeColor = onStrikeColor,
                            examColor = examColor,
                            onExamColor = onExamColor,
                            hideCancelled = hideCancelled,
                            onHideCancelledChange = onHideCancelledChange,
                            modifier = settingsModifier,
                        )
                    }
                }
            }
        }

        Screen.TEACHERS -> TeachersListScreen(
            teachers = teachers,
            modifier = Modifier.padding(padding),
        )

        Screen.EVENTS -> EventsScreen(
            onExamClick = onExamClick,
            events = timetableVm.events,
            holidays = timetableVm.holidays,
            messages = timetableVm.dayMessages,
            roomsById = timetableVm.roomsById,
            loading = timetableVm.eventsLoading,
            onRefresh = timetableVm::refreshEvents,
            onEventClick = { onOpenEvent(it, Screen.EVENTS) },
            exams = timetableVm.exams,
            subjectsById = timetableVm.subjectsById,
            subjectAliases = subjectAliases,
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
        } ?: timetableContent()

        Screen.MESSAGES -> MessagesScreen(
            vm = messagesVm,
            onMessageClick = { onScreenChange(Screen.MESSAGE_DETAILS) },
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
            val subjectName = lesson.subject.firstOrNull()
                ?.let { timetableVm.subjectsById[it.id]?.name?.takeIf { name -> name.isNotBlank() } ?: it.name }

            LessonDetailsScreen(
                entry = lesson,
                subjectsById = timetableVm.subjectsById,
                teachersById = timetableVm.teachersById,
                roomsById = timetableVm.roomsById,
                color = subjectColors[subjectName]?.let { Color(it) } ?: Color(defaultSubjectColor),
                textColor = Color(
                    subjectName?.let { subjectTextColors[it] } ?: lessonTextColor,
                ),
                subjectAliases = subjectAliases,
                notes = timetableVm.lessonNotes,
                exam = timetableVm.examFor(lesson),
                lessonOptions = lessonOptions,
                cancelledStrike = cancelledStrike,
                cancelledDesaturation = cancelledDesaturation,
                cancelledBrightness = cancelledBrightness,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } ?: timetableContent()

        else -> timetableContent()
    }
}
