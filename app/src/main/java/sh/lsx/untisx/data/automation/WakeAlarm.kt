package sh.lsx.untisx.data.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import sh.lsx.untisx.R
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.planner.PlannerPrefs
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** YUntis' own alarm clock: rings before the first lesson that takes place and moves when it is cancelled. */
object WakeAlarm {
    private const val PREFS = "wake_alarm"
    private const val CHANNEL = "wake_alarm_v2"
    private const val SNOOZE_CHANNEL = "wake_alarm_snooze"
    const val NOTIFICATION_ID = 730_001
    private const val SNOOZE_NOTIFICATION_ID = 730_002
    private const val REQUEST_FIRE = 4601
    private const val REQUEST_SHOW = 4602
    private const val REQUEST_TEST = 4603
    const val ACTION_FIRE = "de.theunknown.yuntis.alarm.FIRE"
    const val ACTION_SNOOZE = "de.theunknown.yuntis.alarm.SNOOZE"
    const val ACTION_DISMISS = "de.theunknown.yuntis.alarm.DISMISS"
    const val EXTRA_LABEL = "label"
    const val EXTRA_TEST = "test"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        reschedule(context)
    }

    fun snoozeMinutes(context: Context) = prefs(context).getInt("snooze", 9)
    fun setSnoozeMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt("snooze", minutes.coerceIn(1, 30)).apply()

    /** The alarm currently set (epoch millis) and what it is for, or null. */
    fun scheduled(context: Context): Pair<Long, String>? {
        val at = prefs(context).getLong("at", 0L).takeIf { it > System.currentTimeMillis() } ?: return null
        return at to prefs(context).getString("label", "").orEmpty()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Android 14+ only shows the alarm over the lock screen with this permission. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun fireIntent(context: Context, label: String) = PendingIntent.getBroadcast(
        context,
        REQUEST_FIRE,
        Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_LABEL, label),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showIntent(context: Context) = PendingIntent.getActivity(
        context,
        REQUEST_SHOW,
        Intent(context, sh.lsx.untisx.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Lessons of the active account from the cache, or [fresh] ones (e.g. just loaded by the background check). */
    fun lessons(context: Context, fresh: List<TimetableEntry>? = null): Pair<List<TimetableEntry>, List<sh.lsx.untisx.data.model.Holiday>> {
        val auth = AccountStore.active ?: AccountPrefs.getActive()
        val cache = auth?.let { TimetableCache.load(context, AccountPrefs.accountId(it)) }
        val cached = cache?.weeks?.values?.flatten().orEmpty()
        // fresh days replace the cached ones of the same date
        val lessons = if (fresh == null) cached else fresh + cached.filter { entry -> fresh.none { it.date == entry.date } }
        return lessons to cache?.holidays.orEmpty()
    }

    private fun labelFor(context: Context, plan: WakePlan, subjects: Map<Int, sh.lsx.untisx.data.model.MasterDataItem>): String {
        val element = plan.firstLesson.subject.firstOrNull()
        val subject = element?.let { subjects[it.id]?.longName?.takeIf(String::isNotBlank) ?: it.longName ?: it.name }.orEmpty()
        val start = "%02d:%02d".format(plan.firstLesson.startTime / 100, plan.firstLesson.startTime % 100)
        return context.getString(R.string.alarm_first_lesson, start, subject)
    }

    /** Sets (or clears) the alarm for the next school day. Cheap; safe to call often. */
    fun reschedule(context: Context, fresh: List<TimetableEntry>? = null) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        if (!enabled(context)) {
            alarm.cancel(fireIntent(context, ""))
            prefs(context).edit().remove("at").remove("label").apply()
            return
        }
        // a snooze in progress is not overwritten; it is set again, because an app update or a
        // restart clears all alarms
        val snoozedUntil = scheduled(context)?.takeIf { prefs(context).getBoolean("snoozing", false) }
        if (snoozedUntil != null) {
            setAt(context, snoozedUntil.first, snoozedUntil.second, snoozing = true)
            return
        }

        val (lessons, holidays) = lessons(context, fresh)
        val plan = SchoolDayPlan.nextWake(
            LocalDateTime.now(),
            lessons,
            PlannerPrefs.wayMinutes(context),
            PlannerPrefs.prepMinutes(context),
            holidays,
        )
        if (plan == null) {
            alarm.cancel(fireIntent(context, ""))
            prefs(context).edit().remove("at").remove("label").apply()
            return
        }

        val auth = AccountStore.active ?: AccountPrefs.getActive()
        val subjects = auth?.let { TimetableCache.load(context, AccountPrefs.accountId(it)) }?.subjects?.associateBy { it.id }.orEmpty()
        val label = labelFor(context, plan, subjects)
        val at = plan.wakeAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAt(context, at, label, snoozing = false)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setAt(context: Context, at: Long, label: String, snoozing: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = fireIntent(context, label)
        if (canScheduleExact(context)) {
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(context)), fire)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
        prefs(context).edit().putLong("at", at).putString("label", label).putBoolean("snoozing", snoozing).apply()
    }

    fun snooze(context: Context) {
        stopRinging(context)
        val label = prefs(context).getString("ringing_label", null) ?: prefs(context).getString("label", "").orEmpty()
        val at = System.currentTimeMillis() + snoozeMinutes(context) * 60_000L
        setAt(context, at, label, snoozing = true)
        showSnoozing(context, at)
    }

    /** A quiet note while snoozing, so the alarm can still be turned off before it rings again. */
    @SuppressLint("MissingPermission")
    private fun showSnoozing(context: Context, at: Long) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(SNOOZE_CHANNEL, context.getString(R.string.alarm_snooze_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val time = timeText(at).toLocalTime().format(sh.lsx.untisx.ui.util.AppDateFormats.time(context))
        val off = PendingIntent.getBroadcast(
            context,
            REQUEST_FIRE + 11,
            Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            SNOOZE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, SNOOZE_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.alarm_snoozed_until, time))
                .setContentText(prefs(context).getString("ringing_label", "").orEmpty())
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .addAction(0, context.getString(R.string.alarm_dismiss), off)
                .build(),
        )
    }

    fun dismiss(context: Context) {
        stopRinging(context)
        prefs(context).edit().putBoolean("snoozing", false).apply()
        reschedule(context)
    }

    /**
     * A test alarm in [seconds] seconds, to check sound and lock-screen display. It has its own
     * slot, so the real alarm stays set and a reschedule does not cancel the test.
     */
    @SuppressLint("ScheduleExactAlarm")
    fun test(context: Context, seconds: Int = 10) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + seconds * 1000L
        val fire = PendingIntent.getBroadcast(
            context,
            REQUEST_TEST,
            Intent(context, WakeAlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_LABEL, context.getString(R.string.alarm_test_label))
                .putExtra(EXTRA_TEST, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact(context)) {
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(context)), fire)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    fun stopRinging(context: Context) {
        WakeAlarmService.stop(context)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(SNOOZE_NOTIFICATION_ID)
    }

    /** Whether anything would keep the alarm from being heard or seen; shown in the settings. */
    fun notificationsBlocked(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return true
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL) ?: return false
        return channel.importance < NotificationManager.IMPORTANCE_HIGH
    }

    fun alarmVolumeMuted(context: Context): Boolean =
        (context.getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1) == 0

    fun openNotificationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // the first version let the notification make the sound; the service does that now
        manager.deleteNotificationChannel("wake_alarm_v1")
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    /** The notification of the ringing alarm: opens the alarm screen, also over the lock screen. */
    fun ringingNotification(context: Context, label: String): Notification {
        ensureChannel(context)
        val screen = PendingIntent.getActivity(
            context,
            REQUEST_SHOW + 1,
            Intent(context, WakeAlarmActivity::class.java)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(name: String, request: Int) = PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, WakeAlarmReceiver::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alarm_title))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            // no setSilent(): it puts the notification into a quiet group, and Android then
            // suppresses the full-screen alarm. The channel has no sound; the service plays it.
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.alarm_snooze), action(ACTION_SNOOZE, REQUEST_FIRE + 10))
            .addAction(0, context.getString(R.string.alarm_dismiss), action(ACTION_DISMISS, REQUEST_FIRE + 11))
            .build()
    }

    /** Rings: sound, vibration and the alarm screen, through [WakeAlarmService]. */
    @SuppressLint("MissingPermission")
    fun ring(context: Context, label: String) {
        prefs(context).edit().putString("ringing_label", label).apply()
        NotificationManagerCompat.from(context).cancel(SNOOZE_NOTIFICATION_ID)
        runCatching { WakeAlarmService.start(context, label) }
            .onFailure { error ->
                // without the service at least the notification shows up
                android.util.Log.w("YUntis", "alarm service could not start", error)
                if (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, ringingNotification(context, label))
                }
            }
    }

    fun timeText(at: Long): LocalDateTime = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WakeAlarm.ACTION_FIRE -> {
                WakeAlarm.ring(context, intent.getStringExtra(WakeAlarm.EXTRA_LABEL).orEmpty())
                if (!intent.getBooleanExtra(WakeAlarm.EXTRA_TEST, false)) {
                    // the next school day is planned right away; a snooze later sets its own time
                    context.getSharedPreferences("wake_alarm", Context.MODE_PRIVATE).edit().putBoolean("snoozing", false).apply()
                    WakeAlarm.reschedule(context)
                }
            }
            WakeAlarm.ACTION_SNOOZE -> WakeAlarm.snooze(context)
            WakeAlarm.ACTION_DISMISS -> WakeAlarm.dismiss(context)
        }
    }
}
