package sh.lsx.untisx.data.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeHistoryTest {
    private fun record(at: Long, current: String = "B204") =
        ChangeRecord(detectedAt = at, type = "ROOM", date = 20261111, startTime = 955, endTime = 1040, subject = "M", previous = "A101", current = current)

    @Test
    fun `the same change noticed twice is kept once, newest first`() {
        val first = ChangeHistory.merge(emptyList(), listOf(record(1)))
        val again = ChangeHistory.merge(first, listOf(record(2), record(3, current = "C1")))

        assertEquals(listOf(3L, 1L), again.map { it.detectedAt })
    }

    @Test
    fun `history is capped`() {
        val many = (1..(ChangeHistory.LIMIT + 50)).map { record(it.toLong(), current = "R$it") }
        val merged = ChangeHistory.merge(emptyList(), many)

        assertEquals(ChangeHistory.LIMIT, merged.size)
        assertEquals((ChangeHistory.LIMIT + 50).toLong(), merged.first().detectedAt)
    }
}
