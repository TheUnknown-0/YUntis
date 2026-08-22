package sh.lsx.untisx

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.AuthData
import sh.lsx.untisx.data.AuthPrefs
import sh.lsx.untisx.data.ThemePrefs
import sh.lsx.untisx.ui.screens.OnboardingScreen
import sh.lsx.untisx.ui.theme.UntisXTheme

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

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(ThemePrefs.get(context)) }
    var onboardingDone by remember { mutableStateOf(readOnboardingDone(context)) }
    var auth by remember { mutableStateOf(AuthPrefs.load(context)) }

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
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveTheme(newTheme: AppTheme) = ThemePrefs.save(context, newTheme)

    UntisXTheme(mode = theme.mode, preset = theme.preset) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            if (!onboardingDone) {
                OnboardingScreen(
                    theme = theme,
                    onThemeChange = ::saveTheme,
                    loggedIn = auth != null,
                    onFinish = {
                        prefsEdit(context).putBoolean(UntisXApp.KEY_ONBOARDING_DONE, true).apply()
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                HomeScreen(
                    auth = auth,
                    onLogout = {
                        AuthPrefs.clear(context)
                        prefsEdit(context).putBoolean(UntisXApp.KEY_ONBOARDING_DONE, false).apply()
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    auth: AuthData?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (auth == null) {
            Text(
                text = "Not signed in.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Finish the login step in onboarding to connect your school account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            OutlinedButton(onClick = onLogout) {
                Text("Open onboarding")
            }
        } else {
            Text(
                text = "Logged in as ${auth.user}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "at ${auth.displayName}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Button(onClick = onLogout) {
                Text("Logout")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Debug", style = MaterialTheme.typography.titleMedium)

                    Spacer(Modifier.height(8.dp))

                    DebugRow("Server", auth.server)
                    DebugRow("School login name", auth.loginName)
                    DebugRow("Session ID", auth.session.sessionId.ifBlank { "-" })
                    DebugRow("Person ID", auth.session.personId.toString())
                    DebugRow("Person type", personTypeName(auth.session.personType))
                    DebugRow("Klasse ID", auth.session.klasseId.toString())
                }
            }
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private fun personTypeName(type: Int): String = when (type) {
    2 -> "Teacher"
    5 -> "Student"
    else -> type.toString()
}
