package sh.lsx.untisx.ui.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.notebook.NotebookNotificationContract

class NotebookFocusGateTest {

    @Test
    fun `raw notebook extras without a token fail closed`() {
        var consumeCalls = 0

        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = NotebookNotificationContract.FOCUS_NOTEBOOK,
                date = 0,
                token = null,
                accountId = "account-a",
                itemId = "item-a",
            ),
        ) { _, _, _ ->
            consumeCalls++
            false
        }

        assertTrue(decision.notebookRequested)
        assertFalse(decision.notebookTrusted)
        assertNull(decision.screen)
        assertNull(decision.notebookAccountId)
        assertNull(decision.notebookItemId)
        assertEquals(1, consumeCalls)
    }

    @Test
    fun `valid token makes notebook focus trusted`() {
        var consumedToken: String? = null

        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = null,
                date = 0,
                token = "token-a",
                accountId = "account-a",
                itemId = "item-a",
            ),
        ) { token, accountId, itemId ->
            consumedToken = token
            token == "token-a" && accountId == "account-a" && itemId == "item-a"
        }

        assertEquals("token-a", consumedToken)
        assertTrue(decision.notebookTrusted)
        assertEquals(NotebookNotificationContract.FOCUS_NOTEBOOK, decision.screen)
        assertEquals("account-a", decision.notebookAccountId)
        assertEquals("item-a", decision.notebookItemId)
    }

    @Test
    fun `invalid notebook extras do not disturb normal focus`() {
        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = "events",
                date = 20261029,
                token = "bad-token",
                accountId = "account-a",
                itemId = "item-a",
            ),
        ) { _, _, _ -> false }

        assertFalse(decision.notebookTrusted)
        assertEquals("events", decision.screen)
        assertEquals(20261029, decision.date)
        assertNull(decision.notebookAccountId)
        assertNull(decision.notebookItemId)
    }

    @Test
    fun `normal focus without notebook extras does not consume`() {
        var consumeCalls = 0

        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = "events",
                date = 0,
                token = null,
                accountId = null,
                itemId = null,
            ),
        ) { _, _, _ ->
            consumeCalls++
            true
        }

        assertFalse(decision.notebookRequested)
        assertFalse(decision.notebookTrusted)
        assertEquals("events", decision.screen)
        assertEquals(0, consumeCalls)
    }

    @Test
    fun `date-only focus preserves the date without notebook access`() {
        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = null,
                date = 20261029,
                token = null,
                accountId = null,
                itemId = null,
            ),
        ) { _, _, _ -> true }

        assertFalse(decision.notebookRequested)
        assertNull(decision.screen)
        assertEquals(20261029, decision.date)
        assertNull(decision.notebookAccountId)
        assertNull(decision.notebookItemId)
    }

    @Test
    fun `valid token wins over a normal screen`() {
        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = "events",
                date = 0,
                token = "token-a",
                accountId = "account-a",
                itemId = "item-a",
            ),
        ) { _, _, _ -> true }

        assertTrue(decision.notebookTrusted)
        assertEquals(NotebookNotificationContract.FOCUS_NOTEBOOK, decision.screen)
        assertEquals("account-a", decision.notebookAccountId)
        assertEquals("item-a", decision.notebookItemId)
    }
}
