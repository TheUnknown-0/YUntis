package sh.lsx.untisx.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DinoWorldTest {

    private val dt = 1f / 120f

    private fun day(cancelled: Set<Int> = emptySet()) = DinoDay(
        "Testtag",
        listOf(480 to 525, 530 to 575, 595 to 640, 645 to 690, 710 to 755, 760 to 805).mapIndexed { i, (s, e) ->
            DinoLesson("F$i", "Fach $i", 0xFF8EC5FC.toInt(), 0xFF111111.toInt(), s, e, cancelled = i in cancelled)
        },
    )

    /** A world that is already running, with the dino back on the ground after the start hop. */
    private fun runningWorld(days: List<DinoDay>, seed: Long): DinoWorld {
        val world = DinoWorld(days, seed = seed)
        world.onSize(1100f)
        world.pressJump()
        world.releaseJump()
        while (!world.grounded) world.step(dt)
        return world
    }

    /** Steps until the dino is [distance] units in front of [x]. */
    private fun runTo(world: DinoWorld, x: Float, distance: Float = 0f) {
        while (world.dinoWorldX() < x - distance && world.phase == DinoWorld.Phase.RUNNING) world.step(dt)
    }

    /** Jumps (holding) over holes and things on the ground, ducks under paper planes. */
    private fun playWithBot(world: DinoWorld, seconds: Float): Boolean {
        world.onSize(1100f)
        world.pressJump()
        world.releaseJump()
        var holding = false
        var ducking = false
        var pressedAt = -1f
        var t = 0f
        while (t < seconds) {
            val x = world.dinoWorldX()
            val lead = world.speed * 0.30f
            val gapAhead = world.segments.any { it.hasGap && it.gapStart - x in 0f..lead }
            val pileAhead = world.obstacles.any { it.kind != ObstacleKind.PLANE && it.x - x in 0f..(world.speed * 0.22f) }
            val planeNear = world.obstacles.any {
                it.kind == ObstacleKind.PLANE && x > it.x - world.speed * 0.3f && x < it.x + it.width + 60f
            }
            val onGround = world.grounded

            if (planeNear != ducking) {
                ducking = planeNear
                world.setDuck(ducking)
            }
            // let go near the top of the jump, and always once back on the ground
            if (holding && (world.dinoY > 300f || (onGround && t - pressedAt > 0.05f))) {
                world.releaseJump()
                holding = false
            }
            if ((gapAhead || pileAhead) && onGround && !holding) {
                world.pressJump()
                holding = true
                pressedAt = t
            }
            world.step(dt)
            if (world.phase == DinoWorld.Phase.OVER || world.phase == DinoWorld.Phase.FALLING) return false
            t += dt
        }
        return true
    }

    @Test
    fun `real cancellations become holes when there is room`() {
        // the first lesson has nothing in front of it, so its cancellation must be a hole
        val world = DinoWorld(listOf(day(cancelled = setOf(0))), seed = 1)
        val lesson = world.segments.first { it.lesson?.label == "F0" }
        assertTrue(lesson.cancelled)
        assertTrue(lesson.hasGap)
        assertTrue(!lesson.randomCancel)
        assertTrue(lesson.gapStart > lesson.x && lesson.gapEnd < lesson.end)
    }

    @Test
    fun `real cancellations always show as cancelled`() {
        for (seed in 1L..20L) {
            val world = DinoWorld(listOf(day(cancelled = setOf(1, 2, 3))), seed = seed)
            val lessons = world.segments.filter { it.lesson?.label in setOf("F1", "F2", "F3") && it.dayIndex == 0 }
            assertEquals(3, lessons.size)
            assertTrue(lessons.all { it.cancelled })
        }
    }

    @Test
    fun `hazards always leave room for a full jump`() {
        for (seed in 1L..30L) {
            val world = DinoWorld(listOf(day(cancelled = setOf(1, 2)), day()), seed = seed)
            val hazards = (world.segments.filter { it.hasGap }.map { it.gapStart to it.gapEnd } +
                world.obstacles.map { it.x to it.x + it.width }).sortedBy { it.first }
            hazards.zipWithNext().forEach { (a, b) ->
                val speed = DinoWorld.speedAt(b.first)
                assertTrue("seed $seed: ${b.first - a.second} too close at speed $speed", b.first - a.second >= speed * 1.2f)
            }
        }
    }

    @Test
    fun `the first lesson of a round is calm`() {
        for (seed in 1L..30L) {
            val world = DinoWorld(listOf(day()), seed = seed)
            val first = world.segments.first { it.kind == SegmentKind.LESSON }
            assertFalse("seed $seed", first.hasGap)
            assertTrue("seed $seed", world.obstacles.none { it.x >= first.x && it.x < first.end })
            // and there is ground far to the left of the start, for every screen width
            assertTrue(world.segments.first().x < DinoWorld.START_X - 2000f)
        }
    }

    @Test
    fun `the same seed gives the same track however it is played`() {
        val days = listOf(day(cancelled = setOf(2)), day())
        val idle = DinoWorld(days, seed = 7)
        val played = DinoWorld(days, seed = 7)
        playWithBot(played, seconds = 6f)
        val from = played.segments.first().x
        fun layout(world: DinoWorld) = world.segments.filter { it.x >= from }.map { Triple(it.x, it.gapStart, it.gapEnd) }
        // the played world has generated further ahead, compare what both have
        val common = minOf(layout(idle).size, layout(played).size)
        assertTrue(common >= 3)
        assertEquals(layout(idle).take(common), layout(played).take(common))
    }

    @Test
    fun `speed rises smoothly and never passes the maximum`() {
        assertEquals(DinoWorld.START_SPEED, DinoWorld.speedAt(0f), 0.001f)
        var previous = DinoWorld.speedAt(0f)
        var d = 500f
        while (d < 400_000f) {
            val speed = DinoWorld.speedAt(d)
            // far out the curve is flat within float precision
            assertTrue(speed >= previous)
            if (d < 100_000f) assertTrue(speed > previous)
            assertTrue(speed <= DinoWorld.MAX_SPEED)
            previous = speed
            d += 500f
        }
        assertTrue(DinoWorld.speedAt(400_000f) > DinoWorld.MAX_SPEED - 1f)
    }

    @Test
    fun `standing still in front of a hole means falling`() {
        val world = DinoWorld(listOf(day(cancelled = setOf(1))), seed = 2)
        world.onSize(1100f)
        world.pressJump()
        world.releaseJump()
        // the start tap is the only jump, after that the dino just runs
        repeat(120 * 30) { world.step(dt) }
        assertEquals(DinoWorld.Phase.OVER, world.phase)
        assertEquals(DinoWorld.DeathCause.FELL, world.deathCause)
    }

    @Test
    fun `holding the jump goes higher than a tap`() {
        fun peak(hold: Boolean): Float {
            val world = runningWorld(listOf(day()), seed = 3)
            world.pressJump()
            if (!hold) world.releaseJump()
            var top = 0f
            do {
                world.step(dt)
                top = maxOf(top, world.dinoY)
            } while (!world.grounded)
            return top
        }
        val tap = peak(hold = false)
        val held = peak(hold = true)
        assertTrue("tap $tap, held $held", held > tap * 2f)
        assertTrue(tap > 60f)
    }

    @Test
    fun `a jump pressed just before landing is not lost`() {
        val world = runningWorld(listOf(day()), seed = 3)
        world.pressJump()
        world.releaseJump()
        // wait until the dino is on its way down and close to the ground
        while (!(world.dinoVy < 0f && world.dinoY < 30f)) world.step(dt)
        assertFalse(world.grounded)
        world.pressJump()
        var jumpedAgain = false
        repeat(40) {
            val before = world.jumpTime
            world.step(dt)
            if (world.jumpTime > before) jumpedAgain = true
        }
        assertTrue(jumpedAgain)
        assertTrue(world.dinoY > 30f)
    }

    @Test
    fun `a jump pressed much too early is forgotten`() {
        val world = runningWorld(listOf(day()), seed = 3)
        world.pressJump()
        // at the top of a full jump the ground is far more than the buffer away
        while (world.dinoVy > 0f) world.step(dt)
        world.releaseJump()
        world.pressJump()
        world.releaseJump()
        while (!world.grounded) world.step(dt)
        repeat(10) { world.step(dt) }
        assertTrue(world.grounded)
    }

    @Test
    fun `a jump shortly after the edge still works`() {
        val world = runningWorld(listOf(day(cancelled = setOf(0))), seed = 1)
        val hole = world.segments.first { it.hasGap }
        runTo(world, hole.gapStart)
        // run on until the ground is gone
        while (world.grounded) world.step(dt)
        assertEquals(DinoWorld.Phase.RUNNING, world.phase)
        world.step(dt)
        world.step(dt)
        world.pressJump()
        repeat(150) { world.step(dt) }
        assertEquals(DinoWorld.Phase.RUNNING, world.phase)
        assertTrue(world.dinoWorldX() > hole.gapEnd)
        assertEquals(1, world.jumpedCancels)
    }

    @Test
    fun `the grace period after the edge is short`() {
        val world = runningWorld(listOf(day(cancelled = setOf(0))), seed = 1)
        val hole = world.segments.first { it.hasGap }
        runTo(world, hole.gapStart)
        while (world.grounded) world.step(dt)
        repeat(30) { world.step(dt) }
        world.pressJump()
        repeat(240) { world.step(dt) }
        assertEquals(DinoWorld.Phase.OVER, world.phase)
    }

    /** Finds a seed whose track has a paper plane and returns the running world with it. */
    private fun worldWithPlane(): Pair<DinoWorld, Obstacle> {
        // one lesson per day: the first day is calm, so the plane can be the very first hazard
        val short = DinoDay("Kurz", day().lessons.take(1))
        for (seed in 1L..200L) {
            val world = DinoWorld(listOf(short), seed = seed)
            val plane = world.obstacles.firstOrNull { it.kind == ObstacleKind.PLANE } ?: continue
            val before = world.obstacles.count { it.x < plane.x } + world.segments.count { it.hasGap && it.gapStart < plane.x }
            if (before == 0) return world to plane
        }
        throw AssertionError("no seed with a paper plane as first hazard")
    }

    @Test
    fun `no paper planes on the first day`() {
        // short days, so the second one is part of the track that exists from the start
        val short = DinoDay("Kurz", day().lessons.take(2))
        for (seed in 1L..30L) {
            val world = DinoWorld(listOf(short), seed = seed)
            val secondDay = world.segments.first { it.dayIndex == 1 }.x
            assertTrue("seed $seed", world.obstacles.none { it.kind == ObstacleKind.PLANE && it.x < secondDay })
        }
    }

    @Test
    fun `ducking gets under a paper plane and running into it does not`() {
        val (ducked, plane) = worldWithPlane()
        ducked.onSize(1100f)
        ducked.pressJump()
        ducked.releaseJump()
        runTo(ducked, plane.x, distance = 150f)
        assertEquals(DinoWorld.Phase.RUNNING, ducked.phase)
        ducked.setDuck(true)
        assertTrue(ducked.ducking)
        runTo(ducked, plane.x + plane.width + 80f)
        ducked.setDuck(false)
        assertEquals(DinoWorld.Phase.RUNNING, ducked.phase)
        assertEquals(1, ducked.jumpedObstacles)

        val (upright, samePlane) = worldWithPlane()
        upright.onSize(1100f)
        upright.pressJump()
        upright.releaseJump()
        runTo(upright, samePlane.x + samePlane.width + 80f)
        assertEquals(DinoWorld.Phase.OVER, upright.phase)
        assertEquals(DinoWorld.DeathCause.HIT, upright.deathCause)
    }

    @Test
    fun `ducking in the air dives`() {
        fun airTime(dive: Boolean): Int {
            val world = runningWorld(listOf(day()), seed = 3)
            world.pressJump()
            var steps = 0
            do {
                if (dive && world.dinoVy < 0f) world.setDuck(true)
                world.step(dt)
                steps++
            } while (!world.grounded)
            return steps
        }
        assertTrue(airTime(dive = true) < airTime(dive = false))
    }

    @Test
    fun `resizing the screen moves the camera and not the dino`() {
        val world = runningWorld(listOf(day()), seed = 3)
        repeat(60) { world.step(dt) }
        val x = world.dinoWorldX()
        val y = world.dinoY
        world.onSize(2200f)
        assertEquals(x, world.dinoWorldX(), 0f)
        assertEquals(y, world.dinoY, 0f)
        assertTrue(world.cameraX < x)
        world.onSize(950f)
        assertEquals(x, world.dinoWorldX(), 0f)
    }

    @Test
    fun `stars and cleared hazards score, streaks pay more`() {
        val world = DinoWorld(listOf(day(cancelled = setOf(3)), day()), seed = 4)
        assertTrue(playWithBot(world, seconds = 40f))
        assertTrue(world.stars > 0)
        assertTrue(world.streak >= 2)
        assertEquals(world.streak, world.bestStreak)
        val cleared = world.jumpedCancels + world.jumpedObstacles
        val plain = (world.distance / 40f).toInt() +
            world.jumpedCancels * DinoWorld.POINTS_CANCEL + world.jumpedObstacles * DinoWorld.POINTS_OBSTACLE +
            world.stars * DinoWorld.POINTS_STAR + world.daysDone * DinoWorld.POINTS_DAY
        assertTrue(cleared >= 2)
        assertTrue("score ${world.score}, without streaks $plain", world.score > plain)
        assertTrue(world.pollEvents() and DinoWorld.EVENT_CLEAR != 0)
        assertEquals(0, world.pollEvents())
    }

    @Test
    fun `every hole and pile can be cleared`() {
        // many random worlds, a simple bot must survive two full school days in each
        for (seed in 1L..30L) {
            val world = DinoWorld(listOf(day(cancelled = setOf(3)), day()), seed = seed)
            val survived = playWithBot(world, seconds = 70f)
            val x = world.dinoWorldX()
            val near = world.segments.filter { it.hasGap && kotlin.math.abs(it.gapStart - x) < 600f }
                .joinToString { "gap ${it.gapStart - x}..${it.gapEnd - x} (w=${it.gapEnd - it.gapStart})" }
            val piles = world.obstacles.filter { kotlin.math.abs(it.x - x) < 600f }
                .joinToString { "${it.kind} ${it.x - x} h=${it.height}" }
            assertTrue(
                "seed $seed: ${world.phase} at ${world.score} points, speed=${world.speed}, y=${world.dinoY}, $near | $piles",
                survived,
            )
            assertTrue("seed $seed: no hole jumped", world.jumpedCancels > 0)
        }
    }

    @Test
    fun `clock follows the lessons`() {
        val world = DinoWorld(listOf(day()), seed = 3)
        val first = world.segments.first { it.kind == SegmentKind.LESSON }
        assertEquals(480, world.minuteAt(first.x + 1f))
        assertEquals(502, world.minuteAt(first.x + first.width / 2f))
    }

    @Test
    fun `night falls after school and the next morning starts dark`() {
        val world = DinoWorld(listOf(DinoDay("Kurz", day().lessons.take(2))), seed = 3)
        val lesson = world.segments.first { it.kind == SegmentKind.LESSON }
        assertEquals(8f, world.skyHourAt(lesson.x + 1f), 0.05f)
        val end = world.segments.first { it.kind == SegmentKind.SCHOOL_END }
        assertEquals(22f, world.skyHourAt(end.end - 1f), 0.05f)
        val nextStart = world.segments.first { it.kind == SegmentKind.SCHOOL_START && it.dayIndex == 1 }
        assertEquals(4f, world.skyHourAt(nextStart.x + 1f), 0.05f)
        assertEquals(8f, world.skyHourAt(nextStart.end - 1f), 0.05f)
        // the first day is always clear, and clear weather never shows
        assertEquals(Weather.CLEAR, world.segments.first().weather)
        assertEquals(0f, world.weatherAmountAt(lesson.x + 1f), 0f)
    }
}
