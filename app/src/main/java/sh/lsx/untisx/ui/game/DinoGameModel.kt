package sh.lsx.untisx.ui.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One lesson as the game sees it. Times are minutes since midnight. */
data class DinoLesson(
    val label: String,
    val name: String,
    val color: Int,
    val textColor: Int,
    val start: Int,
    val end: Int,
    val cancelled: Boolean,
)

data class DinoDay(
    val title: String,
    val lessons: List<DinoLesson>,
)

internal enum class SegmentKind { SCHOOL_START, LESSON, BREAK, SCHOOL_END }

internal class Segment(
    val kind: SegmentKind,
    val x: Float,
    val width: Float,
    val dayIndex: Int,
    val dayTitle: String,
    val lesson: DinoLesson? = null,
    /** Cancelled lessons leave a hole between these world positions. */
    val gapStart: Float = 0f,
    val gapEnd: Float = 0f,
    /** True when the cancellation is invented by the game, not from the real timetable. */
    val randomCancel: Boolean = false,
    /** Shown as cancelled (grey). Usually with a hole, see [hasGap]. */
    val cancelled: Boolean = false,
    /** Clock range covered by the segment, used for the in-game clock. */
    val fromMinute: Int = 0,
    val toMinute: Int = 0,
) {
    val end: Float get() = x + width
    val hasGap: Boolean get() = gapEnd > gapStart
    var counted: Boolean = false
}

internal class Obstacle(val x: Float, val width: Float, val height: Float, val colors: List<Int>) {
    var counted: Boolean = false
}

internal class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float)

/**
 * The whole game world in "units": the visible height is [WORLD_HEIGHT] units, the ground top
 * sits at [GROUND_Y]. y grows downwards like on screen; the dino stands on the ground when
 * [dinoY] is 0 and [dinoY] grows when it rises.
 */
internal class DinoWorld(
    private val days: List<DinoDay>,
    seed: Long = System.nanoTime(),
) {
    companion object {
        const val WORLD_HEIGHT = 900f
        const val GROUND_Y = 690f
        const val DINO_X_FRACTION = 0.2f
        const val DINO_W = 92f
        const val DINO_H = 104f

        const val GRAVITY = 3000f
        const val JUMP_SPEED = 1150f
        const val START_SPEED = 380f
        const val MAX_SPEED = 760f
        const val UNITS_PER_MINUTE = 11f
        const val RANDOM_CANCEL_CHANCE = 0.22f
        const val PILE_WIDTH = 64f
    }

    enum class Phase { READY, RUNNING, FALLING, OVER }

    private val random = Random(seed)

    val segments = ArrayList<Segment>()
    val obstacles = ArrayList<Obstacle>()
    val particles = ArrayList<Particle>()

    var phase = Phase.READY
        private set

    /** World x of the left screen edge. */
    var cameraX = 0f
        private set
    var speed = START_SPEED
        private set
    var dinoY = 0f
        private set
    private var dinoVy = 0f
    var runPhase = 0f
        private set
    var time = 0f
        private set
    var deathTime = 0f
        private set

    var distance = 0f
        private set
    var jumpedCancels = 0
        private set
    var jumpedObstacles = 0
        private set
    var daysDone = 0
        private set

    val score: Int get() = (distance / 40f).toInt() + jumpedCancels * 25 + jumpedObstacles * 10 + daysDone * 100

    private var holdingJump = false
    private var generatedUntil = 0f
    private var nextDay = 0
    private var visibleWidth = 1600f
    private var lastHazardEnd = -10_000f

    init {
        generateAhead(4000f)
    }

    fun dinoWorldX(): Float = cameraX + visibleWidth * DINO_X_FRACTION

    fun onSize(widthUnits: Float) {
        visibleWidth = widthUnits
    }

    fun pressJump() {
        holdingJump = true
        when (phase) {
            Phase.READY -> {
                phase = Phase.RUNNING
                jump()
            }
            Phase.RUNNING -> if (dinoY <= 0.5f) jump()
            else -> Unit
        }
    }

    fun releaseJump() {
        holdingJump = false
        // short tap = short hop
        if (dinoVy > 0f) dinoVy *= 0.55f
    }

    private fun jump() {
        dinoVy = JUMP_SPEED
        dust(dinoWorldX(), 10)
    }

    fun step(dt: Float) {
        time += dt
        updateParticles(dt)

        when (phase) {
            Phase.READY -> {
                runPhase += dt * 6f
                return
            }
            Phase.OVER -> return
            Phase.FALLING -> {
                dinoVy -= GRAVITY * dt
                dinoY += dinoVy * dt
                cameraX += speed * 0.25f * dt
                if (dinoY < -420f) {
                    phase = Phase.OVER
                    deathTime = time
                }
                return
            }
            Phase.RUNNING -> Unit
        }

        speed = min(MAX_SPEED, START_SPEED + distance / 90f)
        val dx = speed * dt
        cameraX += dx
        distance += dx
        runPhase += dt * speed / 26f

        // vertical movement
        val wasAirborne = dinoY > 0.5f
        if (dinoY > 0f || dinoVy > 0f) {
            val gravity = if (holdingJump && dinoVy > 0f) GRAVITY * 0.62f else GRAVITY
            dinoVy -= gravity * dt
            dinoY += dinoVy * dt
        }

        val x = dinoWorldX()
        val overGap = isOverGap(x)

        if (dinoY <= 0f && !overGap) {
            if (wasAirborne) dust(x, 6)
            dinoY = 0f
            dinoVy = 0f
        } else if (dinoY <= 0f && overGap) {
            phase = Phase.FALLING
            if (dinoVy > 0f) dinoVy = 0f
            return
        }

        if (hitsObstacle(x)) {
            phase = Phase.OVER
            deathTime = time
            dinoVy = 0f
            return
        }

        countPassed(x)
        generateAhead(cameraX + visibleWidth + 2400f)
        prune()
    }

    private fun isOverGap(x: Float): Boolean {
        val margin = DINO_W * 0.32f
        return segments.any { it.hasGap && x - margin > it.gapStart && x + margin < it.gapEnd }
    }

    private fun hitsObstacle(x: Float): Boolean {
        val left = x - DINO_W * 0.34f
        val right = x + DINO_W * 0.34f
        return obstacles.any { o ->
            right > o.x + 6f && left < o.x + o.width - 6f && dinoY < o.height - 10f
        }
    }

    private fun countPassed(x: Float) {
        for (segment in segments) {
            if (segment.hasGap && !segment.counted && x > segment.gapEnd) {
                segment.counted = true
                jumpedCancels++
            }
            if (segment.kind == SegmentKind.SCHOOL_END && !segment.counted && x > segment.x) {
                segment.counted = true
                daysDone++
            }
        }
        for (obstacle in obstacles) {
            if (!obstacle.counted && x > obstacle.x + obstacle.width) {
                obstacle.counted = true
                jumpedObstacles++
            }
        }
    }

    private fun prune() {
        val limit = cameraX - 600f
        segments.removeAll { it.end < limit }
        obstacles.removeAll { it.x + it.width < limit }
    }

    /** Segment under a world position, for the HUD and the sky. */
    fun segmentAt(x: Float): Segment? = segments.firstOrNull { x >= it.x && x < it.end }

    /** In-game clock (minutes since midnight) at a world position. */
    fun minuteAt(x: Float): Int {
        val segment = segmentAt(x) ?: return 8 * 60
        val t = ((x - segment.x) / segment.width).coerceIn(0f, 1f)
        return (segment.fromMinute + (segment.toMinute - segment.fromMinute) * t).toInt()
    }

    private fun generateAhead(until: Float) {
        while (generatedUntil < until) appendDay()
    }

    private fun appendDay() {
        val dayIndex = nextDay
        val day = days[dayIndex % days.size]
        nextDay++

        val lessons = day.lessons.sortedBy { it.start }
        val first = lessons.firstOrNull()?.start ?: (8 * 60)
        val last = lessons.lastOrNull()?.end ?: (13 * 60)

        // start: schoolyard in front of the building
        val startWidth = if (dayIndex == 0) 900f else 700f
        add(Segment(SegmentKind.SCHOOL_START, generatedUntil, startWidth, dayIndex, day.title,
            fromMinute = first - 15, toMinute = first))

        var previousEnd = first
        // later days get harder: more invented cancellations
        val cancelChance = RANDOM_CANCEL_CHANCE + min(0.18f, dayIndex * 0.04f)

        for (lesson in lessons) {
            val gapMinutes = lesson.start - previousEnd
            if (gapMinutes > 0) {
                val w = (gapMinutes * UNITS_PER_MINUTE).coerceIn(140f, 520f)
                add(Segment(SegmentKind.BREAK, generatedUntil, w, dayIndex, day.title,
                    fromMinute = previousEnd, toMinute = lesson.start))
            }

            val width = max(260f, (lesson.end - lesson.start) * UNITS_PER_MINUTE)
            val randomCancel = !lesson.cancelled && dayIndex + lessons.indexOf(lesson) > 0 &&
                random.nextFloat() < cancelChance
            val cancelled = lesson.cancelled || randomCancel
            val x = generatedUntil

            var hole: Pair<Float, Float>? = null
            if (cancelled) {
                // the hole grows with speed so it always stays jumpable but never trivial
                val speed = expectedSpeed(x)
                val gapWidth = (150f + random.nextFloat() * 70f) * (0.85f + 0.3f * speed / START_SPEED)
                val preferred = x + (width - gapWidth) * (0.35f + random.nextFloat() * 0.3f)
                val gapStart = max(preferred, lastHazardEnd + safeSpacing(x))
                if (gapStart + gapWidth <= x + width - 30f) {
                    hole = gapStart to gapStart + gapWidth
                    lastHazardEnd = gapStart + gapWidth
                }
            }

            // an invented cancellation without room for a fair hole is simply dropped;
            // a real one stays visible as a grey lesson without a hole
            val shownCancelled = lesson.cancelled || hole != null
            add(Segment(SegmentKind.LESSON, x, width, dayIndex, day.title, lesson,
                gapStart = hole?.first ?: 0f, gapEnd = hole?.second ?: 0f,
                randomCancel = randomCancel && hole != null, cancelled = shownCancelled,
                fromMinute = lesson.start, toMinute = lesson.end))

            // homework piles show up from the second lesson on, never right behind another hazard
            if (!shownCancelled && dayIndex + lessons.indexOf(lesson) > 0 && random.nextFloat() < 0.28f + dayIndex * 0.05f) {
                val books = 1 + random.nextInt(if (dayIndex > 1) 3 else 2)
                val ox = max(x + width * (0.35f + random.nextFloat() * 0.35f), lastHazardEnd + safeSpacing(x))
                if (ox + PILE_WIDTH <= x + width - 20f) {
                    obstacles += Obstacle(ox, PILE_WIDTH, 30f * books + 8f, List(books) { BOOK_COLORS[random.nextInt(BOOK_COLORS.size)] })
                    lastHazardEnd = ox + PILE_WIDTH
                }
            }

            previousEnd = lesson.end
        }

        add(Segment(SegmentKind.SCHOOL_END, generatedUntil, 900f, dayIndex, day.title,
            fromMinute = last, toMinute = last + 30))
    }

    /** Speed the dino will roughly have when it reaches world position [x]. */
    private fun expectedSpeed(x: Float): Float = min(MAX_SPEED, START_SPEED + max(0f, x) / 90f)

    /** Room after a hazard: a full jump at that speed plus a short run-up for the next one. */
    private fun safeSpacing(x: Float): Float = expectedSpeed(x) * 1.2f + 160f

    private fun add(segment: Segment) {
        segments += segment
        generatedUntil = segment.end
    }

    private fun dust(x: Float, count: Int) {
        repeat(count) {
            particles += Particle(
                x = x - 20f + random.nextFloat() * 30f,
                y = 0f,
                vx = -80f - random.nextFloat() * 160f,
                vy = 60f + random.nextFloat() * 160f,
                life = 0.45f + random.nextFloat() * 0.25f,
                size = 6f + random.nextFloat() * 8f,
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy -= 500f * dt
        }
    }
}

private val BOOK_COLORS = listOf(
    0xFFE4572E, 0xFF29335C, 0xFFF3A712, 0xFF669BBC, 0xFF8AB17D, 0xFF9B5DE5,
).map { it.toInt() }
