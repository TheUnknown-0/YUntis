package sh.lsx.untisx

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.AuthData
import sh.lsx.untisx.data.AuthPrefs
import sh.lsx.untisx.data.HapticsPrefs
import sh.lsx.untisx.data.ThemePrefs
import sh.lsx.untisx.ui.screens.LoginScreen
import sh.lsx.untisx.ui.screens.OnboardingScreen
import sh.lsx.untisx.ui.screens.SettingsScreen
import sh.lsx.untisx.ui.screens.TimetableScreen
import sh.lsx.untisx.ui.theme.UntisXTheme
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.LocalHapticsEnabled

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

private fun readOnboardingDone(context: Context): Boolean =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(UntisXApp.KEY_ONBOARDING_DONE, false)

private fun prefsEdit(context: Context) =
    context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE).edit()

private enum class Tab(
    val label: String,
    val icon: ImageVector,
) {
    TIMETABLE("Timetable", Icons.Filled.DateRange),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    var onboardingDone by remember { mutableStateOf(readOnboardingDone(context)) }
    var auth by remember { mutableStateOf(AuthPrefs.load(context)) }
    var hapticsEnabled by remember { mutableStateOf(HapticsPrefs.isEnabled(context)) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in ThemePrefs.KEYS) {
                theme = ThemePrefs.get(context)
            }

            if (key == UntisXApp.KEY_ONBOARDING_DONE) {
                onboardingDone = readOnboardingDone(context)
            }

            if (key == AuthPrefs.KEY_BLOB) {
                auth = AuthPrefs.load(context)
            }

            if (key == HapticsPrefs.KEY) {
                hapticsEnabled = HapticsPrefs.isEnabled(context)
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveTheme(newTheme: AppTheme) = ThemePrefs.save(context, newTheme)

    CompositionLocalProvider(LocalHapticsEnabled provides hapticsEnabled) {
        UntisXTheme(mode = theme.mode, preset = theme.preset) {
            val currentAuth = auth

            when {
                !onboardingDone -> OnboardingScreen(
                    theme = theme,
                    onThemeChange = ::saveTheme,
                    loggedIn = currentAuth != null,
                    onFinish = {
                        prefsEdit(context).putBoolean(UntisXApp.KEY_ONBOARDING_DONE, true).apply()
                    },
                )

                currentAuth == null -> Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    LoginScreen(modifier = Modifier.padding(padding))
                }

                else -> MainApp(
                    auth = currentAuth,
                    theme = theme,
                    onThemeChange = ::saveTheme,
                    hapticsEnabled = hapticsEnabled,
                    onHapticsChange = { HapticsPrefs.setEnabled(context, it) },
                    onLogout = { AuthPrefs.clear(context) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    auth: AuthData,
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    var currentTab by remember { mutableStateOf(Tab.TIMETABLE) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentTab.label) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == currentTab,
                        onClick = Haptics.haptic { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (currentTab) {
            Tab.TIMETABLE -> TimetableScreen(modifier = Modifier.padding(padding))

            Tab.SETTINGS -> SettingsScreen(
                auth = auth,
                theme = theme,
                onThemeChange = onThemeChange,
                hapticsEnabled = hapticsEnabled,
                onHapticsChange = onHapticsChange,
                onLogout = onLogout,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
