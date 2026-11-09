package sh.lsx.untisx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.SubjectColorResolver.Origin
import sh.lsx.untisx.data.SubjectColorResolver.Sources

class SubjectColorResolverTest {

    private val orange = 0xFFF49F25.toInt()
    private val blue = 0xFF4F8DF5.toInt()
    private val own = 0xFF123456.toInt()

    @Test
    fun `parses untis color formats`() {
        assertEquals(orange, SubjectColorResolver.parseUntisColor("f49f25"))
        assertEquals(orange, SubjectColorResolver.parseUntisColor("#F49F25"))
        assertEquals(0x80F49F25.toInt(), SubjectColorResolver.parseUntisColor("#80F49F25"))
        assertEquals(0xFFFFAA00.toInt(), SubjectColorResolver.parseUntisColor("fa0"))
        assertNull(SubjectColorResolver.parseUntisColor(""))
        assertNull(SubjectColorResolver.parseUntisColor(null))
        assertNull(SubjectColorResolver.parseUntisColor("zzzzzz"))
        assertNull(SubjectColorResolver.parseUntisColor("12345"))
    }

    @Test
    fun `school color wins by default`() {
        val sources = Sources(own = mapOf("D" to own), school = mapOf("D" to orange))
        assertEquals(Origin.SCHOOL, SubjectColorResolver.origin("D", sources))
        assertEquals(orange, SubjectColorResolver.cardColors(listOf("D"), sources)["D"])
    }

    @Test
    fun `own color wins when school priority is off`() {
        val sources = Sources(own = mapOf("D" to own), school = mapOf("D" to orange), preferSchool = false)
        assertEquals(Origin.OWN, SubjectColorResolver.origin("D", sources))
        assertEquals(own, SubjectColorResolver.cardColors(listOf("D"), sources)["D"])
    }

    @Test
    fun `school color is still used when there is no own color and priority is off`() {
        val sources = Sources(school = mapOf("D" to orange), preferSchool = false)
        assertEquals(Origin.SCHOOL, SubjectColorResolver.origin("D", sources))
    }

    @Test
    fun `subjects without colors get distinct automatic colors`() {
        val names = listOf("Bio", "Ch", "Ph", "G", "Ku", "Mu", "Sp", "Eth")
        val colors = SubjectColorResolver.cardColors(names, Sources())
        assertEquals(names.toSet(), colors.keys)
        assertEquals(names.size, colors.values.toSet().size)
        assertTrue(colors.values.all { it in SubjectColorResolver.AUTO_PALETTE })
    }

    @Test
    fun `automatic colors avoid colors that are already taken`() {
        val taken = SubjectColorResolver.AUTO_PALETTE[0]
        val sources = Sources(own = mapOf("M" to taken))
        val colors = SubjectColorResolver.cardColors(listOf("M", "A", "B", "C"), sources)
        assertEquals(taken, colors["M"])
        assertFalse(listOf("A", "B", "C").any { colors[it] == taken })
    }

    @Test
    fun `automatic colors are stable when a new subject appears`() {
        val before = SubjectColorResolver.cardColors(listOf("Bio", "Ch", "Ph"), Sources())
        val after = SubjectColorResolver.cardColors(listOf("Bio", "Ch", "Ph", "Inf"), Sources())
        // a new subject may only take a free color, existing ones keep theirs unless they collided
        assertEquals(before["Bio"], after["Bio"])
    }

    @Test
    fun `without automatic colors only fixed colors are returned`() {
        val sources = Sources(own = mapOf("M" to own), autoColors = false)
        val colors = SubjectColorResolver.cardColors(listOf("M", "D"), sources)
        assertEquals(mapOf("M" to own), colors)
        assertEquals(Origin.DEFAULT, SubjectColorResolver.origin("D", sources))
    }

    @Test
    fun `school text color only applies together with the school card color`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val withPriority = Sources(
            own = mapOf("M" to own),
            ownText = mapOf("M" to black),
            school = mapOf("M" to blue),
            schoolText = mapOf("M" to white),
        )
        assertEquals(white, SubjectColorResolver.textColors(listOf("M"), withPriority)["M"])
        assertEquals(black, SubjectColorResolver.textColors(listOf("M"), withPriority.copy(preferSchool = false))["M"])
    }
}
