package sh.lsx.untisx.ui.theme

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookPaperGeometryTest {
    @Test
    fun `marker swipes stay connected and alternate direction`() {
        for (size in listOf(Size(72f, 5f), Size(72f, 54f), Size(72f, 600f), Size(40f, 400f))) {
            val swipes = highlighterSwipes(size, 1f, 92)

            assertTrue(swipes.isNotEmpty())
            assertEquals(swipes, highlighterSwipes(size, 1f, 92))
            assertTrue(swipes.all { it.start.x < it.end.x && abs(it.start.y - it.end.y) > 0f })
            assertTrue(swipes.all { it.thickness > 0f && it.thickness <= minOf(size.width, size.height) })
            assertTrue(swipes.zipWithNext().all { (a, b) ->
                maxOf(b.start.y - a.start.y, b.end.y - a.end.y) < a.thickness
            })
            assertTrue(swipes.zipWithNext().all { (a, b) ->
                (a.start.y - a.end.y) * (b.start.y - b.end.y) < 0f
            })
        }
    }

    @Test
    fun `marker ignores empty bounds and invalid density`() {
        assertTrue(highlighterSwipes(Size.Zero, 1f, 0).isEmpty())
        assertTrue(highlighterSwipes(Size(72f, 0f), 1f, 0).isEmpty())
        assertTrue(highlighterSwipes(Size(72f, 100f), 0f, 0).isEmpty())
        assertTrue(highlighterSwipes(Size(72f, 100f), -1f, 0).isEmpty())
    }

    @Test
    fun `paper variation is stable for each lesson`() {
        val seeds = (0..50).map { notebookPaperSeed(it, 20261109, 800) }
        val variations = seeds.map(::paperVariation)

        seeds.forEachIndexed { index, seed -> assertEquals(variations[index], paperVariation(seed)) }
        assertTrue(variations.toSet().size > 1)
        assertTrue(variations.any { it.badgeRotation > 0f } && variations.any { it.badgeRotation < 0f })
        assertNotEquals(notebookPaperSeed(1, 20261109, 800), notebookPaperSeed(1, 20261110, 800))
    }

    @Test
    fun `fold fits small notes and reflects corner across a straight crease`() {
        for (seed in 0..30) {
            val fold = paperFold(Size(60f, 40f), 12f, seed)
            assertTrue(fold.width in 0f..60f && fold.height in 0f..40f)
            val tip = fold.foldedTip(60f, 40f)
            assertTrue(tip.x in 0f..60f && tip.y in 0f..40f)
            val a = fold.creaseStart(60f, 40f)
            val b = fold.creaseEnd(60f, 40f)

            for (p in listOf(a, b)) {
                val originalDistance = (60f - p.x) * (60f - p.x) + (40f - p.y) * (40f - p.y)
                val reflectedDistance = (tip.x - p.x) * (tip.x - p.x) + (tip.y - p.y) * (tip.y - p.y)
                assertEquals(originalDistance, reflectedDistance, 0.002f)
            }
        }
        assertEquals(0f, paperFold(Size.Zero, 12f, 5).width)
    }

    @Test
    fun `tear removes about half at a slant with a stable irregular edge`() {
        for (seed in 0..50) {
            val size = Size(220f, 130f)
            val points = paperTearPoints(size, 2f, seed)
            assertEquals(points, paperTearPoints(size, 2f, seed))
            assertEquals(0f, points.first().x)
            assertEquals(size.width, points.last().x)
            assertTrue(points.all { it.y in 0f..size.height })
            assertNotEquals(points.first().y, points.last().y)
            assertTrue(points.zipWithNext().all { (a, b) -> a.x < b.x })
            val area = points.zipWithNext().sumOf { (a, b) -> ((b.x - a.x) * (a.y + b.y) / 2f).toDouble() }
            assertTrue(area / (size.width * size.height) in 0.35..0.65)
            assertTrue(points.any { p ->
                val onLine = points.first().y + (points.last().y - points.first().y) * p.x / size.width
                abs(p.y - onLine) > 0.5f
            })
        }
    }

    @Test
    fun `tear stays below the text on the remaining strip and keeps its slant`() {
        for (seed in 0..50) {
            val size = Size(220f, 60f)
            val points = paperTearPoints(size, 2f, seed, minY = 40f)
            assertTrue(points.all { it.y in 40f..size.height })
            assertNotEquals(points.first().y, points.last().y)
        }
    }
}
