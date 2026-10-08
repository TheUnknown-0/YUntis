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
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.ui.AppRoot

class MainActivity : AppCompatActivity() {

    private var focusDate by mutableIntStateOf(0)
    private var focusScreen by mutableStateOf<String?>(null)
    private var focusNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LanguagePrefs.apply(LanguagePrefs.get(this))
        handleIntent(intent)

        setContent {
            AppRoot(
                focusDate = focusDate,
                focusScreen = focusScreen,
                focusNonce = focusNonce,
                onFocusConsumed = ::consumeFocus,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val date = intent?.getIntExtra(Notifications.EXTRA_FOCUS_DATE, 0) ?: 0
        val screen = intent?.getStringExtra(Notifications.EXTRA_FOCUS_SCREEN)

        if (date > 0) {
            focusDate = date
            focusNonce++
        }

        if (!screen.isNullOrBlank()) {
            focusScreen = screen
            focusNonce++
        }
    }

    private fun consumeFocus() {
        focusDate = 0
        focusScreen = null
    }
}
