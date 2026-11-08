package sh.lsx.untisx.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.random.Random
import sh.lsx.untisx.R

internal val NotebookHandwriting = FontFamily(
    Font(R.font.kalam_regular),
    Font(R.font.kalam_bold, FontWeight.Bold),
)

internal val NotebookLessonTextStyle = TextStyle(
    fontFamily = NotebookHandwriting,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Proportional,
        trim = LineHeightStyle.Trim.Both,
        mode = LineHeightStyle.Mode.Tight,
    ),
)

internal fun notebookLessonTypeScale(cellHeightDp: Float, fontScale: Float): Float =
    ((cellHeightDp - 10f) / (40f * fontScale)).coerceIn(0.5f, 1f)

internal val NotebookShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
internal fun Modifier.notebookDayHighlight(): Modifier =
    background(MaterialTheme.colorScheme.primary.copy(alpha = 0.17f), RoundedCornerShape(2.dp))
        .padding(horizontal = 4.dp)

internal fun notebookTypography(): Typography {
    fun TextStyle.handwritten() = copy(fontFamily = NotebookHandwriting, letterSpacing = 0.sp)

    val base = Typography()

    return base.copy(
        displayLarge = base.displayLarge.handwritten(),
        displayMedium = base.displayMedium.handwritten(),
        displaySmall = base.displaySmall.handwritten(),
        headlineLarge = base.headlineLarge.handwritten(),
        headlineMedium = base.headlineMedium.handwritten(),
        headlineSmall = base.headlineSmall.handwritten(),
        titleLarge = base.titleLarge.handwritten().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.handwritten().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.handwritten().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.handwritten(),
        bodyMedium = base.bodyMedium.handwritten(),
        bodySmall = base.bodySmall.handwritten(),
        labelLarge = base.labelLarge.handwritten().copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.handwritten(),
        labelSmall = base.labelSmall.handwritten(),
    )
}

internal fun ColorScheme.onNotebookPaper(dark: Boolean): ColorScheme = if (dark) copy(
    background = Color(0xFF25241F),
    surface = Color(0xFF25241F),
    surfaceDim = Color(0xFF201F1B),
    surfaceBright = Color(0xFF48453B),
    surfaceContainerLowest = Color(0xFF1B1A17),
    surfaceContainerLow = Color(0xFF2C2A24),
    surfaceContainer = Color(0xFF333128),
    surfaceContainerHigh = Color(0xFF3A372E),
    surfaceContainerHighest = Color(0xFF444035),
    surfaceVariant = Color(0xFF444035),
    onBackground = Color(0xFFF2E9D5),
    onSurface = Color(0xFFF2E9D5),
    onSurfaceVariant = Color(0xFFD0C7B5),
    outline = Color(0xFFA59B85),
    outlineVariant = Color(0xFF6D6553),
) else copy(
    background = Color(0xFFF5EFDF),
    surface = Color(0xFFF5EFDF),
    surfaceDim = Color(0xFFE6DDC7),
    surfaceBright = Color(0xFFFFFBF1),
    surfaceContainerLowest = Color(0xFFFFFDF6),
    surfaceContainerLow = Color(0xFFFAF5E8),
    surfaceContainer = Color(0xFFFFFAED),
    surfaceContainerHigh = Color(0xFFFFFBF1),
    surfaceContainerHighest = Color(0xFFECE3CD),
    surfaceVariant = Color(0xFFECE3CD),
    onBackground = Color(0xFF302E29),
    onSurface = Color(0xFF302E29),
    onSurfaceVariant = Color(0xFF625B4D),
    outline = Color(0xFF827766),
    outlineVariant = Color(0xFFC6BAA0),
)

internal fun Modifier.paperGrain(dark: Boolean): Modifier = drawWithCache {
    val random = Random(73)
    val fibers = Path()
    val count = ((size.width * size.height) / (density * density * 75f)).toInt().coerceAtMost(12_000)

    repeat(count) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height

        fibers.moveTo(x, y)
        fibers.lineTo(x + (0.3f + random.nextFloat()) * density, y + 0.25f * density)
    }

    onDrawWithContent {
        drawContent()

        drawPath(
            fibers,
            if (dark) Color(0xFFF7E7C2).copy(alpha = 0.055f) else Color(0xFF70552D).copy(alpha = 0.065f),
            style = Stroke(width = 0.45.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
internal fun Modifier.notebookPaper(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val dark = LocalThemeAppearance.current.dark

    return drawWithCache {
        val ruling = Path()
        val spacing = 24.dp.toPx()
        var y = spacing

        while (y < size.height) {
            ruling.moveTo(0f, y)
            ruling.lineTo(size.width, y)
            y += spacing
        }

        onDrawBehind {
            drawRect(background)

            drawPath(
                path = ruling,
                color = Color(0xFF6A95B0).copy(alpha = if (dark) 0.16f else 0.20f),
                style = Stroke(0.6.dp.toPx()),
            )

            drawLine(
                color = Color(0xFFCA7770).copy(alpha = if (dark) 0.22f else 0.30f),
                start = Offset(49.dp.toPx(), 0f),
                end = Offset(49.dp.toPx(), size.height),
                strokeWidth = 0.7.dp.toPx(),
            )
        }
    }
}

internal fun notebookNoteColor(subject: Color, dark: Boolean): Color {
    val stock = if (dark) Color(0xFFEEE7DA) else Color(0xFFFFF5DF)
    val pastel = lerp(subject.copy(alpha = 1f), stock, if (dark) 0.60f else 0.68f)

    return if (pastel.luminance() < 0.42f) lerp(pastel, stock, 0.60f) else pastel
}

internal fun notebookInk(paper: Color, preferred: Color): Color {
    fun contrast(ink: Color): Float {
        val a = paper.luminance()
        val b = ink.luminance()

        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }

    if (preferred.alpha == 1f && contrast(preferred) >= 4.5f) return preferred

    val darkInk = Color(0xFF25231F)
    val lightInk = Color(0xFFFFF8E9)

    if (contrast(darkInk) >= 4.5f) return darkInk
    if (contrast(lightInk) >= 4.5f) return lightInk

    return if (contrast(Color.Black) >= contrast(Color.White)) Color.Black else Color.White
}

internal fun Modifier.stickyNoteBackground(
    paper: Color,
    examColor: Color?,
    seed: Int = 0,
): Modifier = drawWithCache {
    val bottom = (size.height - 2.dp.toPx()).coerceAtLeast(0f)
    val right = (size.width - 1.dp.toPx()).coerceAtLeast(0f)
    val fold = paperFold(Size(right, bottom), 9.dp.toPx(), seed)
    val sheet = paperSheetPath(right, bottom, fold)
    val corner = paperFlapPath(right, bottom, fold)
    val shading = Brush.linearGradient(
        listOf(lerp(paper, Color.White, 0.07f), paper, lerp(paper, Color.Black, 0.06f)),
        start = Offset.Zero,
        end = Offset(size.width * 0.3f, size.height),
    )
    val shadowX = 0.25.dp.toPx()
    val shadowY = 0.6.dp.toPx()

    onDrawBehind {
        for (step in 3 downTo 1) {
            withTransform({ translate(shadowX * step, shadowY * step) }) {
                drawPath(sheet, Color.Black.copy(alpha = 0.035f))
            }
        }

        drawPath(sheet, shading)
        drawRect(Color.Black.copy(alpha = 0.035f), size = Size(right, min(7.dp.toPx(), bottom)))
        drawPath(corner, lerp(paper, Color.White, 0.22f))
        drawLine(
            color = lerp(paper, Color.Black, 0.16f),
            start = fold.creaseStart(right, bottom),
            end = fold.creaseEnd(right, bottom),
            strokeWidth = 0.45.dp.toPx(),
        )
        drawLine(
            color = lerp(paper, Color.Black, 0.18f),
            start = Offset(0f, 0.5.dp.toPx()),
            end = Offset(right, 0.5.dp.toPx()),
            strokeWidth = 0.5.dp.toPx(),
        )

        if (examColor != null) drawPath(sheet, examColor, style = Stroke(1.5.dp.toPx()))
    }
}

internal fun Modifier.tornNotebookPaper(seed: Int): Modifier = drawWithCache {
    val points = paperTearPoints(size, density, seed)
    val sheet = Path().apply {
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        points.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
    val edge = Path().apply {
        points.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }

    onDrawWithContent {
        clipPath(sheet) {
            this@onDrawWithContent.drawContent()
            drawPath(edge, Color(0xFFDFD8C6).copy(alpha = 0.65f), style = Stroke(0.7.dp.toPx()))
        }
    }
}
