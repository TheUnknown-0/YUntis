package sh.lsx.untisx.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notebook.NotebookReadFailure
import sh.lsx.untisx.data.prefs.AccountRemovalFailure
import sh.lsx.untisx.data.prefs.AccountRemovalResult

class NotebookAccountDeletionTest {

    @Test
    fun `empty readable account needs no warning`() {
        val state = NotebookAccountDeletionState()

        assertFalse(state.requiresWarning)
        assertTrue(state.canDelete)
    }

    @Test
    fun `content requires export or explicit acknowledgement`() {
        val state = NotebookAccountDeletionState(notebookCount = 3)

        assertTrue(state.requiresWarning)
        assertFalse(state.canDelete)
        assertTrue(state.copy(exported = true).canDelete)
    }

    @Test
    fun `successful export allows deletion`() {
        val state = NotebookAccountDeletionState(notebookCount = 3, exported = true)

        assertTrue(state.canDelete)
    }

    @Test
    fun `unreadable notebook cannot be exported and requires acknowledgement`() {
        val state = NotebookAccountDeletionState(
            notebookCount = 2,
            notebookReadFailure = NotebookReadFailure.CORRUPT,
        )

        assertFalse(state.exportAvailable)
        assertFalse(state.copy(exported = true).canDelete)
        assertTrue(state.copy(allowDeleteWithoutExport = true).canDelete)
    }

    @Test
    fun `cancelled or failed export does not enable deletion`() {
        val state = NotebookAccountDeletionState(notebookCount = 1, exported = false)

        assertFalse(state.canDelete)
    }

    @Test
    fun `removal results map to exact outcomes`() {
        assertEquals(
            NotebookAccountRemovalOutcome.REMOVED,
            notebookAccountRemovalOutcome(AccountRemovalResult.Removed),
        )
        assertEquals(
            NotebookAccountRemovalOutcome.CLEANUP_PENDING,
            notebookAccountRemovalOutcome(
                AccountRemovalResult.RemovedCleanupPending("account-a"),
            ),
        )
        assertEquals(
            NotebookAccountRemovalOutcome.RETAINED,
            notebookAccountRemovalOutcome(
                AccountRemovalResult.Retained(
                    "account-a",
                    AccountRemovalFailure.ACCOUNTS_UNAVAILABLE,
                ),
            ),
        )
        assertEquals(
            NotebookAccountRemovalOutcome.INDETERMINATE,
            notebookAccountRemovalOutcome(AccountRemovalResult.Indeterminate("account-a")),
        )
    }

    @Test
    fun `retained failures map to specific localized messages`() {
        assertEquals(
            R.string.notebook_delete_retained_accounts_unavailable,
            notebookRetainedMessage(AccountRemovalFailure.ACCOUNTS_UNAVAILABLE),
        )
        assertEquals(
            R.string.notebook_delete_retained_marker_failed,
            notebookRetainedMessage(AccountRemovalFailure.DELETION_MARKER_FAILED),
        )
        assertEquals(
            R.string.notebook_delete_retained_persistence_failed,
            notebookRetainedMessage(AccountRemovalFailure.ACCOUNT_PERSISTENCE_FAILED),
        )
    }
}
