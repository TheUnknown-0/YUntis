package sh.lsx.untisx.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.accountFile
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.TimetableEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BaselineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountId = "https://school.example/school/max"

    @Test
    fun `change baseline round trips and clears`() {
        val entry = TimetableEntry(id = 1, date = 20261005, startTime = 800, endTime = 845)

        ChangeBaseline.save(context, accountId, mapOf("20261005" to listOf(entry)))

        assertEquals(listOf(entry), ChangeBaseline.load(context, accountId)?.weeks?.get("20261005"))

        ChangeBaseline.clear(context, accountId)

        assertNull(ChangeBaseline.load(context, accountId))
    }

    @Test
    fun `change baseline with an unknown version is ignored`() {
        val file = accountFile(context, "notif_baseline_", accountId)
        file.writeText("""{"weeks":{},"version":0}""")

        assertNull(ChangeBaseline.load(context, accountId))

        ChangeBaseline.clear(context, accountId)
    }

    @Test
    fun `news baseline round trips sets and timestamps`() {
        NewsBaseline.save(
            context,
            accountId,
            NewsBaseline.Data(messages = setOf("m:1"), messagesUpdatedAt = 42L),
        )

        val loaded = NewsBaseline.load(context, accountId)

        assertEquals(setOf("m:1"), loaded?.messages)
        assertEquals(42L, loaded?.messagesUpdatedAt)
        assertNull(loaded?.events)
    }

    @Test
    fun `news baseline is stale after 24 hours`() {
        val now = 1_000_000_000_000L

        assertFalse(NewsBaseline.isStale(0L, now))
        assertFalse(NewsBaseline.isStale(now - 60_000L, now))
        assertTrue(NewsBaseline.isStale(now - 24 * 60 * 60 * 1000L - 1, now))
    }

    @Test
    fun `news diff returns new items in order without duplicates`() {
        val fresh = NewsDiff.newItems(listOf("a", "b", "a", "c"), baseline = setOf("a")) { it }

        assertEquals(listOf("b", "c"), fresh)
    }

    @Test
    fun `news signatures prefer ids and fall back to content`() {
        assertEquals("m:5", NewsSignatures.message(DirectMessage(id = 5)))
        assertEquals("a:7", NewsSignatures.announcement(DayMessage(id = 7)))

        assertTrue(NewsSignatures.message(DirectMessage(id = 0, subject = "Hi")).startsWith("m:"))
        assertTrue(NewsSignatures.announcement(DayMessage(id = 0, body = "Hello")).startsWith("a:"))
    }
}
