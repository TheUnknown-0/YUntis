package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import sh.lsx.untisx.data.TimetableCache
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import java.time.LocalDate

class TimetableSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        Notifications.ensureChannels(context)

        if (!NotificationPrefs.isEnabled(context) || !NotificationPrefs.areChangesEnabled(context)) {
            return Result.success()
        }

        if (!Notifications.canPost(context)) return Result.success()

        val auth = AccountPrefs.getActive(context) ?: return Result.success()
        val accountId = AccountPrefs.accountId(auth)
        val monday = LocalDate.now().toUntisWeekStart()

        return try {
            val baseline = ChangeBaseline.load(context, accountId)
            val cache = TimetableCache.load(context, accountId)

            val subjects = cache?.subjects?.associateBy { it.id }.orEmpty()
            val teachers = cache?.teachers?.associateBy { it.id }.orEmpty()
            val rooms = cache?.rooms?.associateBy { it.id }.orEmpty()

            val filter = ChangeFilter(
                cancelled = NotificationPrefs.notifyCancelled(context),
                room = NotificationPrefs.notifyRoomChange(context),
                teacher = NotificationPrefs.notifyTeacherChange(context),
            )

            val changes = ArrayList<ScheduleChange>()
            val observed = HashMap<String, List<TimetableEntry>>()
            var fresh: AuthData = auth

            for (week in listOf(monday, monday.plusWeeks(1))) {
                val fetched = TimetableSource.fetchWeek(context, fresh, week)
                fetched.fresh?.let { fresh = it }

                val key = week.toUntisDate().toString()
                val previous = baseline?.weeks?.get(key)

                if (previous != null) {
                    changes += ScheduleChangeDetector.detect(
                        previous = previous,
                        fresh = fetched.entries,
                        subjects = subjects,
                        teachers = teachers,
                        rooms = rooms,
                        filter = filter,
                    )
                }

                observed[key] = fetched.entries
            }

            val currentKey = monday.toUntisDate()
            val pruned = observed.filterKeys { key ->
                key.toIntOrNull()?.let { it >= currentKey } ?: true
            }

            if (changes.isNotEmpty()) {
                Log.d(TAG, "changes -> ${changes.size}")

                val silent = NotificationPrefs.changesSilent(context) ||
                    NotificationRules.isDuringLessons(context, observed.values.flatten())

                Notifications.notifyChanges(context, changes, silent)
            }

            ChangeBaseline.save(context, accountId, pruned)
            NotificationPrefs.setLastSyncAt(context, System.currentTimeMillis())

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "sync failed: ${e.javaClass.simpleName}")

            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "TimetableSyncWorker"
    }
}
