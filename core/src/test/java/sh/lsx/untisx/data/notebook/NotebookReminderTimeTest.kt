package sh.lsx.untisx.data.notebook

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotebookReminderTimeTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val utc = ZoneId.of("UTC")

    @Test
    fun `reminder uses its own date not the due date`() {
        val reminder = ReminderSpec(date = 20260104, minutesOfDay = 17 * 60)
        val millis = NotebookReminderTime.triggerAt(reminder, berlin)

        assertNotNull(millis)
        val local = Instant.ofEpochMilli(millis!!).atZone(berlin)

        assertEquals(20260104, local.year * 10000 + local.monthValue * 100 + local.dayOfMonth)
        assertEquals(17, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun `dst start resolves to the local wall time`() {
        val millis = NotebookReminderTime.triggerAt(20260329, 8 * 60, berlin)

        assertNotNull(millis)
        val local = Instant.ofEpochMilli(millis!!).atZone(berlin)
        assertEquals(8, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun `the same local date is preserved across zones`() {
        val berlinMillis = NotebookReminderTime.triggerAt(20260105, 8 * 60, berlin)!!
        val utcMillis = NotebookReminderTime.triggerAt(20260105, 8 * 60, utc)!!

        val berlinLocal = Instant.ofEpochMilli(berlinMillis).atZone(berlin)
        val utcLocal = Instant.ofEpochMilli(utcMillis).atZone(utc)

        assertEquals(20260105, berlinLocal.year * 10000 + berlinLocal.monthValue * 100 + berlinLocal.dayOfMonth)
        assertEquals(20260105, utcLocal.year * 10000 + utcLocal.monthValue * 100 + utcLocal.dayOfMonth)
    }

    @Test
    fun `invalid minutes and dates return null`() {
        assertNull(NotebookReminderTime.triggerAt(20260105, -1, berlin))
        assertNull(NotebookReminderTime.triggerAt(20260105, 1440, berlin))
        assertNull(NotebookReminderTime.triggerAt(20260230, 8 * 60, berlin))
        assertNull(NotebookReminderTime.triggerAt(18991231, 8 * 60, berlin))
    }
}
