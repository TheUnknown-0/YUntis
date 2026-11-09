package sh.lsx.untisx.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DinoWorldTest {

    private fun day(cancelled: Set<Int> = emptySet()) = DinoDay(
        "Testtag",
        listOf(480 to 525, 530 to 575, 595 to 640, 645 to 690, 710 to 755, 760 to 805).mapIndexed { i, (s, e) ->
            DinoLesson("F$i", "Fach $i", 0xFF8EC5FC.toInt(), 0xFF111111.toInt(), s, e, cancelled = i in cancelled)
        },
    )

    /** Jumps (holding) when a hole or a homework pile is close ahead. */
    private fun playWithBot(world: DinoWorld, seconds: Float): Boolean {
        world.onSize(1100f)
        world.pressJump()
        world.releaseJump()
        var holding = false
        var pressedAt = -1f
        var t = 0f
        val dt = 1f / 120f
        while (t < seconds) {
            val x = world.dinoWorldX()
            val lead = world.speed * 0.30f
            val gapAhead = world.segments.any { it.hasGap && it.gapStart - x in 0f..lead }
            val pileAhead = world.obstacles.any { it.x - x in 0f..(world.speed * 0.22f) }
            val onGround = world.dinoY <= 0.5f

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
                val speed = minOf(DinoWorld.MAX_SPEED, DinoWorld.START_SPEED + b.first / 90f)
                assertTrue("seed $seed: ${b.first - a.second} too close at speed $speed", b.first - a.second >= speed * 1.2f)
            }
        }
    }

    @Test
    fun `standing still in front of a hole means falling`() {
        val world = DinoWorld(listOf(day(cancelled = setOf(1))), seed = 2)
        world.onSize(1100f)
        world.pressJump()
        world.releaseJump()
        // the start tap is the only jump, after that the dino just runs
        repeat(120 * 30) { world.step(1f / 120f) }
        assertEquals(DinoWorld.Phase.OVER, world.phase)
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
                .joinToString { "pile ${it.x - x} h=${it.height}" }
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
}
