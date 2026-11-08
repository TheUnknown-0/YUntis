package sh.lsx.untisx.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.data.notebook.NotebookLessonLinks
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.ui.notebook.NotebookLessonSection
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.LocalThemeAppearance
import sh.lsx.untisx.ui.theme.NotebookExamMark
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.theme.notebookInk
import sh.lsx.untisx.ui.theme.notebookNoteColor
import sh.lsx.untisx.ui.theme.notebookPaperSeed
import sh.lsx.untisx.ui.theme.stickyNoteBackground
import sh.lsx.untisx.ui.theme.tornNotebookPaper
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.timetable.LessonCardColors
import sh.lsx.untisx.ui.timetable.LessonCardOptions
import sh.lsx.untisx.ui.timetable.StrikeOverlay
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.timetable.cancelledCardColors
import sh.lsx.untisx.ui.timetable.lessonCardBackground
import sh.lsx.untisx.ui.timetable.rememberToday
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.sharedElementBounds
import java.time.LocalDate

@Composable
fun LessonDetailsScreen(
    entry: TimetableEntry,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    notes: TimetableViewModel.NotesState? = null,
    exam: ExamInfo? = null,
    lessonOptions: LessonCardOptions = LessonCardOptions(),
    cancelledStrike: Boolean = true,
    cancelledDesaturation: Float = 1f,
    cancelledBrightness: Float = 1f,
    subjectAliases: Map<String, SubjectAlias> = emptyMap(),
    notebookLinks: NotebookLessonLinks? = null,
    onNotebookItemClick: (String) -> Unit = {},
    onCreateNote: () -> Unit = {},
    onCreateTask: () -> Unit = {},
    onJumpToDate: (Int) -> Unit = {},
) {
    val timeFormat = rememberTimeFormat()

    val subjectEl = entry.subject.firstOrNull()
    val subjectName = subjectEl
        ?.let { subjectsById[it.id]?.name?.takeIf { name -> name.isNotBlank() } ?: it.name }
        .orEmpty()

    val date = remember(entry.date) { entry.date.toUntisLocalDateOrNull() }

    val examType = exam?.examType?.takeIf { it.isNotBlank() } ?: entry.exam?.examtype
    val examName = exam?.name?.takeIf { it.isNotBlank() } ?: entry.exam?.name
    val examText = exam?.text?.takeIf { it.isNotBlank() } ?: entry.exam?.text
    val hasExam = examType != null || examName != null || examText != null
    val notesFade = motionEffects<Float>()
    val today = rememberToday()

    val cancelled = entry.cancelled
    val notebook = LocalThemeAppearance.current.style == ThemeStyle.NOTEBOOK
    val paperSeed = notebookPaperSeed(entry.id, entry.date, entry.startTime)
    val paper = if (notebook) notebookNoteColor(color, LocalThemeAppearance.current.dark) else color
    val ink = if (notebook) notebookInk(paper, textColor) else textColor

    val baseColors = if (cancelled) {
        cancelledCardColors(paper, ink, cancelledDesaturation, cancelledBrightness, notebook)
    } else {
        LessonCardColors(container = paper, title = ink, body = ink.copy(alpha = if (notebook) 1f else 0.8f))
    }

    val cardColors = if (notebook) {
        val noteInk = notebookInk(baseColors.container, ink)

        baseColors.copy(title = noteInk, body = noteInk)
    } else {
        baseColors
    }

    val showExamOutline = exam != null || entry.exam != null
    val examColor = lessonOptions.examColor ?: MaterialTheme.colorScheme.error
    val examBadgeText = if (lessonOptions.examColor == null) {
        MaterialTheme.colorScheme.onError
    } else if (examColor.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    val showExamBadge = showExamOutline && lessonOptions.showExamBadge

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .sharedElementBounds("lesson-${entry.id}-${entry.date}-${entry.startTime}")
                .fillMaxWidth()
                .then(
                    if (notebook && cancelled && cancelledStrike) Modifier.tornNotebookPaper(paperSeed) else Modifier,
                )
                .then(
                    if (notebook) {
                        Modifier.stickyNoteBackground(cardColors.container, null, paperSeed)
                    } else {
                        Modifier.lessonCardBackground(
                            container = cardColors.container,
                            cornerRadius = 16.dp,
                            examColor = if (showExamOutline) examColor else null,
                            strikeSlit = cancelled && cancelledStrike,
                        )
                    },
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(
                    text = subjectLongLabel(subjectAliases, subjectName)
                        .ifBlank { entry.lstext?.takeIf { it.isNotBlank() } ?: "?" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardColors.title,
                    modifier = if (showExamBadge) Modifier.padding(end = if (notebook) 124.dp else 84.dp) else Modifier,
                )

                subjectEl?.let { it -> subjectsById[it.id]?.longName }
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardColors.body,
                        )
                    }

                date?.let {
                    Text(
                        text = formatLessonDate(it, entry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardColors.body,
                    )
                }
            }

            if (showExamBadge) {
                if (notebook) {
                    NotebookExamMark(
                        text = examType ?: stringResource(R.string.lesson_exam),
                        paper = cardColors.container,
                        seed = paperSeed,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(examColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = examType ?: stringResource(R.string.lesson_exam),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = examBadgeText,
                        )
                    }
                }
            }

            if (!notebook && cancelled && cancelledStrike) {
                StrikeOverlay(
                    color = lessonOptions.strikeColor,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        if (hasExam) {
            DetailCard {
                Text(
                    text = stringResource(R.string.lesson_exam),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                examType?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                examName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                exam?.let { examInfo ->
                    if (examInfo.startTime != 0) {
                        DetailRow(
                            stringResource(R.string.lesson_time),
                            AnnotatedString(
                                "${examInfo.startTime.toUntisLocalTime().format(timeFormat)} - " +
                                    examInfo.endTime.toUntisLocalTime().format(timeFormat),
                            ),
                        )
                    }

                    val roomNames = examInfo.roomIds
                        .mapNotNull { id -> roomsById[id]?.name?.takeIf { name -> name.isNotBlank() } }
                        .distinct()

                    if (roomNames.isNotEmpty()) {
                        DetailRow(stringResource(R.string.lesson_room), AnnotatedString(roomNames.joinToString(", ")))
                    }

                    val teacherNames = examInfo.teacherIds
                        .mapNotNull { id -> teachersById[id]?.name?.takeIf { name -> name.isNotBlank() } }
                        .distinct()

                    if (teacherNames.isNotEmpty()) {
                        DetailRow(stringResource(R.string.lesson_teacher), AnnotatedString(teacherNames.joinToString(", ")))
                    }
                }

                examText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        DetailCard {
            DetailRow(stringResource(R.string.lesson_time), AnnotatedString("${entry.startTime.toUntisLocalTime().format(timeFormat)} - ${entry.endTime.toUntisLocalTime().format(timeFormat)}"))
            ElementRow(stringResource(R.string.lesson_teacher), entry.teacher, teachersById)
            ElementRow(stringResource(R.string.lesson_room), entry.room, roomsById)
        }

        val loaded = notes as? TimetableViewModel.NotesState.Loaded

        val infoText = (loaded?.info ?: entry.info ?: entry.lessonText ?: entry.lstext).takeAsContent()
        val substitutionText = (loaded?.substitution ?: entry.substText).takeAsContent()

        infoText?.let { info ->
            DetailCard {
                Text(
                    text = stringResource(R.string.lesson_info),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        substitutionText?.takeIf { it != infoText }?.let { substitution ->
            DetailCard {
                Text(
                    text = stringResource(R.string.lesson_substitution),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = substitution,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        AnimatedContent(
            targetState = notes,
            transitionSpec = {
                fadeIn(animationSpec = notesFade) togetherWith
                    fadeOut(animationSpec = notesFade)
            },
            label = "lessonNotes",
        ) { state ->
            when (state) {
                is TimetableViewModel.NotesState.Loading -> {
                    DetailCard {
                        Text(
                            text = stringResource(R.string.lesson_notes_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is TimetableViewModel.NotesState.Loaded -> {
                    state.topic?.let { topic ->
                        DetailCard {
                            Text(
                                text = stringResource(R.string.lesson_topic),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Text(
                                text = topic,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (state.notes.isNotEmpty()) {
                        DetailCard {
                            Text(
                                text = stringResource(R.string.lesson_notes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            state.notes.forEach { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        NotebookLessonSection(
            links = notebookLinks,
            today = today,
            onItemClick = onNotebookItemClick,
            onCreateNote = onCreateNote,
            onCreateTask = onCreateTask,
            onJumpToDate = onJumpToDate,
        )
    }
}

@Composable
private fun ElementRow(
    label: String,
    elements: List<TimetableElement>,
    data: Map<Int, MasterDataItem>?,
) {
    if (elements.isEmpty()) return

    val text = buildAnnotatedString {
        elements.forEachIndexed { index, el ->
            if (index > 0) append("  ")

            val current = data?.get(el.id)?.displayName() ?: el.name
            val orgId = el.orgId?.takeIf { it > 0 && it != el.id }
            val orig = when {
                orgId != null -> data?.get(orgId)?.displayName() ?: el.orgName
                else -> el.orgName?.takeIf { it.isNotBlank() }
            }

            if (orig.isNullOrBlank()) {
                append(current)
            } else {
                append(strikethroughChange(orig, current))
            }
        }
    }

    DetailRow(label, text)
}

private fun MasterDataItem.displayName(): String {
    val long = longName?.takeIf { it.isNotBlank() }
    val short = name.takeIf { it.isNotBlank() }

    return when {
        long != null && short != null && long != short -> "$long ($short)"
        long != null -> long
        else -> short.orEmpty()
    }
}

private fun strikethroughChange(orig: String, current: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            append(orig)
        }

        append(" → ")
        append(current)
    }

private fun String?.takeAsContent(): String? {
    val value = this?.trim() ?: return null

    if (value.isBlank() || value == "-" || value == "–" || value == "—") return null

    return value
}

@Composable
private fun formatLessonDate(date: LocalDate, entry: TimetableEntry): String {
    val dayName = rememberDateFormat("EEEEdMMMMyyyy").format(date)

    return if (date == LocalDate.now()) {
        stringResource(R.string.lesson_date_today, dayName)
    } else {
        dayName
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, text: AnnotatedString) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
