package sh.lsx.untisx

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import sh.lsx.untisx.data.LanguagePrefs
import sh.lsx.untisx.data.wear.WearSync
import sh.lsx.untisx.data.notebook.NotebookNotificationContract
import sh.lsx.untisx.data.notifications.NotebookNotificationFocus
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.ui.AppRoot
import sh.lsx.untisx.ui.notebook.NotebookFocusIntent
import sh.lsx.untisx.ui.notebook.resolveNotebookFocusIntent

class MainActivity : AppCompatActivity() {

    private var focusDate by mutableIntStateOf(0)
    private var focusScreen by mutableStateOf<String?>(null)
    private var focusNonce by mutableIntStateOf(0)
    private var focusNotebookItemId by mutableStateOf<String?>(null)
    private var focusNotebookAccountId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WearSync.observeAccounts(applicationContext)

        LanguagePrefs.apply(LanguagePrefs.get(this))

        val restoredState = savedInstanceState

        if (restoredState != null && restoredState.getBoolean(KEY_FOCUS_TRUSTED, false)) {
            restoreFocus(restoredState)
        } else {
            handleIntent(intent)
        }

        setContent {
            AppRoot(
                focusDate = focusDate,
                focusScreen = focusScreen,
                focusNonce = focusNonce,
                focusNotebookItemId = focusNotebookItemId,
                focusNotebookAccountId = focusNotebookAccountId,
                onFocusConsumed = ::consumeFocus,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val trusted = focusDate > 0 || focusScreen != null || focusNotebookItemId != null

        outState.putBoolean(KEY_FOCUS_TRUSTED, trusted)
        outState.putInt(KEY_FOCUS_DATE, focusDate)
        outState.putString(KEY_FOCUS_SCREEN, focusScreen)
        outState.putInt(KEY_FOCUS_NONCE, focusNonce)
        outState.putString(KEY_NOTEBOOK_ITEM, focusNotebookItemId)
        outState.putString(KEY_NOTEBOOK_ACCOUNT, focusNotebookAccountId)
    }

    private fun handleIntent(intent: Intent?) {
        val date = intent?.getIntExtra(Notifications.EXTRA_FOCUS_DATE, 0) ?: 0
        val screen = intent?.getStringExtra(Notifications.EXTRA_FOCUS_SCREEN)
        val token = intent?.getStringExtra(NotebookNotificationFocus.EXTRA_FOCUS_TOKEN)
        val notebookItemId = intent?.getStringExtra(
            NotebookNotificationContract.EXTRA_NOTEBOOK_ITEM_ID,
        )
        val notebookAccountId = intent?.getStringExtra(
            NotebookNotificationContract.EXTRA_NOTEBOOK_ACCOUNT_ID,
        )

        val decision = resolveNotebookFocusIntent(
            NotebookFocusIntent(
                screen = screen,
                date = date,
                token = token,
                accountId = notebookAccountId,
                itemId = notebookItemId,
            ),
        ) { candidateToken, candidateAccount, candidateItem ->
            runCatching {
                NotebookNotificationFocus.consume(
                    this,
                    candidateToken,
                    candidateAccount,
                    candidateItem,
                )
            }.getOrDefault(false)
        }

        val previousScreen = focusScreen

        focusNotebookItemId = decision.notebookItemId
        focusNotebookAccountId = decision.notebookAccountId

        if (decision.notebookRequested &&
            !decision.notebookTrusted &&
            previousScreen == NotebookNotificationContract.FOCUS_NOTEBOOK
        ) {
            focusScreen = null
        }

        if (!decision.notebookRequested &&
            decision.screen == null &&
            previousScreen == NotebookNotificationContract.FOCUS_NOTEBOOK
        ) {
            focusScreen = null
        }

        if (decision.date > 0) {
            focusDate = decision.date
            focusNonce++
        }

        if (!decision.screen.isNullOrBlank()) {
            focusScreen = decision.screen
            focusNonce++
        }
    }

    private fun restoreFocus(state: Bundle) {
        focusDate = state.getInt(KEY_FOCUS_DATE, 0)
        focusScreen = state.getString(KEY_FOCUS_SCREEN)
        focusNonce = state.getInt(KEY_FOCUS_NONCE, 0)
        focusNotebookItemId = state.getString(KEY_NOTEBOOK_ITEM)
        focusNotebookAccountId = state.getString(KEY_NOTEBOOK_ACCOUNT)
    }

    private fun consumeFocus() {
        focusDate = 0
        focusScreen = null
        focusNotebookItemId = null
        focusNotebookAccountId = null
    }

    private companion object {
        const val KEY_FOCUS_TRUSTED = "focus_trusted"
        const val KEY_FOCUS_DATE = "focus_date"
        const val KEY_FOCUS_SCREEN = "focus_screen"
        const val KEY_FOCUS_NONCE = "focus_nonce"
        const val KEY_NOTEBOOK_ITEM = "focus_notebook_item"
        const val KEY_NOTEBOOK_ACCOUNT = "focus_notebook_account"
    }
}
