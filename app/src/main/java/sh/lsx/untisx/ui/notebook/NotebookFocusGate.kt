package sh.lsx.untisx.ui.notebook

import sh.lsx.untisx.data.notebook.NotebookNotificationContract

data class NotebookFocusIntent(
    val screen: String?,
    val date: Int,
    val token: String?,
    val accountId: String?,
    val itemId: String?,
)

data class NotebookFocusDecision(
    val screen: String?,
    val date: Int,
    val notebookItemId: String?,
    val notebookAccountId: String?,
    val notebookRequested: Boolean,
    val notebookTrusted: Boolean,
)

fun resolveNotebookFocusIntent(
    intent: NotebookFocusIntent,
    consume: (token: String?, accountId: String?, itemId: String?) -> Boolean,
): NotebookFocusDecision {
    val notebookRequested = intent.token != null ||
        intent.accountId != null ||
        intent.itemId != null ||
        intent.screen == NotebookNotificationContract.FOCUS_NOTEBOOK

    val trusted = notebookRequested && consume(intent.token, intent.accountId, intent.itemId)

    val screen = when {
        trusted -> NotebookNotificationContract.FOCUS_NOTEBOOK
        intent.screen == NotebookNotificationContract.FOCUS_NOTEBOOK -> null
        else -> intent.screen
    }

    return NotebookFocusDecision(
        screen = screen,
        date = intent.date,
        notebookItemId = if (trusted) intent.itemId else null,
        notebookAccountId = if (trusted) intent.accountId else null,
        notebookRequested = notebookRequested,
        notebookTrusted = trusted,
    )
}
