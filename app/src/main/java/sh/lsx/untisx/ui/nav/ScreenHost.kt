package sh.lsx.untisx.ui.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import sh.lsx.untisx.data.SubjectStats
import sh.lsx.untisx.data.prefs.StatsPrefs
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.ui.screens.TeacherInfoScreen
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sh.lsx.untisx.ui.components.LocalSubjectColorState
import sh.lsx.untisx.ui.components.SubjectColorDialog
import sh.lsx.untisx.ui.components.ExamLessonSlot
import sh.lsx.untisx.ui.components.ExamSubjectChoice
import sh.lsx.untisx.ui.components.OwnExamDialog
import sh.lsx.untisx.ui.components.newOwnExam
import sh.lsx.untisx.ui.timetable.resolveSubject
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.subjectLongLabel
import androidx.compose.ui.res.stringResource
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.ui.game.DinoGameScreen
import sh.lsx.untisx.ui.homework.HomeworkScreen
import sh.lsx.untisx.ui.absences.AbsencesScreen
import sh.lsx.untisx.ui.game.buildDinoDays
import sh.lsx.untisx.ui.game.sampleDays
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookSearch
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.messages.MessageDetailsScreen
import sh.lsx.untisx.ui.messages.MessagesScreen
import sh.lsx.untisx.ui.messages.MessagesViewModel
import sh.lsx.untisx.ui.notebook.NotebookEditorScreen
import sh.lsx.untisx.ui.notebook.NotebookExportImportAction
import sh.lsx.untisx.ui.notebook.NotebookFocusNotice
import sh.lsx.untisx.ui.notebook.NotebookLessonTarget
import sh.lsx.untisx.ui.notebook.NotebookListScreen
import sh.lsx.untisx.ui.notebook.NotebookSubjectChoices
import sh.lsx.untisx.ui.notebook.NotebookViewModel
import sh.lsx.untisx.ui.notebook.notebookBadgeMap
import sh.lsx.untisx.ui.notebook.notebookEntryRef
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
import sh.lsx.untisx.ui.timetable.CompareSource
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.motionSpatial

@Composable
internal fun ScreenHost(
    screen: Screen,
    padding: PaddingValues,
    auth: AuthData,
    timetableVm: TimetableViewModel,
    compare: CompareSource? = null,
    lessonVm: TimetableViewModel = timetableVm,
    lessonReadOnly: Boolean = false,
    profileStartAdding: Boolean = false,
    selectedSubjectId: Int = 0,
    onSubjectClick: (Int) -> Unit = {},
    onShare: (sh.lsx.untisx.data.share.SharePayload) -> Unit = {},
    messagesVm: MessagesViewModel,
    notebookVm: NotebookViewModel,
    notebookItems: List<NotebookItem>,
    notebookFocusNotice: NotebookFocusNotice?,
    onNotebookFocusNoticeShown: () -> Unit,
    onNotebookJumpToDate: (Int) -> Unit,
    notebookEditorReturn: Screen,
    onNotebookEditorClosed: () -> Unit,
    notebookLessonTarget: NotebookLessonTarget?,
    onNotebookOpenItem: (String, Screen) -> Unit,
    onNotebookCreate: (NotebookKind, LessonRef?, Screen) -> Unit,
    onNotebookPickLesson: (NotebookLessonTarget) -> Unit,
    onNotebookLessonSelected: (TimetableEntry) -> Unit,
    teachers: List<MasterDataItem>,
    settingsCategory: SettingsCategory?,
    onSettingsCategoryChange: (SettingsCategory?) -> Unit,
    selectedLesson: TimetableEntry?,
    selectedEvent: TimetableEntry?,
    onLessonClick: (TimetableEntry) -> Unit,
    selectedTeacherId: Int = 0,
    onTeacherClick: (Int) -> Unit = {},
    onShowTargetTimetable: (TimetableTarget) -> Unit = {},
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
    val colorState = LocalSubjectColorState.current
    var colorDialogFor by remember { mutableStateOf<String?>(null) }
    var ownExamEdit by remember { mutableStateOf<Pair<ExamInfo, Boolean>?>(null) }
    val defaultExamType = stringResource(R.string.own_exam_default_type)

    ownExamEdit?.let { (exam, isNew) ->
        val subjectChoices = remember(knownSubjects, timetableVm.subjectsById, subjectAliases) {
            knownSubjects.map { name ->
                ExamSubjectChoice(
                    id = timetableVm.subjectsById.values.firstOrNull { it.name == name }?.id ?: 0,
                    name = name,
                    label = subjectLongLabel(subjectAliases, name).let { label ->
                        if (label != name) "$label ($name)" else
                            timetableVm.subjectsById.values.firstOrNull { it.name == name }?.longName
                                ?.takeIf { it.isNotBlank() && it != name }?.let { "$it ($name)" } ?: name
                    },
                )
            }
        }

        OwnExamDialog(
            initial = exam,
            isNew = isNew,
            subjects = subjectChoices,
            lessonsOn = { date, name -> examLessonSlots(timetableVm, date, name) },
            onSave = {
                timetableVm.saveOwnExam(it)
                ownExamEdit = null
            },
            onDelete = {
                timetableVm.deleteOwnExam(exam.id)
                ownExamEdit = null
            },
            onDismiss = { ownExamEdit = null },
        )
    }
    val effectsSpec = motionEffects<Float>()

    val notebookSubjectChoices by notebookVm.subjectChoices.collectAsStateWithLifecycle()
    val notebookSubjects = (notebookSubjectChoices as? NotebookSubjectChoices.Ready)
        ?.subjects
        .orEmpty()

    val notebookBadges = remember(notebookItems, timetableVm.weeks) {
        notebookBadgeMap(timetableVm.weeks.values.flatten(), notebookItems)
    }

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
            notebookBadges = notebookBadges,
            focusDate = focusDate,
            focusNonce = focusNonce,
            onFocusConsumed = onFocusConsumed,
            compare = compare,
            onLessonClick = onLessonClick,
            onEventClick = { onOpenEvent(it, Screen.TIMETABLE) },
            selectingLesson = notebookLessonTarget != null,
            onLessonSelected = onNotebookLessonSelected,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    when (screen) {
        Screen.PROFILE -> ProfileScreen(
            startAdding = profileStartAdding,
            onOpenRights = { onScreenChange(Screen.RIGHTS) },
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
                            dataTransferContent = {
                                androidx.compose.runtime.key(notebookVm.accountId) {
                                    NotebookExportImportAction(
                                        vm = notebookVm,
                                        accountLabel = auth.user.ifBlank { auth.loginName },
                                    )
                                }
                            },
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

                        SettingsCategory.CALENDAR -> sh.lsx.untisx.ui.screens.settings.CalendarSettingsScreen(
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

        Screen.NOTEBOOK -> NotebookListScreen(
            vm = notebookVm,
            subjects = notebookSubjects,
            subjectAliases = subjectAliases,
            accountLabel = auth.user.ifBlank { auth.loginName },
            focusNotice = notebookFocusNotice,
            onFocusNoticeShown = onNotebookFocusNoticeShown,
            onOpenItem = { itemId -> onNotebookOpenItem(itemId, Screen.NOTEBOOK) },
            onCreate = { kind, origin -> onNotebookCreate(kind, origin, Screen.NOTEBOOK) },
            onJumpToDate = onNotebookJumpToDate,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.ABSENCES -> AbsencesScreen(
            lessons = timetableVm.ownEntries(),
            subjects = timetableVm.subjectsById,
            onOpenDate = onExamClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.HOMEWORK -> HomeworkScreen(
            items = notebookItems,
            subjectColors = subjectColors,
            defaultColor = defaultSubjectColor,
            subjectAliases = subjectAliases,
            onToggle = notebookVm::toggleCompleted,
            onOpen = { itemId -> onNotebookOpenItem(itemId, Screen.HOMEWORK) },
            onCreate = { onNotebookCreate(NotebookKind.TASK, null, Screen.HOMEWORK) },
            onShare = { item ->
                onShare(
                    sh.lsx.untisx.data.share.SharePayload.Homework(
                        from = auth.user,
                        title = item.title.ifBlank { item.displayTitle },
                        body = item.body.takeIf { it != item.displayTitle }.orEmpty(),
                        subject = item.subject?.longName?.takeIf { it.isNotBlank() } ?: item.subject?.name.orEmpty(),
                        due = item.due?.date ?: 0,
                    ),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.NOTEBOOK_EDITOR -> NotebookEditorScreen(
            vm = notebookVm,
            onBack = {
                notebookVm.closeEditor()
                onNotebookEditorClosed()
            },
            onPickOriginLesson = { onNotebookPickLesson(NotebookLessonTarget.ORIGIN) },
            onPickDueLesson = { onNotebookPickLesson(NotebookLessonTarget.DUE) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.TEACHERS -> TeachersListScreen(
            teachers = teachers,
            modifier = Modifier.padding(padding),
        )

        Screen.EXAMS -> sh.lsx.untisx.ui.screens.ExamPlanScreen(
            exams = timetableVm.exams,
            weeks = timetableVm.ownWeeks(),
            holidays = timetableVm.holidays,
            subjectsById = timetableVm.subjectsById,
            subjectAliases = subjectAliases,
            colorFor = { name -> subjectColors[name]?.let { Color(it) } ?: Color(defaultSubjectColor) },
            onOpenDate = onExamClick,
            onLoadWeeksUntil = timetableVm::preloadWeeksUntil,
            onShare = { exam ->
                val subject = timetableVm.subjectsById[exam.subjectId]
                onShare(
                    sh.lsx.untisx.data.share.SharePayload.Exam(
                        from = auth.user,
                        type = exam.examType,
                        subject = subject?.longName?.takeIf { it.isNotBlank() } ?: subject?.name ?: exam.subjectName,
                        date = exam.date,
                        start = exam.startTime,
                        end = exam.endTime,
                        name = exam.name,
                        text = exam.text,
                    ),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.RIGHTS -> sh.lsx.untisx.ui.screens.RightsScreen(
            auth = auth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.SEARCH -> {
            val data = remember(
                timetableVm.subjectsById, timetableVm.teachersById, timetableVm.roomsById,
                timetableVm.exams, timetableVm.events, notebookItems, messagesVm.incoming, timetableVm.weeks,
            ) {
                sh.lsx.untisx.ui.screens.SearchData(
                    subjects = timetableVm.subjectsById,
                    teachers = timetableVm.teachersById,
                    rooms = timetableVm.roomsById,
                    exams = timetableVm.exams,
                    events = timetableVm.events,
                    notebook = notebookItems,
                    messages = messagesVm.incoming,
                    lessons = timetableVm.ownEntries(),
                )
            }

            sh.lsx.untisx.ui.screens.SearchScreen(
                data = data,
                onOpen = { target ->
                    when (target) {
                        is sh.lsx.untisx.ui.screens.SearchTarget.Subject -> onSubjectClick(target.id)
                        is sh.lsx.untisx.ui.screens.SearchTarget.Teacher -> onTeacherClick(target.id)
                        sh.lsx.untisx.ui.screens.SearchTarget.Rooms -> onScreenChange(Screen.ROOMS)
                        is sh.lsx.untisx.ui.screens.SearchTarget.Note -> onNotebookOpenItem(target.itemId, Screen.SEARCH)
                        is sh.lsx.untisx.ui.screens.SearchTarget.Date -> onExamClick(target.date)
                        is sh.lsx.untisx.ui.screens.SearchTarget.Event -> onOpenEvent(target.entry, Screen.SEARCH)
                        is sh.lsx.untisx.ui.screens.SearchTarget.Lesson -> onLessonClick(target.entry)
                        is sh.lsx.untisx.ui.screens.SearchTarget.Message -> {
                            messagesVm.open(target.message)
                            onScreenChange(Screen.MESSAGE_DETAILS)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }

        Screen.CHANGES -> sh.lsx.untisx.ui.screens.ChangeHistoryScreen(
            accountId = sh.lsx.untisx.data.prefs.AccountPrefs.accountId(auth),
            onOpenDate = onExamClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
            onAddExam = { ownExamEdit = newOwnExam(type = defaultExamType) to true },
            onEditOwnExam = { ownExamEdit = it to false },
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
            onSubjectClick = onSubjectClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.SUBJECT_YEAR -> {
            val year = timetableVm.statsSchoolYear()
            val subjectName = timetableVm.subjectsById[selectedSubjectId]?.name.orEmpty()

            sh.lsx.untisx.ui.screens.SubjectYearScreen(
                subjectId = selectedSubjectId,
                subjectsById = timetableVm.subjectsById,
                stats = timetableVm.currentYearStats(),
                yearStart = year?.startDate?.toUntisLocalDateOrNull(),
                yearEnd = year?.endDate?.toUntisLocalDateOrNull(),
                weeks = timetableVm.ownWeeks(),
                holidays = timetableVm.holidays,
                exams = timetableVm.exams,
                notebook = notebookItems,
                color = subjectColors[subjectName]?.let { Color(it) } ?: Color(defaultSubjectColor),
                onOpenDate = onExamClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }

        Screen.DINO -> {
            val realDays = remember(timetableVm.weeks, subjectColors, subjectTextColors, subjectAliases) {
                buildDinoDays(
                    weeks = timetableVm.weeks,
                    subjectsById = timetableVm.subjectsById,
                    aliases = subjectAliases,
                    colors = subjectColors,
                    textColors = subjectTextColors,
                    defaultColor = defaultSubjectColor,
                    defaultTextColor = lessonTextColor,
                    today = AppClock.today(),
                )
            }

            DinoGameScreen(
                days = realDays.ifEmpty { sampleDays() },
                usesRealTimetable = realDays.isNotEmpty(),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }

        Screen.ABOUT -> AboutScreen(
            auth = auth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )

        Screen.TEACHER_INFO -> {
            val context = LocalContext.current
            val yearStats = timetableVm.currentYearStats()
            val totals = remember(yearStats) {
                SubjectStats.totals(yearStats?.days?.values.orEmpty(), StatsPrefs.includeEvents(context))
            }
            val classStats = timetableVm.classYearStats()
            val ownAbsence = remember(yearStats, selectedTeacherId) {
                SubjectStats.teacherTotals(yearStats?.days?.values.orEmpty(), selectedTeacherId)
            }
            val classAbsence = remember(classStats, selectedTeacherId) {
                SubjectStats.teacherTotals(classStats?.days?.values.orEmpty(), selectedTeacherId)
            }

            // fills in missing weeks: the own ones and, for the class rate, the own class
            LaunchedEffect(Unit) {
                timetableVm.loadHistoricalStats()
                timetableVm.loadClassStats()
            }

            TeacherInfoScreen(
                teacherId = selectedTeacherId,
                teachersById = timetableVm.teachersById,
                subjectsById = timetableVm.subjectsById,
                subjectAliases = subjectAliases,
                ownEntries = remember(timetableVm.weeks, timetableVm.viewTarget) { timetableVm.ownEntries() },
                subjectTotals = totals,
                colorFor = { name -> subjectColors[name]?.let { Color(it) } ?: Color(defaultSubjectColor) },
                textColorFor = { name -> Color(subjectTextColors[name] ?: lessonTextColor) },
                onLessonClick = onLessonClick,
                onShowTimetable = onShowTargetTimetable,
                ownAbsence = ownAbsence,
                classAbsence = classAbsence,
                className = timetableVm.targetClasses.firstOrNull { it.id == timetableVm.ownClassId }?.name.orEmpty(),
                classLoading = timetableVm.classStatsLoading,
                classProgress = timetableVm.classStatsProgress,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }

        Screen.LESSON_DETAILS -> if (selectedLesson == null) {
            timetableContent()
        } else {
            val lesson = selectedLesson
            val subjectName = lesson.subject.firstOrNull()
                ?.let { lessonVm.subjectsById[it.id]?.name?.takeIf { name -> name.isNotBlank() } ?: it.name }

            val lessonRef = remember(lesson) { notebookEntryRef(lesson) }
            val notebookLinks = remember(notebookItems, lessonRef, lessonReadOnly) {
                if (lessonReadOnly) null else NotebookSearch.linksForLesson(notebookItems, lessonRef)
            }

            LessonDetailsScreen(
                entry = lesson,
                subjectsById = lessonVm.subjectsById,
                teachersById = lessonVm.teachersById,
                roomsById = lessonVm.roomsById,
                color = subjectColors[subjectName]?.let { Color(it) } ?: Color(defaultSubjectColor),
                textColor = Color(
                    subjectName?.let { subjectTextColors[it] } ?: lessonTextColor,
                ),
                subjectAliases = subjectAliases,
                notes = lessonVm.lessonNotes,
                exam = lessonVm.examFor(lesson),
                lessonOptions = lessonOptions,
                cancelledStrike = cancelledStrike,
                cancelledDesaturation = cancelledDesaturation,
                cancelledBrightness = cancelledBrightness,
                notebookLinks = notebookLinks,
                onNotebookItemClick = { itemId ->
                    onNotebookOpenItem(itemId, Screen.LESSON_DETAILS)
                },
                onCreateNote = {
                    onNotebookCreate(NotebookKind.NOTE, lessonRef, Screen.LESSON_DETAILS)
                },
                onCreateTask = {
                    onNotebookCreate(NotebookKind.TASK, lessonRef, Screen.LESSON_DETAILS)
                },
                onJumpToDate = onNotebookJumpToDate,
                onTeacherClick = onTeacherClick.takeUnless { lessonReadOnly },
                showNotebook = !lessonReadOnly,
                onEditColor = subjectName?.takeIf { it.isNotBlank() }?.let { { colorDialogFor = it } },
                ownExam = lessonVm.examFor(lesson)?.takeIf { it.own },
                onAddExam = if (lessonReadOnly) null else {
                    {
                    val existing = lessonVm.examFor(lesson)?.takeIf { it.own }
                    ownExamEdit = if (existing != null) {
                        existing to false
                    } else {
                        newOwnExam(
                            date = lesson.date,
                            subjectId = lesson.subject.firstOrNull()?.id ?: 0,
                            subjectName = subjectName.orEmpty(),
                            startTime = lesson.startTime,
                            endTime = lesson.endTime,
                            type = defaultExamType,
                        ) to true
                    }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            colorDialogFor?.let { name ->
                SubjectColorDialog(
                    subject = name,
                    cardColor = colorState.sources.own[name],
                    textColor = colorState.sources.ownText[name],
                    defaultCardColor = colorState.fallbackColor(name, defaultSubjectColor),
                    defaultTextColor = lessonTextColor,
                    schoolColor = colorState.sources.school[name],
                    lockedBySchool = colorState.lockedBySchool(name),
                    onApply = { card, text ->
                        onSubjectColor(name, card)
                        onSubjectTextColor(name, text)
                        colorDialogFor = null
                    },
                    onDismiss = { colorDialogFor = null },
                )
            }
        }

        else -> timetableContent()
    }
}

/** Lessons of a subject on a day, offered as time choices for an own exam. */
private fun examLessonSlots(vm: TimetableViewModel, date: Int, subjectName: String): List<ExamLessonSlot> =
    vm.weeks.values.flatten()
        .filter { it.date == date && !it.cancelled && resolveSubject(it, vm.subjectsById) == subjectName }
        .distinctBy { it.startTime to it.endTime }
        .sortedBy { it.startTime }
        .map {
            ExamLessonSlot(
                startTime = it.startTime,
                endTime = it.endTime,
                label = "%02d:%02d – %02d:%02d".format(it.startTime / 100, it.startTime % 100, it.endTime / 100, it.endTime % 100),
            )
        }
