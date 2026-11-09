package sh.lsx.untisx.data.automation

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.util.Log
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.LocalDateTime
import java.time.ZoneId

/** Silences the phone during lessons that take place, and turns it back on in breaks, free periods and after school. */
object QuietMode {
    private const val PREFS = "quiet_mode"
    private const val REQUEST = 4701
    private const val TAG = "QuietMode"
    private val CONDITION: Uri = Uri.parse("yuntis://quiet/lessons")

    enum class Kind { DND, VIBRATE }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("enabled", on).apply()
        if (!on) apply(context, false)
        reschedule(context)
    }

    fun kind(context: Context) = runCatching { Kind.valueOf(prefs(context).getString("kind", Kind.DND.name)!!) }.getOrDefault(Kind.DND)
    fun setKind(context: Context, kind: Kind) {
        // leave the old mode before switching
        apply(context, false)
        prefs(context).edit().putString("kind", kind.name).apply()
        reschedule(context)
    }

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun openAccessSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** True while YUntis keeps the phone quiet. */
    fun active(context: Context) = prefs(context).getBoolean("applied", false)

    /** The automatic rule shown under "Do not disturb" (Android 10+); created once. */
    private fun ruleId(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val manager = context.getSystemService(NotificationManager::class.java)
        prefs(context).getString("rule", null)?.let { id -> if (manager.getAutomaticZenRule(id) != null) return id }

        val rule = AutomaticZenRule(
            context.getString(R.string.quiet_rule_name),
            null,
            ComponentName(context, MainActivity::class.java),
            CONDITION,
            null,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        return runCatching { manager.addAutomaticZenRule(rule) }
            .onFailure { Log.d(TAG, "rule not created: ${it.javaClass.simpleName}") }
            .getOrNull()
            ?.also { prefs(context).edit().putString("rule", it).apply() }
    }

    /** Turns the quiet state on or off; only undoes what YUntis itself switched on. */
    fun apply(context: Context, quiet: Boolean) {
        val applied = active(context)
        if (quiet == applied) return
        val manager = context.getSystemService(NotificationManager::class.java)

        when (kind(context)) {
            Kind.DND -> {
                if (!hasAccess(context)) return
                val id = ruleId(context)
                if (id != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val state = if (quiet) Condition.STATE_TRUE else Condition.STATE_FALSE
                    runCatching {
                        manager.setAutomaticZenRuleState(id, Condition(CONDITION, context.getString(R.string.quiet_rule_summary), state))
                    }.onFailure { Log.d(TAG, "rule state failed: ${it.javaClass.simpleName}") }
                } else if (quiet) {
                    if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    } else {
                        // already silenced by the user: nothing of ours to undo later
                        return
                    }
                } else if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }

            Kind.VIBRATE -> {
                val audio = context.getSystemService(AudioManager::class.java)
                if (quiet) {
                    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
                    runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
                } else if (audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    runCatching { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL }
                }
            }
        }
        prefs(context).edit().putBoolean("applied", quiet).apply()
    }

    private fun transitionIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, QuietModeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Applies the state for now and plans the next switch. Cheap; safe to call often. */
    @SuppressLint("ScheduleExactAlarm")
    fun reschedule(context: Context, fresh: List<TimetableEntry>? = null) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = transitionIntent(context)
        alarm.cancel(intent)
        if (!enabled(context)) return

        val (lessons, _) = WakeAlarm.lessons(context, fresh)
        val now = LocalDateTime.now()
        apply(context, SchoolDayPlan.isQuiet(now, lessons))

        val next = SchoolDayPlan.nextTransition(now, lessons) ?: return
        val at = next.first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (WakeAlarm.canScheduleExact(context)) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
        prefs(context).edit().putLong("next", at).putBoolean("next_quiet", next.second).apply()
    }

    /** Next planned switch (epoch millis) and whether it switches to quiet, or null. */
    fun nextSwitch(context: Context): Pair<Long, Boolean>? {
        val at = prefs(context).getLong("next", 0L).takeIf { it > System.currentTimeMillis() } ?: return null
        return at to prefs(context).getBoolean("next_quiet", false)
    }
}

class QuietModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = QuietMode.reschedule(context)
}

/** Everything that follows the timetable outside the app: alarm clock and quiet mode. */
object Automation {
    fun refresh(context: Context, fresh: List<TimetableEntry>? = null) {
        runCatching { WakeAlarm.reschedule(context, fresh) }.onFailure { Log.d("Automation", "alarm: ${it.javaClass.simpleName}") }
        runCatching { QuietMode.reschedule(context, fresh) }.onFailure { Log.d("Automation", "quiet: ${it.javaClass.simpleName}") }
    }
}
