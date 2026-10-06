package sh.lsx.untisx.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class ScheduleChangeDetectorTest {

    @Test
    fun sameRoomsInDifferentOrderAreNoChange() {
        val previous = listOf(
            entry(
                teacher = listOf(teacher(TEACHER_1)),
                room = listOf(room(ROOM_1), room(ROOM_2)),
            ),
        )
        val fresh = listOf(
            entry(
                teacher = listOf(teacher(TEACHER_1)),
                room = listOf(room(ROOM_2), room(ROOM_1)),
            ),
        )

        assertTrue(detect(previous, fresh).isEmpty())
    }

    @Test
    fun reorderedParallelEntriesWithSameRoomsAreNoChange() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_2))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_2))),
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )

        assertTrue(detect(previous, fresh).isEmpty())
    }

    @Test
    fun parallelGroupsWithSwappedRoomsReportTwoRoomChanges() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_2))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_2))),
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_1))),
        )

        val changes = detect(previous, fresh)

        assertEquals(2, changes.size)
        assertTrue(changes.all { it.type == ChangeType.ROOM })
    }

    @Test
    fun elementWithSameIdButWithoutNameIsNoChange() {
        val previous = listOf(
            entry(
                teacher = listOf(TimetableElement(id = TEACHER_1, name = "Müller")),
                room = listOf(TimetableElement(id = ROOM_1, name = "R101")),
            ),
        )
        val fresh = listOf(
            entry(
                teacher = listOf(TimetableElement(id = TEACHER_1)),
                room = listOf(TimetableElement(id = ROOM_1)),
            ),
        )

        assertTrue(detect(previous, fresh).isEmpty())
    }

    @Test
    fun realRoomChangeReportsOneRoomChange() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_2))),
        )

        val changes = detect(previous, fresh)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.ROOM, changes.single().type)
    }

    @Test
    fun realTeacherChangeReportsOneTeacherChange() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_1))),
        )

        val changes = detect(previous, fresh)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.TEACHER, changes.single().type)
    }

    @Test
    fun newlyCancelledEntryReportsCancellation() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(
                teacher = listOf(teacher(TEACHER_1)),
                room = listOf(room(ROOM_1)),
                code = "cancelled",
            ),
        )

        val changes = detect(previous, fresh)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.CANCELLED, changes.single().type)
    }

    @Test
    fun unchangedWeekReportsNothing() {
        val entries = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )

        assertTrue(detect(entries, entries).isEmpty())
    }

    @Test
    fun newAndRemovedEntriesAreNotReported() {
        val entry = entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1)))

        assertTrue(detect(emptyList(), listOf(entry)).isEmpty())
        assertTrue(detect(listOf(entry), emptyList()).isEmpty())
    }

    @Test
    fun roomAndTeacherChangeReportsBoth() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_2))),
        )

        val changes = detect(previous, fresh)

        assertEquals(2, changes.size)
        assertEquals(
            setOf(ChangeType.ROOM, ChangeType.TEACHER),
            changes.map { it.type }.toSet(),
        )
    }

    @Test
    fun cancellationWinsOverRoomAndTeacherChanges() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(
                teacher = listOf(teacher(TEACHER_2)),
                room = listOf(room(ROOM_2)),
                code = "cancelled",
            ),
        )

        val changes = detect(previous, fresh)

        assertEquals(1, changes.size)
        assertEquals(ChangeType.CANCELLED, changes.single().type)
    }

    @Test
    fun filterSuppressesSelectedChangeTypes() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_2)), room = listOf(room(ROOM_2))),
        )

        assertTrue(
            detect(previous, fresh, filter = ChangeFilter(room = false, teacher = false)).isEmpty(),
        )
        assertEquals(
            listOf(ChangeType.ROOM),
            detect(previous, fresh, filter = ChangeFilter(teacher = false)).map { it.type },
        )
    }

    @Test
    fun cancellationRespectsItsFilter() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(
                teacher = listOf(teacher(TEACHER_1)),
                room = listOf(room(ROOM_1)),
                code = "cancelled",
            ),
        )

        assertTrue(detect(previous, fresh, filter = ChangeFilter(cancelled = false)).isEmpty())
    }

    @Test
    fun masterDataNamesArePreferredOverElementNames() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_2))),
        )

        val changes = detect(
            previous,
            fresh,
            rooms = mapOf(
                ROOM_1 to MasterDataItem(id = ROOM_1, name = "Altbau"),
                ROOM_2 to MasterDataItem(id = ROOM_2, name = "Neubau"),
            ),
        )

        assertEquals("Altbau", changes.single().previousValue)
        assertEquals("Neubau", changes.single().newValue)
    }

    @Test
    fun subjectAliasesAreApplied() {
        val previous = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_1))),
        )
        val fresh = listOf(
            entry(teacher = listOf(teacher(TEACHER_1)), room = listOf(room(ROOM_2))),
        )

        val changes = detect(previous, fresh, subjectAliases = mapOf("Mathe" to SubjectAlias(short = "MA")))

        assertEquals(1, changes.size)
        assertEquals("MA", changes.single().subject)
    }

    private fun detect(
        previous: List<TimetableEntry>,
        fresh: List<TimetableEntry>,
        subjects: Map<Int, MasterDataItem> = emptyMap(),
        teachers: Map<Int, MasterDataItem> = emptyMap(),
        rooms: Map<Int, MasterDataItem> = emptyMap(),
        subjectAliases: Map<String, SubjectAlias> = emptyMap(),
        filter: ChangeFilter = ChangeFilter(),
    ): List<ScheduleChange> = ScheduleChangeDetector.detect(
        previous = previous,
        fresh = fresh,
        subjects = subjects,
        teachers = teachers,
        rooms = rooms,
        subjectAliases = subjectAliases,
        filter = filter,
    )

    private fun entry(
        date: Int = DATE,
        start: Int = START,
        end: Int = END,
        teacher: List<TimetableElement> = emptyList(),
        room: List<TimetableElement> = emptyList(),
        code: String? = null,
    ): TimetableEntry = TimetableEntry(
        date = date,
        startTime = start,
        endTime = end,
        klasse = listOf(TimetableElement(id = KLASSE_1, name = "Klasse")),
        subject = listOf(TimetableElement(id = SUBJECT_1, name = "Mathe")),
        teacher = teacher,
        room = room,
        code = code,
    )

    private fun teacher(id: Int): TimetableElement = TimetableElement(id = id, name = "Lehrer $id")

    private fun room(id: Int): TimetableElement = TimetableElement(id = id, name = "Raum $id")

    private companion object {
        const val DATE = 20261005
        const val START = 800
        const val END = 845
        const val SUBJECT_1 = 1
        const val KLASSE_1 = 10
        const val TEACHER_1 = 5
        const val TEACHER_2 = 6
        const val ROOM_1 = 7
        const val ROOM_2 = 8
    }
}
