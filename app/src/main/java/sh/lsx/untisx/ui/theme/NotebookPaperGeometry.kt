package sh.lsx.untisx.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

internal fun notebookPaperSeed(id: Int, date: Int, startTime: Int): Int =
    (id * 31 + date) * 31 + startTime

internal data class PaperVariation(
    val foldScale: Float,
    val foldAspect: Float,
    val badgeRotation: Float,
    val tearLeft: Float,
    val tearRight: Float,
)

internal fun paperVariation(seed: Int): PaperVariation {
    val random = Random(seed)
    val foldScale = 0.85f + random.nextFloat() * 0.30f
    val foldAspect = 0.86f + random.nextFloat() * 0.28f
    val badgeRotation = (2f + random.nextFloat() * 3f) * if (random.nextBoolean()) 1f else -1f
    val low = 0.34f + random.nextFloat() * 0.07f
    val high = 0.64f + random.nextFloat() * 0.07f
    val reverse = random.nextBoolean()

    return PaperVariation(
        foldScale = foldScale,
        foldAspect = foldAspect,
        badgeRotation = badgeRotation,
        tearLeft = if (reverse) low else high,
        tearRight = if (reverse) high else low,
    )
}

internal data class PaperFold(val width: Float, val height: Float) {
    fun creaseStart(right: Float, bottom: Float) = Offset(right - width, bottom)
    fun creaseEnd(right: Float, bottom: Float) = Offset(right, bottom - height)

    fun foldedTip(right: Float, bottom: Float): Offset {
        val divisor = width * width + height * height
        if (divisor == 0f) return Offset(right, bottom)

        val factor = 2f * width * height / divisor

        return Offset(right - factor * height, bottom - factor * width)
    }
}

internal fun paperFold(size: Size, base: Float, seed: Int): PaperFold {
    val variation = paperVariation(seed)
    val limit = min(size.width, size.height).coerceAtLeast(0f) * 0.20f
    val width = min(base * variation.foldScale, limit)

    return PaperFold(width, min(width * variation.foldAspect, limit))
}

internal fun paperSheetPath(right: Float, bottom: Float, fold: PaperFold): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(right, 0f)
    lineTo(right, bottom - fold.height)
    lineTo(right - fold.width, bottom)
    lineTo(0f, bottom)
    close()
}

internal fun paperFlapPath(right: Float, bottom: Float, fold: PaperFold): Path = Path().apply {
    val start = fold.creaseStart(right, bottom)
    val end = fold.creaseEnd(right, bottom)
    val tip = fold.foldedTip(right, bottom)

    moveTo(start.x, start.y)
    lineTo(end.x, end.y)
    lineTo(tip.x, tip.y)
    close()
}

internal class FoldedPaperShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            paperSheetPath(size.width, size.height, paperFold(size, with(density) { 12.dp.toPx() }, seed)),
        )
}

internal fun paperTearPoints(size: Size, density: Float, seed: Int): List<Offset> {
    val variation = paperVariation(seed)
    val random = Random(seed xor 0x71EA2)
    val points = mutableListOf<Offset>()
    var x = 0f

    while (x < size.width) {
        val fraction = x / size.width
        val y = size.height * (variation.tearLeft + (variation.tearRight - variation.tearLeft) * fraction)

        points += Offset(x, (y + (random.nextFloat() - 0.5f) * 2.4f * density).coerceIn(0f, size.height))
        x += (2f + random.nextFloat() * 2.5f) * density
    }

    points += Offset(size.width, size.height * variation.tearRight)

    return points
}

internal data class HighlighterSwipe(val start: Offset, val end: Offset, val thickness: Float)

internal fun highlighterSwipes(size: Size, density: Float, seed: Int): List<HighlighterSwipe> {
    if (size.width <= 0f || size.height <= 0f || density <= 0f) return emptyList()

    val random = Random(seed xor 0x41616)
    val thickness = min(26f * density, min(size.width * 0.55f, size.height))
    val radius = thickness / 2f
    val inset = min(1.2f * density, size.width * 0.025f)
    val travel = size.height - thickness
    val count = if (travel <= 0f) 1 else ceil(travel / (thickness * 0.80f)).toInt() + 1

    return List(count) { index ->
        val y = radius + if (count == 1) 0f else travel * index / (count - 1)
        val left = radius + inset + random.nextFloat() * inset
        val right = size.width - radius - inset - random.nextFloat() * inset
        val tilt = min((right - left) * (0.045f + random.nextFloat() * 0.025f), thickness * 0.085f)
        val slant = tilt * if (index % 2 == 0) 1f else -1f

        HighlighterSwipe(Offset(left, y + slant), Offset(right, y - slant), thickness)
    }
}
