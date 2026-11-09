package sh.lsx.untisx.ui.game

import android.content.Context
import androidx.core.content.edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

private object DinoPrefs {
    private const val NAME = "dino_game"
    private const val KEY_BEST = "best_score"

    fun best(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_BEST, 0)

    fun saveBest(context: Context, score: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit { putInt(KEY_BEST, score) }
    }
}

@Composable
fun DinoGameScreen(
    days: List<DinoDay>,
    usesRealTimetable: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var round by remember { mutableIntStateOf(0) }
    val world = remember(round, days) { DinoWorld(days) }
    var frame by remember { mutableLongStateOf(0L) }
    var best by remember { mutableIntStateOf(DinoPrefs.best(context)) }
    var newBest by remember(round) { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(world) {
        var last = 0L
        var reported = false
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(1f / 20f)
                last = now
                world.step(dt)
                frame = now
            }
            if (world.phase == DinoWorld.Phase.OVER && !reported) {
                reported = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (world.score > best) {
                    best = world.score
                    newBest = true
                    DinoPrefs.saveBest(context, best)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF8EC9F2))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(world) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (world.phase == DinoWorld.Phase.READY || world.phase == DinoWorld.Phase.RUNNING) {
                            world.pressJump()
                        }
                        waitForUpOrCancellation()
                        world.releaseJump()
                    }
                },
        ) {
            frame // read so the canvas redraws every frame
            drawWorld(world, textMeasurer)
        }

        Hud(world = world, best = best, frameTick = frame)

        AnimatedVisibility(
            visible = world.phase == DinoWorld.Phase.READY && frame >= 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 124.dp),
        ) {
            Panel {
                Text(
                    text = stringResource(R.string.dino_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D2B3A),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dino_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF34495E),
                    textAlign = TextAlign.Center,
                )
                if (!usesRealTimetable) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.dino_sample_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D6D7E),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.dino_tap_to_start),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32),
                )
            }
        }

        AnimatedVisibility(
            visible = world.phase == DinoWorld.Phase.OVER && world.time - world.deathTime >= 0f && frame >= 0L,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 124.dp),
        ) {
            Panel {
                Text(
                    text = stringResource(R.string.dino_game_over),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC0392B),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dino_score, world.score),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D2B3A),
                )
                if (newBest) {
                    Text(
                        text = stringResource(R.string.dino_new_best),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE67E22),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.dino_summary,
                        world.jumpedCancels,
                        world.jumpedObstacles,
                        world.daysDone,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF34495E),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { round++ }) {
                    Text(stringResource(R.string.dino_again))
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 360.dp)
            .background(Color(0xF2FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun Hud(world: DinoWorld, best: Int, frameTick: Long) {
    if (frameTick < 0L) return
    val segment = world.segmentAt(world.dinoWorldX())
    val minute = world.minuteAt(world.dinoWorldX())
    val clock = "%02d:%02d".format(minute / 60, minute % 60)
    val lesson = segment?.lesson
    val now = when {
        segment == null -> ""
        segment.kind == SegmentKind.SCHOOL_START -> stringResource(R.string.dino_school_start)
        segment.kind == SegmentKind.SCHOOL_END -> stringResource(R.string.dino_school_end)
        segment.kind == SegmentKind.BREAK -> stringResource(R.string.dino_break)
        lesson != null && segment.cancelled -> stringResource(R.string.dino_cancelled, lesson.name)
        lesson != null -> lesson.name
        else -> ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            HudChip(text = "${segment?.dayTitle.orEmpty()}  $clock")
            Column(horizontalAlignment = Alignment.End) {
                HudChip(text = stringResource(R.string.dino_score, world.score), strong = true)
                Spacer(Modifier.height(4.dp))
                HudChip(text = stringResource(R.string.dino_best, best))
            }
        }
        Spacer(Modifier.height(6.dp))
        if (now.isNotEmpty()) HudChip(text = now, strong = segment?.cancelled == true)
    }
}

@Composable
private fun HudChip(text: String, strong: Boolean = false) {
    Text(
        text = text,
        color = if (strong) Color.White else Color(0xFF1D2B3A),
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        fontSize = 14.sp,
        modifier = Modifier
            .background(if (strong) Color(0xCC1D2B3A) else Color(0xCCFFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ----------------------------------------------------------------------------------------------
// Drawing. Everything is in world units and scaled so the world is WORLD_HEIGHT units tall.
// ----------------------------------------------------------------------------------------------

private const val MIN_VISIBLE_UNITS = 950f

private fun DrawScope.drawWorld(world: DinoWorld, tm: TextMeasurer) {
    // keep at least MIN_VISIBLE_UNITS of track visible, so there is time to react on narrow screens;
    // spare height above the world becomes sky
    val scale = minOf(size.height / DinoWorld.WORLD_HEIGHT, size.width / MIN_VISIBLE_UNITS)
    val widthUnits = size.width / scale
    // on tall screens the track sits at about two thirds of the height, ground fills the rest
    val yOffset = (size.height - DinoWorld.WORLD_HEIGHT * scale) * 0.55f
    val bottomUnits = (size.height - yOffset) / scale
    world.onSize(widthUnits)

    val dinoX = world.dinoWorldX()
    val minute = world.minuteAt(dinoX)
    val dayProgress = ((minute - 7 * 60) / (9f * 60f)).coerceIn(0f, 1f)

    // shake the camera for a moment after a crash
    val since = world.time - world.deathTime
    val shake = if (world.phase == DinoWorld.Phase.OVER && since < 0.35f) sin(since * 90f) * 10f * (1f - since / 0.35f) else 0f

    drawSky(dayProgress)

    translate(left = shake * scale, top = yOffset) {
        drawSun(dayProgress, scale)
        drawClouds(world.cameraX, scale, widthUnits)
        drawHills(world.cameraX, scale, widthUnits, 0.18f, 560f, 90f, Color(0xFFB5D99C), 0.0021f)
        drawHills(world.cameraX, scale, widthUnits, 0.38f, 610f, 60f, Color(0xFF8CC084), 0.0034f)
        drawSchools(world, scale, widthUnits, tm)
        drawGround(world, scale, widthUnits, bottomUnits, tm)
        drawObstacles(world, scale)
        drawParticles(world, scale)
        drawDino(world, scale)
    }
}

private fun DrawScope.drawSky(p: Float) {
    val morningTop = Color(0xFF7EB6E8)
    val morningBottom = Color(0xFFFFD9B0)
    val noonTop = Color(0xFF3F8FDB)
    val noonBottom = Color(0xFFBFE3FA)
    val lateTop = Color(0xFF5B6FC2)
    val lateBottom = Color(0xFFFFC28A)

    val (top, bottom) = if (p < 0.5f) {
        val t = p / 0.5f
        lerp(morningTop, noonTop, t) to lerp(morningBottom, noonBottom, t)
    } else {
        val t = (p - 0.5f) / 0.5f
        lerp(noonTop, lateTop, t) to lerp(noonBottom, lateBottom, t)
    }

    drawRect(Brush.verticalGradient(listOf(top, bottom)))
}

private fun DrawScope.drawSun(p: Float, scale: Float) {
    val x = size.width * (0.1f + 0.8f * p)
    val y = (420f - sin(p * PI.toFloat()) * 300f) * scale
    val r = 46f * scale
    drawCircle(Color(0x33FFF3B0), radius = r * 2.2f, center = Offset(x, y))
    drawCircle(Color(0x66FFE680), radius = r * 1.45f, center = Offset(x, y))
    drawCircle(Color(0xFFFFD54F), radius = r, center = Offset(x, y))
}

private fun DrawScope.drawClouds(cameraX: Float, scale: Float, widthUnits: Float) {
    val parallax = 0.1f
    val spacing = 520f
    val offset = cameraX * parallax
    val first = floor(offset / spacing).toInt() - 1
    val count = (widthUnits / spacing).toInt() + 3
    for (i in first until first + count) {
        val seed = (i * 7919) and 0xFFFF
        val x = i * spacing - offset + (seed % 200)
        val y = 90f + (seed % 170)
        val s = 0.7f + (seed % 50) / 100f
        val c = Color(0xEFFFFFFF)
        drawCircle(c, 38f * s * scale, Offset(x * scale, y * scale))
        drawCircle(c, 52f * s * scale, Offset((x + 50f * s) * scale, (y - 18f * s) * scale))
        drawCircle(c, 40f * s * scale, Offset((x + 105f * s) * scale, y * scale))
        drawRoundRect(
            c,
            topLeft = Offset((x - 20f * s) * scale, y * scale),
            size = Size(150f * s * scale, 38f * s * scale),
            cornerRadius = CornerRadius(19f * s * scale),
        )
    }
}

private fun DrawScope.drawHills(
    cameraX: Float,
    scale: Float,
    widthUnits: Float,
    parallax: Float,
    baseY: Float,
    amplitude: Float,
    color: Color,
    frequency: Float,
) {
    val offset = cameraX * parallax
    val path = Path()
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= widthUnits + 20f) {
        val wx = x + offset
        val y = baseY - amplitude * (0.6f * sin(wx * frequency) + 0.4f * sin(wx * frequency * 2.3f + 1.7f))
        path.lineTo(x * scale, y * scale)
        x += 20f
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawSchools(world: DinoWorld, scale: Float, widthUnits: Float, tm: TextMeasurer) {
    val parallax = 0.55f
    for (segment in world.segments) {
        if (segment.kind != SegmentKind.SCHOOL_START) continue
        // the building drifts slower than the ground, it sits behind the schoolyard
        val x = (segment.x + 180f - world.cameraX) * parallax + widthUnits * 0.18f
        if (x > widthUnits + 50f || x < -700f) continue
        drawSchool(x, scale, segment.dayTitle, tm)
    }
}

private fun DrawScope.drawSchool(x: Float, scale: Float, title: String, tm: TextMeasurer) {
    val base = DinoWorld.GROUND_Y - 4f
    val w = 520f
    val h = 260f
    val top = base - h
    fun r(l: Float, t: Float, ww: Float, hh: Float, c: Color, radius: Float = 0f) =
        drawRoundRect(c, Offset(l * scale, t * scale), Size(ww * scale, hh * scale), CornerRadius(radius * scale))

    // main building with a center tower
    r(x, top, w, h, Color(0xFFE8D5B9), 6f)
    r(x, top, w, 26f, Color(0xFFC0563F), 6f)
    r(x + w / 2 - 70f, top - 90f, 140f, 100f, Color(0xFFEFDFC6), 6f)
    val roof = Path().apply {
        moveTo((x + w / 2 - 90f) * scale, (top - 88f) * scale)
        lineTo((x + w / 2) * scale, (top - 150f) * scale)
        lineTo((x + w / 2 + 90f) * scale, (top - 88f) * scale)
        close()
    }
    drawPath(roof, Color(0xFFB04A36))
    // clock
    drawCircle(Color.White, 26f * scale, Offset((x + w / 2) * scale, (top - 42f) * scale))
    drawCircle(Color(0xFF34495E), 26f * scale, Offset((x + w / 2) * scale, (top - 42f) * scale), style = Stroke(3f * scale))
    drawLine(Color(0xFF34495E), Offset((x + w / 2) * scale, (top - 42f) * scale), Offset((x + w / 2) * scale, (top - 60f) * scale), 3f * scale, StrokeCap.Round)
    drawLine(Color(0xFF34495E), Offset((x + w / 2) * scale, (top - 42f) * scale), Offset((x + w / 2 + 13f) * scale, (top - 42f) * scale), 3f * scale, StrokeCap.Round)
    // flag
    drawLine(Color(0xFF5D6D7E), Offset((x + w / 2) * scale, (top - 150f) * scale), Offset((x + w / 2) * scale, (top - 210f) * scale), 3f * scale)
    r(x + w / 2, top - 210f, 46f, 26f, Color(0xFFF39C12), 3f)
    // windows
    for (row in 0 until 2) {
        for (col in 0 until 6) {
            if (col == 2 || col == 3) continue
            r(x + 30f + col * 80f, top + 50f + row * 90f, 50f, 56f, Color(0xFF9CC9E8), 6f)
            r(x + 30f + col * 80f, top + 50f + row * 90f + 26f, 50f, 4f, Color(0xFFE8D5B9))
        }
    }
    // door
    r(x + w / 2 - 45f, base - 120f, 90f, 120f, Color(0xFF7A4B2A), 10f)
    // name sign
    val text = tm.measure(title, TextStyle(fontSize = (22f * scale / density).sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A4B2A)))
    r(x + w / 2 - text.size.width / scale / 2 - 14f, top + 34f, text.size.width / scale + 28f, 40f, Color(0xFFFFF4E0), 8f)
    drawText(text, topLeft = Offset((x + w / 2) * scale - text.size.width / 2f, (top + 54f) * scale - text.size.height / 2f))
}

private fun DrawScope.drawGround(world: DinoWorld, scale: Float, widthUnits: Float, bottom: Float, tm: TextMeasurer) {
    val cam = world.cameraX
    val groundTop = DinoWorld.GROUND_Y
    val tile = 54f

    for (segment in world.segments) {
        val left = segment.x - cam
        val right = segment.end - cam
        if (right < -50f || left > widthUnits + 50f) continue

        fun solid(from: Float, to: Float, top: Color, earth: Color) {
            val l = from - cam
            val w = to - from
            drawRect(earth, Offset(l * scale, (groundTop + tile) * scale), Size(w * scale, (bottom - groundTop - tile) * scale))
            drawRect(top, Offset(l * scale, groundTop * scale), Size(w * scale, tile * scale))
            drawRect(Color(0x33FFFFFF), Offset(l * scale, groundTop * scale), Size(w * scale, 6f * scale))
            drawRect(Color(0x22000000), Offset(l * scale, (groundTop + tile - 6f) * scale), Size(w * scale, 6f * scale))
        }

        when (segment.kind) {
            SegmentKind.SCHOOL_START, SegmentKind.SCHOOL_END -> {
                solid(segment.x, segment.end, Color(0xFFB9B4AC), Color(0xFF8D6E63))
                // paving stones
                var px = segment.x
                while (px < segment.end) {
                    drawLine(Color(0x33000000), Offset((px - cam) * scale, groundTop * scale), Offset((px - cam) * scale, (groundTop + tile) * scale), 2f * scale)
                    px += 60f
                }
                if (segment.kind == SegmentKind.SCHOOL_END) {
                    drawFinishBanner(segment.x - cam + 40f, scale, tm)
                }
            }

            SegmentKind.BREAK -> {
                solid(segment.x, segment.end, Color(0xFF9CCC65), Color(0xFF795548))
                // grass blades
                var gx = segment.x + 10f
                while (gx < segment.end - 10f) {
                    val sx = (gx - cam) * scale
                    drawLine(Color(0xFF7CB342), Offset(sx, groundTop * scale), Offset(sx - 5f * scale, (groundTop - 12f) * scale), 3f * scale, StrokeCap.Round)
                    drawLine(Color(0xFF7CB342), Offset(sx + 6f * scale, groundTop * scale), Offset(sx + 9f * scale, (groundTop - 9f) * scale), 3f * scale, StrokeCap.Round)
                    gx += 46f
                }
            }

            SegmentKind.LESSON -> {
                val lesson = segment.lesson ?: continue
                val base = Color(lesson.color)
                val cancelled = segment.cancelled
                val tileColor = if (cancelled) lerp(base, Color(0xFF7F8C8D), 0.75f) else base
                val earth = lerp(tileColor, Color(0xFF4E342E), 0.6f)

                if (segment.hasGap) {
                    solid(segment.x, segment.gapStart, tileColor, earth)
                    solid(segment.gapEnd, segment.end, tileColor, earth)
                    // pit: dark gradient so the hole reads clearly
                    val gl = (segment.gapStart - cam) * scale
                    val gw = (segment.gapEnd - segment.gapStart) * scale
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2C3E50), Color(0xFF0B1118)),
                            startY = groundTop * scale,
                            endY = size.height,
                        ),
                        Offset(gl, groundTop * scale),
                        Size(gw, size.height - groundTop * scale),
                    )
                    // warning stripes on the edges
                    drawHazard(segment.gapStart - cam - 18f, scale)
                    drawHazard(segment.gapEnd - cam, scale)
                    drawCancelSign((segment.gapStart + segment.gapEnd) / 2f - cam, scale, lesson, segment.randomCancel, tm)
                } else {
                    solid(segment.x, segment.end, tileColor, earth)
                }

                // separator line between lessons
                drawLine(Color(0x55000000), Offset(left * scale, groundTop * scale), Offset(left * scale, (groundTop + tile) * scale), 3f * scale)

                // subject label and time on the tile, repeated on long lessons
                val textColor = if (cancelled) Color(0xDDFFFFFF) else Color(lesson.textColor)
                val label = tm.measure(
                    lesson.label,
                    TextStyle(fontSize = (32f * scale / density).sp, fontWeight = FontWeight.ExtraBold, color = textColor),
                )
                val time = "%02d:%02d".format(lesson.start / 60, lesson.start % 60)
                val timeText = tm.measure(
                    time,
                    TextStyle(fontSize = (19f * scale / density).sp, fontWeight = FontWeight.SemiBold, color = textColor.copy(alpha = 0.8f)),
                )
                val labelX = left + 22f
                val labelY = groundTop + (tile * scale - label.size.height) / 2f / scale
                if (!segment.hasGap || labelX + label.size.width / scale < segment.gapStart - cam) {
                    drawText(label, topLeft = Offset(labelX * scale, labelY * scale))
                    drawText(timeText, topLeft = Offset(labelX * scale + label.size.width + 10f * scale, (groundTop + tile / 2f) * scale - timeText.size.height / 2f))
                }
            }
        }
    }
}

private fun DrawScope.drawHazard(x: Float, scale: Float) {
    val top = DinoWorld.GROUND_Y
    clipRect(x * scale, top * scale, (x + 18f) * scale, (top + 54f) * scale) {
        drawRect(Color(0xFFF1C40F), Offset(x * scale, top * scale), Size(18f * scale, 54f * scale))
        var y = top - 20f
        while (y < top + 60f) {
            drawLine(Color(0xFF2C3E50), Offset(x * scale, y * scale), Offset((x + 18f) * scale, (y + 18f) * scale), 6f * scale)
            y += 18f
        }
    }
}

private fun DrawScope.drawCancelSign(centerX: Float, scale: Float, lesson: DinoLesson, invented: Boolean, tm: TextMeasurer) {
    val top = DinoWorld.GROUND_Y - 300f
    val w = 190f
    val h = 78f
    // a hanging banner, swinging a little
    val swing = sin(centerX * 0.01f) * 4f
    rotate(swing, pivot = Offset(centerX * scale, (top - 60f) * scale)) {
        drawLine(Color(0xFF5D6D7E), Offset((centerX - 60f) * scale, (top - 60f) * scale), Offset((centerX - 60f) * scale, top * scale), 3f * scale)
        drawLine(Color(0xFF5D6D7E), Offset((centerX + 60f) * scale, (top - 60f) * scale), Offset((centerX + 60f) * scale, top * scale), 3f * scale)
        drawRoundRect(Color(0xFFE74C3C), Offset((centerX - w / 2) * scale, top * scale), Size(w * scale, h * scale), CornerRadius(14f * scale))
        drawRoundRect(Color(0x33FFFFFF), Offset((centerX - w / 2 + 6f) * scale, (top + 6f) * scale), Size((w - 12f) * scale, (h - 12f) * scale), CornerRadius(10f * scale), style = Stroke(2f * scale))
        val title = tm.measure("ENTFALL", TextStyle(fontSize = (22f * scale / density).sp, fontWeight = FontWeight.Black, color = Color.White))
        drawText(title, topLeft = Offset(centerX * scale - title.size.width / 2f, (top + 10f) * scale))
        val sub = tm.measure(
            if (invented) "${lesson.label} · Zufall" else lesson.label,
            TextStyle(fontSize = (15f * scale / density).sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFE0DC)),
        )
        drawText(sub, topLeft = Offset(centerX * scale - sub.size.width / 2f, (top + 44f) * scale))
    }
}

private fun DrawScope.drawFinishBanner(x: Float, scale: Float, tm: TextMeasurer) {
    val top = DinoWorld.GROUND_Y - 330f
    drawLine(Color(0xFF5D6D7E), Offset(x * scale, top * scale), Offset(x * scale, DinoWorld.GROUND_Y * scale), 6f * scale)
    drawLine(Color(0xFF5D6D7E), Offset((x + 300f) * scale, top * scale), Offset((x + 300f) * scale, DinoWorld.GROUND_Y * scale), 6f * scale)
    drawRoundRect(Color(0xFF27AE60), Offset(x * scale, top * scale), Size(300f * scale, 70f * scale), CornerRadius(10f * scale))
    // checkered stripe
    for (i in 0 until 15) {
        val c = if (i % 2 == 0) Color.White else Color(0xFF1D2B3A)
        drawRect(c, Offset((x + i * 20f) * scale, (top + 56f) * scale), Size(20f * scale, 14f * scale))
    }
    val text = tm.measure("SCHULSCHLUSS!", TextStyle(fontSize = (24f * scale / density).sp, fontWeight = FontWeight.Black, color = Color.White))
    drawText(text, topLeft = Offset((x + 150f) * scale - text.size.width / 2f, (top + 10f) * scale))
}

private fun DrawScope.drawObstacles(world: DinoWorld, scale: Float) {
    val ground = DinoWorld.GROUND_Y
    for (o in world.obstacles) {
        val left = o.x - world.cameraX
        var y = ground
        o.colors.forEachIndexed { index, c ->
            val h = 30f
            val inset = if (index % 2 == 0) 0f else 5f
            y -= h
            drawRoundRect(Color(c), Offset((left + inset) * scale, y * scale), Size((o.width - inset) * scale, (h - 2f) * scale), CornerRadius(4f * scale))
            drawRect(Color(0xFFFDF6E3), Offset((left + inset + 4f) * scale, (y + 5f) * scale), Size((o.width - inset - 10f) * scale, 4f * scale))
            drawRect(Color(0x33000000), Offset((left + inset) * scale, (y + h - 8f) * scale), Size((o.width - inset) * scale, 4f * scale))
        }
        // a sheet of homework on top
        drawRect(Color.White, Offset((left + 10f) * scale, (y - 6f) * scale), Size(40f * scale, 6f * scale))
    }
}

private fun DrawScope.drawParticles(world: DinoWorld, scale: Float) {
    val ground = DinoWorld.GROUND_Y
    for (p in world.particles) {
        val alpha = (p.life / 0.7f).coerceIn(0f, 1f)
        drawCircle(
            Color(0xFFF5E6CC).copy(alpha = alpha * 0.8f),
            radius = p.size * scale,
            center = Offset((p.x - world.cameraX) * scale, (ground - p.y) * scale),
        )
    }
}

private fun DrawScope.drawDino(world: DinoWorld, scale: Float) {
    val x = world.dinoWorldX() - world.cameraX
    val feet = DinoWorld.GROUND_Y - world.dinoY
    val airborne = world.dinoY > 0.5f || world.phase == DinoWorld.Phase.FALLING
    val dead = world.phase == DinoWorld.Phase.OVER || world.phase == DinoWorld.Phase.FALLING
    val bob = if (!airborne && world.phase == DinoWorld.Phase.RUNNING) abs(sin(world.runPhase)) * 4f else 0f
    val tilt = when {
        world.phase == DinoWorld.Phase.FALLING -> -25f
        airborne -> -8f
        else -> 0f
    }

    val body = Color(0xFF4CAF50)
    val belly = Color(0xFFC5E1A5)
    val dark = Color(0xFF2E7D32)
    fun o(dx: Float, dy: Float) = Offset((x + dx) * scale, (feet - bob + dy) * scale)
    fun s(w: Float, h: Float) = Size(w * scale, h * scale)

    // shadow
    if (world.phase != DinoWorld.Phase.FALLING) {
        val shadowScale = (1f - world.dinoY / 500f).coerceIn(0.3f, 1f)
        drawOval(Color(0x33000000), Offset((x - 40f * shadowScale) * scale, (DinoWorld.GROUND_Y - 6f) * scale), Size(80f * shadowScale * scale, 12f * scale))
    }

    rotate(tilt, pivot = o(0f, -50f)) {
        // legs
        val swing = if (airborne || world.phase != DinoWorld.Phase.RUNNING) 0f else sin(world.runPhase) * 14f
        drawRoundRect(dark, o(-22f + swing, -30f), s(16f, 30f), CornerRadius(8f * scale))
        drawRoundRect(dark, o(6f - swing, -30f), s(16f, 30f), CornerRadius(8f * scale))
        // tail
        val tail = Path().apply {
            moveTo(o(-30f, -62f).x, o(-30f, -62f).y)
            quadraticTo(o(-78f, -60f).x, o(-78f, -60f).y, o(-86f, -84f).x, o(-86f, -84f).y)
            quadraticTo(o(-64f, -40f).x, o(-64f, -40f).y, o(-26f, -36f).x, o(-26f, -36f).y)
            close()
        }
        drawPath(tail, body)
        // body
        drawOval(body, o(-42f, -92f), s(84f, 70f))
        drawOval(belly, o(-14f, -70f), s(46f, 44f))
        // school bag
        drawRoundRect(Color(0xFFFF7043), o(-50f, -92f), s(34f, 44f), CornerRadius(10f * scale))
        drawRoundRect(Color(0xFFFFAB91), o(-46f, -80f), s(26f, 10f), CornerRadius(4f * scale))
        drawLine(Color(0xFFD84315), o(-22f, -88f), o(0f, -60f), 4f * scale, StrokeCap.Round)
        // head
        drawOval(body, o(4f, -128f), s(60f, 50f))
        drawOval(body, o(30f, -116f), s(40f, 30f))
        // spikes
        for (i in 0 until 3) {
            val sx = -24f + i * 20f
            val spike = Path().apply {
                moveTo(o(sx, -88f).x, o(sx, -88f).y)
                lineTo(o(sx + 9f, -104f).x, o(sx + 9f, -104f).y)
                lineTo(o(sx + 18f, -88f).x, o(sx + 18f, -88f).y)
                close()
            }
            drawPath(spike, Color(0xFFFFCA28))
        }
        // eye
        if (dead) {
            drawLine(Color(0xFF1D2B3A), o(30f, -114f), o(42f, -102f), 4f * scale, StrokeCap.Round)
            drawLine(Color(0xFF1D2B3A), o(42f, -114f), o(30f, -102f), 4f * scale, StrokeCap.Round)
        } else {
            val blink = (world.time % 3.2f) > 3.05f
            drawCircle(Color.White, 10f * scale, o(36f, -108f))
            if (blink) {
                drawLine(Color(0xFF1D2B3A), o(28f, -108f), o(44f, -108f), 3f * scale, StrokeCap.Round)
            } else {
                drawCircle(Color(0xFF1D2B3A), 5f * scale, o(39f, -108f))
                drawCircle(Color.White, 1.8f * scale, o(40.5f, -110f))
            }
        }
        // cheek and smile
        drawCircle(Color(0x66FF8A80), 6f * scale, o(52f, -96f))
        drawArc(
            Color(0xFF1D2B3A),
            startAngle = 20f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = o(48f, -108f),
            size = s(18f, 14f),
            style = Stroke(3f * scale, cap = StrokeCap.Round),
        )
        // little arm
        drawLine(dark, o(22f, -62f), o(34f, -54f), 7f * scale, StrokeCap.Round)
    }
}
