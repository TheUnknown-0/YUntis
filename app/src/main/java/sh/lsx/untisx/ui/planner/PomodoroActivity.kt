package sh.lsx.untisx.ui.planner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sh.lsx.untisx.R
import sh.lsx.untisx.data.planner.Pomodoro
import sh.lsx.untisx.data.planner.PomodoroPhase
import sh.lsx.untisx.data.planner.PomodoroRules
import sh.lsx.untisx.data.planner.PomodoroSession
import sh.lsx.untisx.ui.theme.UntisXTheme

/** What to do in a break. */
enum class BreakExercise(val title: Int) {
    BOX(R.string.pomodoro_ex_box),
    RELAX(R.string.pomodoro_ex_478),
    MEDITATE(R.string.pomodoro_ex_meditate),
    FREE(R.string.pomodoro_ex_free),
}

/** One step of a breathing pattern: what to do, how long, and the circle size it ends at. */
private data class BreathStep(val text: Int, val seconds: Int, val scale: Float)

private val BOX_STEPS = listOf(
    BreathStep(R.string.pomodoro_breathe_in, 4, 1f),
    BreathStep(R.string.pomodoro_breathe_hold, 4, 1f),
    BreathStep(R.string.pomodoro_breathe_out, 4, 0.55f),
    BreathStep(R.string.pomodoro_breathe_hold, 4, 0.55f),
)
private val RELAX_STEPS = listOf(
    BreathStep(R.string.pomodoro_breathe_in, 4, 1f),
    BreathStep(R.string.pomodoro_breathe_hold, 7, 1f),
    BreathStep(R.string.pomodoro_breathe_out, 8, 0.55f),
)
private val MEDITATION = listOf(
    R.string.pomodoro_med_1, R.string.pomodoro_med_2, R.string.pomodoro_med_3,
    R.string.pomodoro_med_4, R.string.pomodoro_med_5, R.string.pomodoro_med_6,
)

/** Study timer for a study block: study rounds with breaks, breathing or a short meditation in between. */
class PomodoroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Pomodoro.load(this)

        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val examKey = intent.getStringExtra(EXTRA_EXAM).orEmpty()
        val slotKey = intent.getStringExtra(EXTRA_SLOT).orEmpty()
        val slotMinutes = intent.getLongExtra(EXTRA_MINUTES, 0L)

        setContent {
            UntisXTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val session by Pomodoro.session.collectAsState()
                    val current = session
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (current == null) {
                            StartPanel(label, slotMinutes) { work, brk, rounds ->
                                Pomodoro.start(this@PomodoroActivity, label.ifBlank { getString(R.string.pomodoro_free) }, examKey, slotKey, work, brk, rounds)
                            }
                        } else {
                            Running(current, onClose = { finish() })
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_EXAM = "exam"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_MINUTES = "minutes"

        fun intent(context: Context, label: String, examKey: String = "", slotKey: String = "", minutes: Long = 0) =
            Intent(context, PomodoroActivity::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_EXAM, examKey)
                .putExtra(EXTRA_SLOT, slotKey)
                .putExtra(EXTRA_MINUTES, minutes)
    }
}

@Composable
private fun Stepper(label: String, value: Int, unit: String, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(enabled = value - step >= range.first, onClick = { onChange(value - step) }) { Text("−", fontSize = 20.sp) }
        Text("$value $unit", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        TextButton(enabled = value + step <= range.last, onClick = { onChange(value + step) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun StartPanel(label: String, slotMinutes: Long, onStart: (Int, Int, Int) -> Unit) {
    val context = LocalContext.current
    var work by remember { mutableIntStateOf(Pomodoro.workMinutes(context)) }
    var brk by remember { mutableIntStateOf(Pomodoro.breakMinutes(context)) }
    var rounds by remember { mutableIntStateOf(if (slotMinutes > 0) PomodoroRules.roundsFor(slotMinutes, Pomodoro.workMinutes(context), Pomodoro.breakMinutes(context)) else 2) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.pomodoro_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.pomodoro_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Stepper(stringResource(R.string.pomodoro_work), work, "min", 5..90, 5) { work = it }
        Stepper(stringResource(R.string.pomodoro_break), brk, "min", 1..30) { brk = it }
        Stepper(stringResource(R.string.pomodoro_rounds), rounds, "×", 1..8) { rounds = it }
        Text(
            stringResource(R.string.pomodoro_total, rounds * work + (rounds - 1) * brk),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onStart(work, brk, rounds) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.pomodoro_start), fontSize = 18.sp)
        }
    }
}

@Composable
private fun Running(session: PomodoroSession, onClose: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // the phase alarm may be late in doze; the screen moves on by itself when it is open
            val s = Pomodoro.session.value
            if (s != null && !s.paused && s.phase != PomodoroPhase.DONE && s.endsAt in 1..now) Pomodoro.skip(context, alert = false)
            delay(50)
        }
    }

    val remaining = session.remaining(now)
    val progress = if (session.phase == PomodoroPhase.DONE) 1f else 1f - remaining.toFloat() / session.phaseMillis()
    val breakTime = session.phase == PomodoroPhase.BREAK
    val ring = if (breakTime) Color(0xFF43A047) else MaterialTheme.colorScheme.primary

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(session.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(
            when (session.phase) {
                PomodoroPhase.WORK -> stringResource(R.string.pomodoro_phase_work_round, session.round, session.rounds)
                PomodoroPhase.BREAK -> stringResource(R.string.pomodoro_phase_break)
                PomodoroPhase.DONE -> stringResource(R.string.pomodoro_done_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = ring,
        )

        // round dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(session.rounds) { i ->
                val done = i + 1 < session.round || session.phase == PomodoroPhase.DONE || (i + 1 == session.round && breakTime)
                Box(Modifier.size(10.dp).background(if (done) ring else ring.copy(alpha = 0.25f), CircleShape))
            }
        }

        if (session.phase == PomodoroPhase.DONE) {
            Spacer(Modifier.height(24.dp))
            Text("🎉", fontSize = 64.sp)
            Text(
                stringResource(if (session.slotKey.isNotBlank()) R.string.pomodoro_done_slot else R.string.pomodoro_done_text),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { Pomodoro.stop(context); onClose() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.close))
            }
            return@Column
        }

        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            val track = ring.copy(alpha = 0.15f)
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(ring, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (breakTime) {
                BreakCompanion(session, now, ring)
            } else {
                Text(clock(remaining), fontSize = 64.sp, fontWeight = FontWeight.Black)
            }
        }
        if (breakTime) Text(clock(remaining), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (breakTime) {
            ExercisePicker()
        } else {
            Text(stringResource(R.string.pomodoro_work_tip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (session.paused) Pomodoro.resume(context) else Pomodoro.pause(context) }) {
                Text(stringResource(if (session.paused) R.string.pomodoro_resume else R.string.pomodoro_pause))
            }
            OutlinedButton(onClick = { Pomodoro.skip(context) }) {
                Text(stringResource(if (breakTime) R.string.pomodoro_skip_break else R.string.pomodoro_skip_work))
            }
        }
        Button(onClick = { Pomodoro.finishNow(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (session.slotKey.isNotBlank()) R.string.pomodoro_finish_slot else R.string.pomodoro_finish))
        }
        TextButton(onClick = { Pomodoro.stop(context); onClose() }) { Text(stringResource(R.string.pomodoro_stop)) }
    }
}

@Composable
private fun ExercisePicker() {
    val context = LocalContext.current
    var exercise by remember { mutableStateOf(Pomodoro.exercise(context)) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BreakExercise.entries.forEach { ex ->
            FilterChip(
                selected = exercise == ex.name,
                onClick = {
                    exercise = ex.name
                    Pomodoro.setExercise(context, ex.name)
                },
                label = { Text(stringResource(ex.title)) },
            )
        }
    }
}

/** The breathing circle or the meditation prompts inside the ring during a break. */
@Composable
private fun BreakCompanion(session: PomodoroSession, now: Long, color: Color) {
    val context = LocalContext.current
    // read on every frame: the chips below write the choice
    val chosen = runCatching { BreakExercise.valueOf(Pomodoro.exercise(context)) }.getOrDefault(BreakExercise.BOX)
    val elapsed = (now - session.phaseStartedAt).coerceAtLeast(0)

    when (chosen) {
        BreakExercise.BOX, BreakExercise.RELAX -> {
            val steps = if (chosen == BreakExercise.BOX) BOX_STEPS else RELAX_STEPS
            val cycle = steps.sumOf { it.seconds } * 1000L
            var t = if (session.paused) 0L else elapsed % cycle
            var index = 0
            while (t >= steps[index].seconds * 1000L) {
                t -= steps[index].seconds * 1000L
                index++
            }
            val step = steps[index]
            val from = steps[(index - 1 + steps.size) % steps.size].scale
            val fraction = t / (step.seconds * 1000f)
            val eased = (1 - kotlin.math.cos(fraction * Math.PI).toFloat()) / 2
            val scale = from + (step.scale - from) * eased
            val secondsLeft = step.seconds - (t / 1000).toInt()
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(200.dp).scale(scale).background(color.copy(alpha = 0.22f), CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(step.text), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("$secondsLeft", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        BreakExercise.MEDITATE -> {
            val prompt = MEDITATION[((elapsed / 20_000L) % MEDITATION.size).toInt()]
            AnimatedContent(targetState = prompt, label = "meditation") { id ->
                Text(
                    stringResource(id),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(36.dp),
                )
            }
        }

        BreakExercise.FREE -> Text(
            stringResource(R.string.pomodoro_free_break),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(36.dp),
        )
    }
}

private fun clock(millis: Long): String {
    val total = (millis + 999) / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}
