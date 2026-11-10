package sh.lsx.untisx.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.model.TimetableEntry

class ParallelLessonsTest {

    private fun lesson(
        id: Int,
        label: String,
        start: Int,
        end: Int,
        cancelled: Boolean = false,
        layoutStart: Int = 0,
        layoutWidth: Int = 1000,
    ) = LessonDisplay(
        entry = TimetableEntry(
            id = id,
            date = 20261109,
            startTime = start,
            endTime = end,
            code = if (cancelled) "cancelled" else null,
            layoutStartPosition = layoutStart,
            layoutWidth = layoutWidth,
            subject = listOf(sh.lsx.untisx.data.model.TimetableElement(id = id)),
        ),
        subject = label,
        rooms = "",
        teachers = "",
    )

    @Test
    fun `parallel courses become one card with the others hidden`() {
        val stacks = stackParallelLessons(
            listOf(
                lesson(1, "MA_L", 800, 845),
                lesson(2, "DE_L", 800, 845),
                lesson(3, "BI_L", 800, 845),
                lesson(4, "EN_L", 850, 935),
            ),
        )

        assertEquals(2, stacks.size)
        assertEquals("BI_L", stacks[0].shown.label)
        assertEquals(listOf("DE_L", "MA_L"), stacks[0].hidden.map { it.label })
        assertEquals("EN_L", stacks[1].shown.label)
        assertTrue(stacks[1].hidden.isEmpty())
    }

    @Test
    fun `a held lesson is shown before a cancelled one`() {
        val stacks = stackParallelLessons(
            listOf(
                lesson(1, "AA", 800, 845, cancelled = true),
                lesson(2, "ZZ", 800, 845),
            ),
        )

        assertEquals("ZZ", stacks.single().shown.label)
        assertEquals("AA", stacks.single().hidden.single().label)
    }

    @Test
    fun `lessons with a server layout keep their own card`() {
        val stacks = stackParallelLessons(
            listOf(
                lesson(1, "MA", 800, 845, layoutWidth = 500),
                lesson(2, "DE", 800, 845, layoutStart = 500, layoutWidth = 500),
            ),
        )

        assertEquals(2, stacks.size)
        assertTrue(stacks.all { it.hidden.isEmpty() })
    }

    @Test
    fun `a lesson made narrow only by an activity gets the full width`() {
        // the server halves the lesson because an activity runs at the same time
        val stack = stackParallelLessons(listOf(lesson(1, "IN_L", 1205, 1335, cancelled = true, layoutWidth = 500))).single()

        assertEquals(0, stack.shown.entry.layoutStartPosition)
        assertEquals(1000, stack.shown.entry.layoutWidth)
    }

    @Test
    fun `the free label avoids cancelled cards`() {
        // E_GI until 08:45, DE cancelled 08:50 to 09:35, Ch from 09:55
        val gap = FreeGap(845, 955)
        val cancelled = lesson(2, "DE_G3", 850, 935, cancelled = true).entry

        assertEquals(935 to 955, freeLabelSpot(gap, listOf(cancelled)).let { untisTimeOf(it.first) to untisTimeOf(it.second) })
        // nothing in the gap: the whole gap
        assertEquals(minutesOf(845) to minutesOf(955), freeLabelSpot(gap, emptyList()))
        // fully covered: the last minutes
        val covering = lesson(3, "X", 845, 955, cancelled = true).entry
        assertEquals(minutesOf(943) to minutesOf(955), freeLabelSpot(gap, listOf(covering)))
    }

    @Test
    fun `in a class timetable the own course is shown first`() {
        val lessons = listOf(
            lesson(1, "BI_L", 800, 845),
            lesson(2, "DE_L", 800, 845),
            lesson(3, "MA_L", 800, 845),
            lesson(4, "PH_L", 800, 845),
        )
        val stack = stackParallelLessons(lessons, ownSubjectIds = setOf(3)).single()
        assertEquals("MA_L", stack.shown.label)
        assertEquals(listOf("BI_L", "DE_L", "PH_L"), stack.hidden.map { it.label })

        // without own courses the order stays as before
        assertEquals("BI_L", stackParallelLessons(lessons).single().shown.label)
    }
}
