package sh.lsx.untisx.data.notifications

import android.content.Context
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import java.time.LocalDate
import java.time.LocalTime

object NotificationRules {

    fun isDuringLessons(context: Context, entries: List<TimetableEntry>? = null): Boolean {
        if (!NotificationPrefs.silentDuringLessons(context)) return false

        val today = LocalDate.now()
        val allEntries = entries ?: cachedEntries(context, today)

        return NotificationTimes.isWithinLessonSpan(
            entries = allEntries,
            today = today.toUntisDate(),
            nowMinute = currentMinute(),
        )
    }

    fun isHoliday(context: Context, date: LocalDate): Boolean {
        val auth = AccountPrefs.getActive() ?: return false
        val cache = TimetableCache.load(context, AccountPrefs.accountId(auth)) ?: return false
        val dateInt = date.toUntisDate()

        return cache.holidays.any { dateInt >= it.startDate && dateInt <= it.endDate }
    }

    private fun cachedEntries(context: Context, date: LocalDate): List<TimetableEntry> {
        val auth = AccountPrefs.getActive() ?: return emptyList()
        val cache = TimetableCache.load(context, AccountPrefs.accountId(auth)) ?: return emptyList()
        val key = date.toUntisWeekStart().toUntisDate().toString()

        return cache.weeks[key].orEmpty()
    }

    fun currentMinute(now: LocalTime = LocalTime.now()): Int = now.hour * 60 + now.minute
}
