package sh.lsx.untisx.data.notifications

import android.content.Context
import androidx.core.content.edit
import sh.lsx.untisx.UntisXApp

object NotificationPrefs {

    const val MIN_SYNC_INTERVAL_MINUTES = 30
    const val MAX_SYNC_INTERVAL_MINUTES = 8 * 60
    const val INTERVAL_STEP_MINUTES = 30

    const val DEFAULT_SYNC_INTERVAL_MINUTES = 120
    const val DEFAULT_OVERVIEW_MINUTE = 7 * 60 + 15

    const val MIN_OVERVIEW_MINUTE = 0
    const val MAX_OVERVIEW_MINUTE = 23 * 60 + 59

    private const val KEY_ENABLED = "notif_enabled"
    private const val KEY_CHANGES_ENABLED = "notif_changes_enabled"
    private const val KEY_CHANGE_CANCELLED = "notif_change_cancelled"
    private const val KEY_CHANGE_ROOM = "notif_change_room"
    private const val KEY_CHANGE_TEACHER = "notif_change_teacher"
    private const val KEY_CHANGES_SILENT = "notif_changes_silent"
    private const val KEY_SILENT_DURING_LESSONS = "notif_silent_during_lessons"
    private const val KEY_SYNC_INTERVAL = "notif_sync_interval"

    private const val KEY_OVERVIEW_ENABLED = "notif_overview_enabled"
    private const val KEY_OVERVIEW_MINUTE = "notif_overview_minute"
    private const val KEY_OVERVIEW_SHOW_CHANGES = "notif_overview_show_changes"
    private const val KEY_OVERVIEW_DETAILED = "notif_overview_detailed"
    private const val KEY_OVERVIEW_SILENT = "notif_overview_silent"

    private const val KEY_LAST_SYNC = "notif_last_sync"

    private fun prefs(context: Context) =
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }

    fun areChangesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHANGES_ENABLED, true)

    fun setChangesEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_CHANGES_ENABLED, value) }

    fun notifyCancelled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHANGE_CANCELLED, true)

    fun setNotifyCancelled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_CHANGE_CANCELLED, value) }

    fun notifyRoomChange(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHANGE_ROOM, true)

    fun setNotifyRoomChange(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_CHANGE_ROOM, value) }

    fun notifyTeacherChange(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHANGE_TEACHER, true)

    fun setNotifyTeacherChange(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_CHANGE_TEACHER, value) }

    fun changesSilent(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHANGES_SILENT, false)

    fun setChangesSilent(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_CHANGES_SILENT, value) }

    fun silentDuringLessons(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SILENT_DURING_LESSONS, true)

    fun setSilentDuringLessons(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_SILENT_DURING_LESSONS, value) }

    fun syncIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL_MINUTES)
            .coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)

    fun setSyncIntervalMinutes(context: Context, minutes: Int) =
        prefs(context).edit {
            putInt(KEY_SYNC_INTERVAL, minutes.coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES))
        }

    fun isOverviewEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERVIEW_ENABLED, false)

    fun setOverviewEnabled(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_OVERVIEW_ENABLED, value) }

    fun overviewMinute(context: Context): Int =
        prefs(context).getInt(KEY_OVERVIEW_MINUTE, DEFAULT_OVERVIEW_MINUTE)
            .coerceIn(MIN_OVERVIEW_MINUTE, MAX_OVERVIEW_MINUTE)

    fun setOverviewMinute(context: Context, minute: Int) =
        prefs(context).edit {
            putInt(KEY_OVERVIEW_MINUTE, minute.coerceIn(MIN_OVERVIEW_MINUTE, MAX_OVERVIEW_MINUTE))
        }

    fun overviewShowChanges(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERVIEW_SHOW_CHANGES, true)

    fun setOverviewShowChanges(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_OVERVIEW_SHOW_CHANGES, value) }

    fun overviewDetailed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERVIEW_DETAILED, true)

    fun setOverviewDetailed(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_OVERVIEW_DETAILED, value) }

    fun overviewSilent(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERVIEW_SILENT, false)

    fun setOverviewSilent(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(KEY_OVERVIEW_SILENT, value) }

    fun lastSyncAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncAt(context: Context, timestamp: Long) =
        prefs(context).edit { putLong(KEY_LAST_SYNC, timestamp) }
}
