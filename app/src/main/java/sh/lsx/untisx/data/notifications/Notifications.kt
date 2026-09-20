package sh.lsx.untisx.data.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.appLocale
import sh.lsx.untisx.data.model.toUntisLocalTime
import java.time.format.DateTimeFormatter

object Notifications {

    const val EXTRA_FOCUS_DATE = "sh.lsx.untisx.extra.FOCUS_DATE"

    private const val CHANNEL_CHANGES = "schedule_changes"
    private const val CHANNEL_CHANGES_SILENT = "schedule_changes_silent"
    private const val CHANNEL_OVERVIEW = "timetable_overview"
    private const val CHANNEL_OVERVIEW_SILENT = "timetable_overview_silent"

    private const val OVERVIEW_ID = 2001
    private const val INDIVIDUAL_BASE = 10_000
    private const val CHANGE_ID_RANGE = 100_000

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHANGES,
                context.getString(R.string.notif_channel_changes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_changes_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHANGES_SILENT,
                context.getString(R.string.notif_channel_changes_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_changes_silent_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERVIEW,
                context.getString(R.string.notif_channel_overview),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_overview_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERVIEW_SILENT,
                context.getString(R.string.notif_channel_overview_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_overview_silent_desc) },
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun notifyChanges(
        context: Context,
        changes: List<ScheduleChange>,
        silent: Boolean,
    ) {
        if (changes.isEmpty() || !canPost(context)) return

        val channel = channel(CHANNEL_CHANGES, CHANNEL_CHANGES_SILENT, silent)
        val manager = NotificationManagerCompat.from(context)

        changes.forEach { change ->
            val notification = baseBuilder(context, channel)
                .setContentTitle(title(context, change))
                .setContentText(text(context, change))
                .setStyle(NotificationCompat.BigTextStyle().bigText(line(context, change)))
                .setContentIntent(contentIntent(context, change.entry.date))
                .build()

            manager.notify(changeNotificationId(change), notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyOverview(
        context: Context,
        title: String,
        content: String,
        silent: Boolean,
        focusDate: Int?,
    ) {
        if (!canPost(context)) return

        val channel = channel(CHANNEL_OVERVIEW, CHANNEL_OVERVIEW_SILENT, silent)

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentIntent(context, focusDate))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(OVERVIEW_ID, notification)
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun changeNotificationId(change: ScheduleChange): Int {
        val key = "${change.entry.date}|${change.entry.startTime}|${change.type}"

        var hash = 17
        for (character in key) hash = hash * 31 + character.code

        return INDIVIDUAL_BASE + (hash and 0x7FFFFFFF) % CHANGE_ID_RANGE
    }

    private fun baseBuilder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, null))

    private fun title(context: Context, change: ScheduleChange): String = when (change.type) {
        ChangeType.CANCELLED -> context.getString(R.string.notif_change_cancelled_title)
        ChangeType.ROOM -> context.getString(R.string.notif_change_room_title)
        ChangeType.TEACHER -> context.getString(R.string.notif_change_teacher_title)
    }

    private fun text(context: Context, change: ScheduleChange): String = when (change.type) {
        ChangeType.CANCELLED -> context.getString(
            R.string.notif_change_cancelled_text,
            change.subject,
            date(context, change.entry),
            time(change.entry),
        )

        ChangeType.ROOM -> context.getString(
            R.string.notif_change_room_text,
            change.subject,
            fromTo(context, change),
            date(context, change.entry),
            time(change.entry),
        )

        ChangeType.TEACHER -> context.getString(
            R.string.notif_change_teacher_text,
            change.subject,
            fromTo(context, change),
            date(context, change.entry),
            time(change.entry),
        )
    }

    private fun line(context: Context, change: ScheduleChange): String =
        "${title(context, change)}: ${text(context, change)}"

    private fun fromTo(context: Context, change: ScheduleChange): String =
        context.getString(
            R.string.notif_change_from_to,
            change.previousValue?.takeIf { it.isNotBlank() } ?: "?",
            change.newValue?.takeIf { it.isNotBlank() } ?: "?",
        )

    private fun date(context: Context, entry: TimetableEntry): String =
        runCatching {
            DateTimeFormatter.ofPattern("EEE, d. MMM", context.appLocale())
                .format(entry.date.toUntisLocalDate())
        }.getOrDefault("")

    private fun time(entry: TimetableEntry): String =
        "${entry.startTime.toUntisLocalTime().format(timeFormat)}–${entry.endTime.toUntisLocalTime().format(timeFormat)}"

    private fun channel(normal: String, silent: String, silentEnabled: Boolean): String =
        if (silentEnabled) silent else normal

    private fun contentIntent(context: Context, date: Int?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

            if (date != null) putExtra(EXTRA_FOCUS_DATE, date)
        }

        return PendingIntent.getActivity(
            context,
            date ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
