package sh.lsx.untisx.data.absences

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.ui.util.AppDateFormats
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** An absence whose excuse still has to be handed in. */
data class ExcuseDue(
    val key: String,
    val item: AbsenceItem,
    val deadline: LocalDate?,
) {
    fun daysLeft(today: LocalDate): Long? = deadline?.let { ChronoUnit.DAYS.between(today, it) }
}

/**
 * Reminds every day, from the day after an absence on, to hand in the excuse: for absences the
 * school records and for ones entered in the app, until they are excused or marked as handed in.
 */
object ExcuseReminders {
    private const val PREFS = "excuse_reminders"
    private const val CHANNEL = "excuse_reminders"
    private const val NOTIFICATION_BASE = 740_000
    private const val REQUEST_DAILY = 4701
    private const val LOOK_BACK_DAYS = 60L
    const val ACTION_HANDED_IN = "de.theunknown.yuntis.excuse.HANDED_IN"
    const val EXTRA_ACCOUNT = "account"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        schedule(context)
    }

    /** Time of the daily reminder in minutes after midnight (default 07:00, before school). */
    fun minute(context: Context) = prefs(context).getInt("minute", 7 * 60)
    fun setMinute(context: Context, minute: Int) {
        prefs(context).edit().putInt("minute", minute.coerceIn(0, 24 * 60 - 1)).apply()
        schedule(context)
    }

    fun keyOf(item: AbsenceItem): String =
        item.own?.let { "o:${it.id}" } ?: "s:${item.date}-${item.startTime}-${item.endTime}"

    fun handedIn(context: Context, accountId: String): Set<String> =
        prefs(context).getStringSet("handed_$accountId", null)?.toSet().orEmpty()

    fun setHandedIn(context: Context, accountId: String, key: String, handed: Boolean) {
        val current = handedIn(context, accountId)
        prefs(context).edit().putStringSet("handed_$accountId", if (handed) current + key else current - key).apply()
    }

    /** Pure selection, also used by the tests: unexcused, not handed in, from yesterday back. */
    fun due(items: List<AbsenceItem>, handed: Set<String>, today: LocalDate, deadlineDays: Int): List<ExcuseDue> {
        val first = today.minusDays(LOOK_BACK_DAYS).toUntisDate()
        val last = today.minusDays(1).toUntisDate()
        return items
            .filter { !it.excused && it.date in first..last }
            .map { ExcuseDue(keyOf(it), it, AbsenceAssistant.deadline(it, deadlineDays)) }
            .filter { it.key !in handed }
            .distinctBy { it.key }
            .sortedBy { it.item.date * 10000L + it.item.startTime }
    }

    /** What is open for one account, from the cached school list and the own entries. */
    fun dueFor(context: Context, accountId: String, today: LocalDate = LocalDate.now()): List<ExcuseDue> {
        val school = OwnAbsencesStore.loadSchoolCache(context, accountId).orEmpty().map(AbsenceItem::of)
        val own = OwnAbsencesStore.load(context, accountId).map(AbsenceItem::of)
        return due(school + own, handedIn(context, accountId), today, OwnAbsencesStore.deadlineDays(context))
    }

    private fun dailyIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_DAILY,
        Intent(context, ExcuseReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Plans the next daily check. */
    @android.annotation.SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = dailyIntent(context)
        alarm.cancel(intent)
        if (!enabled(context)) return
        val now = ZonedDateTime.now()
        val minute = minute(context)
        var at = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.toInstant().toEpochMilli()
        // inexact alarms may come up to an hour late, too late before school
        if (sh.lsx.untisx.data.automation.WakeAlarm.canScheduleExact(context)) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarm.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, intent)
        }
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "excuse_reminders",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ExcuseReminderWorker>().build(),
        )
    }

    private fun notificationId(accountId: String) = NOTIFICATION_BASE + (accountId.hashCode() and 0xFFF)

    fun cancel(context: Context, accountId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(accountId))

    /** Takes the reminder away once nothing is open any more (excused, handed in or deleted). */
    fun clearIfDone(context: Context, accountId: String) {
        if (dueFor(context, accountId).isEmpty()) cancel(context, accountId)
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, accountId: String, accountName: String?, due: List<ExcuseDue>) {
        if (due.isEmpty()) {
            cancel(context, accountId)
            return
        }
        if (!Notifications.canPost(context)) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.excuse_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )

        val today = LocalDate.now()
        val dateFormat = AppDateFormats.formatter(context, "EEEdMMM")
        val timeFormat = AppDateFormats.time(context)
        fun time(v: Int) = LocalTime.of((v / 100).coerceIn(0, 23), (v % 100).coerceIn(0, 59)).format(timeFormat)

        val lines = due.map { entry ->
            val item = entry.item
            val day = item.date.toUntisLocalDateOrNull()?.let(dateFormat::format).orEmpty()
            val span = if (item.wholeDay) context.getString(R.string.absences_whole_day) else time(item.startTime) + " – " + time(item.endTime)
            val left = entry.daysLeft(today)
            val status = when {
                left == null -> ""
                left < 0 -> context.getString(R.string.excuse_overdue)
                left == 0L -> context.getString(R.string.excuse_last_day)
                else -> context.resources.getQuantityString(R.plurals.excuse_days_left, left.toInt(), left.toInt())
            }
            listOf("$day, $span", status).filter(String::isNotBlank).joinToString(" · ")
        }
        val urgent = due.mapNotNull { it.daysLeft(today) }.minOrNull()
        val title = when {
            urgent != null && urgent < 0 -> context.getString(R.string.excuse_title_overdue)
            urgent == 0L -> context.getString(R.string.excuse_title_today)
            else -> context.resources.getQuantityString(R.plurals.excuse_title, due.size, due.size)
        }

        val id = notificationId(accountId)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Notifications.EXTRA_FOCUS_SCREEN, AbsenceAlerts.FOCUS_ABSENCES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val handed = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ExcuseReminderReceiver::class.java)
                .setAction(ACTION_HANDED_IN)
                .putExtra(EXTRA_ACCOUNT, accountId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(lines.first())
                .setSubText(accountName)
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(open)
                .addAction(0, context.getString(if (due.size == 1) R.string.excuse_handed_in_one else R.string.excuse_handed_in_all), handed)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class ExcuseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ExcuseReminders.ACTION_HANDED_IN) {
            val accountId = intent.getStringExtra(ExcuseReminders.EXTRA_ACCOUNT) ?: return
            ExcuseReminders.dueFor(context, accountId).forEach { ExcuseReminders.setHandedIn(context, accountId, it.key, true) }
            ExcuseReminders.cancel(context, accountId)
            return
        }
        ExcuseReminders.runNow(context)
        ExcuseReminders.schedule(context)
    }
}

class ExcuseReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ExcuseReminders.enabled(context)) return Result.success()
        AccountStore.ensureLoaded(context)
        val accounts = AccountStore.snapshot
        accounts.forEach { auth ->
            val accountId = AccountPrefs.accountId(auth)
            ExcuseReminders.notify(
                context,
                accountId,
                auth.displayName.takeIf { accounts.size > 1 && it.isNotBlank() },
                ExcuseReminders.dueFor(context, accountId),
            )
        }
        return Result.success()
    }
}
