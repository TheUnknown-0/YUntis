package sh.lsx.untisx.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The player character: a small green dino with a school bag, a red scarf and sneakers.
 * Nothing is a fixed picture. Every frame the pose is computed from the game state:
 * a run cycle with real knees, tucked and stretched legs in the air, a low duck,
 * a tumble after a crash and flailing when it drops into a hole.
 *
 * Local coordinates: the origin is between the feet, x points in running direction,
 * y points down (so the head is at negative y).
 */
internal class DinoSprite {

    private val body = Color(0xFF58B368)
    private val bodyDark = Color(0xFF3F9257)
    private val outline = Color(0xFF1F5137)
    private val belly = Color(0xFFF4E9C1)
    private val plate = Color(0xFFFFC145)
    private val plateDark = Color(0xFFE09A1F)
    private val ink = Color(0xFF1D2B3A)
    private val scarfRed = Color(0xFFD7263D)
    private val scarfDark = Color(0xFFA81B2E)
    private val bag = Color(0xFFFF8A3D)
    private val bagDark = Color(0xFFE0661A)
    private val bagLight = Color(0xFFFFB37A)
    private val shoe = Color(0xFF3D6FD9)
    private val shoeDark = Color(0xFF2A4FA3)

    private val tailPath = Path()
    private val scarfPath = Path()
    private val platePath = Path()

    private val scarfStroke = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val smileStroke = Stroke(width = 3f, cap = StrokeCap.Round)
    private val tailOutline = Stroke(width = 5f, join = StrokeJoin.Round)

    // smoothed pose values
    private var crouch = 0f
    private var lastTime = 0f

    // result of the last solveKnee call
    private var kneeX = 0f
    private var kneeY = 0f

    fun draw(scope: DrawScope, world: DinoWorld, x: Float, groundY: Float) = with(scope) {
        val phase = world.phase
        val t = world.time
        val dt = (t - lastTime).coerceIn(0f, 0.05f)
        lastTime = t

        val hit = phase == DinoWorld.Phase.OVER && world.deathCause == DinoWorld.DeathCause.HIT
        // dropped out of sight
        if (phase == DinoWorld.Phase.OVER && !hit) return@with
        val dropping = phase == DinoWorld.Phase.FALLING
        val running = phase == DinoWorld.Phase.RUNNING && world.grounded
        val air = phase == DinoWorld.Phase.RUNNING && !world.grounded

        val crouchTarget = if (world.ducking) 1f else 0f
        crouch += (crouchTarget - crouch) * min(1f, dt * 20f)
        if (phase != DinoWorld.Phase.RUNNING) crouch = 0f
        val c = crouch

        val sinceDeath = t - world.deathTime
        val fallen = if (hit) min(1f, sinceDeath / 0.55f) else 0f

        // shadow on the ground, smaller the higher the dino is
        if (!dropping) {
            val s = (1f - world.dinoY / 520f).coerceIn(0.35f, 1f) * (1f + c * 0.2f)
            val sx = if (hit) x - 52f * fallen else x
            drawOval(Color.Black, Offset(sx - 44f * s, groundY - 5f), Size(88f * s, 12f), alpha = 0.2f)
        }

        // whole body transform: lean, squash and stretch, tumble
        var rotation = 0f
        var offsetX = 0f
        var offsetY = 0f
        var scaleX = 1f
        var scaleY = 1f
        var pivotY = -50f
        when {
            hit -> {
                val e = fallen * fallen * (3f - 2f * fallen)
                rotation = -90f * e
                offsetX = -44f * e
                offsetY = -sin(fallen * PI.toFloat()) * 64f - 46f * e
                pivotY = 0f
            }
            dropping -> rotation = min(75f, -world.dinoY * 0.22f) + sin(t * 9f) * 6f
            air -> {
                rotation = if (world.ducking) 14f else (-world.dinoVy / DinoWorld.JUMP_SPEED * 11f).coerceIn(-11f, 13f)
                val stretch = exp(-(t - world.jumpTime) * 7f)
                scaleY = 1f + 0.13f * stretch
                scaleX = 1f - 0.09f * stretch
            }
            running -> {
                val age = t - world.landTime
                val k = world.landImpact * exp(-age * 11f) * cos(age * 24f)
                scaleY = 1f - 0.2f * k
                scaleX = 1f + 0.15f * k
                rotation = 3f * (1f - c)
            }
            else -> scaleY = 1f + 0.016f * sin(t * 2.4f)
        }

        val cycle = world.runPhase
        val feetY = groundY - world.dinoY

        withTransform({
            translate(x + offsetX, feetY + offsetY)
            rotate(rotation, Offset(0f, pivotY))
            scale(scaleX, scaleY, Offset.Zero)
        }) {
            drawBody(world, c, cycle, t, running, air, dropping, hit)
        }

        // dizzy stars circle over the head once it lies on its back
        if (hit && sinceDeath > 0.5f) {
            val cx = x - 44f - 104f
            val cy = groundY - 96f
            val fade = min(1f, (sinceDeath - 0.5f) * 3f)
            for (i in 0 until 3) {
                val a = t * 3.2f + i * (2f * PI.toFloat() / 3f)
                val sx = cx + cos(a) * 30f
                val sy = cy + sin(a) * 9f
                val r = 5f + sin(a) * 1.5f
                drawCircle(plateDark, r + 1.5f, Offset(sx, sy), alpha = fade)
                drawCircle(Color(0xFFFFE680), r, Offset(sx, sy), alpha = fade)
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Two bone leg: finds the knee between hip and ankle, bending forward. */
    private fun solveKnee(hx: Float, hy: Float, ax: Float, ay: Float, upper: Float, lower: Float) {
        val dx = ax - hx
        val dy = ay - hy
        val d = sqrt(dx * dx + dy * dy).coerceIn(0.5f, upper + lower - 0.2f)
        val cosine = ((upper * upper + d * d - lower * lower) / (2f * upper * d)).coerceIn(-1f, 1f)
        val angle = atan2(dy, dx) - acos(cosine)
        kneeX = hx + cos(angle) * upper
        kneeY = hy + sin(angle) * upper
    }

    private fun DrawScope.limb(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        drawLine(c, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
    }

    private fun DrawScope.oval(c: Color, cx: Float, cy: Float, rx: Float, ry: Float, a: Float = 1f) {
        drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), alpha = a)
    }

    private fun DrawScope.leg(color: Color, hx: Float, hy: Float, ax: Float, ay: Float, far: Boolean) {
        solveKnee(hx, hy, ax, ay, 22f, 22f)
        limb(outline, hx, hy, kneeX, kneeY, 19f)
        limb(outline, kneeX, kneeY, ax, ay, 16f)
        limb(color, hx, hy, kneeX, kneeY, 15f)
        limb(color, kneeX, kneeY, ax, ay, 12f)
        // sneaker: sole, upper, toe cap, a lace
        val upper = if (far) shoeDark else shoe
        drawRoundRect(Color.White, Offset(ax - 10f, ay + 2f), Size(29f, 6f), CornerRadius(3f, 3f))
        drawRoundRect(upper, Offset(ax - 9f, ay - 6f), Size(26f, 10f), CornerRadius(5f, 5f))
        drawRoundRect(Color.White, Offset(ax + 9f, ay - 4f), Size(8f, 7f), CornerRadius(3.5f, 3.5f), alpha = 0.9f)
        drawLine(Color.White, Offset(ax - 1f, ay - 5f), Offset(ax + 4f, ay - 1f), 1.6f, alpha = 0.9f)
    }

    private fun DrawScope.plateAt(x: Float, y: Float, lean: Float, size: Float) {
        val p = platePath
        p.rewind()
        p.moveTo(x - size * 0.8f, y + size * 0.5f)
        p.quadraticTo(x + lean - size * 0.3f, y - size * 0.9f, x + lean, y - size)
        p.quadraticTo(x + lean + size * 0.4f, y - size * 0.6f, x + size * 0.8f, y + size * 0.5f)
        p.close()
        drawPath(p, plateDark)
        withTransform({ scale(0.72f, 0.72f, Offset(x, y + size * 0.5f)) }) {
            drawPath(p, plate)
        }
    }

    private fun DrawScope.drawBody(
        world: DinoWorld,
        c: Float,
        cycle: Float,
        t: Float,
        running: Boolean,
        air: Boolean,
        dropping: Boolean,
        hit: Boolean,
    ) {
        val idle = !running && !air && !dropping && !hit
        val rise = (world.dinoVy / 700f).coerceIn(-1f, 1f)

        // --- skeleton -------------------------------------------------------------------
        val bob = if (running) 2.6f * cos(cycle * 2f) * (1f - c * 0.5f) else 0f
        val hipX = -6f
        val hipY = lerp(-42f, -25f, c) + bob
        val bodyX = lerp(-6f, -8f, c)
        val bodyY = lerp(-64f, -38f, c) + bob
        val bodyRx = lerp(34f, 40f, c)
        val bodyRy = lerp(33f, 23f, c)
        val headBob = if (running) 1.6f * cos(cycle * 2f - 0.8f) else if (idle) sin(t * 2.4f) * 1.2f else 0f
        val headX = lerp(24f, 42f, c)
        val headY = lerp(-104f, -42f, c) + bob + headBob

        // feet (ankle positions)
        val nearX: Float
        val nearY: Float
        val farX: Float
        val farY: Float
        when {
            running -> {
                val stride = lerp(21f, 14f, c)
                val lift = lerp(15f, 7f, c)
                nearX = hipX - stride * cos(cycle)
                nearY = -7f - max(0f, sin(cycle)) * lift
                farX = hipX - stride * cos(cycle + PI.toFloat())
                farY = -7f - max(0f, sin(cycle + PI.toFloat())) * lift
            }
            air -> {
                // tucked while rising, stretched towards the ground while coming down
                val k = (rise + 1f) / 2f
                nearX = hipX + lerp(16f, 10f, k)
                nearY = hipY + lerp(40f, 22f, k)
                farX = hipX + lerp(-4f, -10f, k)
                farY = hipY + lerp(41f, 27f, k)
            }
            dropping -> {
                val kick = sin(t * 15f) * 14f
                nearX = hipX + 6f + kick
                nearY = hipY + 36f
                farX = hipX - 6f - kick
                farY = hipY + 36f
            }
            hit -> {
                val kick = sin(t * 9f) * 5f
                nearX = hipX + 14f + kick
                nearY = hipY + 34f
                farX = hipX - 6f - kick
                farY = hipY + 37f
            }
            else -> {
                nearX = hipX + 11f
                nearY = -7f
                farX = hipX - 9f
                farY = -7f
            }
        }

        // tail
        val tailSway = when {
            running -> sin(cycle * 2f + 1f) * 5f
            air -> -rise * 14f
            dropping -> -22f + sin(t * 12f) * 6f
            hit -> 6f
            else -> sin(t * 3f) * 6f
        }
        val tailBaseX = bodyX - bodyRx * 0.62f
        val tailBaseY = bodyY + 4f
        val tailTipX = bodyX - bodyRx - lerp(48f, 56f, c)
        val tailTipY = bodyY - lerp(6f, 12f, c) + tailSway
        val tailMidX = (tailBaseX + tailTipX) / 2f - 4f
        val tail = tailPath
        tail.rewind()
        tail.moveTo(tailBaseX, tailBaseY - 20f)
        tail.quadraticTo(tailMidX, tailTipY - 15f, tailTipX, tailTipY)
        tail.quadraticTo(tailMidX, tailBaseY + 22f + tailSway * 0.3f, tailBaseX + 8f, tailBaseY + 22f)
        tail.close()

        // neck: from the shoulders to the back of the head
        val neckFromX = bodyX + bodyRx * 0.4f
        val neckFromY = bodyY - bodyRy * 0.5f
        val neckToX = headX - 8f
        val neckToY = headY + 8f

        // arms
        val shoulderX = bodyX + bodyRx * 0.5f
        val shoulderY = bodyY - bodyRy * 0.22f
        val armAngle = when {
            hit -> -70f
            dropping -> -60f + sin(t * 16f) * 50f
            air -> if (world.ducking) 70f else lerp(25f, -35f, (rise + 1f) / 2f)
            running -> lerp(62f + sin(cycle + PI.toFloat()) * 36f, 84f, c)
            else -> 68f + sin(t * 2.4f) * 4f
        }
        val armRad = armAngle * (PI.toFloat() / 180f)
        val farArmRad = (if (running) lerp(62f + sin(cycle) * 36f, 84f, c) else armAngle + 14f) * (PI.toFloat() / 180f)

        // --- far side -------------------------------------------------------------------
        leg(bodyDark, hipX - 5f, hipY, farX, farY, far = true)
        limb(outline, shoulderX + 4f, shoulderY, shoulderX + 4f + cos(farArmRad) * 18f, shoulderY + sin(farArmRad) * 18f, 12f)
        limb(bodyDark, shoulderX + 4f, shoulderY, shoulderX + 4f + cos(farArmRad) * 18f, shoulderY + sin(farArmRad) * 18f, 8f)

        // --- silhouette outline ---------------------------------------------------------
        drawPath(tail, outline, style = tailOutline)
        oval(outline, bodyX, bodyY, bodyRx + 2.5f, bodyRy + 2.5f)
        limb(outline, neckFromX, neckFromY, neckToX, neckToY, 37f)
        oval(outline, headX, headY, 31.5f, 26.5f)
        oval(outline, headX + 22f, headY + 6f, 22.5f, 17.5f)

        // --- back plates, their bases disappear behind the body -------------------------
        plateAt(tailBaseX - 14f, lerp(tailBaseY - 16f, tailTipY - 8f, 0.3f), -3f, 9f)
        plateAt(tailBaseX - 32f, lerp(tailBaseY - 16f, tailTipY - 8f, 0.68f), -3f, 7f)
        plateAt(bodyX + 2f, bodyY - bodyRy - 1f, -2f, 10f)
        plateAt(headX - 22f, headY - 17f, -4f, 8f)
        plateAt(headX - 8f, headY - 24f, -2f, 8f)

        // --- school bag on the back -----------------------------------------------------
        val bagX = bodyX - bodyRx - lerp(12f, 6f, c)
        val bagY = bodyY - lerp(36f, 28f, c)
        val bagH = lerp(46f, 38f, c)
        // pencil and ruler look out at the top
        limb(Color(0xFFF4C430), bagX + 10f, bagY + 4f, bagX + 6f, bagY - 12f, 4f)
        limb(Color(0xFFFF8FA3), bagX + 6f, bagY - 12f, bagX + 5.5f, bagY - 14f, 4f)
        limb(Color(0xFF9CC9E8), bagX + 20f, bagY + 4f, bagX + 22f, bagY - 9f, 5f)
        drawRoundRect(outline, Offset(bagX - 2f, bagY - 2f), Size(36f, bagH + 4f), CornerRadius(12f, 12f))
        drawRoundRect(bag, Offset(bagX, bagY), Size(32f, bagH), CornerRadius(10f, 10f))
        drawRoundRect(bagDark, Offset(bagX, bagY), Size(32f, 17f), CornerRadius(10f, 10f))
        drawRoundRect(bagLight, Offset(bagX + 5f, bagY + 22f), Size(20f, bagH - 28f), CornerRadius(5f, 5f))
        drawRoundRect(Color(0xFFF1C40F), Offset(bagX + 12f, bagY + 13f), Size(8f, 7f), CornerRadius(2f, 2f))
        drawRoundRect(Color.White, Offset(bagX + 3f, bagY + bagH - 6f), Size(26f, 3f), CornerRadius(1.5f, 1.5f), alpha = 0.75f)

        // --- body ------------------------------------------------------------------------
        drawPath(tail, body)
        // stripes on the tail
        limb(bodyDark, lerp(tailBaseX, tailTipX, 0.45f), lerp(tailBaseY, tailTipY, 0.45f) - 5f, lerp(tailBaseX, tailTipX, 0.45f) + 2f, lerp(tailBaseY, tailTipY, 0.45f) + 7f, 4f)
        limb(bodyDark, lerp(tailBaseX, tailTipX, 0.7f), lerp(tailBaseY, tailTipY, 0.7f) - 3f, lerp(tailBaseX, tailTipX, 0.7f) + 1.5f, lerp(tailBaseY, tailTipY, 0.7f) + 4f, 3.5f)
        oval(body, bodyX, bodyY, bodyRx, bodyRy)
        limb(body, neckFromX, neckFromY, neckToX, neckToY, 32f)
        oval(bodyDark, bodyX - bodyRx * 0.2f, bodyY + bodyRy * 0.55f, bodyRx * 0.7f, bodyRy * 0.36f, 0.45f)
        oval(belly, bodyX + bodyRx * 0.26f, bodyY + bodyRy * 0.22f, bodyRx * 0.6f, bodyRy * 0.68f)
        oval(Color.White, bodyX - bodyRx * 0.2f, bodyY - bodyRy * 0.58f, bodyRx * 0.36f, bodyRy * 0.16f, 0.2f)

        // bag strap over the shoulder
        limb(bagDark, bodyX - bodyRx * 0.55f, bodyY - bodyRy * 0.8f, bodyX + bodyRx * 0.2f, bodyY + bodyRy * 0.05f, 5f)

        // --- near leg --------------------------------------------------------------------
        leg(body, hipX + 6f, hipY, nearX, nearY, far = false)

        // --- head ------------------------------------------------------------------------
        oval(body, headX, headY, 29f, 24f)
        oval(body, headX + 22f, headY + 6f, 20f, 15f)
        oval(Color.White, headX - 6f, headY - 14f, 12f, 5f, 0.22f)
        oval(bodyDark, headX + 37f, headY + 1f, 1.8f, 2.4f)
        drawCircle(Color(0xFFFF8A80), 5.5f, Offset(headX + 17f, headY + 11f), alpha = 0.5f)
        drawFace(headX, headY, t, air, dropping, hit, rise)

        // --- scarf -----------------------------------------------------------------------
        val neckMidX = (neckFromX + neckToX) / 2f + 1f
        val neckMidY = (neckFromY + neckToY) / 2f
        val flutter = when {
            running || air -> 1f
            dropping -> 1.4f
            hit -> 0f
            else -> 0.35f
        }
        val droop = when {
            air -> -rise * 7f
            dropping -> -9f
            running -> 1.5f
            else -> 6f
        }
        val scarf = scarfPath
        scarf.rewind()
        val startX = neckMidX - 12f
        val startY = neckMidY - 3f
        scarf.moveTo(startX, startY)
        for (j in 1..4) {
            scarf.lineTo(
                startX - j * 11f,
                startY + j * droop + sin(t * 11f - j * 1.3f) * 3.2f * flutter * j / 2f,
            )
        }
        drawPath(scarf, scarfDark, style = scarfStroke)
        // the wrap around the neck, across its direction
        val nx = neckToX - neckFromX
        val ny = neckToY - neckFromY
        val len = max(1f, sqrt(nx * nx + ny * ny))
        val px = -ny / len * 15f
        val py = nx / len * 15f
        limb(scarfRed, neckMidX - px, neckMidY - py, neckMidX + px, neckMidY + py, 11f)
        limb(Color.White, neckMidX - px * 0.5f, neckMidY - py * 0.5f - 1f, neckMidX - px * 0.2f, neckMidY - py * 0.2f - 1f, 2f)

        // --- near arm --------------------------------------------------------------------
        val handX = shoulderX + cos(armRad) * 19f
        val handY = shoulderY + sin(armRad) * 19f
        limb(outline, shoulderX, shoulderY, handX, handY, 13f)
        limb(body, shoulderX, shoulderY, handX, handY, 9f)
        drawCircle(belly, 3f, Offset(handX, handY))
    }

    private fun DrawScope.drawFace(
        headX: Float,
        headY: Float,
        t: Float,
        air: Boolean,
        dropping: Boolean,
        hit: Boolean,
        rise: Float,
    ) {
        val eyeX = headX + 9f
        val eyeY = headY - 6f
        when {
            hit -> {
                drawLine(ink, Offset(eyeX - 6f, eyeY - 6f), Offset(eyeX + 6f, eyeY + 6f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(eyeX + 6f, eyeY - 6f), Offset(eyeX - 6f, eyeY + 6f), 4f, StrokeCap.Round)
                // a wobbly mouth
                drawLine(ink, Offset(headX + 20f, headY + 15f), Offset(headX + 26f, headY + 12f), 3f, StrokeCap.Round)
                drawLine(ink, Offset(headX + 26f, headY + 12f), Offset(headX + 32f, headY + 15f), 3f, StrokeCap.Round)
            }
            dropping -> {
                // wide eyes, small pupils, raised brow, round mouth
                oval(Color.White, eyeX, eyeY, 11f, 12f)
                drawCircle(ink, 3.2f, Offset(eyeX + 1f, eyeY + 3f))
                drawLine(ink, Offset(eyeX - 9f, eyeY - 17f), Offset(eyeX + 8f, eyeY - 14f), 3f, StrokeCap.Round)
                oval(Color(0xFF7A1F2B), headX + 27f, headY + 14f, 6f, 6.5f)
            }
            else -> {
                val blink = (t % 3.4f) > 3.27f
                if (blink) {
                    drawLine(ink, Offset(eyeX - 8f, eyeY), Offset(eyeX + 8f, eyeY), 3f, StrokeCap.Round)
                } else {
                    oval(Color.White, eyeX, eyeY, 9f, 10.5f)
                    // looks where it is going: up while rising, down while coming back
                    val lookY = if (air) -rise * 2.5f else 0f
                    drawCircle(ink, 5f, Offset(eyeX + 3f, eyeY + 1f + lookY))
                    drawCircle(Color.White, 1.8f, Offset(eyeX + 4.6f, eyeY - 1f + lookY))
                }
                if (air && rise > 0.1f) {
                    // open, happy mouth on the way up
                    oval(Color(0xFF7A1F2B), headX + 27f, headY + 13f, 7f, 5f)
                    oval(Color(0xFFFF8A80), headX + 26f, headY + 15.5f, 4f, 2.2f)
                } else {
                    drawArc(
                        ink,
                        startAngle = 20f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(headX + 17f, headY + 3f),
                        size = Size(22f, 14f),
                        style = smileStroke,
                    )
                }
            }
        }
    }
}
