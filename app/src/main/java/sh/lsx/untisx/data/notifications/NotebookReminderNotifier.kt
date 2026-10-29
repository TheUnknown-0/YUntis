package sh.lsx.untisx.data.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookNotificationContract
import sh.lsx.untisx.ui.util.AppDateFormats

object NotebookReminderNotifier {

    const val CHANNEL_ID = "notebook_reminders"
    const val NOTIFICATION_ID = 220_001

    private const val TAG_PREFIX = "notebook:"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notebook_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notebook_channel_reminders_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        accountId: String,
        item: NotebookItem,
        triggerAt: Long,
    ): Boolean {
        ensureChannel(context)

        if (!Notifications.canPost(context)) return false

        val accountHash = NotebookReminderScheduler.accountHash(accountId)
        val workName = NotebookReminderScheduler.workName(accountId, item.id)
        val tag = notificationTag(accountHash, workName)
        val title = item.title.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notebook_notification_default_title)
        val text = reminderText(context, item)
        val focusToken = try {
            NotebookNotificationFocus.issue(context, accountId, item.id)
        } catch (e: Exception) {
            null
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            stableRequestCode("open", accountHash, workName),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("untisx://notebook/open/$accountHash/$workName")
                putExtra(Notifications.EXTRA_FOCUS_SCREEN, NotebookNotificationContract.FOCUS_NOTEBOOK)
                putExtra(NotebookNotificationContract.EXTRA_NOTEBOOK_ITEM_ID, item.id)
                putExtra(NotebookNotificationContract.EXTRA_NOTEBOOK_ACCOUNT_ID, accountId)

                if (focusToken != null) {
                    putExtra(NotebookNotificationFocus.EXTRA_FOCUS_TOKEN, focusToken)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val doneIntent = PendingIntent.getBroadcast(
            context,
            stableRequestCode("done", accountHash, workName),
            Intent(context, NotebookReminderActionReceiver::class.java).apply {
                action = NotebookNotificationContract.ACTION_MARK_DONE
                data = Uri.parse("untisx://notebook/done/$accountHash/$workName")
                putExtra(NotebookNotificationContract.EXTRA_NOTEBOOK_ITEM_ID, item.id)
                putExtra(NotebookNotificationContract.EXTRA_NOTEBOOK_ACCOUNT_ID, accountId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notebook_notification_private))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setWhen(triggerAt)
            .setShowWhen(true)
            .addAction(
                0,
                context.getString(R.string.notebook_notification_mark_done),
                doneIntent,
            )
            .build()

        NotificationManagerCompat.from(context).notify(tag, NOTIFICATION_ID, notification)

        return true
    }

    fun cancel(context: Context, accountHash: String, workName: String) {
        NotificationManagerCompat.from(context).cancel(
            notificationTag(accountHash, workName),
            NOTIFICATION_ID,
        )
    }

    fun cancelAccount(context: Context, accountHash: String) {
        val prefix = "$TAG_PREFIX$accountHash:"
        cancelTags(context) { it.startsWith(prefix) }
    }

    fun cancelAll(context: Context) {
        cancelTags(context) { it.startsWith(TAG_PREFIX) }
    }

    internal fun notificationTag(accountHash: String, workName: String): String =
        "$TAG_PREFIX$accountHash:${workName.substringAfterLast('_')}"

    private fun cancelTags(context: Context, predicate: (String) -> Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.activeNotifications.forEach { notification ->
            val tag = notification.tag ?: return@forEach

            if (predicate(tag)) {
                manager.cancel(tag, notification.id)
            }
        }
    }

    private fun reminderText(context: Context, item: NotebookItem): String {
        val due = item.due?.date
        val date = due?.let {
            runCatching {
                AppDateFormats.formatter(context, "EEEdMMM").format(it.toUntisLocalDate())
            }.getOrDefault("")
        }.orEmpty()

        val dueText = date.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.notebook_notification_due, it) }
            .orEmpty()

        val subject = item.subject?.displayName?.trim().orEmpty()

        return listOfNotNull(
            subject.takeIf { it.isNotBlank() },
            dueText.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank {
            context.getString(R.string.notebook_notification_generic)
        }
    }

    private fun stableRequestCode(kind: String, accountHash: String, workName: String): Int =
        "$kind:$accountHash:$workName".hashCode()
}
