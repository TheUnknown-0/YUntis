package sh.lsx.untisx.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookNotificationFocusTest {

    private lateinit var context: Context

    private val baseNow = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        context.getSharedPreferences(NotebookNotificationFocus.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        NotebookNotificationFocus.clock = { baseNow }
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(NotebookNotificationFocus.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        NotebookNotificationFocus.resetForTests()
    }

    private fun prefs() =
        context.getSharedPreferences(NotebookNotificationFocus.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `issued token is one-use and bound to account and item`() {
        val token = NotebookNotificationFocus.issue(context, "account-A", "item-1")

        assertTrue(token.length >= 32)
        assertFalse(NotebookNotificationFocus.consume(context, token, "account-B", "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, token, "account-A", "item-2"))
        assertTrue(NotebookNotificationFocus.consume(context, token, "account-A", "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, token, "account-A", "item-1"))
    }

    @Test
    fun `missing blank and unknown tokens are denied`() {
        assertFalse(NotebookNotificationFocus.consume(context, null, "account-A", "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, "", "account-A", "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, "unknown", "account-A", "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, "unknown", null, "item-1"))
        assertFalse(NotebookNotificationFocus.consume(context, "unknown", "account-A", null))
    }

    @Test
    fun `expired token is denied and pruned`() {
        val token = NotebookNotificationFocus.issue(context, "account-A", "item-1")

        NotebookNotificationFocus.clock = {
            baseNow + NotebookNotificationFocus.TTL_MILLIS + 1
        }

        assertFalse(NotebookNotificationFocus.consume(context, token, "account-A", "item-1"))
        assertTrue(prefs().all.isEmpty())
    }

    @Test
    fun `issuing prunes expired tokens`() {
        val first = NotebookNotificationFocus.issue(context, "account-A", "item-1")

        NotebookNotificationFocus.clock = {
            baseNow + NotebookNotificationFocus.TTL_MILLIS + 1
        }

        val second = NotebookNotificationFocus.issue(context, "account-A", "item-2")

        assertEquals(1, prefs().all.size)
        assertFalse(NotebookNotificationFocus.consume(context, first, "account-A", "item-1"))
        assertTrue(NotebookNotificationFocus.consume(context, second, "account-A", "item-2"))
    }

    @Test
    fun `token store is bounded`() {
        val tokens = (0..NotebookNotificationFocus.MAX_ENTRIES).map { index ->
            NotebookNotificationFocus.issue(context, "account-A", "item-$index")
        }

        assertTrue(prefs().all.size <= NotebookNotificationFocus.MAX_ENTRIES)

        val newest = tokens.last()
        assertTrue(
            NotebookNotificationFocus.consume(
                context,
                newest,
                "account-A",
                "item-${NotebookNotificationFocus.MAX_ENTRIES}",
            ),
        )
    }

    @Test
    fun `raw token is not stored`() {
        val token = NotebookNotificationFocus.issue(context, "account-A", "item-1")

        assertFalse(prefs().all.keys.contains(token))
        assertFalse(
            prefs().all.values.any { value ->
                (value as? String)?.contains(token) == true
            },
        )
    }
}
