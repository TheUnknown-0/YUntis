package sh.lsx.untisx.data.planner

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.R
import sh.lsx.untisx.data.automation.WakeAlarm
import sh.lsx.untisx.data.notifications.Notifications

enum class PomodoroPhase { WORK, BREAK, DONE }

/** A running study session: rounds of [workMinutes] study and [breakMinutes] break. */
@Serializable
data class PomodoroSession(
    val label: String,
    val examKey: String = "",
    /** The study block that is checked off at the end; blank for free study. */
    val slotKey: String = "",
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val rounds: Int = 2,
    val round: Int = 1,
    val phase: PomodoroPhase = PomodoroPhase.WORK,
    /** End of the phase (epoch millis) while running. */
    val endsAt: Long = 0,
    /** Time left in the phase while paused; 0 when running. */
    val pausedLeft: Long = 0,
    /** When the current phase began, for the breathing animation. */
    val phaseStartedAt: Long = 0,
) {
    val paused: Boolean get() = pausedLeft > 0
    fun phaseMillis(): Long = (if (phase == PomodoroPhase.BREAK) breakMinutes else workMinutes) * 60_000L
    fun remaining(now: Long): Long = when {
        phase == PomodoroPhase.DONE -> 0
        paused -> pausedLeft
        else -> (endsAt - now).coerceAtLeast(0)
    }
}

/** The pure steps of a session, also used by the tests. */
object PomodoroRules {
    /** Rounds that fit a study block of [minutes]; at least one. */
    fun roundsFor(minutes: Long, work: Int, brk: Int): Int = (minutes / (work + brk).coerceAtLeast(1)).toInt().coerceIn(1, 8)

    fun start(label: String, examKey: String, slotKey: String, work: Int, brk: Int, rounds: Int, now: Long) = PomodoroSession(
        label = label, examKey = examKey, slotKey = slotKey,
        workMinutes = work, breakMinutes = brk, rounds = rounds,
        endsAt = now + work * 60_000L, phaseStartedAt = now,
    )

    /** The next phase: study, break, study, ..., and done after the last study phase. */
    fun next(session: PomodoroSession, now: Long): PomodoroSession = when (session.phase) {
        PomodoroPhase.WORK ->
            if (session.round >= session.rounds) {
                session.copy(phase = PomodoroPhase.DONE, endsAt = 0, pausedLeft = 0, phaseStartedAt = now)
            } else {
                session.copy(phase = PomodoroPhase.BREAK, endsAt = now + session.breakMinutes * 60_000L, pausedLeft = 0, phaseStartedAt = now)
            }
        PomodoroPhase.BREAK ->
            session.copy(phase = PomodoroPhase.WORK, round = session.round + 1, endsAt = now + session.workMinutes * 60_000L, pausedLeft = 0, phaseStartedAt = now)
        PomodoroPhase.DONE -> session
    }

    fun pause(session: PomodoroSession, now: Long) =
        if (session.paused || session.phase == PomodoroPhase.DONE) session else session.copy(pausedLeft = (session.endsAt - now).coerceAtLeast(1))

    fun resume(session: PomodoroSession, now: Long) =
        if (!session.paused) session else session.copy(endsAt = now + session.pausedLeft, pausedLeft = 0)
}

/** Keeps the session, its phase alarms and its notification. One session at a time. */
object Pomodoro {
    private const val PREFS = "pomodoro"
    private const val CHANNEL_RUNNING = "pomodoro_running"
    private const val CHANNEL_ALERT = "pomodoro_alert"
    private const val ID_RUNNING = 750_001
    private const val ID_ALERT = 750_002
    private const val REQUEST_PHASE = 4801
    const val ACTION_PHASE_END = "de.theunknown.yuntis.pomodoro.PHASE_END"
    const val ACTION_PAUSE = "de.theunknown.yuntis.pomodoro.PAUSE"
    const val ACTION_RESUME = "de.theunknown.yuntis.pomodoro.RESUME"
    const val ACTION_STOP = "de.theunknown.yuntis.pomodoro.STOP"

    private val json = Json { ignoreUnknownKeys = true }
    private val _session = MutableStateFlow<PomodoroSession?>(null)
    val session: StateFlow<PomodoroSession?> = _session.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun workMinutes(context: Context) = prefs(context).getInt("work", 25)
    fun breakMinutes(context: Context) = prefs(context).getInt("break", 5)
    fun setDurations(context: Context, work: Int, brk: Int) =
        prefs(context).edit().putInt("work", work.coerceIn(5, 90)).putInt("break", brk.coerceIn(1, 30)).apply()

    /** The chosen break exercise (see [BreakExercise]). */
    fun exercise(context: Context): String = prefs(context).getString("exercise", "BOX").orEmpty()
    fun setExercise(context: Context, name: String) = prefs(context).edit().putString("exercise", name).apply()

    fun load(context: Context): PomodoroSession? {
        val stored = prefs(context).getString("session", null)
            ?.let { runCatching { json.decodeFromString<PomodoroSession>(it) }.getOrNull() }
        _session.value = stored
        return stored
    }

    private fun save(context: Context, session: PomodoroSession?) {
        prefs(context).edit().apply {
            if (session == null) remove("session") else putString("session", json.encodeToString(session))
        }.apply()
        _session.value = session
    }

    fun start(context: Context, label: String, examKey: String, slotKey: String, work: Int, brk: Int, rounds: Int) {
        setDurations(context, work, brk)
        val session = PomodoroRules.start(label, examKey, slotKey, work, brk, rounds, System.currentTimeMillis())
        save(context, session)
        schedule(context, session)
        showRunning(context, session)
    }

    fun pause(context: Context) = update(context) { PomodoroRules.pause(it, System.currentTimeMillis()) }
    fun resume(context: Context) = update(context) { PomodoroRules.resume(it, System.currentTimeMillis()) }

    /** Ends the current phase now (also when its alarm goes off). */
    fun skip(context: Context, alert: Boolean = false) {
        val current = load(context) ?: return
        val next = PomodoroRules.next(current, System.currentTimeMillis())
        if (next.phase == PomodoroPhase.DONE) {
            finish(context, current, alert)
            return
        }
        save(context, next)
        schedule(context, next)
        showRunning(context, next)
        if (alert) showAlert(context, next)
    }

    /** Done with studying: the study block is checked off. */
    fun finishNow(context: Context) {
        val current = load(context) ?: return
        finish(context, current, alert = false)
    }

    private fun finish(context: Context, session: PomodoroSession, alert: Boolean) {
        if (session.examKey.isNotBlank() && session.slotKey.isNotBlank()) {
            PlannerPrefs.setSlotDone(context, session.examKey, session.slotKey, true)
        }
        val done = session.copy(phase = PomodoroPhase.DONE, endsAt = 0, pausedLeft = 0, phaseStartedAt = System.currentTimeMillis())
        save(context, done)
        cancelAlarm(context)
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        if (alert) showAlert(context, done)
    }

    fun stop(context: Context) {
        save(context, null)
        cancelAlarm(context)
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        NotificationManagerCompat.from(context).cancel(ID_ALERT)
    }

    private fun update(context: Context, change: (PomodoroSession) -> PomodoroSession) {
        val current = load(context) ?: return
        val next = change(current)
        save(context, next)
        if (next.paused) cancelAlarm(context) else schedule(context, next)
        showRunning(context, next)
    }

    private fun phaseIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_PHASE,
        Intent(context, PomodoroReceiver::class.java).setAction(ACTION_PHASE_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(context: Context, session: PomodoroSession) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        if (session.phase == PomodoroPhase.DONE || session.paused) return cancelAlarm(context)
        if (WakeAlarm.canScheduleExact(context)) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endsAt, phaseIntent(context))
        } else {
            alarm.setWindow(AlarmManager.RTC_WAKEUP, session.endsAt, 30_000L, phaseIntent(context))
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(phaseIntent(context))
    }

    private fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.pomodoro_channel_running), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, context.getString(R.string.pomodoro_channel_alert), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun openScreen(context: Context) = PendingIntent.getActivity(
        context,
        REQUEST_PHASE + 1,
        Intent(context, sh.lsx.untisx.ui.planner.PomodoroActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, name: String, request: Int) = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, PomodoroReceiver::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun phaseTitle(context: Context, session: PomodoroSession) = when (session.phase) {
        PomodoroPhase.WORK -> context.getString(R.string.pomodoro_phase_work_round, session.round, session.rounds)
        PomodoroPhase.BREAK -> context.getString(R.string.pomodoro_phase_break)
        PomodoroPhase.DONE -> context.getString(R.string.pomodoro_done_title)
    }

    @SuppressLint("MissingPermission")
    private fun showRunning(context: Context, session: PomodoroSession) {
        if (!Notifications.canPost(context) || session.phase == PomodoroPhase.DONE) return
        channels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(phaseTitle(context, session))
            .setContentText(session.label)
            .setContentIntent(openScreen(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (session.paused) {
            builder.setSubText(context.getString(R.string.pomodoro_paused))
                .addAction(0, context.getString(R.string.pomodoro_resume), action(context, ACTION_RESUME, REQUEST_PHASE + 2))
        } else {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(session.endsAt)
                .setShowWhen(true)
                .addAction(0, context.getString(R.string.pomodoro_pause), action(context, ACTION_PAUSE, REQUEST_PHASE + 3))
        }
        builder.addAction(0, context.getString(R.string.pomodoro_stop), action(context, ACTION_STOP, REQUEST_PHASE + 4))
        NotificationManagerCompat.from(context).notify(ID_RUNNING, builder.build())
    }

    /** A short sound and message when a phase changes. */
    @SuppressLint("MissingPermission")
    private fun showAlert(context: Context, session: PomodoroSession) {
        if (!Notifications.canPost(context)) return
        channels(context)
        val text = when (session.phase) {
            PomodoroPhase.BREAK -> context.getString(R.string.pomodoro_alert_break, session.breakMinutes)
            PomodoroPhase.WORK -> context.getString(R.string.pomodoro_alert_work, session.round, session.rounds)
            PomodoroPhase.DONE -> context.getString(R.string.pomodoro_alert_done)
        }
        NotificationManagerCompat.from(context).notify(
            ID_ALERT,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(phaseTitle(context, session))
                .setContentText(text)
                .setContentIntent(openScreen(context))
                .setAutoCancel(true)
                .setTimeoutAfter(5 * 60_000L)
                .build(),
        )
    }
}

class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Pomodoro.ACTION_PHASE_END -> Pomodoro.skip(context, alert = true)
            Pomodoro.ACTION_PAUSE -> Pomodoro.pause(context)
            Pomodoro.ACTION_RESUME -> Pomodoro.resume(context)
            Pomodoro.ACTION_STOP -> Pomodoro.stop(context)
        }
    }
}
