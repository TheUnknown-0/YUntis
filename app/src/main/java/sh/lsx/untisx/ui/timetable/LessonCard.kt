package sh.lsx.untisx.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.sqrt
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.theme.LocalThemeAppearance
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.ui.theme.notebookNoteColor
import sh.lsx.untisx.ui.theme.notebookInk
import sh.lsx.untisx.ui.theme.stickyNoteBackground
import sh.lsx.untisx.ui.theme.tornNotebookPaper
import sh.lsx.untisx.ui.theme.notebookPaperSeed
import sh.lsx.untisx.ui.theme.paperVariation
import sh.lsx.untisx.ui.theme.NotebookLessonTextStyle
import sh.lsx.untisx.ui.theme.notebookLessonTypeScale
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.ui.components.OwnExamColor
import sh.lsx.untisx.data.notebook.NotebookLessonSummary
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.sharedElementBounds


@Immutable
data class LessonCardOptions(
    val showTeacher: Boolean = true,
    val showRoom: Boolean = true,
    val showChanges: Boolean = true,
    val showExamBadge: Boolean = true,
    val strikeColor: Color = Color(SubjectColorsPrefs.DEFAULT_STRIKE_COLOR),
    val examColor: Color? = null,
)

internal fun changedText(orig: String?, current: String, showChanges: Boolean = true): AnnotatedString =
    if (!showChanges || orig.isNullOrBlank()) {
        AnnotatedString(current)
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(orig)
            }

            append(" → ")
            append(current)
        }
    }

internal data class LessonCardColors(
    val container: Color,
    val title: Color,
    val body: Color,
)

internal fun cancelledCardColors(
    color: Color,
    textColor: Color,
    desaturation: Float,
    brightness: Float,
    notebook: Boolean = false,
): LessonCardColors {
    val grayAmount = desaturation.coerceIn(if (notebook) 0.8f else 0f, 1f)
    val darkAmount = if (notebook) 0.55f + 0.25f * brightness.coerceIn(0f, 1f) else brightness.coerceIn(0f, 1f)

    val luma = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val desaturated = lerp(color, Color(luma, luma, luma, color.alpha), grayAmount)
    val factor = 1f - 0.58f * darkAmount
    val container = Color(
        red = desaturated.red * factor,
        green = desaturated.green * factor,
        blue = desaturated.blue * factor,
        alpha = desaturated.alpha,
    )

    val fade = (grayAmount + darkAmount) / 2f

    return LessonCardColors(
        container = container,
        title = textColor.copy(alpha = 1f - 0.3f * fade),
        body = textColor.copy(alpha = 0.8f - 0.25f * fade),
    )
}

internal fun Modifier.lessonCardBackground(
    container: Color,
    cornerRadius: Dp,
    examColor: Color?,
    outline: Color? = null,
): Modifier = drawBehind {
    val radius = cornerRadius.toPx()

    drawRoundRect(
        color = container,
        cornerRadius = CornerRadius(radius),
    )

    (examColor ?: outline)?.let { border ->
        val width = (if (examColor != null) 2.dp else 1.5.dp).toPx()

        drawRoundRect(
            color = border,
            topLeft = Offset(width / 2f, width / 2f),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius((radius - width / 2f).coerceAtLeast(0f)),
            style = Stroke(width = width),
        )
    }
}

/**
 * Look of a cancelled lesson: a pale card in the subject colour, tinted towards [mark]
 * (the cancellation colour, red by default). Text is struck through by the caller and the
 * card gets a [mark] outline.
 */
internal fun cancelledMarkColors(color: Color, mark: Color, surface: Color): LessonCardColors {
    val tinted = lerp(color, mark, 0.3f)
    val container = lerp(tinted, surface, 0.55f).copy(alpha = 1f)
    val ink = if (container.luminance() > 0.45f) Color(0xFF1C1B1F) else Color(0xFFF4EFF4)

    return LessonCardColors(
        container = container,
        title = ink.copy(alpha = 0.8f),
        body = ink.copy(alpha = 0.65f),
    )
}

@Composable
internal fun LessonCard(
    lesson: LessonDisplay,
    color: Color,
    textColor: Color,
    strike: Boolean = true,
    cancelledDesaturation: Float = 1f,
    cancelledBrightness: Float = 1f,
    options: LessonCardOptions = LessonCardOptions(),
    notebookBadge: NotebookLessonSummary? = null,
    sharedElement: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val entry = lesson.entry
    val cancelled = entry.cancelled
    val notebook = LocalThemeAppearance.current.style == ThemeStyle.NOTEBOOK
    val paperSeed = notebookPaperSeed(entry.id, entry.date, entry.startTime)
    val badgeRotation = remember(paperSeed) { paperVariation(paperSeed).badgeRotation }
    val noteColor = if (notebook) notebookNoteColor(color, LocalThemeAppearance.current.dark) else color
    val inkColor = if (notebook) notebookInk(noteColor, textColor) else textColor

    val bounds = if (sharedElement) {
        Modifier.sharedElementBounds("lesson-${entry.id}-${entry.date}-${entry.startTime}")
    } else {
        Modifier
    }

    val surface = MaterialTheme.colorScheme.surface
    val marked = cancelled && strike && !notebook
    val target = if (marked) {
        cancelledMarkColors(noteColor, options.strikeColor, surface)
    } else if (cancelled) {
        cancelledCardColors(noteColor, inkColor, cancelledDesaturation, cancelledBrightness, notebook).let {
            if (notebook) {
                val ink = notebookInk(it.container, inkColor)
                it.copy(title = ink, body = ink)
            } else {
                it
            }
        }
    } else {
        LessonCardColors(
            container = noteColor,
            title = inkColor,
            body = inkColor.copy(alpha = if (notebook) 1f else 0.8f),
        )
    }

    val animations = LocalAnimationsEnabled.current

    val container by animateColorAsState(
        targetValue = target.container,
        animationSpec = if (animations) AppMotion.effects() else snap(),
        label = "lessonContainer",
    )

    val titleColor by animateColorAsState(
        targetValue = target.title,
        animationSpec = if (animations) AppMotion.effects() else snap(),
        label = "lessonTitleColor",
    )

    val bodyColor by animateColorAsState(
        targetValue = target.body,
        animationSpec = if (animations) AppMotion.effects() else snap(),
        label = "lessonBodyColor",
    )

    val ownExam = lesson.exam?.own == true
    val examColor = if (ownExam) OwnExamColor else options.examColor ?: MaterialTheme.colorScheme.error
    val examTextColor = if (ownExam) {
        Color.White
    } else if (options.examColor == null) {
        MaterialTheme.colorScheme.onError
    } else if (examColor.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }

    var cell by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val measured = cell.width > 0 && cell.height > 0
    val compact = measured && with(density) { cell.width.toDp() } < 64.dp
    val shortNote = notebook && measured && with(density) { cell.height.toDp() } < 72.dp
    val noteTypeScale = if (shortNote) {
        notebookLessonTypeScale(with(density) { cell.height.toDp().value }, density.fontScale)
    } else {
        1f
    }
    val noteTextStyle = if (notebook) NotebookLessonTextStyle else LocalTextStyle.current
    val cardCornerRadius = if (notebook) 1.dp else 6.dp

    Box(
        modifier = modifier
            .then(bounds)
            .onSizeChanged { cell = it }
            .then(if (notebook && cancelled && strike) Modifier.tornNotebookPaper(paperSeed) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (notebook) 2.dp else 1.dp)
                .then(
                    if (notebook) {
                        Modifier.stickyNoteBackground(container, if (lesson.exam != null) examColor else null, paperSeed)
                    } else {
                        Modifier.lessonCardBackground(
                            container = container,
                            cornerRadius = cardCornerRadius,
                            examColor = if (lesson.exam != null) examColor else null,
                            outline = if (marked) options.strikeColor else null,
                        )
                    },
                )
                .padding(horizontal = if (compact) 2.dp else 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (lesson.teachers.isNotBlank()) {
                Text(
                    text = changedText(lesson.origTeachers, lesson.teachers, options.showChanges),
                    style = noteTextStyle,
                    fontSize = (if (notebook && !shortNote) 10.sp else 9.sp) * noteTypeScale,
                    lineHeight = (if (notebook) 12.sp else 11.sp) * noteTypeScale,
                    color = bodyColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (marked) TextDecoration.LineThrough else null,
                    modifier = Modifier
                        .alpha(if (options.showTeacher) 1f else 0f)
                        .then(if (options.showTeacher) Modifier else Modifier.clearAndSetSemantics {}),
                )
            }

            Text(
                text = lesson.label,
                style = noteTextStyle,
                fontSize = (if (notebook) {
                    if (compact) 11.sp else if (shortNote) 13.sp else 15.sp
                } else {
                    if (compact) 9.sp else 13.sp
                }) * noteTypeScale,
                lineHeight = if (notebook) {
                    (if (compact) 14.sp else if (shortNote) 16.sp else 18.sp) * noteTypeScale
                } else {
                    TextUnit.Unspecified
                },
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                textDecoration = if (marked) TextDecoration.LineThrough else null,
                modifier = Modifier.fillMaxWidth(),
            )

            if (lesson.rooms.isNotBlank()) {
                Text(
                    text = changedText(lesson.origRooms, lesson.rooms, options.showChanges),
                    style = noteTextStyle,
                    fontSize = (if (notebook) {
                        if (shortNote || compact) 9.sp else 10.sp
                    } else {
                        if (compact) 8.sp else 9.sp
                    }) * noteTypeScale,
                    lineHeight = (if (notebook) 12.sp else 11.sp) * noteTypeScale,
                    color = bodyColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (marked) TextDecoration.LineThrough else null,
                    modifier = Modifier
                        .alpha(if (options.showRoom) 1f else 0f)
                        .then(if (options.showRoom) Modifier else Modifier.clearAndSetSemantics {}),
                )
            }
        }

        if (options.showExamBadge) {
            lesson.exam?.let { exam ->
                val label = exam.examType.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.lesson_exam)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 1.dp)
                        .background(
                            examColor,
                            RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 4.dp,
                                bottomEnd = 0.dp,
                                bottomStart = 4.dp,
                            ),
                        )
                        .padding(horizontal = if (compact) 3.dp else 4.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = if (compact) 7.sp else 8.sp,
                        lineHeight = if (compact) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = examTextColor,
                        maxLines = 1,
                    )
                }
            }
        }

        val badgeCount = notebookBadge?.badgeCount ?: 0

        if (badgeCount > 0) {
            val badgeDescription = stringResource(R.string.notebook_badge_description, badgeCount)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
                    .graphicsLayer { rotationZ = if (notebook) badgeRotation else 0f }
                    .background(
                        examColor,
                        RoundedCornerShape(
                            topStart = if (notebook) 0.dp else cardCornerRadius,
                            topEnd = 0.dp,
                            bottomEnd = if (notebook) 0.dp else cardCornerRadius,
                            bottomStart = 0.dp,
                        ),
                    )
                    .clearAndSetSemantics { contentDescription = badgeDescription }
                    .padding(horizontal = if (compact) 3.dp else 4.dp),
            ) {
                Text(
                    text = badgeCount.toString(),
                    fontSize = if (compact) 7.sp else 8.sp,
                    lineHeight = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = examTextColor,
                    maxLines = 1,
                )
            }
        }
    }
}
