package sh.lsx.untisx.data.automation

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.theme.UntisXTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen alarm, shown over the lock screen: snooze or turn off. */
class WakeAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val label = intent.getStringExtra(WakeAlarm.EXTRA_LABEL).orEmpty()

        setContent {
            UntisXTheme {
                var now by remember { mutableStateOf(LocalTime.now()) }
                LaunchedEffect(Unit) {
                    var seconds = 0
                    while (true) {
                        now = LocalTime.now()
                        // turned off from the notification or timed out: nothing left to show
                        if (seconds++ > 2 && !WakeAlarmService.ringing) {
                            finish()
                            break
                        }
                        delay(1000)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        now.format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(48.dp))
                    Button(
                        onClick = {
                            WakeAlarm.dismiss(this@WakeAlarmActivity)
                            finishAndUnlock()
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(),
                    ) { Text(stringResource(R.string.alarm_dismiss), fontSize = 20.sp) }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            WakeAlarm.snooze(this@WakeAlarmActivity)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.alarm_snooze_minutes, WakeAlarm.snoozeMinutes(this@WakeAlarmActivity)), fontSize = 18.sp)
                    }
                }
            }
        }
    }

    private fun finishAndUnlock() {
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        finish()
    }
}
