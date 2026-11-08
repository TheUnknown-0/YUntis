package sh.lsx.untisx.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.ThemeStyle

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val seed = currentCompositeKeyHashCode.hashCode()

    Card(
        modifier = modifier.appCardEdge(seed),
        shape = appCardShape(shape, seed),
        colors = colors,
        elevation = elevation,
        border = appCardBorder(border),
        content = content,
    )
}

@Composable
fun AppCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val seed = currentCompositeKeyHashCode.hashCode()

    Card(
        onClick = onClick,
        modifier = modifier.appCardEdge(seed),
        enabled = enabled,
        shape = appCardShape(shape, seed),
        colors = colors,
        elevation = elevation,
        border = appCardBorder(border),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
internal fun appCardShape(default: Shape, seed: Int): Shape = when (LocalThemeAppearance.current.style) {
    ThemeStyle.DEFAULT -> default
    ThemeStyle.NOTEBOOK -> remember(seed) { FoldedPaperShape(seed) }
}

@Composable
private fun appCardBorder(border: BorderStroke?): BorderStroke? =
    border ?: when (LocalThemeAppearance.current.style) {
        ThemeStyle.DEFAULT -> null
        ThemeStyle.NOTEBOOK -> {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

@Composable
internal fun Modifier.appCardEdge(seed: Int): Modifier = when (LocalThemeAppearance.current.style) {
    ThemeStyle.DEFAULT -> this
    ThemeStyle.NOTEBOOK -> paperEdge(seed)
}

@Composable
private fun Modifier.paperEdge(seed: Int): Modifier {
    val edge = MaterialTheme.colorScheme.outlineVariant
    val dark = LocalThemeAppearance.current.dark

    return drawWithCache {
        val fold = paperFold(size, 12.dp.toPx(), seed)
        val corner = paperFlapPath(size.width, size.height, fold)

        onDrawWithContent {
            drawContent()
            drawLine(
                color = edge.copy(alpha = 0.55f),
                start = Offset(2.dp.toPx(), 0.5.dp.toPx()),
                end = Offset(size.width - 2.dp.toPx(), 0.5.dp.toPx()),
                strokeWidth = 0.6.dp.toPx(),
            )
            drawPath(corner, if (dark) Color(0xFF8D8267) else Color(0xFFDFD1AE))
            drawLine(
                color = edge.copy(alpha = 0.5f),
                start = fold.creaseStart(size.width, size.height),
                end = fold.creaseEnd(size.width, size.height),
                strokeWidth = 0.5.dp.toPx(),
            )
        }
    }
}
