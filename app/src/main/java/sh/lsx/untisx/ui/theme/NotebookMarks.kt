package sh.lsx.untisx.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R

private val NotebookMarker = FontFamily(Font(R.font.permanent_marker))

internal fun notebookMarkerInk(paper: Color): Color {
    val reds = listOf(Color(0xFFA51E2C), Color(0xFFFFD9D0))

    return reds.firstOrNull { ink ->
        (maxOf(paper.luminance(), ink.luminance()) + 0.05f) /
            (minOf(paper.luminance(), ink.luminance()) + 0.05f) >= 4.5f
    } ?: notebookInk(paper, reds.first())
}

@Composable
internal fun NotebookExamMark(
    text: String,
    paper: Color,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val rotation = remember(seed) { -5f + paperVariation(seed).badgeRotation * 0.35f }
    val ink = notebookMarkerInk(paper)

    Text(
        text = text,
        fontFamily = NotebookMarker,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        color = ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 120.dp)
            .graphicsLayer { rotationZ = rotation }
            .drawWithCache {
                onDrawBehind {
                    drawLine(
                        color = ink.copy(alpha = 0.82f),
                        start = Offset(2.dp.toPx(), size.height - 3.dp.toPx()),
                        end = Offset(size.width - 1.dp.toPx(), size.height - 4.dp.toPx()),
                        strokeWidth = 1.4.dp.toPx(),
                    )
                }
            }
            .padding(bottom = 5.dp),
    )
}

internal fun Modifier.notebookHighlighter(seed: Int, dark: Boolean): Modifier = drawWithCache {
    val swipes = highlighterSwipes(size, density, seed)
    val wash = Path()
    val pigment = Path()

    swipes.forEachIndexed { index, swipe ->
        wash.moveTo(swipe.start.x, swipe.start.y)
        wash.lineTo(swipe.end.x, swipe.end.y)

        if (index % 3 == 1) {
            pigment.moveTo(swipe.start.x, swipe.start.y)
            pigment.lineTo(swipe.end.x, swipe.end.y)
        }
    }

    val thickness = swipes.firstOrNull()?.thickness ?: 0f
    val color = if (dark) Color(0xFFE5D36A) else Color(0xFFDEBF45)

    onDrawBehind {
        if (thickness > 0f) {
            clipRect {
                drawPath(wash, color.copy(alpha = 0.025f), style = Stroke(thickness + 1.dp.toPx(), cap = StrokeCap.Round))
                drawPath(wash, color.copy(alpha = if (dark) 0.21f else 0.19f), style = Stroke(thickness, cap = StrokeCap.Round))
                drawPath(pigment, color.copy(alpha = 0.025f), style = Stroke(thickness, cap = StrokeCap.Round))
            }
        }
    }
}
