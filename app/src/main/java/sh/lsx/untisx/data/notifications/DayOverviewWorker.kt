package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import sh.lsx.untisx.R
import sh.lsx.untisx.data.TimetableCache
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.CredentialsPrefs
import sh.lsx.untisx.ui.util.AppDateFormats
import java.io.IOException
import java.time.LocalDate

class DayOverviewWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        Notifications.ensureChannels(context)

        if (!NotificationPrefs.isEnabled(context) || !NotificationPrefs.isOverviewEnabled(context)) {
            return Result.success()
        }

        if (!Notifications.canPost(context)) return Result.success()

        val overviewMinute = NotificationPrefs.overviewMinute(context)
        val today = LocalDate.now()
        val target = NotificationTimes.overviewTargetDate(today, overviewMinute)

        AccountStore.ensureLoaded(context)

        val auth = AccountStore.active ?: return Result.success()
        val accountId = AccountPrefs.accountId(auth)

        if (CredentialsPrefs.isInvalid(context, accountId)) return Result.success()

        if (NotificationRules.isHoliday(context, target)) return Result.success()

        val cache = TimetableCache.load(context, accountId)

        val entries = try {
            TimetableSource.fetchRange(context, auth, target, target).entries
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.BadCredentials) {
            Log.d(TAG, "overview stopped: bad credentials")

            if (CredentialsPrefs.markInvalid(context, accountId)) {
                Notifications.notifyLoginRequired(context)
            }

            return Result.success()
        } catch (e: UntisException.Api) {
            Log.d(TAG, "overview stopped: api error ${e.code}")
            return Result.success()
        } catch (e: IOException) {
            Log.d(TAG, "overview fetch failed, using cache: io")
            cachedEntries(context, accountId, target)
        } catch (e: Exception) {
            Log.d(TAG, "overview fetch failed, using cache: ${e.javaClass.simpleName}")
            cachedEntries(context, accountId, target)
        }

        val lessons = entries.filter { it.subject.isNotEmpty() }
        if (lessons.isEmpty()) return Result.success()

        val merged = mergeConsecutive(lessons)

        val subjects = cache?.subjects?.associateBy { it.id }.orEmpty()
        val teachers = cache?.teachers?.associateBy { it.id }.orEmpty()
        val rooms = cache?.rooms?.associateBy { it.id }.orEmpty()

        val content = buildContent(context, merged, subjects, teachers, rooms)

        Notifications.notifyOverview(
            context = context,
            title = formatDate(context, target),
            content = content,
            silent = NotificationPrefs.overviewSilent(context) || NotificationRules.isDuringLessons(context),
            focusDate = target.toUntisDate(),
        )

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            Notifications.FOREGROUND_OVERVIEW_ID,
            Notifications.foregroundNotification(applicationContext),
        )

    private fun cachedEntries(context: Context, accountId: String, date: LocalDate): List<TimetableEntry> {
        val cache = TimetableCache.load(context, accountId) ?: return emptyList()
        val key = date.toUntisWeekStart().toUntisDate().toString()

        return cache.weeks[key].orEmpty()
    }

    private fun mergeConsecutive(entries: List<TimetableEntry>): List<TimetableEntry> {
        val sorted = entries.sortedWith(compareBy({ it.date }, { it.startTime }, { it.endTime }))
        val merged = ArrayList<TimetableEntry>(sorted.size)

        for (entry in sorted) {
            val last = merged.lastOrNull()

            if (last != null &&
                last.date == entry.date &&
                last.code == entry.code &&
                last.subject == entry.subject &&
                last.teacher == entry.teacher &&
                last.klasse == entry.klasse &&
                last.room == entry.room &&
                last.endTime == entry.startTime
            ) {
                merged[merged.size - 1] = last.copy(endTime = entry.endTime)
            } else {
                merged.add(entry)
            }
        }

        return merged
    }

    private fun buildContent(
        context: Context,
        lessons: List<TimetableEntry>,
        subjects: Map<Int, MasterDataItem>,
        teachers: Map<Int, MasterDataItem>,
        rooms: Map<Int, MasterDataItem>,
    ): String {
        val sorted = lessons.sortedWith(compareBy({ it.startTime }, { it.endTime }))
        val detailed = NotificationPrefs.overviewDetailed(context)
        val showChanges = NotificationPrefs.overviewShowChanges(context)

        return sorted.joinToString("\n") { entry ->
            val time = entry.startTime.toUntisLocalTime().format(AppDateFormats.time(context))
            val subject = names(entry.subject, subjects).ifBlank { "?" }

            val details = ArrayList<String>(3)

            if (detailed) {
                names(entry.room, rooms).takeIf { it.isNotBlank() }?.let { details += it }
                names(entry.teacher, teachers).takeIf { it.isNotBlank() }?.let { details += it }
            }

            if (entry.code == "cancelled") {
                details += context.getString(R.string.lesson_cancelled)
            } else if (showChanges) {
                entry.lstext?.trim()?.takeIf { it.isNotBlank() }?.let { details += it }
            }

            if (details.isEmpty()) {
                "$time  $subject"
            } else {
                "$time  $subject · ${details.joinToString(" · ")}"
            }
        }
    }

    private fun names(
        elements: List<TimetableElement>,
        data: Map<Int, MasterDataItem>,
    ): String = elements
        .mapNotNull { element ->
            data[element.id]?.name?.takeIf { it.isNotBlank() }
                ?: element.name.takeIf { it.isNotBlank() }
        }
        .joinToString(", ")

    private fun formatDate(context: Context, date: LocalDate): String =
        AppDateFormats.formatter(context, "EEEEdMMMM").format(date)

    companion object {
        private const val TAG = "DayOverviewWorker"
    }
}
