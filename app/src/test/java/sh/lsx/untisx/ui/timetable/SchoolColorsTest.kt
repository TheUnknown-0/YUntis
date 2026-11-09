package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.lsx.untisx.data.model.MasterDataItem

class SchoolColorsTest {

    private fun subject(id: Int, name: String, color: String?) = MasterDataItem(id = id, name = name, backColor = color)

    @Test
    fun `a color most subjects share counts as no color`() {
        // like the real school: lots of grey courses, a few with real colors
        val grey = "bfc9cf"
        val subjects = (1..20).map { subject(it, "K$it", grey) } +
            listOf(subject(100, "Ma", "4f8b2a"), subject(101, "Ch", "e8305a"))
        val byId = subjects.associateBy { it.id }

        val (back, _) = schoolColorsOf(emptyList(), byId, relevant = setOf("K1", "K2", "Ma", "Ch"))

        assertEquals(setOf("Ma", "Ch"), back.keys)
    }

    @Test
    fun `a color shared by three of my subjects counts as no color`() {
        // the school has many colors overall, but my courses all share one grey
        val subjects = (1..40).map { subject(it, "X$it", "%06x".format(it * 99991)) } +
            listOf(
                subject(100, "IN_L", "bfc8cf"), subject(101, "PW_G2", "bfc8cf"), subject(102, "DE_G3", "bfc8cf"),
                subject(103, "Ma", "4f8b2a"),
            )
        val (back, _) = schoolColorsOf(emptyList(), subjects.associateBy { it.id }, setOf("IN_L", "PW_G2", "DE_G3", "Ma"))
        assertEquals(setOf("Ma"), back.keys)
    }

    @Test
    fun `only relevant subjects are stored`() {
        val byId = listOf(subject(1, "Ma", "4f8b2a"), subject(2, "Other", "112233")).associateBy { it.id }
        val (back, _) = schoolColorsOf(emptyList(), byId, relevant = setOf("Ma"))
        assertEquals(setOf("Ma"), back.keys)
    }

    @Test
    fun `few subjects never produce a placeholder`() {
        assertNull(placeholderColor(listOf(1, 1, 1, 2)))
        assertEquals(1, placeholderColor(listOf(1, 1, 1, 1, 1, 2, 3)))
        assertNull(placeholderColor((1..40).toList() + listOf(7, 7, 7, 7, 7).take(4)))
    }
}
