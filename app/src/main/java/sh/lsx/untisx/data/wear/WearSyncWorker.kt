package sh.lsx.untisx.data.wear

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.CredentialsPrefs
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.ui.timetable.TimetableFetch
import sh.lsx.untisx.ui.timetable.mergeConsecutiveLessons
import sh.lsx.untisx.ui.timetable.resolveElementIds
import sh.lsx.untisx.ui.timetable.WeekFetch
import java.time.LocalDate

class WearSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        AccountStore.ensureLoaded(context)
        val requestId = inputData.getString("request_id").orEmpty()

        val auth = AccountStore.active ?: run {
            WearSync.push(context, TimetableCache.Data(), null, requestId, WearRefreshResult.FAILED)
            return Result.success()
        }
        val accountId = AccountPrefs.accountId(auth)

        val cached = TimetableCache.load(context, accountId) ?: TimetableCache.Data()

        if (CredentialsPrefs.isInvalid(context, accountId)) {
            WearSync.push(context, cached, auth, requestId, WearRefreshResult.FAILED)
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val monday = AppClock.today().toUntisWeekStart()

        val isFresh = listOf(monday, monday.plusWeeks(1)).all { week ->
            val key = week.toUntisDate().toString()
            key in cached.weeks && now - (cached.weekFetchedAt[key] ?: 0L) in 0..WearSync.FRESH_WINDOW_MS
        }

        if (isFresh) {
            return if (WearSync.push(context, cached, auth, requestId)) Result.success() else Result.retry()
        }

        return try {
            val updated = refresh(context, auth, accountId, cached)

            val complete = listOf(monday, monday.plusWeeks(1)).all { week ->
                val key = week.toUntisDate().toString()
                key in updated.weeks && System.currentTimeMillis() - (updated.weekFetchedAt[key] ?: 0L) in 0..WearSync.FRESH_WINDOW_MS &&
                    (0..6).none { week.plusDays(it.toLong()).toUntisDate() in updated.restrictedDays }
            }
            val result = if (complete) WearRefreshResult.UPDATED else WearRefreshResult.FAILED

            if (WearSync.push(context, updated, auth, requestId, result)) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.RateLimited) {
            Log.d(TAG, "refresh deferred: rate limited")

            WearSync.push(context, TimetableCache.load(context, accountId) ?: cached, auth, requestId, WearRefreshResult.FAILED)
            if (runAttemptCount < 2) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.d(TAG, "refresh failed: ${e.javaClass.simpleName}")

            WearSync.push(context, TimetableCache.load(context, accountId) ?: cached, auth, requestId, WearRefreshResult.FAILED)
            Result.success()
        }
    }

    private suspend fun refresh(
        context: Context,
        auth: AuthData,
        accountId: String,
        cached: TimetableCache.Data,
    ): TimetableCache.Data {
        val monday = AppClock.today().toUntisWeekStart()
        val subjectsById = cached.subjects.associateBy { it.id }
        val teachersById = cached.teachers.associateBy { it.id }
        val roomsById = cached.rooms.associateBy { it.id }
        var currentAuth = auth

        val fetch = TimetableFetch(
            context = context,
            requireAuth = { currentAuth },
            withPassword = {
                PasswordVault.retrieve(context, accountId) ?: throw UntisException.BadCredentials
            },
            onFreshSession = {
                currentAuth = it
                AccountStore.updateSession(context, it)
            },
            resolveElementIds = { entries -> resolveElementIds(entries, subjectsById, teachersById, roomsById) },
        )

        return refreshWearWeeks(
            monday, cached,
            isActive = { AccountStore.activeId.value == accountId },
            fetch = { week ->
                val result = fetch.fetchWeek(week.toUntisDate(), week, monday)
                result.fresh?.let {
                    currentAuth = it
                    AccountStore.updateSession(context, it)
                }
                result
            },
            save = { AccountStore.mergeTimetableCache(context, accountId, it) },
        )
    }

    companion object {
        private const val TAG = "WearSyncWorker"
    }
}

internal suspend fun refreshWearWeeks(
    monday: LocalDate,
    cached: TimetableCache.Data,
    isActive: () -> Boolean,
    fetch: suspend (LocalDate) -> WeekFetch,
    save: suspend (TimetableCache.Data) -> TimetableCache.Data?,
    now: () -> Long = System::currentTimeMillis,
): TimetableCache.Data {
    var updated = cached
    for (week in listOf(monday, monday.plusWeeks(1))) {
        if (!isActive()) return updated

        val startedAt = now()
        val key = week.toUntisDate().toString()
        if (key in updated.weeks && startedAt - (updated.weekFetchedAt[key] ?: 0L) in 0..WearSync.FRESH_WINDOW_MS) continue

        val result = fetch(week)
        if (!isActive()) return updated

        val dates = (0..6).map { week.plusDays(it.toLong()).toUntisDate() }.toSet()
        updated = save(TimetableCache.Data(
            weeks = if (result.allRefused) emptyMap() else mapOf(key to result.entries
                .let(::mergeConsecutiveLessons).sortedWith(compareBy({ it.date }, { it.startTime }))),
            weekFetchedAt = mapOf(key to startedAt),
            restrictedDays = if (result.allRefused) dates else result.refusedDays.toSet(),
            lastFetch = if (result.allRefused) updated.lastFetch else startedAt,
        )) ?: return updated
    }

    return updated
}
