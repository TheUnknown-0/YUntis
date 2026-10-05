package sh.lsx.untisx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal fun colorToHsv(color: Color): Hsv {
    val r = color.red
    val g = color.green
    val b = color.blue

    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }

    return Hsv(
        hue = if (hue < 0f) hue + 360f else hue,
        saturation = if (max == 0f) 0f else delta / max,
        value = max,
    )
}

internal fun hsvToColor(hsv: Hsv): Color {
    val hue = ((hsv.hue % 360f) + 360f) % 360f
    val saturation = hsv.saturation.coerceIn(0f, 1f)
    val value = hsv.value.coerceIn(0f, 1f)

    val chroma = value * saturation
    val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - chroma

    val (r, g, b) = when {
        hue < 60f -> Triple(chroma, x, 0f)
        hue < 120f -> Triple(x, chroma, 0f)
        hue < 180f -> Triple(0f, chroma, x)
        hue < 240f -> Triple(0f, x, chroma)
        hue < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }

    return Color(r + m, g + m, b + m)
}

internal fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val first = relativeLuminance(a)
    val second = relativeLuminance(b)
    val lighter = max(first, second)
    val darker = min(first, second)

    return (lighter + 0.05f) / (darker + 0.05f)
}

private val HUE_GRADIENT = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

@Composable
internal fun HsvColorPicker(
    hsv: Hsv,
    onHsvChange: (Hsv) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val latest by rememberUpdatedState(hsv)
    val hueColor = hsvToColor(Hsv(hue = hsv.hue, saturation = 1f, value = 1f))

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                fun apply(position: Offset) {
                                    onHsvChange(
                                        latest.copy(
                                            saturation = (position.x / size.width).coerceIn(0f, 1f),
                                            value = 1f - (position.y / size.height).coerceIn(0f, 1f),
                                        ),
                                    )
                                }

                                val down = awaitFirstDown()
                                apply(down.position)
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) break

                                    apply(change.position)
                                    change.consume()
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(
                    x = hsv.saturation * size.width,
                    y = (1f - hsv.value) * size.height,
                )

                drawCircle(Color.White, radius = 10.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                drawCircle(hsvToColor(hsv), radius = 7.dp.toPx(), center = center)
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(HUE_GRADIENT))
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                fun apply(position: Offset) {
                                    onHsvChange(
                                        latest.copy(hue = (position.x / size.width).coerceIn(0f, 1f) * 360f),
                                    )
                                }

                                val down = awaitFirstDown()
                                apply(down.position)
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) break

                                    apply(change.position)
                                    change.consume()
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(
                    x = (hsv.hue / 360f) * size.width,
                    y = size.height / 2f,
                )

                drawCircle(Color.White, radius = 12.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                drawCircle(hsvToColor(Hsv(hsv.hue, 1f, 1f)), radius = 9.dp.toPx(), center = center)
            }
        }
    }
}
