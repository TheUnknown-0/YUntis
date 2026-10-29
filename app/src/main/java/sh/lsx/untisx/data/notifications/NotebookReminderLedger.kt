package sh.lsx.untisx.data.notifications

import android.content.Context

internal object NotebookReminderLedger {

    internal const val PREFS = "notebook_reminder_ledger"
    private const val SCHEDULE_PREFIX = "s:"
    private const val DELIVERY_PREFIX = "d:"

    data class ScheduleEntry(
        val triggerAt: Long,
        val revision: Long,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun scheduleEntry(context: Context, accountHash: String, workName: String): ScheduleEntry? {
        val value = prefs(context).getString(scheduleKey(accountHash, workName), null) ?: return null
        val parts = value.split('|', limit = 2)
        if (parts.size != 2) return null

        val triggerAt = parts[0].toLongOrNull() ?: return null
        val revision = parts[1].toLongOrNull() ?: return null

        return ScheduleEntry(triggerAt, revision)
    }

    fun recordSchedule(
        context: Context,
        accountHash: String,
        workName: String,
        triggerAt: Long,
        revision: Long,
    ) {
        val editor = prefs(context).edit()
            .putString(scheduleKey(accountHash, workName), "$triggerAt|$revision")

        prefs(context).all.keys
            .filter { it.startsWith(deliveryPrefix(accountHash, workName)) }
            .filterNot { it == deliveryKey(accountHash, workName, triggerAt) }
            .forEach { editor.remove(it) }

        editor.apply()
    }

    fun clearSchedule(context: Context, accountHash: String, workName: String) {
        val editor = prefs(context).edit()
            .remove(scheduleKey(accountHash, workName))

        prefs(context).all.keys
            .filter { it.startsWith(deliveryPrefix(accountHash, workName)) }
            .forEach { editor.remove(it) }

        editor.apply()
    }

    fun scheduledWorkNames(context: Context, accountHash: String): Set<String> {
        val prefix = "$SCHEDULE_PREFIX$accountHash:"

        return prefs(context).all.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }

    fun accountHashes(context: Context): Set<String> =
        prefs(context).all.keys
            .filter { it.startsWith(SCHEDULE_PREFIX) || it.startsWith(DELIVERY_PREFIX) }
            .mapNotNull { it.split(':', limit = 3).getOrNull(1) }
            .toSet()

    fun clearSchedules(context: Context, accountHash: String) {
        val prefix = "$SCHEDULE_PREFIX$accountHash:"
        val editor = prefs(context).edit()

        prefs(context).all.keys
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }

        editor.apply()
    }

    fun clearAccount(context: Context, accountHash: String) {
        val schedulePrefix = "$SCHEDULE_PREFIX$accountHash:"
        val deliveryKeyPrefix = "$DELIVERY_PREFIX$accountHash:"
        val editor = prefs(context).edit()

        prefs(context).all.keys
            .filter { it.startsWith(schedulePrefix) || it.startsWith(deliveryKeyPrefix) }
            .forEach { editor.remove(it) }

        editor.apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun isDelivered(
        context: Context,
        accountHash: String,
        workName: String,
        triggerAt: Long,
    ): Boolean = prefs(context).getBoolean(deliveryKey(accountHash, workName, triggerAt), false)

    fun markDelivered(
        context: Context,
        accountHash: String,
        workName: String,
        triggerAt: Long,
    ) {
        prefs(context).edit()
            .putBoolean(deliveryKey(accountHash, workName, triggerAt), true)
            .apply()
    }

    private fun scheduleKey(accountHash: String, workName: String): String =
        "$SCHEDULE_PREFIX$accountHash:$workName"

    private fun deliveryPrefix(accountHash: String, workName: String): String =
        "$DELIVERY_PREFIX$accountHash:$workName:"

    private fun deliveryKey(accountHash: String, workName: String, triggerAt: Long): String =
        "${deliveryPrefix(accountHash, workName)}$triggerAt"
}
