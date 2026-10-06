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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.sqrt
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.sharedElementBounds

private val RibbonWidth = 6.dp
private val SlitGap = 1.5.dp

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
): LessonCardColors {
    val grayAmount = desaturation.coerceIn(0f, 1f)
    val darkAmount = brightness.coerceIn(0f, 1f)

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

@Composable
internal fun LessonCard(
    lesson: LessonDisplay,
    color: Color,
    textColor: Color,
    strike: Boolean = true,
    cancelledDesaturation: Float = 1f,
    cancelledBrightness: Float = 1f,
    options: LessonCardOptions = LessonCardOptions(),
    sharedElement: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val entry = lesson.entry
    val cancelled = entry.cancelled

    val bounds = if (sharedElement) {
        Modifier.sharedElementBounds("lesson-${entry.id}-${entry.date}-${entry.startTime}")
    } else {
        Modifier
    }

    val target = if (cancelled) {
        cancelledCardColors(color, textColor, cancelledDesaturation, cancelledBrightness)
    } else {
        LessonCardColors(
            container = color,
            title = textColor,
            body = textColor.copy(alpha = 0.8f),
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

    val examColor = options.examColor ?: MaterialTheme.colorScheme.error
    val examTextColor = if (options.examColor == null) {
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
    val angle = if (measured) {
        Math.toDegrees(atan2(cell.height.toFloat(), cell.width.toFloat()).toDouble()).toFloat()
    } else 0f

    Box(
        modifier = modifier
            .then(bounds)
            .onSizeChanged { cell = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .drawBehind {
                    drawRoundRect(
                        color = container,
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )

                    if (lesson.exam != null) {
                        drawRoundRect(
                            color = examColor,
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }

                    if (cancelled && strike && measured) {
                        val radius = 6.dp.toPx()
                        val bandWidth = RibbonWidth.toPx() + 3 * SlitGap.toPx()
                        val bandLength = size.width * 3f

                        val cardShape = Path().apply {
                            addRoundRect(
                                RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)),
                            )
                        }

                        clipPath(cardShape) {
                            withTransform({ rotate(degrees = -angle, pivot = center) }) {
                                drawRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(center.x - bandLength / 2, center.y - bandWidth / 2),
                                    size = Size(bandLength, bandWidth),
                                    blendMode = BlendMode.Clear,
                                )
                            }
                        }
                    }
                }
                .padding(horizontal = if (compact) 2.dp else 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (lesson.teachers.isNotBlank()) {
                Text(
                    text = changedText(lesson.origTeachers, lesson.teachers, options.showChanges),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = bodyColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .alpha(if (options.showTeacher) 1f else 0f)
                        .then(if (options.showTeacher) Modifier else Modifier.clearAndSetSemantics {}),
                )
            }

            Text(
                text = lesson.subject,
                fontSize = if (compact) 9.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (lesson.rooms.isNotBlank()) {
                Text(
                    text = changedText(lesson.origRooms, lesson.rooms, options.showChanges),
                    fontSize = if (compact) 8.sp else 9.sp,
                    lineHeight = 11.sp,
                    color = bodyColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
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

        AnimatedVisibility(
            visible = cancelled && strike && measured,
            enter = fadeIn(animationSpec = if (animations) AppMotion.effects() else snap()) +
                scaleIn(
                    initialScale = 0.6f,
                    animationSpec = if (animations) AppMotion.spatial() else snap(),
                ),
            exit = fadeOut(animationSpec = if (animations) AppMotion.effects() else snap()),
            modifier = Modifier.matchParentSize(),
        ) {
            val overhangPx = with(density) { 1.dp.toPx() }
            val diagonal = sqrt(cell.width.toFloat() * cell.width + cell.height.toFloat() * cell.height)

            StrikeRibbon(
                angle = angle,
                length = with(density) { (diagonal + overhangPx * 2).toDp() },
                color = options.strikeColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StrikeRibbon(
    angle: Float,
    length: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .requiredWidth(length)
                .requiredHeight(RibbonWidth)
                .graphicsLayer { rotationZ = -angle }
                .background(color, RoundedCornerShape(3.dp)),
        )
    }
}
