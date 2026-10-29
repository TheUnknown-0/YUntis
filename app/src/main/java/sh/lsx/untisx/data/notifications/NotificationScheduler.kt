package sh.lsx.untisx.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.prefs.AccountPrefs
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val SYNC_WORK_NAME = "untisx_timetable_sync"
    const val OVERVIEW_WORK_NAME = "untisx_day_overview"
    const val EXAM_WORK_NAME = "untisx_exam_reminder"
    private const val OVERVIEW_REQUEST_CODE = 4201
    private const val EXAM_REQUEST_CODE = 4202

    fun ensureAll(context: Context) {
        ensureSyncWork(context)
        ensureOverviewAlarm(context)
        ensureExamReminderAlarm(context)
        NotebookReminderScheduler.ensureAll(context)
    }

    fun onAccountRemoved(context: Context, accountId: String) {
        ChangeBaseline.clear(context, accountId)
        SyncSchedule.clear(context, accountId)
        NewsBaseline.clear(context, accountId)
        NotificationPrefs.clearExamReminderSentKeys(context, accountId)
        NotebookReminderScheduler.cancelAccount(context, accountId)

        if (AccountPrefs.getActive() == null) {
            cancelAll(context)
        } else {
            ensureAll(context)
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        cancelOverviewAlarm(context)
        cancelExamReminderAlarm(context)
        NotebookReminderScheduler.cancelAll(context)
        Notifications.cancelAll(context)
    }

    fun ensureSyncWork(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val enabled = NotificationPrefs.isEnabled(context) &&
            (NotificationPrefs.areChangesEnabled(context) || NotificationPrefs.areAnyNewsEnabled(context)) &&
            Notifications.canPost(context)

        if (!enabled) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<TimetableSyncWorker>(
            if (NotificationPrefs.morningBoost(context) && NotificationPrefs.areChangesEnabled(context)) 15L
            else NotificationPrefs.syncProfile(context).changesMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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

    fun ensureExamReminderAlarm(context: Context) {
        cancelExamReminderAlarm(context)

        val enabled = NotificationPrefs.isEnabled(context) &&
            NotificationPrefs.examRemindersEnabled(context) &&
            Notifications.canPost(context)

        if (!enabled) return

        scheduleNextExamReminder(context)
    }

    fun scheduleNextExamReminder(context: Context) {
        val enabled = NotificationPrefs.isEnabled(context) &&
            NotificationPrefs.examRemindersEnabled(context) &&
            Notifications.canPost(context)

        if (!enabled) return

        val trigger = nextExamTrigger(context) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = examReminderPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }

    fun cancelExamReminderAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = examReminderPendingIntent(context)

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

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
    }

    private fun nextTrigger(context: Context): Long? {
        val minute = NotificationPrefs.overviewMinute(context)

        return NotificationTimes.nextOccurrence(minute, AppClock.now()).toInstant().toEpochMilli()
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

    private fun nextExamTrigger(context: Context): Long {
        val minute = NotificationPrefs.examReminderMinute(context)

        return NotificationTimes.nextOccurrence(minute, AppClock.now()).toInstant().toEpochMilli()
    }

    private fun examReminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExamReminderReceiver::class.java)

        return PendingIntent.getBroadcast(
            context,
            EXAM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
