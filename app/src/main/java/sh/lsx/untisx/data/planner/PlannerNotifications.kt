package sh.lsx.untisx.data.planner

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.OwnExamsStore
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.notifications.NotificationPrefs
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.notifications.TimetableSource
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.util.AppDateFormats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Schedules the weekly preview and the evening get-up / leave notice. */
object PlannerScheduler {
    const val KIND_WEEK = "week"
    const val KIND_COMMUTE = "commute"
    private const val EXTRA_KIND = "kind"
    private const val REQUEST_WEEK = 4401
    private const val REQUEST_COMMUTE = 4402

    fun ensureAll(context: Context) {
        schedule(context, KIND_WEEK)
        schedule(context, KIND_COMMUTE)
    }

    private fun enabled(context: Context, kind: String): Boolean =
        NotificationPrefs.isEnabled(context) && Notifications.canPost(context) &&
            if (kind == KIND_WEEK) PlannerPrefs.weekEnabled(context) else PlannerPrefs.commuteEnabled(context)

    fun nextWeekTrigger(now: ZonedDateTime, day: DayOfWeek, minute: Int): ZonedDateTime {
        val atTime = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        val candidate = atTime.with(TemporalAdjusters.nextOrSame(day))
        return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
    }

    fun nextDailyTrigger(now: ZonedDateTime, minute: Int): ZonedDateTime {
        val today = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun schedule(context: Context, kind: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context, kind)
        alarm.cancel(intent)

        if (!enabled(context, kind)) return

        val now = ZonedDateTime.now()
        val trigger = if (kind == KIND_WEEK) {
            nextWeekTrigger(now, PlannerPrefs.weekDay(context), PlannerPrefs.weekMinute(context))
        } else {
            nextDailyTrigger(now, PlannerPrefs.commuteMinute(context))
        }.toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
        }
    }

    private fun pendingIntent(context: Context, kind: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (kind == KIND_WEEK) REQUEST_WEEK else REQUEST_COMMUTE,
        Intent(context, PlannerReceiver::class.java).putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun runNow(context: Context, kind: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "planner_$kind",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PlannerWorker>().setInputData(workDataOf(EXTRA_KIND to kind)).build(),
        )
    }

    internal fun kindOf(intent: Intent) = intent.getStringExtra(EXTRA_KIND)
    internal const val INPUT_KIND = EXTRA_KIND
}

class PlannerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = PlannerScheduler.kindOf(intent) ?: return
        PlannerScheduler.runNow(context, kind)
        PlannerScheduler.schedule(context, kind)
    }
}

class PlannerWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val kind = inputData.getString(PlannerScheduler.INPUT_KIND) ?: return Result.success()

        if (!NotificationPrefs.isEnabled(context) || !Notifications.canPost(context)) return Result.success()

        AccountStore.ensureLoaded(context)
        val auth = AccountStore.active ?: return Result.success()

        try {
            if (kind == PlannerScheduler.KIND_WEEK) week(context, auth) else commute(context, auth)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "$kind failed: ${e.javaClass.simpleName}")
        }

        return Result.success()
    }

    private suspend fun lessons(context: Context, auth: AuthData, from: LocalDate, to: LocalDate): List<TimetableEntry> {
        val fetched = try {
            TimetableSource.fetchRange(context, auth, from, to).let { result ->
                result.fresh?.let { AccountPrefs.updateSession(context, it) }
                result.entries
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "fetch failed, using cache: ${e.javaClass.simpleName}")
            null
        }

        if (fetched != null) return fetched

        val cache = TimetableCache.load(context, AccountPrefs.accountId(auth)) ?: return emptyList()
        val range = from.toUntisDate()..to.toUntisDate()
        return cache.weeks.values.flatten().filter { it.date in range }.distinctBy { Triple(it.id, it.date, it.startTime) }
    }

    private suspend fun week(context: Context, auth: AuthData) {
        val accountId = AccountPrefs.accountId(auth)
        val (from, to) = nextSchoolWeek(LocalDate.now())
        val cache = TimetableCache.load(context, accountId)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

        val notebook = (NotebookRepository(context, accountId).load() as? NotebookReadResult.Loaded)?.data?.items.orEmpty()

        val lines = WeekPreview.lines(
            from = from,
            to = to,
            lessons = lessons(context, auth, from, to),
            exams = ExamsStore.load(context, accountId)?.exams.orEmpty() + OwnExamsStore.load(context, accountId),
            events = cache?.events.orEmpty(),
            holidays = cache?.holidays.orEmpty(),
            homework = notebook,
            subjects = cache?.subjects?.associateBy { it.id }.orEmpty(),
            words = PlannerWords(
                dayShort = { it.getDisplayName(TextStyle.SHORT, locale).trimEnd('.') },
                cancelled = context.getString(R.string.calendar_word_cancelled),
                substituted = context.getString(R.string.calendar_word_substituted),
                exams = context.getString(R.string.exams_title),
                events = context.getString(R.string.events),
                homework = context.getString(R.string.homework_title),
                holidays = context.getString(R.string.calendar_cat_holidays),
                nothing = context.getString(R.string.week_preview_nothing),
            ),
        )

        val title = context.getString(R.string.week_preview_title, AppDateFormats.formatter(context, "dMMMM").format(from))
        notify(context, CHANNEL_WEEK, WEEK_ID, title, lines.joinToString("\n"), from.toUntisDate())
    }

    private suspend fun commute(context: Context, auth: AuthData) {
        val day = LocalDate.now().plusDays(1)
        if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) return

        val plan = Commute.plan(
            lessons(context, auth, day, day),
            PlannerPrefs.wayMinutes(context),
            PlannerPrefs.prepMinutes(context),
        ) ?: return

        if (PlannerPrefs.commuteOnlyChanges(context) && !plan.changed) return

        val time = AppDateFormats.time(context)
        fun t(untis: Int) = Commute.time(untis).format(time)

        val title = when {
            plan.allCancelled -> context.getString(R.string.commute_title_all_cancelled)
            plan.startsLater -> context.getString(R.string.commute_title_later, t(plan.actualStart!!))
            else -> context.getString(R.string.commute_title_normal, t(plan.actualStart!!))
        }

        val text = buildList {
            if (plan.leaveAt != null && plan.wakeAt != null) {
                add(context.getString(R.string.commute_text_times, plan.wakeAt.format(time), plan.leaveAt.format(time)))
            }
            if (plan.startsLater) add(context.getString(R.string.commute_text_usual_start, t(plan.plannedStart)))
            if (plan.endsEarlier) add(context.getString(R.string.commute_text_end, t(plan.actualEnd!!), t(plan.plannedEnd)))
        }.joinToString("\n")

        notify(context, CHANNEL_COMMUTE, COMMUTE_ID, title, text, day.toUntisDate())
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, channel: String, id: Int, title: String, text: String, focusDate: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                channel,
                context.getString(if (channel == CHANNEL_WEEK) R.string.week_preview_channel else R.string.commute_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Notifications.EXTRA_FOCUS_DATE, focusDate),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val TAG = "Planner"
        const val CHANNEL_WEEK = "week_preview"
        const val CHANNEL_COMMUTE = "commute"
        const val WEEK_ID = 610_001
        const val COMMUTE_ID = 620_001
    }
}
