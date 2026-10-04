package sh.lsx.untisx.data.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.ui.util.AppDateFormats

data class ExamReminder(
    val exam: ExamInfo,
    val subject: String,
    val rooms: String,
    val teachers: String,
)

data class NewsEvent(
    val event: TimetableEntry,
    val room: String,
)

object Notifications {

    const val EXTRA_FOCUS_DATE = "sh.lsx.untisx.extra.FOCUS_DATE"
    const val EXTRA_FOCUS_SCREEN = "sh.lsx.untisx.extra.FOCUS_SCREEN"

    const val FOCUS_MESSAGES = "messages"
    const val FOCUS_EVENTS = "events"
    const val FOCUS_ACCOUNT = "account"

    private const val CHANNEL_CHANGES = "schedule_changes"
    private const val CHANNEL_CHANGES_SILENT = "schedule_changes_silent"
    private const val CHANNEL_OVERVIEW = "timetable_overview"
    private const val CHANNEL_OVERVIEW_SILENT = "timetable_overview_silent"
    private const val CHANNEL_EXAMS = "exam_reminders"
    private const val CHANNEL_EXAMS_SILENT = "exam_reminders_silent"
    private const val CHANNEL_NEWS_MESSAGES = "new_messages"
    private const val CHANNEL_NEWS_MESSAGES_SILENT = "new_messages_silent"
    private const val CHANNEL_NEWS_EVENTS = "new_events"
    private const val CHANNEL_NEWS_EVENTS_SILENT = "new_events_silent"
    private const val CHANNEL_NEWS_ANNOUNCEMENTS = "new_announcements"
    private const val CHANNEL_NEWS_ANNOUNCEMENTS_SILENT = "new_announcements_silent"
    private const val CHANNEL_ACCOUNT = "account_alerts"
    private const val CHANNEL_SYNC = "sync_status"

    private const val OVERVIEW_ID = 2001
    private const val CHANGE_NOTIFICATION_ID = 110_001
    private const val EXAM_NOTIFICATION_ID = 210_001
    private const val NEWS_MESSAGES_ID = 310_001
    private const val NEWS_EVENTS_ID = 320_001
    private const val NEWS_ANNOUNCEMENTS_ID = 330_001
    private const val LOGIN_REQUIRED_ID = 400_001

    const val FOREGROUND_SYNC_ID = 500_001
    const val FOREGROUND_OVERVIEW_ID = 500_002
    const val FOREGROUND_EXAM_ID = 500_003

    private const val REQUEST_MESSAGES = 4301
    private const val REQUEST_EVENTS = 4302
    private const val REQUEST_ACCOUNT = 4303

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

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EXAMS,
                context.getString(R.string.notif_channel_exams),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_exams_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EXAMS_SILENT,
                context.getString(R.string.notif_channel_exams_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_exams_silent_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS_MESSAGES,
                context.getString(R.string.notif_channel_new_messages),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_new_messages_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS_MESSAGES_SILENT,
                context.getString(R.string.notif_channel_new_messages_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_new_messages_silent_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS_EVENTS,
                context.getString(R.string.notif_channel_new_events),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_new_events_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS_EVENTS_SILENT,
                context.getString(R.string.notif_channel_new_events_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_new_events_silent_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS_ANNOUNCEMENTS,
                context.getString(R.string.notif_channel_new_announcements),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_new_announcements_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEWS_ANNOUNCEMENTS_SILENT,
                context.getString(R.string.notif_channel_new_announcements_silent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_new_announcements_silent_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACCOUNT,
                context.getString(R.string.notif_channel_account),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_account_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.notif_channel_sync),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_sync_desc) },
        )
    }

    fun foregroundNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_foreground_sync))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

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

            manager.notify(changeTag(change), CHANGE_NOTIFICATION_ID, notification)
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

    @SuppressLint("MissingPermission")
    fun notifyExamReminders(
        context: Context,
        reminders: List<ExamReminder>,
        silent: Boolean,
    ) {
        if (reminders.isEmpty() || !canPost(context)) return

        val channel = channel(CHANNEL_EXAMS, CHANNEL_EXAMS_SILENT, silent)
        val manager = NotificationManagerCompat.from(context)

        reminders.forEach { reminder ->
            val text = examText(context, reminder)

            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(examTitle(context, reminder))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent(context, reminder.exam.date))
                .setAutoCancel(true)
                .build()

            manager.notify(examTag(reminder.exam), EXAM_NOTIFICATION_ID, notification)
        }
    }

    private fun examTitle(context: Context, reminder: ExamReminder): String {
        val exam = reminder.exam

        exam.name.takeIf { it.isNotBlank() }?.let { return it }

        val type = exam.examType.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.lesson_exam)

        return listOfNotNull(type, reminder.subject.takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }

    private fun examText(context: Context, reminder: ExamReminder): String {
        val exam = reminder.exam

        val date = runCatching {
            AppDateFormats.formatter(context, "EEEdMMM").format(exam.date.toUntisLocalDate())
        }.getOrDefault("")

        val time = if (exam.startTime != 0 || exam.endTime != 0) {
            "${exam.startTime.toUntisLocalTime().format(AppDateFormats.time(context))}–${exam.endTime.toUntisLocalTime().format(AppDateFormats.time(context))}"
        } else {
            null
        }

        val whenText = listOfNotNull(date.takeIf { it.isNotBlank() }, time).joinToString(", ")

        return listOfNotNull(
            whenText.takeIf { it.isNotBlank() },
            reminder.rooms.takeIf { it.isNotBlank() },
            reminder.teachers.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    private fun examTag(exam: ExamInfo): String = "exam:${exam.id}|${exam.date}"

    @SuppressLint("MissingPermission")
    fun notifyNewMessages(
        context: Context,
        messages: List<DirectMessage>,
        silent: Boolean,
    ) {
        if (messages.isEmpty() || !canPost(context)) return

        val channel = channel(CHANNEL_NEWS_MESSAGES, CHANNEL_NEWS_MESSAGES_SILENT, silent)
        val manager = NotificationManagerCompat.from(context)

        messages.forEach { message ->
            val key = NewsSignatures.message(message)
            val sender = message.sender?.displayName.orEmpty().trim()

            val builder = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    message.subject.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.messages),
                )
                .setContentIntent(screenIntent(context, FOCUS_MESSAGES, REQUEST_MESSAGES))
                .setAutoCancel(true)

            if (sender.isNotBlank()) builder.setContentText(sender)

            manager.notify(key, NEWS_MESSAGES_ID, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyNewEvents(
        context: Context,
        events: List<NewsEvent>,
        silent: Boolean,
    ) {
        if (events.isEmpty() || !canPost(context)) return

        val channel = channel(CHANNEL_NEWS_EVENTS, CHANNEL_NEWS_EVENTS_SILENT, silent)
        val manager = NotificationManagerCompat.from(context)

        events.forEach { newsEvent ->
            val event = newsEvent.event
            val key = NewsSignatures.event(event)

            val date = runCatching {
                AppDateFormats.formatter(context, "EEEdMMM").format(event.date.toUntisLocalDate())
            }.getOrDefault("")

            val time = if (event.startTime != 0 || event.endTime != 0) {
                "${event.startTime.toUntisLocalTime().format(AppDateFormats.time(context))}–${event.endTime.toUntisLocalTime().format(AppDateFormats.time(context))}"
            } else {
                null
            }

            val whenText = listOfNotNull(date.takeIf { it.isNotBlank() }, time).joinToString(", ")

            val text = listOfNotNull(
                whenText.takeIf { it.isNotBlank() },
                newsEvent.room.takeIf { it.isNotBlank() },
            ).joinToString(" · ")

            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    event.eventTitle?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.events),
                )
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(screenIntent(context, FOCUS_EVENTS, REQUEST_EVENTS))
                .setAutoCancel(true)
                .build()

            manager.notify(key, NEWS_EVENTS_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyNewAnnouncements(
        context: Context,
        announcements: List<DayMessage>,
        silent: Boolean,
    ) {
        if (announcements.isEmpty() || !canPost(context)) return

        val channel = channel(CHANNEL_NEWS_ANNOUNCEMENTS, CHANNEL_NEWS_ANNOUNCEMENTS_SILENT, silent)
        val manager = NotificationManagerCompat.from(context)

        announcements.forEach { announcement ->
            val key = NewsSignatures.announcement(announcement)

            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    announcement.subject.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.events_messages),
                )
                .setContentText(announcement.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(announcement.body))
                .setContentIntent(screenIntent(context, FOCUS_EVENTS, REQUEST_EVENTS))
                .setAutoCancel(true)
                .build()

            manager.notify(key, NEWS_ANNOUNCEMENTS_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyLoginRequired(context: Context) {
        ensureChannels(context)

        if (!canPost(context)) return

        val text = context.getString(R.string.notif_login_required_text)

        val notification = NotificationCompat.Builder(context, CHANNEL_ACCOUNT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_login_required_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(screenIntent(context, FOCUS_ACCOUNT, REQUEST_ACCOUNT))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(LOGIN_REQUIRED_ID, notification)
    }

    private fun changeTag(change: ScheduleChange): String {
        val subjects = change.entry.subject.map { it.id }.sorted().joinToString(",")

        return "chg:${change.entry.date}|${change.entry.startTime}|${change.type}|$subjects" +
            "|${change.previousValue}|${change.newValue}"
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
            time(context, change.entry),
        )

        ChangeType.ROOM -> context.getString(
            R.string.notif_change_room_text,
            change.subject,
            fromTo(context, change),
            date(context, change.entry),
            time(context, change.entry),
        )

        ChangeType.TEACHER -> context.getString(
            R.string.notif_change_teacher_text,
            change.subject,
            fromTo(context, change),
            date(context, change.entry),
            time(context, change.entry),
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
            AppDateFormats.formatter(context, "EEEdMMM").format(entry.date.toUntisLocalDate())
        }.getOrDefault("")

    private fun time(context: Context, entry: TimetableEntry): String =
        "${entry.startTime.toUntisLocalTime().format(AppDateFormats.time(context))}–${entry.endTime.toUntisLocalTime().format(AppDateFormats.time(context))}"
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

    private fun screenIntent(context: Context, screen: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(EXTRA_FOCUS_SCREEN, screen)
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
