package sh.lsx.untisx.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notifications.NotificationPrefs
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.rememberDateFormat
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun NotificationSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(NotificationPrefs.isEnabled(context)) }
    var changesEnabled by remember { mutableStateOf(NotificationPrefs.areChangesEnabled(context)) }
    var notifyCancelled by remember { mutableStateOf(NotificationPrefs.notifyCancelled(context)) }
    var notifyRoom by remember { mutableStateOf(NotificationPrefs.notifyRoomChange(context)) }
    var notifyTeacher by remember { mutableStateOf(NotificationPrefs.notifyTeacherChange(context)) }
    var changesSilent by remember { mutableStateOf(NotificationPrefs.changesSilent(context)) }
    var silentDuringLessons by remember { mutableStateOf(NotificationPrefs.silentDuringLessons(context)) }
    var interval by remember { mutableIntStateOf(NotificationPrefs.syncIntervalMinutes(context)) }

    var overviewEnabled by remember { mutableStateOf(NotificationPrefs.isOverviewEnabled(context)) }
    var overviewMinute by remember { mutableIntStateOf(NotificationPrefs.overviewMinute(context)) }
    var detailed by remember { mutableStateOf(NotificationPrefs.overviewDetailed(context)) }
    var showChanges by remember { mutableStateOf(NotificationPrefs.overviewShowChanges(context)) }
    var overviewSilent by remember { mutableStateOf(NotificationPrefs.overviewSilent(context)) }

    var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
    var exactAlarmsAllowed by remember {
        mutableStateOf(NotificationScheduler.canScheduleExactAlarms(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAllowed = Notifications.canPost(context)
        NotificationScheduler.ensureAll(context)
    }

    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.canPost(context)
        exactAlarmsAllowed = NotificationScheduler.canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    fun reschedule() = NotificationScheduler.ensureAll(context)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard(title = stringResource(R.string.settings_notifications)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_master),
                description = stringResource(R.string.settings_notifications_master_desc),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    NotificationPrefs.setEnabled(context, it)
                    reschedule()
                },
            )

            if (!notificationsAllowed) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_notifications_permission_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                TextButton(
                    onClick = Haptics.haptic {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ) {
                    Text(stringResource(R.string.settings_notifications_grant))
                }
            }

            if (notificationsAllowed && !exactAlarmsAllowed) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.settings_notifications_exact_alarm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(
                    onClick = Haptics.haptic {
                        NotificationScheduler.openExactAlarmSettings(context)
                    },
                ) {
                    Text(stringResource(R.string.settings_notifications_exact_alarm_grant))
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(
                    R.string.settings_notifications_last_sync,
                    formatTimestamp(NotificationPrefs.lastSyncAt(context)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!enabled) return@Column

        SettingsSectionCard(
            title = stringResource(R.string.settings_notifications_changes),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_changes_enable),
                checked = changesEnabled,
                onCheckedChange = {
                    changesEnabled = it
                    NotificationPrefs.setChangesEnabled(context, it)
                    reschedule()
                },
            )

            Spacer(Modifier.height(4.dp))

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_type_cancelled),
                checked = changesEnabled && notifyCancelled,
                enabled = changesEnabled,
                onCheckedChange = {
                    notifyCancelled = it
                    NotificationPrefs.setNotifyCancelled(context, it)
                    reschedule()
                },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_type_room),
                checked = changesEnabled && notifyRoom,
                enabled = changesEnabled,
                onCheckedChange = {
                    notifyRoom = it
                    NotificationPrefs.setNotifyRoomChange(context, it)
                    reschedule()
                },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_type_teacher),
                checked = changesEnabled && notifyTeacher,
                enabled = changesEnabled,
                onCheckedChange = {
                    notifyTeacher = it
                    NotificationPrefs.setNotifyTeacherChange(context, it)
                    reschedule()
                },
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.settings_notifications_interval),
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = intervalLabel(interval),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Slider(
                value = interval.toFloat(),
                onValueChange = { interval = it.roundToInt() },
                onValueChangeFinished = {
                    interval = snapInterval(interval)
                    NotificationPrefs.setSyncIntervalMinutes(context, interval)
                    reschedule()
                },
                valueRange = NotificationPrefs.MIN_SYNC_INTERVAL_MINUTES.toFloat()..
                    NotificationPrefs.MAX_SYNC_INTERVAL_MINUTES.toFloat(),
                steps = ((NotificationPrefs.MAX_SYNC_INTERVAL_MINUTES -
                    NotificationPrefs.MIN_SYNC_INTERVAL_MINUTES) /
                    NotificationPrefs.INTERVAL_STEP_MINUTES) - 1,
                enabled = changesEnabled,
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_silent),
                description = stringResource(R.string.settings_notifications_silent_desc),
                checked = changesSilent,
                enabled = changesEnabled,
                onCheckedChange = {
                    changesSilent = it
                    NotificationPrefs.setChangesSilent(context, it)
                    reschedule()
                },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_silent_lessons),
                description = stringResource(R.string.settings_notifications_silent_lessons_desc),
                checked = silentDuringLessons,
                enabled = changesEnabled,
                onCheckedChange = {
                    silentDuringLessons = it
                    NotificationPrefs.setSilentDuringLessons(context, it)
                    reschedule()
                },
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.settings_notifications_overview),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_overview_enable),
                checked = overviewEnabled,
                onCheckedChange = {
                    overviewEnabled = it
                    NotificationPrefs.setOverviewEnabled(context, it)
                    reschedule()
                },
            )

            if (!overviewEnabled) return@SettingsSectionCard

            SettingsTimeRow(
                label = stringResource(R.string.settings_notifications_overview_time),
                minute = overviewMinute,
                description = stringResource(R.string.settings_notifications_overview_time_desc),
                onPick = {
                    overviewMinute = it
                    NotificationPrefs.setOverviewMinute(context, it)
                    reschedule()
                },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_overview_detailed),
                checked = detailed,
                onCheckedChange = {
                    detailed = it
                    NotificationPrefs.setOverviewDetailed(context, it)
                },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_overview_show_changes),
                checked = showChanges,
                onCheckedChange = {
                    showChanges = it
                    NotificationPrefs.setOverviewShowChanges(context, it)
                },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_silent),
                description = stringResource(R.string.settings_notifications_silent_desc),
                checked = overviewSilent,
                onCheckedChange = {
                    overviewSilent = it
                    NotificationPrefs.setOverviewSilent(context, it)
                },
            )
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun snapInterval(minutes: Int): Int {
    val step = NotificationPrefs.INTERVAL_STEP_MINUTES
    val snapped = ((minutes.toFloat() / step).roundToInt() * step)
        .coerceIn(NotificationPrefs.MIN_SYNC_INTERVAL_MINUTES, NotificationPrefs.MAX_SYNC_INTERVAL_MINUTES)

    return snapped
}

@Composable
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "–"

    return rememberDateFormat("d. MMM, HH:mm")
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}
