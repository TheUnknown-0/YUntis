package sh.lsx.untisx.ui.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
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

/** Purely cosmetic, one per school day. The first day is always clear. */
internal enum class Weather { CLEAR, BREEZY, RAIN, SNOW }

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
    val weather: Weather = Weather.CLEAR,
) {
    val end: Float get() = x + width
    val hasGap: Boolean get() = gapEnd > gapStart
    var counted: Boolean = false
}

/**
 * PILE and BAG stand on the ground and want a jump. A PLANE (paper plane) hovers at head
 * height: duck under it, or clear it with a high jump.
 */
internal enum class ObstacleKind { PILE, BAG, PLANE }

internal class Obstacle(
    val kind: ObstacleKind,
    val x: Float,
    val width: Float,
    val height: Float,
    /** Height of the lower edge above the ground, 0 for everything that stands. */
    val y: Float,
    val colors: IntArray,
    /** Stable per-obstacle number for small drawing variations. */
    val seed: Int,
) {
    var counted: Boolean = false
}

/** A gold star sticker to collect. [y] is the height of its center above the ground. */
internal class Pickup(val x: Float, val y: Float, val seed: Int) {
    var taken: Boolean = false
}

/** Pooled, see [DinoWorld.particles]. [y] is the height above the ground. */
internal class Particle {
    var alive = false
    var kind = 0
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var gravity = 0f
    var life = 0f
    var maxLife = 1f
    var size = 0f
    var color = 0
    var rotation = 0f
    var spin = 0f
}

/** Pooled floating score text such as "+25". */
internal class Popup {
    var alive = false
    var x = 0f
    var y = 0f
    var age = 0f
    var text = ""
    var big = false
}

/**
 * The whole game world in "units": the visible height is [WORLD_HEIGHT] units, the ground top
 * sits at [GROUND_Y]. y grows downwards like on screen; the dino stands on the ground when
 * [dinoY] is 0 and [dinoY] grows when it rises.
 *
 * No Android or Compose types in here, so everything is covered by plain unit tests.
 */
internal class DinoWorld(
    days: List<DinoDay>,
    seed: Long = System.nanoTime(),
    /** Winter days may bring snow instead of rain. Cosmetic only. */
    private val winter: Boolean = false,
) {
    companion object {
        const val WORLD_HEIGHT = 900f
        const val GROUND_Y = 690f
        const val DINO_X_FRACTION = 0.2f
        const val DINO_W = 92f
        const val DINO_H = 104f
        /** Hit box height while ducking. */
        const val DINO_DUCK_H = 58f

        const val GRAVITY = 3000f
        const val JUMP_SPEED = 1150f
        /** Gravity factor while the jump is held and the dino still rises: higher jumps. */
        const val HOLD_GRAVITY = 0.62f
        /** Upward speed kept when the jump is released early: short hops. */
        const val RELEASE_CUT = 0.55f
        /** Gravity factor while ducking in the air: a quick dive back to the ground. */
        const val DIVE_GRAVITY = 2.2f
        /** A jump pressed this long before landing still counts. */
        const val JUMP_BUFFER = 0.13f
        /** A jump pressed this long after running over an edge still counts. */
        const val COYOTE_TIME = 0.09f

        const val START_SPEED = 380f
        const val MAX_SPEED = 760f
        /** Distance after which about two thirds of the speed-up are reached. */
        const val SPEED_RAMP = 30_000f
        const val UNITS_PER_MINUTE = 11f
        const val RANDOM_CANCEL_CHANCE = 0.22f

        const val PILE_WIDTH = 64f
        const val BAG_WIDTH = 100f
        const val BAG_HEIGHT = 46f
        const val PLANE_WIDTH = 86f
        const val PLANE_HEIGHT = 38f
        const val PLANE_Y = 80f

        const val POINTS_CANCEL = 25
        const val POINTS_OBSTACLE = 10
        const val POINTS_STAR = 5
        const val POINTS_DAY = 100
        /** Every hazard in a row adds this much, up to [STREAK_CAP] times. */
        const val STREAK_STEP = 5
        const val STREAK_CAP = 5

        /** Where the dino stands when a round begins. */
        const val START_X = 300f
        private const val LEAD_IN = 2400f

        const val PARTICLE_DUST = 0
        const val PARTICLE_SPARK = 1
        const val PARTICLE_CONFETTI = 2
        const val PARTICLE_PAPER = 3

        const val EVENT_JUMP = 1
        const val EVENT_LAND = 2
        const val EVENT_STAR = 4
        const val EVENT_CLEAR = 8
        const val EVENT_DAY = 16
        const val EVENT_CRASH = 32
        const val EVENT_FALL = 64

        private const val MAX_PARTICLES = 96
        private const val MAX_POPUPS = 8
        const val POPUP_LIFE = 0.9f

        /** Running speed after [distance] units: quick at first, then flattening out. */
        fun speedAt(distance: Float): Float {
            val d = max(0f, distance)
            return START_SPEED + (MAX_SPEED - START_SPEED) * (1f - exp(-d / SPEED_RAMP))
        }

        private fun smooth(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }

    enum class Phase { READY, RUNNING, FALLING, OVER }

    enum class DeathCause { NONE, FELL, HIT }

    private val days: List<DinoDay> = days.ifEmpty { listOf(DinoDay("", emptyList())) }

    /** Level layout only, so a seed always gives the same track no matter how it is played. */
    private val random = Random(seed)

    /** Particles and other decoration. */
    private val fxRandom = Random(seed xor 0x5DEECE66DL)

    val segments = ArrayList<Segment>()
    val obstacles = ArrayList<Obstacle>()
    val pickups = ArrayList<Pickup>()

    /** Fixed pools, entries are reused. Check [Particle.alive] and [Popup.alive]. */
    val particles = Array(MAX_PARTICLES) { Particle() }
    val popups = Array(MAX_POPUPS) { Popup() }
    private var particleCursor = 0
    private var popupCursor = 0

    var phase = Phase.READY
        private set
    var deathCause = DeathCause.NONE
        private set

    /** World x of the dino. The camera follows it, see [cameraX]. */
    var dinoX = START_X
        private set
    var speed = START_SPEED
        private set
    var dinoY = 0f
        private set
    var dinoVy = 0f
        private set
    var grounded = true
        private set
    var runPhase = 0f
        private set
    var time = 0f
        private set
    var deathTime = 0f
        private set

    /** Moments (in [time]) of the last jump and landing, for squash and stretch. */
    var jumpTime = -10f
        private set
    var landTime = -10f
        private set
    /** 0..1, how hard the last landing was. */
    var landImpact = 0f
        private set

    var distance = 0f
        private set
    var jumpedCancels = 0
        private set
    var jumpedObstacles = 0
        private set
    var daysDone = 0
        private set
    var stars = 0
        private set
    /** Hazards cleared in a row. */
    var streak = 0
        private set
    var bestStreak = 0
        private set
    /** How often the player ducked, for the hint that explains the button. */
    var ducks = 0
        private set
    private var bonus = 0

    val score: Int get() = (distance / 40f).toInt() + bonus

    private var holdingJump = false
    private var duckHeld = false
    private var jumpBuffer = 0f
    private var coyote = 0f
    private var fallLimit = Float.MAX_VALUE
    private var lastStep = 0

    private var generatedUntil = 0f
    private var nextDay = 0
    private var visibleWidth = 1600f
    private var dinoScreenX = 320f
    private var lastHazardEnd = -10_000f
    private var events = 0

    /** True while the duck input is held in a running game. */
    val ducking: Boolean get() = duckHeld && phase == Phase.RUNNING

    /** World x of the left screen edge. */
    val cameraX: Float get() = dinoX - dinoScreenX

    init {
        generatedUntil = -LEAD_IN
        generateAhead(4000f)
    }

    fun dinoWorldX(): Float = dinoX

    /**
     * Tells the world how many units are visible. Only the camera moves: the dino keeps its
     * place in the world, so rotating the device in the middle of a jump changes nothing.
     */
    fun onSize(widthUnits: Float) {
        if (widthUnits == visibleWidth || widthUnits <= 0f) return
        visibleWidth = widthUnits
        dinoScreenX = (widthUnits * DINO_X_FRACTION).coerceIn(190f, 420f)
    }

    /** Returns the events since the last call (EVENT_ bits) and clears them. */
    fun pollEvents(): Int {
        val e = events
        events = 0
        return e
    }

    fun pressJump() {
        holdingJump = true
        when (phase) {
            Phase.READY -> {
                phase = Phase.RUNNING
                jump()
            }
            Phase.RUNNING -> if (grounded || coyote > 0f) jump() else jumpBuffer = JUMP_BUFFER
            else -> Unit
        }
    }

    fun releaseJump() {
        holdingJump = false
        // short tap = short hop
        if (dinoVy > 0f) dinoVy *= RELEASE_CUT
    }

    /** Duck on the ground, dive when in the air. */
    fun setDuck(down: Boolean) {
        if (down && !duckHeld && phase == Phase.RUNNING) ducks++
        duckHeld = down
    }

    private fun jump() {
        grounded = false
        coyote = 0f
        jumpBuffer = 0f
        if (dinoY < 0f) dinoY = 0f
        dinoVy = JUMP_SPEED
        jumpTime = time
        events = events or EVENT_JUMP
        dust(dinoX, 9, 1f)
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        time += dt
        updateParticles(dt)
        updatePopups(dt)

        when (phase) {
            Phase.READY -> {
                runPhase += dt * 6f
                return
            }
            Phase.OVER -> return
            Phase.FALLING -> {
                dinoVy -= GRAVITY * dt
                dinoY += dinoVy * dt
                dinoX = min(dinoX + speed * 0.25f * dt, max(dinoX, fallLimit))
                if (dinoY < -520f) {
                    phase = Phase.OVER
                    deathTime = time
                }
                return
            }
            Phase.RUNNING -> Unit
        }

        speed = speedAt(distance)
        val dx = speed * dt
        dinoX += dx
        distance += dx
        runPhase += dt * speed / 26f
        if (jumpBuffer > 0f) jumpBuffer -= dt

        // vertical movement
        if (!grounded) {
            val factor = when {
                duckHeld -> DIVE_GRAVITY
                holdingJump && dinoVy > 0f -> HOLD_GRAVITY
                else -> 1f
            }
            dinoVy -= GRAVITY * factor * dt
            dinoY += dinoVy * dt
            if (coyote > 0f) coyote -= dt
        }

        val x = dinoX
        val gap = gapUnder(x)

        if (grounded) {
            if (gap != null) {
                // ran over the edge: a short grace period in which a jump still works
                grounded = false
                coyote = COYOTE_TIME
                dinoVy = 0f
            } else {
                footsteps(x)
            }
        } else if (dinoY <= 0f && dinoVy <= 0f) {
            if (gap == null) {
                land(x)
            } else if (coyote <= 0f) {
                phase = Phase.FALLING
                deathCause = DeathCause.FELL
                fallLimit = gap.gapEnd - DINO_W * 0.45f
                streak = 0
                events = events or EVENT_FALL
                return
            }
        }

        if (hitsObstacle(x)) {
            phase = Phase.OVER
            deathCause = DeathCause.HIT
            deathTime = time
            dinoVy = 0f
            streak = 0
            events = events or EVENT_CRASH
            paperBurst(x + DINO_W * 0.3f, dinoY + 50f)
            return
        }

        collect(x)
        countPassed(x)
        generateAhead(cameraX + visibleWidth + 2400f)
        prune()
    }

    private fun land(x: Float) {
        val impact = (-dinoVy / 1500f).coerceIn(0.25f, 1f)
        dinoY = 0f
        dinoVy = 0f
        grounded = true
        landTime = time
        landImpact = impact
        events = events or EVENT_LAND
        dust(x, 4 + (impact * 5f).toInt(), impact)
        // a jump pressed just before touching down
        if (jumpBuffer > 0f) jump()
    }

    private fun footsteps(x: Float) {
        val stepIndex = floor(runPhase / Math.PI.toFloat()).toInt()
        if (stepIndex != lastStep) {
            lastStep = stepIndex
            if (!duckHeld) dust(x - 12f, 1, 0.35f)
        }
    }

    /** The hole the dino would drop into at world position [x], if any. */
    private fun gapUnder(x: Float): Segment? {
        val margin = DINO_W * 0.32f
        for (i in segments.indices) {
            val s = segments[i]
            if (s.hasGap && x - margin > s.gapStart && x + margin < s.gapEnd) return s
        }
        return null
    }

    private fun hitsObstacle(x: Float): Boolean {
        val left = x - DINO_W * 0.34f
        val right = x + DINO_W * 0.34f
        val top = dinoY + (if (duckHeld) DINO_DUCK_H else DINO_H - 4f)
        for (i in obstacles.indices) {
            val o = obstacles[i]
            if (right > o.x + 6f && left < o.x + o.width - 6f &&
                dinoY < o.y + o.height - 10f && top > o.y + 6f
            ) return true
        }
        return false
    }

    private fun collect(x: Float) {
        val top = dinoY + (if (duckHeld) DINO_DUCK_H else DINO_H) + 18f
        for (i in pickups.indices) {
            val p = pickups[i]
            if (p.taken || abs(p.x - x) > 52f || p.y < dinoY - 14f || p.y > top) continue
            p.taken = true
            stars++
            bonus += POINTS_STAR
            events = events or EVENT_STAR
            sparkle(p.x, p.y)
        }
    }

    private fun countPassed(x: Float) {
        for (i in segments.indices) {
            val segment = segments[i]
            if (segment.counted) continue
            if (segment.hasGap && x > segment.gapEnd) {
                segment.counted = true
                jumpedCancels++
                reward(POINTS_CANCEL, x)
            } else if (segment.kind == SegmentKind.SCHOOL_END && x > segment.x) {
                segment.counted = true
                daysDone++
                bonus += POINTS_DAY
                events = events or EVENT_DAY
                popup(x, 230f, "+$POINTS_DAY", big = true)
                confetti(x + 160f)
            }
        }
        for (i in obstacles.indices) {
            val obstacle = obstacles[i]
            if (!obstacle.counted && x > obstacle.x + obstacle.width + DINO_W * 0.34f) {
                obstacle.counted = true
                jumpedObstacles++
                reward(POINTS_OBSTACLE, x)
            }
        }
    }

    /** Points for a cleared hazard, a little more for every one in a row. */
    private fun reward(base: Int, x: Float) {
        streak++
        if (streak > bestStreak) bestStreak = streak
        val points = base + min(streak - 1, STREAK_CAP) * STREAK_STEP
        bonus += points
        events = events or EVENT_CLEAR
        popup(x, max(dinoY, 0f) + 170f, "+$points", big = false)
    }

    private fun prune() {
        val limit = cameraX - 700f
        while (segments.isNotEmpty() && segments[0].end < limit) segments.removeAt(0)
        while (obstacles.isNotEmpty() && obstacles[0].x + obstacles[0].width < limit) obstacles.removeAt(0)
        while (pickups.isNotEmpty() && pickups[0].x < limit) pickups.removeAt(0)
    }

    /** Segment under a world position, for the HUD and the sky. */
    fun segmentAt(x: Float): Segment? {
        for (i in segments.indices) {
            val s = segments[i]
            if (x >= s.x && x < s.end) return s
        }
        return null
    }

    /** In-game clock (minutes since midnight) at a world position. */
    fun minuteAt(x: Float): Int {
        val segment = segmentAt(x) ?: return 8 * 60
        val t = ((x - segment.x) / segment.width).coerceIn(0f, 1f)
        return (segment.fromMinute + (segment.toMinute - segment.fromMinute) * t).toInt()
    }

    /**
     * Hour of the day (4..22) the sky should show at a world position. During lessons this
     * is the real clock. After school the evening and the night pass quickly, and the way
     * to school the next morning starts before sunrise. 22 and 4 are both deep night, so
     * the jump between two days is not visible.
     */
    fun skyHourAt(x: Float): Float {
        val segment = segmentAt(x) ?: return 8f
        val t = ((x - segment.x) / segment.width).coerceIn(0f, 1f)
        val clock = (segment.fromMinute + (segment.toMinute - segment.fromMinute) * t) / 60f
        return when (segment.kind) {
            SegmentKind.SCHOOL_END -> {
                val from = (segment.fromMinute / 60f).coerceIn(7f, 17.5f)
                from + (22f - from) * smooth(t)
            }
            SegmentKind.SCHOOL_START -> {
                val to = (segment.toMinute / 60f).coerceIn(7f, 17.5f)
                if (segment.dayIndex == 0) to else 4f + (to - 4f) * smooth(t)
            }
            else -> clock.coerceIn(7f, 17.5f)
        }
    }

    /** 0..1, how much of the day's weather shows at a world position. Fades at both ends of a day. */
    fun weatherAmountAt(x: Float): Float {
        val segment = segmentAt(x) ?: return 0f
        if (segment.weather == Weather.CLEAR) return 0f
        val t = ((x - segment.x) / segment.width).coerceIn(0f, 1f)
        return when (segment.kind) {
            SegmentKind.SCHOOL_START -> smooth(t)
            SegmentKind.SCHOOL_END -> 1f - smooth(t * 1.6f)
            else -> 1f
        }
    }

    // ------------------------------------------------------------------------------------------
    // Track generation
    // ------------------------------------------------------------------------------------------

    private fun generateAhead(until: Float) {
        while (generatedUntil < until) appendDay()
    }

    private fun pickWeather(dayIndex: Int): Weather {
        if (dayIndex == 0) return Weather.CLEAR
        val roll = random.nextFloat()
        return when {
            roll < 0.34f -> Weather.CLEAR
            roll < 0.58f -> Weather.BREEZY
            winter && roll < 0.86f -> Weather.SNOW
            else -> Weather.RAIN
        }
    }

    private fun appendDay() {
        val dayIndex = nextDay
        val day = days[dayIndex % days.size]
        nextDay++

        val lessons = day.lessons.sortedBy { it.start }
        val first = lessons.firstOrNull()?.start ?: (8 * 60)
        val last = lessons.lastOrNull()?.end ?: (13 * 60)
        val weather = pickWeather(dayIndex)

        // start: schoolyard in front of the building. The very first one reaches far to the
        // left, so there is ground under the camera on every screen width.
        val startWidth = if (dayIndex == 0) 900f + LEAD_IN else 700f
        add(Segment(SegmentKind.SCHOOL_START, generatedUntil, startWidth, dayIndex, day.title,
            fromMinute = first - 15, toMinute = first, weather = weather))

        var previousEnd = first
        // later days get harder: more invented cancellations, more things in the way
        val cancelChance = RANDOM_CANCEL_CHANCE + min(0.18f, dayIndex * 0.04f)
        val groundChance = 0.26f + min(0.2f, dayIndex * 0.05f)
        val planeChance = if (dayIndex >= 1) min(0.22f, 0.08f + dayIndex * 0.04f) else 0f

        for ((index, lesson) in lessons.withIndex()) {
            val gapMinutes = lesson.start - previousEnd
            if (gapMinutes > 0) {
                val w = (gapMinutes * UNITS_PER_MINUTE).coerceIn(140f, 520f)
                add(Segment(SegmentKind.BREAK, generatedUntil, w, dayIndex, day.title,
                    fromMinute = previousEnd, toMinute = lesson.start, weather = weather))
            }

            val width = max(260f, (lesson.end - lesson.start) * UNITS_PER_MINUTE)
            // the very first lesson of a round is always calm
            val calm = dayIndex + index == 0
            val randomCancel = !lesson.cancelled && !calm && random.nextFloat() < cancelChance
            val cancelled = lesson.cancelled || randomCancel
            val x = generatedUntil

            var holeStart = 0f
            var holeEnd = 0f
            if (cancelled) {
                // the hole grows with speed so it always stays jumpable but never trivial
                val speed = speedAt(x)
                val gapWidth = (150f + random.nextFloat() * 70f) * (0.85f + 0.3f * speed / START_SPEED)
                val preferred = x + (width - gapWidth) * (0.35f + random.nextFloat() * 0.3f)
                val gapStart = max(preferred, lastHazardEnd + safeSpacing(x))
                if (gapStart + gapWidth <= x + width - 30f) {
                    holeStart = gapStart
                    holeEnd = gapStart + gapWidth
                    lastHazardEnd = holeEnd
                    // an arc of stars that shows the way over
                    val mid = (holeStart + holeEnd) / 2f
                    addStar(mid - gapWidth * 0.46f, 165f)
                    addStar(mid, 225f)
                    addStar(mid + gapWidth * 0.46f, 165f)
                }
            }
            val hasHole = holeEnd > holeStart

            // an invented cancellation without room for a fair hole is simply dropped;
            // a real one stays visible as a grey lesson without a hole
            val shownCancelled = lesson.cancelled || hasHole
            add(Segment(SegmentKind.LESSON, x, width, dayIndex, day.title, lesson,
                gapStart = holeStart, gapEnd = holeEnd,
                randomCancel = randomCancel && hasHole, cancelled = shownCancelled,
                fromMinute = lesson.start, toMinute = lesson.end, weather = weather))

            // things in the way show up from the second lesson on, never right behind another hazard
            if (!shownCancelled && !calm) {
                val roll = random.nextFloat()
                val at = x + width * (0.35f + random.nextFloat() * 0.35f)
                when {
                    roll < groundChance -> placeGroundObstacle(at, x, width, dayIndex)
                    roll < groundChance + planeChance -> placePlane(at, x, width)
                    roll > 0.62f -> {
                        // a free lesson: a row of stars, on the ground or as a small optional hop
                        val high = random.nextFloat() < 0.35f
                        val from = x + width * 0.3f
                        for (k in 0 until 3) addStar(from + k * 70f, if (high) 150f else 48f)
                    }
                }
            }

            previousEnd = lesson.end
        }

        add(Segment(SegmentKind.SCHOOL_END, generatedUntil, 900f, dayIndex, day.title,
            fromMinute = last, toMinute = last + 30, weather = weather))
    }

    private fun placeGroundObstacle(preferred: Float, x: Float, width: Float, dayIndex: Int) {
        val bag = random.nextFloat() < 0.28f
        val books = 1 + random.nextInt(if (dayIndex > 1) 3 else 2)
        val obstacleSeed = random.nextInt(1 shl 20)
        val colors = IntArray(if (bag) 1 else books) { BOOK_COLORS[random.nextInt(BOOK_COLORS.size)] }
        val w = if (bag) BAG_WIDTH else PILE_WIDTH
        val h = if (bag) BAG_HEIGHT else 30f * books + 8f
        val ox = max(preferred, lastHazardEnd + safeSpacing(x))
        if (ox + w > x + width - 20f) return
        obstacles += Obstacle(if (bag) ObstacleKind.BAG else ObstacleKind.PILE, ox, w, h, 0f, colors, obstacleSeed)
        lastHazardEnd = ox + w
        addStar(ox + w / 2f, h + 120f)
    }

    private fun placePlane(preferred: Float, x: Float, width: Float) {
        val obstacleSeed = random.nextInt(1 shl 20)
        val ox = max(preferred, lastHazardEnd + safeSpacing(x))
        if (ox + PLANE_WIDTH > x + width - 20f) return
        obstacles += Obstacle(ObstacleKind.PLANE, ox, PLANE_WIDTH, PLANE_HEIGHT, PLANE_Y, IntArray(0), obstacleSeed)
        lastHazardEnd = ox + PLANE_WIDTH
        // a reward for staying low
        addStar(ox + PLANE_WIDTH / 2f - 40f, 34f)
        addStar(ox + PLANE_WIDTH / 2f + 40f, 34f)
    }

    private fun addStar(x: Float, y: Float) {
        pickups += Pickup(x, y, pickups.size * 31 + x.toInt())
    }

    /** Room after a hazard: a full jump at that speed plus a short run-up for the next one. */
    private fun safeSpacing(x: Float): Float = speedAt(x) * 1.2f + 160f

    private fun add(segment: Segment) {
        segments += segment
        generatedUntil = segment.end
    }

    // ------------------------------------------------------------------------------------------
    // Decoration: particles and score texts. Nothing in here changes the game.
    // ------------------------------------------------------------------------------------------

    private fun spawn(): Particle {
        val p = particles[particleCursor]
        particleCursor = (particleCursor + 1) % particles.size
        p.alive = true
        p.rotation = 0f
        p.spin = 0f
        p.color = 0
        return p
    }

    private fun dust(x: Float, count: Int, strength: Float) {
        repeat(count) {
            val p = spawn()
            p.kind = PARTICLE_DUST
            p.x = x - 24f + fxRandom.nextFloat() * 36f
            p.y = 2f
            p.vx = (-60f - fxRandom.nextFloat() * 170f) * (0.5f + strength * 0.5f)
            p.vy = (40f + fxRandom.nextFloat() * 150f) * (0.4f + strength * 0.6f)
            p.gravity = 420f
            p.maxLife = 0.4f + fxRandom.nextFloat() * 0.3f
            p.life = p.maxLife
            p.size = (5f + fxRandom.nextFloat() * 8f) * (0.6f + strength * 0.4f)
        }
    }

    private fun sparkle(x: Float, y: Float) {
        repeat(7) {
            val p = spawn()
            p.kind = PARTICLE_SPARK
            p.x = x
            p.y = y
            p.vx = -160f + fxRandom.nextFloat() * 320f + speed * 0.5f
            p.vy = -120f + fxRandom.nextFloat() * 330f
            p.gravity = 520f
            p.maxLife = 0.35f + fxRandom.nextFloat() * 0.3f
            p.life = p.maxLife
            p.size = 4f + fxRandom.nextFloat() * 5f
            p.rotation = fxRandom.nextFloat() * 90f
            p.spin = -300f + fxRandom.nextFloat() * 600f
        }
    }

    private fun confetti(x: Float) {
        repeat(26) {
            val p = spawn()
            p.kind = PARTICLE_CONFETTI
            p.x = x - 150f + fxRandom.nextFloat() * 300f
            p.y = 300f + fxRandom.nextFloat() * 60f
            p.vx = -120f + fxRandom.nextFloat() * 240f + speed * 0.3f
            p.vy = 80f + fxRandom.nextFloat() * 260f
            p.gravity = 380f
            p.maxLife = 1.3f + fxRandom.nextFloat() * 0.8f
            p.life = p.maxLife
            p.size = 7f + fxRandom.nextFloat() * 6f
            p.color = BOOK_COLORS[fxRandom.nextInt(BOOK_COLORS.size)]
            p.rotation = fxRandom.nextFloat() * 360f
            p.spin = -420f + fxRandom.nextFloat() * 840f
        }
    }

    private fun paperBurst(x: Float, y: Float) {
        repeat(12) {
            val p = spawn()
            p.kind = PARTICLE_PAPER
            p.x = x
            p.y = y
            p.vx = -260f + fxRandom.nextFloat() * 560f
            p.vy = 180f + fxRandom.nextFloat() * 420f
            p.gravity = 700f
            p.maxLife = 1.0f + fxRandom.nextFloat() * 0.7f
            p.life = p.maxLife
            p.size = 9f + fxRandom.nextFloat() * 7f
            p.rotation = fxRandom.nextFloat() * 360f
            p.spin = -360f + fxRandom.nextFloat() * 720f
        }
    }

    private fun popup(x: Float, y: Float, text: String, big: Boolean) {
        val p = popups[popupCursor]
        popupCursor = (popupCursor + 1) % popups.size
        p.alive = true
        p.x = x
        p.y = y
        p.age = 0f
        p.text = text
        p.big = big
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy -= p.gravity * dt
            p.rotation += p.spin * dt
            // paper and confetti come to rest on the ground
            if (p.y < 0f && p.kind >= PARTICLE_CONFETTI) {
                p.y = 0f
                p.vy = 0f
                p.vx *= 0.8f
                p.spin *= 0.6f
            }
        }
    }

    private fun updatePopups(dt: Float) {
        for (p in popups) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= POPUP_LIFE) p.alive = false
        }
    }
}

private val BOOK_COLORS = longArrayOf(
    0xFFE4572E, 0xFF29335C, 0xFFF3A712, 0xFF669BBC, 0xFF8AB17D, 0xFF9B5DE5,
).map { it.toInt() }
