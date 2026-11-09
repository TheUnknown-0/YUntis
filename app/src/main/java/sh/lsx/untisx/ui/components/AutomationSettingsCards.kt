package sh.lsx.untisx.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.absences.ExcuseReminders
import sh.lsx.untisx.data.automation.QuietMode
import sh.lsx.untisx.data.automation.WakeAlarm
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat

/** Daily reminder to hand in excuses for absences. */
@Composable
fun ExcuseReminderCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(ExcuseReminders.enabled(context)) }
    var minute by remember { mutableIntStateOf(ExcuseReminders.minute(context)) }

    SettingsSectionCard(title = stringResource(R.string.excuse_card)) {
        SettingsSwitchRow(
            title = stringResource(R.string.excuse_enable),
            description = stringResource(R.string.excuse_enable_desc),
            checked = on,
            onCheckedChange = {
                on = it
                ExcuseReminders.setEnabled(context, it)
            },
        )
        if (on) {
            SettingsTimeRow(
                label = stringResource(R.string.excuse_time),
                minute = minute,
                onPick = {
                    minute = it
                    ExcuseReminders.setMinute(context, it)
                },
            )
            TextButton(onClick = Haptics.haptic { ExcuseReminders.runNow(context) }) {
                Text(stringResource(R.string.excuse_check_now))
            }
        }
    }
}

/** Alarm clock and quiet-in-class settings. */
@Composable
fun AutomationSettingsCards() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dayFormat = rememberDateFormat("EEEdMMM")
    val timeFormat = rememberTimeFormat()

    var alarmOn by remember { mutableStateOf(WakeAlarm.enabled(context)) }
    var snooze by remember { mutableIntStateOf(WakeAlarm.snoozeMinutes(context)) }
    var quietOn by remember { mutableStateOf(QuietMode.enabled(context)) }
    var quietKind by remember { mutableStateOf(QuietMode.kind(context)) }
    var tick by remember { mutableIntStateOf(0) }

    // permissions are granted in the system settings; read them again when coming back
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }

    fun refresh(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            tick++
        }
    }

    fun whenText(millis: Long): String {
        val time = WakeAlarm.timeText(millis)
        return dayFormat.format(time.toLocalDate()) + ", " + time.toLocalTime().format(timeFormat)
    }

    SettingsSectionCard(title = stringResource(R.string.alarm_card)) {
        SettingsSwitchRow(
            title = stringResource(R.string.alarm_enable),
            description = stringResource(R.string.alarm_enable_desc),
            checked = alarmOn,
            onCheckedChange = {
                alarmOn = it
                refresh {
                    WakeAlarm.ensureChannel(context)
                    WakeAlarm.setEnabled(context, it)
                }
            },
        )

        if (alarmOn) {
            tick.let { _ ->
                val scheduled = WakeAlarm.scheduled(context)
                Text(
                    scheduled?.let { stringResource(R.string.alarm_next, whenText(it.first)) + "\n" + it.second }
                        ?: stringResource(R.string.alarm_none),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!WakeAlarm.canScheduleExact(context)) {
                    Text(stringResource(R.string.alarm_need_exact), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = Haptics.haptic { NotificationScheduler.openExactAlarmSettings(context) }) {
                        Text(stringResource(R.string.alarm_grant))
                    }
                }
                if (WakeAlarm.notificationsBlocked(context)) {
                    Text(stringResource(R.string.alarm_need_notifications), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = Haptics.haptic { WakeAlarm.openNotificationSettings(context) }) {
                        Text(stringResource(R.string.alarm_grant))
                    }
                }
                if (WakeAlarm.alarmVolumeMuted(context)) {
                    Text(stringResource(R.string.alarm_volume_muted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!WakeAlarm.canUseFullScreen(context)) {
                    Text(stringResource(R.string.alarm_need_fullscreen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = Haptics.haptic { WakeAlarm.openFullScreenSettings(context) }) {
                        Text(stringResource(R.string.alarm_grant))
                    }
                }
            }

            SettingsSliderRow(
                title = stringResource(R.string.alarm_snooze_setting),
                value = snooze.toFloat(),
                valueLabel = stringResource(R.string.minutes_short, snooze),
                valueRange = 1f..30f,
                steps = 28,
                onValueChange = {
                    snooze = it.toInt()
                    WakeAlarm.setSnoozeMinutes(context, snooze)
                },
            )

            val testStarted = stringResource(R.string.alarm_test_started)
            TextButton(onClick = Haptics.haptic {
                WakeAlarm.ensureChannel(context)
                WakeAlarm.test(context)
                Toast.makeText(context, testStarted, Toast.LENGTH_LONG).show()
            }) {
                Text(stringResource(R.string.alarm_test))
            }
        }
    }

    SettingsSectionCard(title = stringResource(R.string.quiet_card)) {
        SettingsSwitchRow(
            title = stringResource(R.string.quiet_enable),
            description = stringResource(R.string.quiet_enable_desc),
            checked = quietOn,
            onCheckedChange = {
                quietOn = it
                refresh { QuietMode.setEnabled(context, it) }
            },
        )

        if (quietOn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(QuietMode.Kind.DND to R.string.quiet_kind_dnd, QuietMode.Kind.VIBRATE to R.string.quiet_kind_vibrate).forEach { (kind, label) ->
                    FilterChip(
                        selected = quietKind == kind,
                        onClick = Haptics.haptic {
                            quietKind = kind
                            refresh { QuietMode.setKind(context, kind) }
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            tick.let { _ ->
                if (quietKind == QuietMode.Kind.DND && !QuietMode.hasAccess(context)) {
                    Text(stringResource(R.string.quiet_need_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = Haptics.haptic { QuietMode.openAccessSettings(context) }) {
                        Text(stringResource(R.string.alarm_grant))
                    }
                } else if (quietKind == QuietMode.Kind.DND) {
                    Text(stringResource(R.string.quiet_dnd_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val status = buildList {
                    if (QuietMode.active(context)) add(stringResource(R.string.quiet_now_on))
                    QuietMode.nextSwitch(context)?.let { (at, quiet) ->
                        add(stringResource(if (quiet) R.string.quiet_next_on else R.string.quiet_next_off, whenText(at)))
                    }
                }
                if (status.isNotEmpty()) Text(status.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
