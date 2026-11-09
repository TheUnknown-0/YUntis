package sh.lsx.untisx.data.calendar

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.OwnExamsStore
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.PasswordVault
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Copies the chosen kinds of school entries into the chosen device calendar. */
class CalendarExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (!CalendarExportPrefs.isEnabled(context)) return Result.success()

        if (!CalendarSync.hasPermission(context)) {
            CalendarExportPrefs.saveResult(context, context.getString(R.string.calendar_result_no_permission))
            return Result.success()
        }

        CalendarExportPrefs.setRunning(context, true)

        try {
            return withContext(Dispatchers.IO) { export(context) }
        } finally {
            CalendarExportPrefs.setRunning(context, false)
        }
    }

    private suspend fun export(context: Context): Result {
        AccountStore.ensureLoaded(context)

        val target = CalendarExportPrefs.target(context)
        val calendarId = if (target == CalendarExportPrefs.OWN_CALENDAR) {
            CalendarSync.ensureOwnCalendar(context, context.getString(R.string.calendar_own_name))
        } else {
            target.takeIf { id -> CalendarSync.writableCalendars(context).any { it.id == id } }
        }

        if (calendarId == null) {
            CalendarExportPrefs.saveResult(context, context.getString(R.string.calendar_result_no_calendar))
            return Result.success()
        }

        val accounts = AccountStore.snapshot.filter { CalendarExportPrefs.isAccountEnabled(context, AccountPrefs.accountId(it)) }
        val selection = CalendarSelection(
            categories = CalendarExportPrefs.categories(context),
            excludedExamTypes = CalendarExportPrefs.excludedExamTypes(context),
        )
        val texts = CalendarTexts(
            exam = context.getString(R.string.calendar_word_exam),
            event = context.getString(R.string.event),
            homework = context.getString(R.string.calendar_word_homework),
            cancelled = context.getString(R.string.calendar_word_cancelled),
            substituted = context.getString(R.string.calendar_word_substituted),
            teacher = context.getString(R.string.calendar_word_teacher),
            room = context.getString(R.string.calendar_word_room),
            instead = context.getString(R.string.calendar_word_instead),
        )
        val today = LocalDate.now()
        var inserted = 0
        var updated = 0
        var deleted = 0
        var total = 0
        var offline = false

        // accounts that are switched off lose the entries written for them earlier
        AccountStore.snapshot
            .map { AccountPrefs.accountId(it) }
            .filterNot { id -> accounts.any { AccountPrefs.accountId(it) == id } }
            .forEach { deleted += CalendarSync.removeAll(context, it) }

        accounts.forEach { auth ->
            val (source, fresh) = source(context, auth, accounts.size > 1, selection)
            if (!fresh) offline = true

            val items = CalendarItems.build(source, selection, today, texts)
            val result = CalendarSync.sync(context, source.accountId, calendarId, items, today)

            inserted += result.inserted
            updated += result.updated
            deleted += result.deleted
            total += result.total
        }

        val summary = context.getString(R.string.calendar_result, total, inserted, updated, deleted) +
            if (offline) " " + context.getString(R.string.calendar_result_offline) else ""
        CalendarExportPrefs.saveResult(context, summary)
        Log.d(TAG, "export done: $total items, +$inserted ~$updated -$deleted offline=$offline")

        return Result.success()
    }

    /** Collects the data of one account; returns false as second value if the school could not be reached. */
    private suspend fun source(
        context: Context,
        auth: AuthData,
        prefixed: Boolean,
        selection: CalendarSelection,
    ): Pair<CalendarSource, Boolean> {
        val accountId = AccountPrefs.accountId(auth)
        val cache = TimetableCache.load(context, accountId)
        val today = LocalDate.now()
        var exams = ExamsStore.load(context, accountId)?.exams.orEmpty()
        var events = cache?.events.orEmpty()
        var fresh = true

        val needsExams = CalendarCategory.EXAMS in selection.categories
        val needsEvents = CalendarCategory.EVENTS in selection.categories

        if (needsExams || needsEvents) {
            val password = PasswordVault.retrieve(context, accountId)

            if (password == null) {
                fresh = false
            } else {
                if (needsExams) {
                    try {
                        exams = UntisClient.fetchExams(auth, password, today.minusDays(CalendarItems.DAYS_BACK), today.plusMonths(EXAMS_AHEAD_MONTHS))
                        ExamsStore.save(context, accountId, ExamsStore.Data(exams, System.currentTimeMillis()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(TAG, "exams refresh failed: ${e.javaClass.simpleName}")
                        fresh = false
                    }
                }

                if (needsEvents) {
                    val to = today.plusMonths(EVENTS_AHEAD_MONTHS)
                    val loaded = try {
                        UntisClient.getTimetableIntern(auth, password, today, to)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(TAG, "events via mobile API failed: ${e.javaClass.simpleName}")
                        // the same fallback the app uses: the public JSON-RPC timetable
                        try {
                            val (entries, updated) = SessionManager.withValidSession(context, auth) { a ->
                                UntisClient.getTimetable(a, today.toUntisDate(), to.toUntisDate())
                            }
                            if (updated != null) AccountPrefs.updateSession(context, updated)
                            entries
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.d(TAG, "events refresh failed: ${e.javaClass.simpleName}")
                            null
                        }
                    }

                    if (loaded != null) events = loaded.filter { it.isEvent } else fresh = false
                }
            }
        }

        val notebook = if (CalendarCategory.HOMEWORK in selection.categories) {
            (NotebookRepository(context, accountId).load() as? NotebookReadResult.Loaded)?.data?.items.orEmpty()
        } else {
            emptyList()
        }

        val source = CalendarSource(
            accountId = accountId,
            accountLabel = auth.user.takeIf { prefixed },
            exams = exams,
            ownExams = OwnExamsStore.load(context, accountId),
            events = mergeEvents(events),
            holidays = cache?.holidays.orEmpty(),
            notebook = notebook,
            lessons = cache?.weeks?.values?.flatten().orEmpty(),
            subjects = cache?.subjects?.associateBy { it.id }.orEmpty(),
            teachers = cache?.teachers?.associateBy { it.id }.orEmpty(),
            rooms = cache?.rooms?.associateBy { it.id }.orEmpty(),
        )

        return source to fresh
    }

    /** Joins an event split into several periods of the same day into one entry. */
    private fun mergeEvents(events: List<sh.lsx.untisx.data.model.TimetableEntry>) =
        events.sortedWith(compareBy({ it.date }, { it.startTime }))
            .fold(ArrayList<sh.lsx.untisx.data.model.TimetableEntry>()) { acc, e ->
                val last = acc.lastOrNull()
                if (last != null && last.date == e.date && last.eventTitle == e.eventTitle && e.startTime <= last.endTime + 30 && e.startTime != 0) {
                    acc[acc.lastIndex] = last.copy(endTime = maxOf(last.endTime, e.endTime))
                } else {
                    acc += e
                }
                acc
            }

    companion object {
        private const val TAG = "CalendarExport"
        private const val PERIODIC = "calendar_export"
        private const val NOW = "calendar_export_now"
        private const val EXAMS_AHEAD_MONTHS = 6L
        private const val EVENTS_AHEAD_MONTHS = 6L

        /** Schedules or cancels the automatic run according to the settings. */
        fun ensure(context: Context) {
            val work = WorkManager.getInstance(context)
            val hours = CalendarExportPrefs.intervalHours(context)

            if (!CalendarExportPrefs.isEnabled(context) || hours <= 0) {
                work.cancelUniqueWork(PERIODIC)
                return
            }

            val request = PeriodicWorkRequestBuilder<CalendarExportWorker>(hours.toLong(), TimeUnit.HOURS).build()
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CalendarExportWorker>().build(),
            )
        }
    }
}
