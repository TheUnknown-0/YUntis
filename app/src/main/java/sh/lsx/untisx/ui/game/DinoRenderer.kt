package sh.lsx.untisx.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Texts painted into the scene itself. They come from string resources. */
internal class DinoLabels(
    val cancelled: String,
    val invented: String,
    val schoolOut: String,
)

/** Cheap, stable pseudo random numbers for scenery that must look the same every frame. */
internal fun dinoHash(i: Int): Int {
    var x = i * -0x61c88647
    x = x xor (x ushr 15)
    x *= -0x7a143595
    x = x xor (x ushr 13)
    return x and 0x7FFFFFFF
}

internal fun dinoHash01(i: Int): Float = (dinoHash(i) % 10_000) / 10_000f

/** Plain sRGB mix. Faster than the perceptual lerp and good enough for scenery. */
internal fun mix(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

private const val MIN_VISIBLE_UNITS = 950f
private const val TILE = 54f

private const val TEXT_LABEL = 0
private const val TEXT_TIME = 1
private const val TEXT_SIGN = 2
private const val TEXT_SIGN_SUB = 3
private const val TEXT_BANNER = 4
private const val TEXT_SCHOOL = 5
private const val TEXT_POPUP = 6
private const val TEXT_POPUP_BIG = 7
private val TEXT_SIZES = floatArrayOf(31f, 18f, 21f, 14f, 24f, 20f, 30f, 42f)
private val TEXT_WEIGHTS = arrayOf(
    FontWeight.ExtraBold, FontWeight.SemiBold, FontWeight.Black, FontWeight.SemiBold,
    FontWeight.Black, FontWeight.Bold, FontWeight.Black, FontWeight.Black,
)

/**
 * Text layouts are measured once in world units and reused every frame. The scene is
 * drawn under a scale transform, so the cache does not depend on the screen size.
 */
private class TextCache(private val measurer: TextMeasurer) {
    private val maps = Array(TEXT_SIZES.size) { HashMap<String, TextLayoutResult>() }
    private var density = 0f
    private var fontScale = 0f

    fun get(scope: Density, style: Int, text: String): TextLayoutResult {
        if (scope.density != density || scope.fontScale != fontScale) {
            density = scope.density
            fontScale = scope.fontScale
            for (map in maps) map.clear()
        }
        val map = maps[style]
        val cached = map[text]
        if (cached != null) return cached
        if (map.size > 160) map.clear()
        val result = measurer.measure(
            text = text,
            style = TextStyle(
                fontSize = (TEXT_SIZES[style] / (density * fontScale)).sp,
                fontWeight = TEXT_WEIGHTS[style],
            ),
            softWrap = false,
            maxLines = 1,
        )
        map[text] = result
        return result
    }
}

// sky keyframes: hour, top colour, bottom colour
private val SKY_HOURS = floatArrayOf(4f, 5.6f, 7.4f, 12f, 16f, 18.4f, 19.8f, 21.2f, 22f)
private val SKY_TOP = arrayOf(
    Color(0xFF0B1030), Color(0xFF2B3A78), Color(0xFF6FB1EA), Color(0xFF3B8EDD), Color(0xFF4A90D9),
    Color(0xFF4B4F9E), Color(0xFF1D2360), Color(0xFF0B1030), Color(0xFF0B1030),
)
private val SKY_BOTTOM = arrayOf(
    Color(0xFF1B2450), Color(0xFFF2A07B), Color(0xFFFFE0BD), Color(0xFFC6E8FB), Color(0xFFD9ECF7),
    Color(0xFFFF9E6B), Color(0xFF6A4C7D), Color(0xFF1B2450), Color(0xFF1B2450),
)

private val NIGHT_TINT = Color(0xFF10173A)
private val WARM_TINT = Color(0xFFFF9E6B)
private val INK = Color(0xFF1D2B3A)
private val LEAF_COLORS = arrayOf(Color(0xFFE07A2F), Color(0xFFC8553D), Color(0xFFE9B44C), Color(0xFF8AA35B))

/**
 * Draws the whole scene. One instance lives as long as the screen: it owns the reusable
 * paths, the cached brushes and the text cache, so a frame allocates next to nothing.
 */
internal class DinoRenderer(measurer: TextMeasurer, private val labels: DinoLabels) {

    /** App theme. The dark theme gets a slightly dimmed scene, the time of day stays. */
    var darkTheme = false

    /** No camera shake and calmer weather when the system animations are switched off. */
    var reducedMotion = false

    private val texts = TextCache(measurer)
    private val sprite = DinoSprite()

    private val hillPath = Path()
    private val shapePath = Path()
    private val starPath = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 1f else 0.46f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }

    private val earthBrush = Brush.verticalGradient(
        listOf(Color(0xFF8B6B4E), Color(0xFF4A3526)),
        startY = DinoWorld.GROUND_Y + TILE,
        endY = DinoWorld.GROUND_Y + TILE + 300f,
    )
    private val pitBrush = Brush.verticalGradient(
        listOf(Color(0xFF27323F), Color(0xFF070A10)),
        startY = DinoWorld.GROUND_Y,
        endY = DinoWorld.GROUND_Y + 230f,
    )
    private val grassBrush = Brush.verticalGradient(
        listOf(Color(0xFFA5D66B), Color(0xFF7DB648)),
        startY = DinoWorld.GROUND_Y,
        endY = DinoWorld.GROUND_Y + TILE,
    )

    private var skyBrush: Brush? = null
    private var skyKey = -1L
    private var skyTop = Color.Black
    private var skyBottom = Color.Black

    // per frame values, set in draw()
    private var cam = 0f
    private var widthUnits = 0f
    private var topUnits = 0f
    private var bottomUnits = 0f
    private var night = 0f
    private var warm = 0f
    private var time = 0f
    private var snow = 0f

    fun draw(scope: DrawScope, world: DinoWorld) = with(scope) {
        // keep at least MIN_VISIBLE_UNITS of track visible, so there is time to react on narrow
        // screens; spare height above the world becomes sky
        val unit = min(size.height / DinoWorld.WORLD_HEIGHT, size.width / MIN_VISIBLE_UNITS)
        if (unit <= 0f) return@with
        widthUnits = size.width / unit
        // on tall screens the track sits at about two thirds of the height, ground fills the rest
        val yOffset = (size.height - DinoWorld.WORLD_HEIGHT * unit) * 0.55f
        topUnits = -yOffset / unit
        bottomUnits = (size.height - yOffset) / unit
        world.onSize(widthUnits)

        cam = world.cameraX
        time = world.time
        val dinoX = world.dinoX
        val hour = world.skyHourAt(dinoX)
        night = nightness(hour)
        warm = max(bump(hour, 5.9f, 1.3f), bump(hour, 18.6f, 1.5f))
        val segment = world.segmentAt(dinoX)
        val weather = segment?.weather ?: Weather.CLEAR
        val amount = world.weatherAmountAt(dinoX)
        val wet = if (weather == Weather.RAIN) amount else 0f
        snow = if (weather == Weather.SNOW) amount else 0f
        val overcast = max(wet, snow)

        drawSky(hour, overcast)

        // a short, soft camera shake after a crash; never with reduced motion
        var shakeX = 0f
        var shakeY = 0f
        if (!reducedMotion && world.phase == DinoWorld.Phase.OVER && world.deathCause == DinoWorld.DeathCause.HIT) {
            val since = world.time - world.deathTime
            if (since < 0.4f) {
                val fade = 1f - since / 0.4f
                shakeX = sin(since * 70f) * 9f * fade
                shakeY = cos(since * 55f) * 5f * fade
            }
        }

        withTransform({
            translate(shakeX * unit, yOffset + shakeY * unit)
            scale(unit, unit, Offset.Zero)
        }) {
            drawStars()
            drawSunAndMoon(hour, overcast)
            drawClouds(0.06f, 430f, 0, 1f, overcast)
            if (overcast > 0.02f) drawClouds(0.11f, 300f, 977, overcast, 1f)
            drawSkyline()
            drawHills(0.2f, 585f, 70f, 0.0021f, mix(Color(0xFFA9D39A), skyBottom, 0.42f))
            drawHills(0.4f, 628f, 46f, 0.0034f, Color(0xFF7FB877))
            drawTrees()
            drawStreet(world)
            drawGround(world)
            drawPickups(world)
            drawObstacles(world)
            drawParticles(world)
            sprite.draw(this, world, world.dinoX - cam, DinoWorld.GROUND_Y)
            // the night and the dark theme dim the playfield a little, texts stay readable
            val dim = night * 0.2f + (if (darkTheme) 0.08f else 0f)
            if (dim > 0.01f) {
                drawRect(NIGHT_TINT, Offset(-20f, topUnits - 20f), Size(widthUnits + 40f, bottomUnits - topUnits + 40f), alpha = dim)
            }
            drawLampGlow()
            drawPopups(world)
            drawWeather(weather, amount)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Light
    // ------------------------------------------------------------------------------------------

    private fun nightness(hour: Float): Float = when {
        hour <= 4.9f || hour >= 20.9f -> 1f
        hour < 6.6f -> 1f - ease((hour - 4.9f) / 1.7f)
        hour > 18.6f -> ease((hour - 18.6f) / 2.3f)
        else -> 0f
    }

    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun bump(x: Float, center: Float, width: Float): Float = ease(1f - abs(x - center) / width)

    /** A scenery colour as it looks in the current light. */
    private fun lit(c: Color): Color {
        var out = c
        if (warm > 0.01f) out = mix(out, WARM_TINT, warm * 0.14f)
        val dark = night * 0.56f + (if (darkTheme) 0.1f else 0f)
        if (dark > 0.01f) out = mix(out, NIGHT_TINT, dark)
        if (snow > 0.01f) out = mix(out, Color(0xFFEFF4F8), snow * 0.35f)
        return out
    }

    private fun skyColor(colors: Array<Color>, hour: Float): Color {
        val h = hour.coerceIn(SKY_HOURS[0], SKY_HOURS[SKY_HOURS.size - 1])
        for (i in 0 until SKY_HOURS.size - 1) {
            if (h <= SKY_HOURS[i + 1]) {
                val t = (h - SKY_HOURS[i]) / (SKY_HOURS[i + 1] - SKY_HOURS[i])
                return mix(colors[i], colors[i + 1], t)
            }
        }
        return colors[colors.size - 1]
    }

    private fun DrawScope.drawSky(hour: Float, overcast: Float) {
        // the gradient is only rebuilt when the light really changed
        val key = (hour * 60f).toLong() * 1_000_000L + (overcast * 50f).toLong() * 10_000L +
            size.height.toLong() + (if (darkTheme) 5_000_000_000L else 0L) + (if (snow > 0f) 900_000_000_000L else 0L)
        if (key != skyKey || skyBrush == null) {
            skyKey = key
            var top = skyColor(SKY_TOP, hour)
            var bottom = skyColor(SKY_BOTTOM, hour)
            if (overcast > 0f) {
                val greyTop = if (snow > 0f) Color(0xFFAEB9C6) else Color(0xFF7F8C9C)
                val greyBottom = if (snow > 0f) Color(0xFFE3E8EE) else Color(0xFFBAC4CF)
                val nightGrey = mix(greyTop, NIGHT_TINT, night * 0.8f)
                top = mix(top, nightGrey, overcast * 0.65f)
                bottom = mix(bottom, mix(greyBottom, NIGHT_TINT, night * 0.7f), overcast * 0.65f)
            }
            if (darkTheme) {
                top = mix(top, Color(0xFF0A1024), 0.22f)
                bottom = mix(bottom, Color(0xFF0A1024), 0.22f)
            }
            skyTop = top
            skyBottom = bottom
            skyBrush = Brush.verticalGradient(listOf(top, mix(top, bottom, 0.55f), bottom), startY = 0f, endY = size.height * 0.8f)
        }
        skyBrush?.let { drawRect(it) }
    }

    // ------------------------------------------------------------------------------------------
    // Small drawing helpers, all in world units
    // ------------------------------------------------------------------------------------------

    private fun DrawScope.box(c: Color, l: Float, t: Float, w: Float, h: Float, a: Float = 1f) {
        if (w <= 0f || h <= 0f) return
        drawRect(c, Offset(l, t), Size(w, h), alpha = a)
    }

    private fun DrawScope.rbox(c: Color, l: Float, t: Float, w: Float, h: Float, r: Float, a: Float = 1f) {
        if (w <= 0f || h <= 0f) return
        drawRoundRect(c, Offset(l, t), Size(w, h), CornerRadius(r, r), alpha = a)
    }

    private fun DrawScope.dot(c: Color, x: Float, y: Float, r: Float, a: Float = 1f) {
        drawCircle(c, r, Offset(x, y), alpha = a)
    }

    private fun DrawScope.blob(c: Color, cx: Float, cy: Float, rx: Float, ry: Float, a: Float = 1f) {
        drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), alpha = a)
    }

    private fun DrawScope.stroke(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, a: Float = 1f) {
        drawLine(c, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round, alpha = a)
    }

    // ------------------------------------------------------------------------------------------
    // Sky
    // ------------------------------------------------------------------------------------------

    private fun DrawScope.drawStars() {
        if (night < 0.05f) return
        val span = 540f - topUnits
        for (i in 0 until 46) {
            val x = dinoHash01(i * 3 + 1) * widthUnits
            val y = topUnits + dinoHash01(i * 3 + 2) * span
            // slow, gentle twinkle
            val twinkle = 0.72f + 0.28f * sin(time * (0.5f + dinoHash01(i) * 0.7f) + i)
            dot(Color.White, x, y, 1.4f + dinoHash01(i * 3 + 3) * 2f, night * twinkle * 0.9f)
        }
    }

    private fun DrawScope.drawSunAndMoon(hour: Float, overcast: Float) {
        val p = (hour - 6f) / 13.5f
        if (p > -0.05f && p < 1.05f) {
            val lift = sin(p.coerceIn(0f, 1f) * PI.toFloat())
            val x = widthUnits * (0.08f + 0.84f * p)
            val y = 610f - lift * 450f
            val core = mix(Color(0xFFFF9248), Color(0xFFFFDD55), lift)
            val a = 1f - overcast * 0.75f
            dot(core, x, y, 150f, 0.07f * a)
            dot(core, x, y, 100f, 0.12f * a)
            dot(core, x, y, 66f, 0.22f * a)
            dot(core, x, y, 46f, a)
            dot(Color.White, x - 10f, y - 12f, 22f, 0.22f * a)
        }
        if (night > 0.03f) {
            val x = widthUnits * 0.74f
            val y = 210f - night * 60f
            val a = night * (1f - overcast * 0.6f)
            dot(Color(0xFFDDE6FF), x, y, 96f, 0.07f * a)
            dot(Color(0xFFDDE6FF), x, y, 64f, 0.1f * a)
            dot(Color(0xFFF4F1DE), x, y, 40f, a)
            dot(Color(0xFFD9D4BC), x - 12f, y - 8f, 9f, a)
            dot(Color(0xFFD9D4BC), x + 14f, y + 6f, 6f, a)
            dot(Color(0xFFD9D4BC), x + 2f, y + 20f, 5f, a)
        }
    }

    private fun DrawScope.drawClouds(parallax: Float, spacing: Float, salt: Int, alpha: Float, grey: Float) {
        val drift = if (reducedMotion) 0f else time * (6f + parallax * 60f)
        val offset = cam * parallax + drift
        val first = floor(offset / spacing).toInt() - 1
        val count = (widthUnits / spacing).toInt() + 3
        val base = mix(mix(Color.White, Color(0xFF9AA5B1), grey * 0.75f), Color(0xFF2A3358), night * 0.72f)
        val body = mix(base, WARM_TINT, warm * 0.25f)
        val shade = mix(body, Color(0xFF5C6B82), 0.28f)
        for (i in first until first + count) {
            val h = dinoHash(i * 13 + salt)
            val x = i * spacing - offset + (h % 190)
            val y = 70f + ((h / 190) % 200) + topUnits * 0.25f
            val s = 0.7f + ((h / 7) % 60) / 100f
            val a = alpha * 0.93f
            // flat shaded underside first, then the lit puffs
            rbox(shade, x - 22f * s, y + 6f * s, 156f * s, 38f * s, 19f * s, a)
            dot(body, x, y, 36f * s, a)
            dot(body, x + 48f * s, y - 20f * s, 50f * s, a)
            dot(body, x + 104f * s, y - 4f * s, 40f * s, a)
            rbox(body, x - 22f * s, y - 2f * s, 156f * s, 36f * s, 18f * s, a)
            dot(Color.White, x + 38f * s, y - 36f * s, 22f * s, a * 0.25f * (1f - night))
        }
    }

    // ------------------------------------------------------------------------------------------
    // Far scenery
    // ------------------------------------------------------------------------------------------

    private fun DrawScope.drawSkyline() {
        val parallax = 0.1f
        val cell = 88f
        val base = 606f
        val offset = cam * parallax
        val first = floor(offset / cell).toInt() - 1
        val count = (widthUnits / cell).toInt() + 3
        val wall = lit(mix(skyBottom, Color(0xFF51648C), 0.5f))
        val wallDark = lit(mix(skyBottom, Color(0xFF3F4F73), 0.6f))
        val window = Color(0xFFFFE08A)
        for (i in first until first + count) {
            val h = dinoHash(i + 4242)
            val x = i * cell - offset
            val w = 54f + h % 30
            val height = 70f + (h / 31) % 170
            val top = base - height
            if (h % 11 == 0) {
                // a slim radio tower with a round platform
                val cx = x + 40f
                box(wallDark, cx - 5f, base - 330f, 10f, 330f)
                dot(wallDark, cx, base - 236f, 22f)
                box(wallDark, cx - 2f, base - 400f, 4f, 80f)
                if (night > 0.2f) dot(Color(0xFFFF6B6B), cx, base - 400f, 4f, night * (0.6f + 0.4f * sin(time * 1.5f)))
                continue
            }
            box(if (h % 2 == 0) wall else wallDark, x, top, w, height)
            when ((h / 5) % 4) {
                0 -> box(wallDark, x + 10f, top - 14f, 22f, 14f)
                1 -> box(wall, x + w * 0.25f, top - 22f, w * 0.5f, 22f)
                2 -> box(wallDark, x + w / 2f - 1.5f, top - 34f, 3f, 34f)
                else -> Unit
            }
            // windows glow at night, in daylight only a hint of them shows
            val rows = min(6, (height / 28f).toInt())
            for (row in 0 until rows) {
                for (col in 0 until 3) {
                    val on = dinoHash(i * 131 + row * 17 + col) % 3 == 0
                    if (!on) continue
                    val wx = x + 8f + col * (w - 16f) / 3f
                    val wy = top + 12f + row * 28f
                    if (night > 0.12f) box(window, wx, wy, 9f, 13f, night * 0.85f)
                    else box(wallDark, wx, wy, 9f, 13f, 0.5f)
                }
            }
        }
    }

    private fun hillY(wx: Float, base: Float, amplitude: Float, frequency: Float): Float =
        base - amplitude * (0.6f * sin(wx * frequency) + 0.4f * sin(wx * frequency * 2.3f + 1.7f))

    private fun DrawScope.drawHills(parallax: Float, base: Float, amplitude: Float, frequency: Float, color: Color) {
        val offset = cam * parallax
        val path = hillPath
        path.rewind()
        path.moveTo(-10f, bottomUnits + 10f)
        var x = -10f
        while (x <= widthUnits + 30f) {
            path.lineTo(x, hillY(x + offset, base, amplitude, frequency))
            x += 24f
        }
        path.lineTo(widthUnits + 30f, bottomUnits + 10f)
        path.close()
        drawPath(path, lit(color))
    }

    private fun DrawScope.drawTrees() {
        val parallax = 0.4f
        val cell = 150f
        val offset = cam * parallax
        val first = floor(offset / cell).toInt() - 1
        val count = (widthUnits / cell).toInt() + 3
        val trunk = lit(Color(0xFF7A5A3C))
        val leaf = lit(Color(0xFF4E9A5B))
        val leafLight = lit(Color(0xFF6DB86F))
        val pine = lit(Color(0xFF3C7D58))
        val pineDark = lit(Color(0xFF2F6648))
        for (i in first until first + count) {
            val h = dinoHash(i + 911)
            if (h % 5 == 0) continue
            val wx = i * cell + (h % 90)
            val x = wx - offset
            val y = hillY(wx, 628f, 46f, 0.0034f) + 8f
            val s = 0.75f + ((h / 90) % 55) / 100f
            val sway = if (reducedMotion) 0f else sin(time * 1.1f + i) * 2.5f * s
            if (h % 3 == 0) {
                // conifer: three stacked tiers
                box(trunk, x - 4f * s, y - 26f * s, 8f * s, 26f * s)
                for (tier in 0 until 3) {
                    val ty = y - 22f * s - tier * 26f * s
                    val half = (34f - tier * 8f) * s
                    val p = shapePath
                    p.rewind()
                    p.moveTo(x - half, ty)
                    p.lineTo(x + sway * (tier + 1) / 3f, ty - 44f * s)
                    p.lineTo(x + half, ty)
                    p.close()
                    drawPath(p, if (tier % 2 == 0) pine else pineDark)
                }
            } else {
                box(trunk, x - 5f * s, y - 44f * s, 10f * s, 44f * s)
                dot(leaf, x - 20f * s + sway * 0.5f, y - 60f * s, 26f * s)
                dot(leaf, x + 20f * s + sway * 0.5f, y - 62f * s, 28f * s)
                dot(leaf, x + sway, y - 84f * s, 32f * s)
                dot(leafLight, x - 8f * s + sway, y - 92f * s, 15f * s)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The street right behind the track: hedges, lamps and the school
    // ------------------------------------------------------------------------------------------

    private val streetParallax = 0.62f
    private val lampCell = 640f

    private fun DrawScope.drawStreet(world: DinoWorld) {
        val offset = cam * streetParallax
        val ground = DinoWorld.GROUND_Y + 4f

        // hedge
        val cell = 230f
        val first = floor(offset / cell).toInt() - 1
        val count = (widthUnits / cell).toInt() + 3
        val hedge = lit(Color(0xFF4C8C4A))
        val hedgeLight = lit(Color(0xFF63A85C))
        for (i in first until first + count) {
            val h = dinoHash(i + 77)
            if (h % 4 == 0) continue
            val x = i * cell - offset + (h % 80)
            val s = 0.8f + ((h / 80) % 40) / 100f
            dot(hedge, x, ground - 16f * s, 26f * s)
            dot(hedge, x + 34f * s, ground - 24f * s, 32f * s)
            dot(hedge, x + 70f * s, ground - 14f * s, 24f * s)
            dot(hedgeLight, x + 28f * s, ground - 36f * s, 12f * s)
        }

        // the school waits at the end of every way to school
        val minute = world.minuteAt(world.dinoX)
        for (index in world.segments.indices) {
            val segment = world.segments[index]
            if (segment.kind != SegmentKind.SCHOOL_START) continue
            val x = (segment.end - 700f - cam) * streetParallax + widthUnits * 0.2f
            if (x > widthUnits + 60f || x < -640f) continue
            drawSchool(x, segment.dayTitle, minute)
        }

        // street lamps
        val firstLamp = floor(offset / lampCell).toInt() - 1
        val lamps = (widthUnits / lampCell).toInt() + 3
        val post = lit(Color(0xFF4A5568))
        for (i in firstLamp until firstLamp + lamps) {
            val x = i * lampCell - offset + 120f
            box(post, x - 3.5f, ground - 236f, 7f, 236f)
            box(post, x - 7f, ground - 22f, 14f, 22f)
            stroke(post, x, ground - 236f, x + 34f, ground - 248f, 6f)
            rbox(post, x + 22f, ground - 254f, 36f, 12f, 6f)
            val lamp = mix(Color(0xFFDCE3EA), Color(0xFFFFE9A8), night)
            rbox(lamp, x + 26f, ground - 246f, 28f, 7f, 3.5f)
        }
    }

    /** Drawn after the night tint, so the light really glows. */
    private fun DrawScope.drawLampGlow() {
        if (night < 0.1f) return
        val offset = cam * streetParallax
        val ground = DinoWorld.GROUND_Y + 4f
        val first = floor(offset / lampCell).toInt() - 1
        val count = (widthUnits / lampCell).toInt() + 3
        val glow = Color(0xFFFFE9A8)
        for (i in first until first + count) {
            val x = i * lampCell - offset + 160f
            val y = ground - 240f
            dot(glow, x, y, 120f, 0.05f * night)
            dot(glow, x, y, 74f, 0.09f * night)
            dot(glow, x, y, 36f, 0.2f * night)
            dot(Color.White, x, y, 9f, 0.75f * night)
        }
    }

    private fun DrawScope.drawSchool(x: Float, title: String, minute: Int) {
        val base = DinoWorld.GROUND_Y + 4f
        val w = 560f
        val h = 250f
        val top = base - h
        val cx = x + w / 2f

        val brick = lit(Color(0xFFE9D2B0))
        val brickShade = lit(Color(0xFFD4B892))
        val roof = lit(Color(0xFFB8503B))
        val roofDark = lit(Color(0xFF964031))
        val stone = lit(Color(0xFFBFB5A6))
        val frame = lit(Color(0xFFFDF6E8))
        val wood = lit(Color(0xFF7A4B2A))
        val glassDay = lit(Color(0xFF9CC9E8))
        val glassNight = Color(0xFF1B2440)
        val glassLit = Color(0xFFFFE08A)

        // wings
        box(brick, x, top, w, h)
        box(brickShade, x, top + h - 34f, w, 34f)
        // faint rows of bricks
        var row = top + 44f
        while (row < base - 40f) {
            box(brickShade, x, row, w, 2f, 0.45f)
            row += 22f
        }
        box(stone, x - 8f, base - 12f, w + 16f, 12f)
        // roof with an overhang and tile lines
        box(roof, x - 12f, top - 8f, w + 24f, 30f)
        box(roofDark, x - 12f, top + 16f, w + 24f, 6f)
        var tile = x - 6f
        while (tile < x + w + 6f) {
            box(roofDark, tile, top - 8f, 2f, 24f, 0.5f)
            tile += 28f
        }

        // tower with a gable
        box(brick, cx - 76f, top - 96f, 152f, 110f)
        box(brickShade, cx - 76f, top - 96f, 10f, 110f, 0.6f)
        val gable = shapePath
        gable.rewind()
        gable.moveTo(cx - 96f, top - 92f)
        gable.lineTo(cx, top - 164f)
        gable.lineTo(cx + 96f, top - 92f)
        gable.close()
        drawPath(gable, roof)
        stroke(roofDark, cx - 96f, top - 92f, cx + 96f, top - 92f, 5f)

        // the clock shows the time in the game
        val clockY = top - 44f
        dot(stone, cx, clockY, 32f)
        dot(Color(0xFFFDFBF4), cx, clockY, 27f)
        for (tick in 0 until 12) {
            val a = tick * (PI.toFloat() / 6f)
            dot(INK, cx + sin(a) * 22f, clockY - cos(a) * 22f, if (tick % 3 == 0) 2.2f else 1.2f)
        }
        val hourAngle = ((minute % 720) / 720f) * 2f * PI.toFloat()
        val minuteAngle = ((minute % 60) / 60f) * 2f * PI.toFloat()
        stroke(INK, cx, clockY, cx + sin(hourAngle) * 13f, clockY - cos(hourAngle) * 13f, 4f)
        stroke(INK, cx, clockY, cx + sin(minuteAngle) * 20f, clockY - cos(minuteAngle) * 20f, 2.6f)
        dot(Color(0xFFC0392B), cx, clockY, 3f)

        // flag, gently waving
        box(lit(Color(0xFF5D6D7E)), cx - 2f, top - 232f, 4f, 70f)
        val flag = shapePath
        flag.rewind()
        val wave = if (reducedMotion) 0f else 1f
        flag.moveTo(cx + 2f, top - 230f)
        for (k in 1..4) flag.lineTo(cx + 2f + k * 13f, top - 230f + sin(time * 4f + k * 0.9f) * 3f * wave * k / 4f)
        for (k in 4 downTo 1) flag.lineTo(cx + 2f + k * 13f, top - 202f + sin(time * 4f + k * 0.9f) * 3f * wave * k / 4f)
        flag.lineTo(cx + 2f, top - 202f)
        flag.close()
        drawPath(flag, lit(Color(0xFFF39C12)))

        // two floors of windows, a few stay lit in the evening
        for (floorIndex in 0 until 2) {
            for (col in 0 until 6) {
                if (col == 2 || col == 3) continue
                val wx = x + 34f + col * 86f
                val wy = top + 50f + floorIndex * 92f
                val on = dinoHash(col * 7 + floorIndex * 3 + title.length) % 3 != 0
                val glass = if (on) mix(glassDay, glassLit, night) else mix(glassDay, glassNight, night * 0.85f)
                rbox(frame, wx - 5f, wy - 5f, 62f, 68f, 5f)
                box(glass, wx, wy, 52f, 58f)
                // reflection
                if (night < 0.5f) {
                    val p = shapePath
                    p.rewind()
                    p.moveTo(wx + 6f, wy + 58f)
                    p.lineTo(wx + 30f, wy)
                    p.lineTo(wx + 42f, wy)
                    p.lineTo(wx + 18f, wy + 58f)
                    p.close()
                    drawPath(p, Color.White, alpha = 0.22f * (1f - night * 2f))
                }
                box(frame, wx + 24f, wy, 4f, 58f)
                box(frame, wx, wy + 26f, 52f, 4f)
                box(stone, wx - 8f, wy + 61f, 68f, 6f)
            }
        }

        // entrance: canopy, double door, steps
        box(stone, cx - 70f, base - 30f, 140f, 10f)
        box(stone, cx - 84f, base - 20f, 168f, 10f)
        rbox(wood, cx - 48f, base - 140f, 96f, 112f, 8f)
        box(lit(Color(0xFF5E3A20)), cx - 2f, base - 136f, 4f, 106f)
        rbox(mix(glassDay, glassLit, night), cx - 38f, base - 128f, 30f, 40f, 4f)
        rbox(mix(glassDay, glassLit, night), cx + 8f, base - 128f, 30f, 40f, 4f)
        dot(Color(0xFFF1C40F), cx - 9f, base - 78f, 3f)
        dot(Color(0xFFF1C40F), cx + 9f, base - 78f, 3f)
        rbox(roofDark, cx - 66f, base - 158f, 132f, 16f, 5f)

        // name sign with the date
        if (title.isNotEmpty()) {
            val text = texts.get(this, TEXT_SCHOOL, title)
            val tw = text.size.width.toFloat()
            rbox(wood, cx - tw / 2f - 18f, top + 2f, tw + 36f, 38f, 8f)
            rbox(frame, cx - tw / 2f - 14f, top + 6f, tw + 28f, 30f, 6f)
            drawText(text, color = Color(0xFF7A4B2A), topLeft = Offset(cx - tw / 2f, top + 21f - text.size.height / 2f))
        }

        // bicycle stand next to the entrance
        val rack = lit(Color(0xFF6B7785))
        for (k in 0 until 4) {
            val rx = x + w + 30f + k * 26f
            drawArc(rack, 180f, 180f, false, Offset(rx, base - 44f), Size(18f, 30f), style = Stroke(4f))
            stroke(rack, rx, base - 29f, rx, base, 4f)
            stroke(rack, rx + 18f, base - 29f, rx + 18f, base, 4f)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Ground
    // ------------------------------------------------------------------------------------------

    private fun DrawScope.solid(from: Float, to: Float, tint: Color) {
        val l = max(from - cam, -60f)
        val r = min(to - cam, widthUnits + 60f)
        if (r <= l) return
        val earthTop = DinoWorld.GROUND_Y + TILE
        drawRect(earthBrush, Offset(l, earthTop), Size(r - l, bottomUnits - earthTop + 10f))
        box(tint, l, earthTop, r - l, 34f, 0.22f)
        box(Color.Black, l, earthTop, r - l, 7f, 0.18f)
        // layers and pebbles in the soil
        box(Color.Black, l, earthTop + 92f, r - l, 3f, 0.1f)
        box(Color.White, l, earthTop + 150f, r - l, 2f, 0.06f)
        val cell = 86f
        var i = floor((l + cam) / cell).toInt()
        val last = floor((r + cam) / cell).toInt()
        while (i <= last) {
            val h = dinoHash(i + 300)
            val px = i * cell + (h % 70) - cam
            if (px > l + 8f && px < r - 8f) {
                val py = earthTop + 24f + (h / 70) % 110
                blob(Color(0xFFB79B7C), px, py, 5f + (h % 5), 3.5f + (h % 3), 0.55f)
                blob(Color(0xFF3B2A1E), px + 26f, py + 44f, 4f, 3f, 0.4f)
            }
            i++
        }
    }

    private fun DrawScope.face(c: Color, from: Float, to: Float) {
        val l = max(from - cam, -60f)
        val r = min(to - cam, widthUnits + 60f)
        if (r <= l) return
        val top = DinoWorld.GROUND_Y
        box(c, l, top, r - l, TILE)
        box(Color.White, l, top, r - l, 5f, 0.3f)
        box(Color.Black, l, top + TILE - 7f, r - l, 7f, 0.14f)
        if (snow > 0.02f) rbox(Color.White, l, top - 3f, r - l, 9f, 4f, snow * 0.9f)
    }

    private fun DrawScope.drawGround(world: DinoWorld) {
        val top = DinoWorld.GROUND_Y
        for (index in world.segments.indices) {
            val segment = world.segments[index]
            val left = segment.x - cam
            val right = segment.end - cam
            if (right < -60f || left > widthUnits + 60f) continue

            when (segment.kind) {
                SegmentKind.SCHOOL_START, SegmentKind.SCHOOL_END -> {
                    solid(segment.x, segment.end, Color(0xFF8D8478))
                    face(Color(0xFFBDB7AE), segment.x, segment.end)
                    drawPaving(segment)
                    if (segment.kind == SegmentKind.SCHOOL_END) drawFinishBanner(left + 60f)
                }

                SegmentKind.BREAK -> {
                    solid(segment.x, segment.end, Color(0xFF6B8E3A))
                    val l = max(left, -60f)
                    val r = min(right, widthUnits + 60f)
                    drawRect(grassBrush, Offset(l, top), Size(r - l, TILE))
                    box(Color.White, l, top, r - l, 4f, 0.22f)
                    box(Color.Black, l, top + TILE - 7f, r - l, 7f, 0.14f)
                    if (snow > 0.02f) rbox(Color.White, l, top - 3f, r - l, 9f, 4f, snow * 0.9f)
                    drawMeadow(segment)
                }

                SegmentKind.LESSON -> drawLesson(segment)
            }
        }
    }

    private fun DrawScope.drawPaving(segment: Segment) {
        val top = DinoWorld.GROUND_Y
        val joint = Color(0xFF6E675E)
        val from = max(segment.x, cam - 60f)
        val to = min(segment.end, cam + widthUnits + 60f)
        var i = floor(from / 62f).toInt()
        while (i * 62f < to) {
            val x = i * 62f - cam
            if (i * 62f >= segment.x + 2f) {
                // two rows of stones, the lower one shifted
                box(joint, x, top, 2f, TILE / 2f, 0.45f)
                if (i * 62f + 31f < segment.end) box(joint, x + 31f, top + TILE / 2f, 2f, TILE / 2f, 0.45f)
            }
            // chalk drawings left over from the break
            val h = dinoHash(i + 5000)
            if (h % 9 == 0 && i * 62f > segment.x + 20f && i * 62f + 60f < segment.end) {
                val cx = x + 30f
                val cy = top + 26f
                val chalk = Color.White
                when ((h / 9) % 3) {
                    0 -> {
                        drawCircle(chalk, 9f, Offset(cx, cy), alpha = 0.55f, style = Stroke(2.2f))
                        for (ray in 0 until 6) {
                            val a = ray * (PI.toFloat() / 3f)
                            stroke(chalk, cx + cos(a) * 12f, cy + sin(a) * 12f, cx + cos(a) * 17f, cy + sin(a) * 17f, 2f, 0.5f)
                        }
                    }
                    1 -> {
                        drawRect(chalk, Offset(cx - 20f, cy - 9f), Size(18f, 18f), alpha = 0.5f, style = Stroke(2.2f))
                        drawRect(chalk, Offset(cx - 2f, cy - 9f), Size(18f, 18f), alpha = 0.5f, style = Stroke(2.2f))
                    }
                    else -> {
                        stroke(chalk, cx - 12f, cy + 8f, cx, cy - 10f, 2.2f, 0.5f)
                        stroke(chalk, cx, cy - 10f, cx + 12f, cy + 8f, 2.2f, 0.5f)
                        stroke(chalk, cx - 7f, cy + 1f, cx + 7f, cy + 1f, 2.2f, 0.5f)
                    }
                }
            }
            i++
        }
        box(joint, max(segment.x - cam, -60f), top + TILE / 2f - 1f, min(segment.end - cam, widthUnits + 60f) - max(segment.x - cam, -60f), 2f, 0.4f)
    }

    private fun DrawScope.drawMeadow(segment: Segment) {
        val top = DinoWorld.GROUND_Y
        val blade = Color(0xFF5E9E36)
        val bladeLight = Color(0xFF8CC65A)
        var gx = segment.x + 12f
        var n = 0
        while (gx < segment.end - 10f) {
            val x = gx - cam
            if (x > -20f && x < widthUnits + 20f) {
                val h = dinoHash((gx.toInt()) + n * 7)
                val lean = if (reducedMotion) 0f else sin(time * 2f + gx * 0.05f) * 2.5f
                stroke(blade, x, top + 2f, x - 5f + lean, top - 12f, 3f)
                stroke(bladeLight, x + 6f, top + 2f, x + 9f + lean, top - 9f, 3f)
                stroke(blade, x + 13f, top + 2f, x + 12f + lean, top - 14f, 2.5f)
                if (h % 3 == 0) {
                    // a small flower
                    val fx = x + 24f
                    val fy = top - 16f
                    stroke(blade, fx, top, fx + lean * 0.6f, fy, 2f)
                    val petal = when ((h / 3) % 3) {
                        0 -> Color(0xFFFFFFFF)
                        1 -> Color(0xFFFFC2D1)
                        else -> Color(0xFFFFE066)
                    }
                    val px = fx + lean * 0.6f
                    dot(petal, px - 4f, fy, 3.6f)
                    dot(petal, px + 4f, fy, 3.6f)
                    dot(petal, px, fy - 4f, 3.6f)
                    dot(petal, px, fy + 4f, 3.6f)
                    dot(Color(0xFFF4A300), px, fy, 2.6f)
                }
            }
            gx += 44f
            n++
        }
        // dark tufts on the face of the grass
        var tx = segment.x + 30f
        while (tx < segment.end - 20f) {
            val x = tx - cam
            if (x > -20f && x < widthUnits + 20f) {
                stroke(Color(0xFF5E9E36), x, top + 24f, x + 4f, top + 15f, 2.5f, 0.6f)
                stroke(Color(0xFF5E9E36), x + 8f, top + 24f, x + 8f, top + 13f, 2.5f, 0.6f)
            }
            tx += 96f
        }
    }

    private fun DrawScope.drawLesson(segment: Segment) {
        val lesson = segment.lesson ?: return
        val top = DinoWorld.GROUND_Y
        val left = segment.x - cam
        val base = Color(lesson.color)
        val cancelled = segment.cancelled
        val tileColor = if (cancelled) mix(base, Color(0xFF7F8C8D), 0.75f) else base

        if (segment.hasGap) {
            solid(segment.x, segment.gapStart, tileColor)
            solid(segment.gapEnd, segment.end, tileColor)
            face(tileColor, segment.x, segment.gapStart)
            face(tileColor, segment.gapEnd, segment.end)
        } else {
            solid(segment.x, segment.end, tileColor)
            face(tileColor, segment.x, segment.end)
        }

        // a darker tab on the left, like the lesson cards in the timetable
        val tabEnd = if (segment.hasGap) min(segment.x + 9f, segment.gapStart) else segment.x + 9f
        box(Color.Black, left, top, tabEnd - segment.x, TILE, 0.2f)

        if (cancelled) drawHatching(segment)

        if (segment.hasGap) drawPit(segment, lesson)

        // subject label and time on the tile
        val textColor = if (cancelled) Color(0xE6FFFFFF) else Color(lesson.textColor)
        val label = texts.get(this, TEXT_LABEL, lesson.label)
        val labelX = left + 22f
        val labelEnd = labelX + label.size.width
        val room = (if (segment.hasGap) segment.gapStart - 26f else segment.end - 8f) - cam
        if (labelEnd < room && labelEnd > -20f && labelX < widthUnits + 20f) {
            val labelY = top + (TILE - label.size.height) / 2f
            drawText(label, color = textColor, topLeft = Offset(labelX, labelY))
            if (cancelled) stroke(textColor, labelX - 3f, top + TILE / 2f + 1f, labelEnd + 3f, top + TILE / 2f + 1f, 3f, 0.9f)
            val clock = clockText(lesson.start)
            val timeText = texts.get(this, TEXT_TIME, clock)
            if (labelEnd + 12f + timeText.size.width < room) {
                drawText(
                    timeText,
                    color = textColor,
                    topLeft = Offset(labelEnd + 12f, top + TILE / 2f - timeText.size.height / 2f),
                    alpha = 0.78f,
                )
            }
        }
    }

    private val clockTexts = HashMap<Int, String>()

    private fun clockText(minute: Int): String {
        val cached = clockTexts[minute]
        if (cached != null) return cached
        val h = minute / 60
        val m = minute % 60
        val text = (if (h < 10) "0$h" else "$h") + ":" + (if (m < 10) "0$m" else "$m")
        if (clockTexts.size > 200) clockTexts.clear()
        clockTexts[minute] = text
        return text
    }

    /** Diagonal stripes over a cancelled lesson. */
    private fun DrawScope.drawHatching(segment: Segment) {
        val top = DinoWorld.GROUND_Y
        val l = max(segment.x - cam, -60f)
        val r = min(segment.end - cam, widthUnits + 60f)
        if (r <= l) return
        clipRect(l, top, r, top + TILE) {
            var i = floor((l + cam - TILE) / 30f).toInt()
            while (i * 30f - cam < r) {
                val x = i * 30f - cam
                drawLine(Color.White, Offset(x, top + TILE), Offset(x + TILE, top), 5f, alpha = 0.13f)
                i++
            }
        }
    }

    private fun DrawScope.drawPit(segment: Segment, lesson: DinoLesson) {
        val top = DinoWorld.GROUND_Y
        val gl = segment.gapStart - cam
        val gr = segment.gapEnd - cam
        if (gr < -80f || gl > widthUnits + 80f) return

        // the hole itself: dark, with the cut soil on both sides
        drawRect(pitBrush, Offset(gl, top), Size(gr - gl, bottomUnits - top + 10f))
        box(Color(0xFF5A4332), gl, top + TILE, 16f, bottomUnits - top, 0.8f)
        box(Color(0xFF2E2118), gr - 16f, top + TILE, 16f, bottomUnits - top, 0.85f)
        box(Color.Black, gl, top, 16f, TILE, 0.3f)
        box(Color.Black, gr - 16f, top, 16f, TILE, 0.42f)
        // crumbs at the broken edges
        dot(Color(0xFF5A4332), gl + 20f, top + 70f, 6f, 0.9f)
        dot(Color(0xFF5A4332), gl + 26f, top + 108f, 4f, 0.8f)
        dot(Color(0xFF2E2118), gr - 22f, top + 82f, 5f, 0.9f)

        drawHazard(gl - 22f)
        drawHazard(gr)

        // a barrier banner between two posts
        val height = 300f
        val post = lit(Color(0xFF6B7785))
        box(post, gl - 14f, top - height, 6f, height)
        box(post, gr + 8f, top - height, 6f, height)
        dot(Color(0xFFF1C40F), gl - 11f, top - height, 6f)
        dot(Color(0xFFF1C40F), gr + 11f, top - height, 6f)
        val cx = (gl + gr) / 2f
        val rope = shapePath
        rope.rewind()
        rope.moveTo(gl - 11f, top - height + 6f)
        rope.quadraticTo(cx, top - height + 36f, gr + 11f, top - height + 6f)
        drawPath(rope, Color(0xFF4A5568), style = Stroke(2.5f))

        val swing = if (reducedMotion) 0f else sin(time * 1.6f + segment.gapStart * 0.01f) * 2.5f
        val signW = 190f
        val signH = 76f
        val signTop = top - height + 34f
        withTransform({ rotate(swing, Offset(cx, signTop - 12f)) }) {
            stroke(Color(0xFF4A5568), cx - 62f, signTop - 14f, cx - 62f, signTop + 4f, 2.5f)
            stroke(Color(0xFF4A5568), cx + 62f, signTop - 14f, cx + 62f, signTop + 4f, 2.5f)
            rbox(Color(0xFF9E2B20), cx - signW / 2f, signTop + 4f, signW, signH, 14f)
            rbox(Color(0xFFE74C3C), cx - signW / 2f, signTop, signW, signH, 14f)
            drawRoundRect(
                Color.White,
                Offset(cx - signW / 2f + 6f, signTop + 6f),
                Size(signW - 12f, signH - 12f),
                CornerRadius(9f, 9f),
                style = Stroke(2f),
                alpha = 0.3f,
            )
            val title = texts.get(this, TEXT_SIGN, labels.cancelled)
            drawText(title, color = Color.White, topLeft = Offset(cx - title.size.width / 2f, signTop + 9f))
            val sub = texts.get(this, TEXT_SIGN_SUB, if (segment.randomCancel) labels.invented else lesson.label)
            drawText(sub, color = Color(0xFFFFE0DC), topLeft = Offset(cx - sub.size.width / 2f, signTop + 44f))
        }
    }

    /** Yellow and dark warning stripes on the tile next to a hole. */
    private fun DrawScope.drawHazard(x: Float) {
        val top = DinoWorld.GROUND_Y
        clipRect(x, top, x + 22f, top + TILE) {
            drawRect(Color(0xFFF1C40F), Offset(x, top), Size(22f, TILE))
            var y = top - 22f
            while (y < top + TILE + 6f) {
                drawLine(Color(0xFF2C3E50), Offset(x - 2f, y), Offset(x + 24f, y + 22f), 7f)
                y += 20f
            }
            drawRect(Color.White, Offset(x, top), Size(22f, 5f), alpha = 0.3f)
        }
    }

    private fun DrawScope.drawFinishBanner(x: Float) {
        if (x > widthUnits + 40f || x < -380f) return
        val ground = DinoWorld.GROUND_Y
        val top = ground - 330f
        val w = 320f
        val post = lit(Color(0xFF6B7785))
        box(post, x - 4f, top - 6f, 8f, ground - top + 6f)
        box(post, x + w - 4f, top - 6f, 8f, ground - top + 6f)
        dot(Color(0xFFF1C40F), x, top - 8f, 8f)
        dot(Color(0xFFF1C40F), x + w, top - 8f, 8f)
        rbox(Color(0xFF1E8449), x, top + 5f, w, 72f, 10f)
        rbox(Color(0xFF27AE60), x, top, w, 72f, 10f)
        // chequered stripe
        for (i in 0 until 16) {
            val c = if (i % 2 == 0) Color.White else INK
            box(c, x + i * 20f, top + 58f, 20f, 7f)
            box(if (i % 2 == 0) INK else Color.White, x + i * 20f, top + 65f, 20f, 7f)
        }
        // bunting to both sides
        val flagColors = LEAF_COLORS
        for (k in 0 until 7) {
            val fx = x + 22f + k * 44f
            val fy = top + 82f + sin(k * 0.9f) * 3f
            val p = shapePath
            p.rewind()
            p.moveTo(fx - 9f, fy)
            p.lineTo(fx + 9f, fy)
            p.lineTo(fx, fy + 18f + (if (reducedMotion) 0f else sin(time * 5f + k) * 2f))
            p.close()
            drawPath(p, flagColors[k % flagColors.size])
        }
        val text = texts.get(this, TEXT_BANNER, labels.schoolOut)
        val tw = min(text.size.width.toFloat(), w - 16f)
        val fit = tw / text.size.width
        withTransform({ scale(fit, fit, Offset(x + w / 2f, top + 30f)) }) {
            drawText(text, color = Color.White, topLeft = Offset(x + w / 2f - text.size.width / 2f, top + 30f - text.size.height / 2f))
        }
    }

    // ------------------------------------------------------------------------------------------
    // Things on the track
    // ------------------------------------------------------------------------------------------

    private fun DrawScope.drawObstacles(world: DinoWorld) {
        val ground = DinoWorld.GROUND_Y
        for (index in world.obstacles.indices) {
            val o = world.obstacles[index]
            val left = o.x - cam
            if (left > widthUnits + 80f || left + o.width < -80f) continue
            when (o.kind) {
                ObstacleKind.PILE -> drawPile(o, left, ground)
                ObstacleKind.BAG -> drawBag(o, left, ground)
                ObstacleKind.PLANE -> drawPlane(o, left, ground)
            }
        }
    }

    private fun DrawScope.drawPile(o: Obstacle, left: Float, ground: Float) {
        blob(Color.Black, left + o.width / 2f, ground + 2f, o.width * 0.62f, 7f, 0.18f)
        var y = ground
        val cream = Color(0xFFFDF6E3)
        for (k in o.colors.indices) {
            val cover = Color(o.colors[k])
            val h = 30f
            val inset = ((dinoHash(o.seed + k) % 9) - 4).toFloat()
            val w = o.width - 4f
            val l = left + 2f + inset * 0.6f
            y -= h
            // cover, pages on the open side, spine with two gold lines
            rbox(mix(cover, Color.Black, 0.3f), l, y + 2f, w, h - 2f, 4f)
            rbox(cover, l, y, w, h - 4f, 4f)
            box(cream, l + 12f, y + 5f, w - 14f, h - 14f)
            box(Color(0xFFD9CFB4), l + 12f, y + 10f, w - 14f, 1.5f)
            box(Color(0xFFD9CFB4), l + 12f, y + 15f, w - 14f, 1.5f)
            box(mix(cover, Color.Black, 0.18f), l, y, 12f, h - 4f)
            box(Color(0xFFF1C40F), l + 3f, y + 4f, 6f, 2f, 0.9f)
            box(Color(0xFFF1C40F), l + 3f, y + h - 10f, 6f, 2f, 0.9f)
            box(Color.White, l + 12f, y, w - 12f, 3f, 0.28f)
            // bookmark ribbon
            if ((o.seed + k) % 3 == 0) box(Color(0xFFD7263D), l + w - 16f, y + h - 6f, 5f, 9f)
        }
        // on top: a sheet of homework and either an apple or a pencil
        val sheetY = y - 5f
        rbox(Color.White, left + 8f, sheetY, 44f, 6f, 1.5f)
        box(Color(0xFF9CC9E8), left + 12f, sheetY + 2.5f, 30f, 1.2f)
        if (o.seed % 2 == 0) {
            dot(Color(0xFFD7263D), left + 34f, sheetY - 10f, 11f)
            dot(Color(0xFFFF6B6B), left + 30f, sheetY - 14f, 4f, 0.8f)
            stroke(Color(0xFF6B4226), left + 34f, sheetY - 20f, left + 36f, sheetY - 26f, 2.5f)
            blob(Color(0xFF6DB86F), left + 41f, sheetY - 25f, 5f, 2.6f)
        } else {
            rbox(Color(0xFFF4C430), left + 10f, sheetY - 7f, 36f, 6f, 1.5f)
            box(Color(0xFFFF8FA3), left + 10f, sheetY - 7f, 6f, 6f)
            val tip = shapePath
            tip.rewind()
            tip.moveTo(left + 46f, sheetY - 7f)
            tip.lineTo(left + 55f, sheetY - 4f)
            tip.lineTo(left + 46f, sheetY - 1f)
            tip.close()
            drawPath(tip, Color(0xFFE8C9A0))
            dot(INK, left + 54f, sheetY - 4f, 1.4f)
        }
    }

    private fun DrawScope.drawBag(o: Obstacle, left: Float, ground: Float) {
        val color = Color(if (o.colors.isNotEmpty()) o.colors[0] else 0xFF29335C.toInt())
        val dark = mix(color, Color.Black, 0.3f)
        val light = mix(color, Color.White, 0.25f)
        val top = ground - o.height
        blob(Color.Black, left + o.width / 2f, ground + 2f, o.width * 0.58f, 7f, 0.18f)
        // handles
        drawArc(dark, 180f, 180f, false, Offset(left + 26f, top - 12f), Size(48f, 30f), style = Stroke(5f, cap = StrokeCap.Round))
        // body
        rbox(dark, left, top + 4f, o.width, o.height - 4f, 17f)
        rbox(color, left, top + 2f, o.width, o.height - 10f, 17f)
        rbox(light, left + 10f, top + 5f, o.width - 20f, 5f, 2.5f, 0.6f)
        // zip and a white stripe
        var z = left + 16f
        while (z < left + o.width - 16f) {
            box(Color(0xFFDDE3EA), z, top + 14f, 5f, 2.5f)
            z += 9f
        }
        rbox(Color(0xFFF1C40F), left + o.width - 20f, top + 11f, 6f, 9f, 2f)
        box(Color.White, left + 6f, top + 24f, o.width - 12f, 5f, 0.85f)
        // end pocket and a name tag
        rbox(dark, left + 4f, top + 12f, 14f, o.height - 22f, 6f, 0.7f)
        rbox(Color(0xFFFDF6E3), left + 58f, top + 30f, 20f, 9f, 2f)
        box(Color(0xFF9CC9E8), left + 61f, top + 34f, 14f, 1.4f)
    }

    private fun DrawScope.drawPlane(o: Obstacle, left: Float, ground: Float) {
        val bob = if (reducedMotion) 0f else (sin(time * 3.2f + o.seed) + 1f) * 3f
        val bottom = ground - o.y - bob
        val top = bottom - o.height
        val mid = top + o.height * 0.52f
        val right = left + o.width
        blob(Color.Black, left + o.width / 2f, ground + 2f, o.width * 0.4f, 5f, 0.1f)

        // dashed loops behind it
        val trail = Color.White
        for (k in 0 until 4) {
            val tx = right + 12f + k * 20f
            val ty = mid + sin(time * 6f + k * 1.1f + o.seed) * (if (reducedMotion) 0f else 3f)
            stroke(trail, tx, ty, tx + 10f, ty, 3f, 0.6f - k * 0.13f)
        }

        val outline = Color(0xFF51627A)
        val p = shapePath
        // far wing
        p.rewind()
        p.moveTo(left, mid)
        p.lineTo(right - 6f, top)
        p.lineTo(right - 24f, mid - 2f)
        p.close()
        drawPath(p, Color(0xFFDDE5EE))
        drawPath(p, outline, style = Stroke(2.2f))
        // keel under the fold
        p.rewind()
        p.moveTo(left, mid)
        p.lineTo(right - 24f, mid - 2f)
        p.lineTo(right - 30f, bottom)
        p.close()
        drawPath(p, Color(0xFFB7C3D1))
        drawPath(p, outline, style = Stroke(2.2f))
        // near wing
        p.rewind()
        p.moveTo(left, mid)
        p.lineTo(right, mid + 9f)
        p.lineTo(right - 24f, mid - 2f)
        p.close()
        drawPath(p, Color.White)
        drawPath(p, outline, style = Stroke(2.2f))
        // ruled lines of the notebook page it was folded from
        stroke(Color(0xFF9CC9E8), left + 30f, mid + 1f, right - 12f, mid + 6f, 1.3f, 0.9f)
        stroke(Color(0xFFFF8FA3), right - 20f, mid - 1f, right - 8f, mid + 7f, 1.3f, 0.9f)
    }

    private fun DrawScope.drawPickups(world: DinoWorld) {
        val ground = DinoWorld.GROUND_Y
        for (index in world.pickups.indices) {
            val p = world.pickups[index]
            if (p.taken) continue
            val x = p.x - cam
            if (x < -40f || x > widthUnits + 40f) continue
            val phase = time * 3f + p.seed
            val y = ground - p.y + (if (reducedMotion) 0f else sin(phase) * 5f)
            val tilt = if (reducedMotion) 0f else sin(phase * 0.7f) * 12f
            dot(Color(0xFFFFE680), x, y, 30f, 0.16f)
            withTransform({
                translate(x, y)
                rotate(tilt, Offset.Zero)
                scale(22f, 22f, Offset.Zero)
            }) {
                drawPath(starPath, Color(0xFFB7791F))
            }
            withTransform({
                translate(x, y)
                rotate(tilt, Offset.Zero)
                scale(17.5f, 17.5f, Offset.Zero)
            }) {
                drawPath(starPath, Color(0xFFFFC83D))
            }
            withTransform({
                translate(x - 1.5f, y - 2.5f)
                rotate(tilt, Offset.Zero)
                scale(8f, 8f, Offset.Zero)
            }) {
                drawPath(starPath, Color(0xFFFFF1B8))
            }
        }
    }

    private fun DrawScope.drawParticles(world: DinoWorld) {
        val ground = DinoWorld.GROUND_Y
        for (p in world.particles) {
            if (!p.alive) continue
            val x = p.x - cam
            if (x < -40f || x > widthUnits + 40f) continue
            val y = ground - p.y
            val t = (p.life / p.maxLife).coerceIn(0f, 1f)
            when (p.kind) {
                DinoWorld.PARTICLE_DUST ->
                    dot(Color(0xFFF2E3C6), x, y, p.size * (1.4f - t * 0.6f), t * 0.7f)

                DinoWorld.PARTICLE_SPARK ->
                    withTransform({ rotate(p.rotation, Offset(x, y)) }) {
                        box(Color(0xFFFFE680), x - p.size * t, y - 1.5f, p.size * 2f * t, 3f, t)
                        box(Color(0xFFFFE680), x - 1.5f, y - p.size * t, 3f, p.size * 2f * t, t)
                    }

                DinoWorld.PARTICLE_CONFETTI ->
                    withTransform({ rotate(p.rotation, Offset(x, y)) }) {
                        box(Color(p.color), x - p.size / 2f, y - p.size / 4f, p.size, p.size / 2f, min(1f, t * 3f))
                    }

                else ->
                    withTransform({ rotate(p.rotation, Offset(x, y)) }) {
                        val a = min(1f, t * 3f)
                        box(Color.White, x - p.size / 2f, y - p.size * 0.65f, p.size, p.size * 1.3f, a)
                        box(Color(0xFF9CC9E8), x - p.size / 2f + 2f, y - 2f, p.size - 4f, 1.3f, a)
                        box(Color(0xFF9CC9E8), x - p.size / 2f + 2f, y + 2.5f, p.size - 4f, 1.3f, a)
                    }
            }
        }
    }

    private fun DrawScope.drawPopups(world: DinoWorld) {
        val ground = DinoWorld.GROUND_Y
        for (p in world.popups) {
            if (!p.alive) continue
            val t = p.age / DinoWorld.POPUP_LIFE
            val text = texts.get(this, if (p.big) TEXT_POPUP_BIG else TEXT_POPUP, p.text)
            val x = p.x - cam
            val y = ground - p.y - (1f - exp(-p.age * 4f)) * 70f
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            // pops in a little larger, then settles
            val pop = 1f + 0.45f * max(0f, 1f - p.age * 7f)
            withTransform({ scale(pop, pop, Offset(x, y)) }) {
                val left = x - text.size.width / 2f
                val topY = y - text.size.height / 2f
                drawText(text, color = INK, topLeft = Offset(left + 2.5f, topY + 3f), alpha = alpha * 0.55f)
                drawText(text, color = if (p.big) Color(0xFFFFE680) else Color.White, topLeft = Offset(left, topY), alpha = alpha)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Weather, in front of everything
    // ------------------------------------------------------------------------------------------

    private fun DrawScope.drawWeather(weather: Weather, amount: Float) {
        if (amount < 0.02f || weather == Weather.CLEAR) return
        val height = bottomUnits - topUnits
        val calm = if (reducedMotion) 0.5f else 1f
        when (weather) {
            Weather.RAIN -> {
                val count = (widthUnits / 22f * calm).toInt().coerceAtMost(90)
                val span = widthUnits + 240f
                val drop = Color(0xFFD6E6FF)
                for (i in 0 until count) {
                    val fall = 1100f + dinoHash01(i + 500) * 500f
                    val yy = (dinoHash01(i * 2 + 2) * height + time * fall).mod(height)
                    val xx = (dinoHash01(i * 2 + 1) * span - time * 170f - yy * 0.2f).mod(span) - 120f
                    drawLine(drop, Offset(xx, topUnits + yy), Offset(xx - 6f, topUnits + yy + 28f), 2.2f, alpha = 0.5f * amount)
                }
            }

            Weather.SNOW -> {
                val count = (widthUnits / 26f * calm).toInt().coerceAtMost(80)
                val span = widthUnits + 120f
                for (i in 0 until count) {
                    val fall = 70f + dinoHash01(i + 500) * 90f
                    val yy = (dinoHash01(i * 2 + 2) * height + time * fall).mod(height)
                    val xx = (dinoHash01(i * 2 + 1) * span + sin(time * 0.8f + i) * 26f - time * 40f).mod(span) - 60f
                    dot(Color.White, xx, topUnits + yy, 2.4f + dinoHash01(i + 900) * 3.4f, 0.9f * amount)
                }
            }

            Weather.BREEZY -> {
                val count = if (reducedMotion) 6 else 13
                val span = widthUnits + 240f
                for (i in 0 until count) {
                    val fly = 250f + dinoHash01(i + 500) * 190f
                    val xx = (dinoHash01(i * 2 + 1) * span - time * fly).mod(span) - 120f
                    val yy = topUnits + height * (0.2f + dinoHash01(i * 2 + 2) * 0.5f) + sin(time * 2f + i * 1.7f) * 42f
                    withTransform({ rotate(time * 190f + i * 47f, Offset(xx, yy)) }) {
                        blob(LEAF_COLORS[i % LEAF_COLORS.size], xx, yy, 8f, 4f, amount * 0.95f)
                        stroke(Color(0xFF6B4226), xx - 8f, yy, xx + 8f, yy, 1.2f, amount * 0.7f)
                    }
                }
            }

            Weather.CLEAR -> Unit
        }
    }
}
