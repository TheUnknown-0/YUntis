package sh.lsx.untisx.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import sh.lsx.untisx.data.prefs.AccountPrefs
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val SYNC_WORK_NAME = "untisx_timetable_sync"
    private const val OVERVIEW_REQUEST_CODE = 4201

    fun ensureAll(context: Context) {
        ensureSyncWork(context)
        ensureOverviewAlarm(context)
    }

    fun onAccountRemoved(context: Context, accountId: String) {
        ChangeBaseline.clear(context, accountId)

        if (AccountPrefs.getActive(context) == null) {
            cancelAll(context)
        } else {
            ensureAll(context)
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        cancelOverviewAlarm(context)
        Notifications.cancelAll(context)
    }

    fun ensureSyncWork(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val enabled = NotificationPrefs.isEnabled(context) &&
            NotificationPrefs.areChangesEnabled(context) &&
            Notifications.canPost(context)

        if (!enabled) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<TimetableSyncWorker>(
            NotificationPrefs.syncIntervalMinutes(context).toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun ensureOverviewAlarm(context: Context) {
        cancelOverviewAlarm(context)

        val enabled = NotificationPrefs.isEnabled(context) &&
            NotificationPrefs.isOverviewEnabled(context) &&
            Notifications.canPost(context)

        if (!enabled) return

        scheduleNextOverview(context)
    }

    fun scheduleNextOverview(context: Context) {
        val enabled = NotificationPrefs.isEnabled(context) &&
            NotificationPrefs.isOverviewEnabled(context) &&
            Notifications.canPost(context)

        if (!enabled) return

        val trigger = nextTrigger(context) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = overviewPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }

    fun cancelOverviewAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = overviewPendingIntent(context)

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
    }

    private fun nextTrigger(context: Context): Long? {
        val now = ZonedDateTime.now()
        val minute = NotificationPrefs.overviewMinute(context)

        val today = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(minute.toLong())

        val candidate = if (today.isAfter(now)) today else today.plusDays(1)

        return candidate.toInstant().toEpochMilli()
    }

    private fun overviewPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DayOverviewReceiver::class.java)

        return PendingIntent.getBroadcast(
            context,
            OVERVIEW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
