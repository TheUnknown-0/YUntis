package sh.lsx.untisx.ui.game

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import sh.lsx.untisx.R
import java.time.LocalDate
import kotlin.math.ceil

private object DinoPrefs {
    private const val NAME = "dino_game"
    private const val KEY_BEST = "best_score"

    fun best(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_BEST, 0)

    fun saveBest(context: Context, score: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit { putInt(KEY_BEST, score) }
    }
}

/**
 * Keeps the running round alive across rotation and other configuration changes, and
 * while the user looks at another screen of the app. Coming back always lands in the pause.
 */
internal class DinoSession : ViewModel() {
    private var world: DinoWorld? = null
    private var worldDays: List<DinoDay>? = null

    var round by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(false)

    /** Seconds until the game goes on after a pause. Not snapshot state: it changes every frame. */
    var countdown = 0f
    var newBest by mutableStateOf(false)
    var reported = false

    fun worldFor(days: List<DinoDay>, winter: Boolean): DinoWorld {
        val current = world
        if (current != null && worldDays == days) return current
        return DinoWorld(days, winter = winter).also {
            world = it
            worldDays = days
            paused = false
            countdown = 0f
            newBest = false
            reported = false
        }
    }

    fun restart() {
        world = null
        round++
    }

    fun pause() {
        val current = world ?: return
        if (current.phase == DinoWorld.Phase.RUNNING) {
            current.releaseJump()
            current.setDuck(false)
            paused = true
        }
    }

    fun resume() {
        if (!paused) return
        countdown = RESUME_COUNTDOWN
        paused = false
    }

    companion object {
        const val RESUME_COUNTDOWN = 1.5f
    }
}

/**
 * What the HUD shows. The frame loop writes into it every frame, but a value only
 * triggers a recomposition when it really changed.
 */
private class HudState {
    var score by mutableIntStateOf(0)
    var minute by mutableIntStateOf(8 * 60)
    var stars by mutableIntStateOf(0)
    var streak by mutableIntStateOf(0)
    var phase by mutableStateOf(DinoWorld.Phase.READY)
    var segment by mutableStateOf<Segment?>(null)
    var overVisible by mutableStateOf(false)
    var duckHint by mutableStateOf(false)
    var countdown by mutableIntStateOf(0)
    var pulse by mutableIntStateOf(0)

    fun sync(world: DinoWorld) {
        val x = world.dinoX
        score = world.score
        minute = world.minuteAt(x)
        stars = world.stars
        streak = world.streak
        phase = world.phase
        segment = world.segmentAt(x)
        val wait = if (world.deathCause == DinoWorld.DeathCause.HIT) 0.75f else 0.15f
        overVisible = world.phase == DinoWorld.Phase.OVER && world.time - world.deathTime > wait
        duckHint = world.phase == DinoWorld.Phase.RUNNING && world.ducks < 3 && planeAhead(world, x)
    }

    private fun planeAhead(world: DinoWorld, x: Float): Boolean {
        for (i in world.obstacles.indices) {
            val o = world.obstacles[i]
            if (o.kind == ObstacleKind.PLANE && o.x - x > -40f && o.x - x < 900f) return true
        }
        return false
    }
}

@Composable
fun DinoGameScreen(
    days: List<DinoDay>,
    usesRealTimetable: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val session: DinoSession = viewModel(key = "dino_session") { DinoSession() }
    val winter = remember { LocalDate.now().monthValue.let { it == 12 || it <= 2 } }
    val round = session.round
    val world = remember(session, round, days) { session.worldFor(days, winter) }
    val hud = remember(world) { HudState().also { it.sync(world) } }
    var frame by remember { mutableLongStateOf(0L) }
    var best by remember { mutableIntStateOf(DinoPrefs.best(context)) }

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // system animations switched off: no shake, calmer scenery, no bouncing numbers
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val textMeasurer = rememberTextMeasurer()
    val signCancelled = stringResource(R.string.dino_sign_cancelled)
    val signInvented = stringResource(R.string.dino_sign_invented)
    val signSchoolOut = stringResource(R.string.dino_sign_school_out)
    val renderer = remember(textMeasurer, signCancelled, signInvented, signSchoolOut) {
        DinoRenderer(textMeasurer, DinoLabels(signCancelled, signInvented, signSchoolOut))
    }
    SideEffect {
        renderer.darkTheme = darkTheme
        renderer.reducedMotion = reducedMotion
    }

    // the app goes to the background, the screen rotates or the user leaves: pause
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { session.pause() }
    DisposableEffect(world) {
        onDispose { session.pause() }
    }

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            if (session.paused) {
                // no frames at all while paused
                snapshotFlow { session.paused }.first { !it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(1f / 20f)
                last = now
                if (session.countdown > 0f) {
                    session.countdown -= dt
                    hud.countdown = ceil(session.countdown / 0.5f).toInt().coerceAtLeast(0)
                } else if (dt > 0.02f) {
                    // slow frame: two smaller steps keep jumps and collisions exact
                    world.step(dt / 2f)
                    world.step(dt / 2f)
                } else {
                    world.step(dt)
                }
                hud.sync(world)
                frame = now
            }

            val events = world.pollEvents()
            if (events and (DinoWorld.EVENT_CLEAR or DinoWorld.EVENT_DAY) != 0) hud.pulse++
            if (events and DinoWorld.EVENT_DAY != 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (events and (DinoWorld.EVENT_CRASH or DinoWorld.EVENT_FALL) != 0) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (world.phase == DinoWorld.Phase.OVER && !session.reported) {
                session.reported = true
                if (world.score > best) {
                    best = world.score
                    session.newBest = true
                    DinoPrefs.saveBest(context, best)
                }
            }
        }
    }

    val canPlay = { !session.paused && session.countdown <= 0f }
    val focusRequester = remember { FocusRequester() }
    var jumpKeyDown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val gameDescription = stringResource(R.string.dino_a11y_game)
    val jumpLabel = stringResource(R.string.dino_jump)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF8EC9F2))
            .focusRequester(focusRequester)
            .focusable()
            // hardware keyboard: space or up jumps, down ducks, P pauses
            .onKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp
                when (event.key) {
                    Key.Spacebar, Key.DirectionUp -> {
                        if (down && !jumpKeyDown && canPlay()) {
                            jumpKeyDown = true
                            world.pressJump()
                        } else if (up) {
                            jumpKeyDown = false
                            world.releaseJump()
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (down && canPlay()) world.setDuck(true) else if (up) world.setDuck(false)
                        true
                    }
                    Key.P -> {
                        if (down) {
                            if (session.paused) session.resume() else session.pause()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .semantics {
                    contentDescription = gameDescription
                    onClick(label = jumpLabel) {
                        if (canPlay()) {
                            world.pressJump()
                            world.releaseJump()
                        }
                        true
                    }
                }
                .pointerInput(world) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (canPlay()) world.pressJump()
                        waitForUpOrCancellation()
                        world.releaseJump()
                    }
                },
        ) {
            frame // read so the canvas redraws every frame
            renderer.draw(this, world)
        }

        if (session.paused) {
            Box(Modifier.fillMaxSize().background(Color(0x66000814)))
        }

        Hud(
            hud = hud,
            best = best,
            reducedMotion = reducedMotion,
            showPause = hud.phase == DinoWorld.Phase.RUNNING && !session.paused,
            onPause = { session.pause() },
        )

        // duck button, bottom left. Everything else on the screen jumps.
        AnimatedVisibility(
            visible = hud.phase == DinoWorld.Phase.RUNNING && !session.paused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        ) {
            DuckButton(
                highlighted = hud.duckHint,
                onDuck = { down -> if (!down || canPlay()) world.setDuck(down) },
            )
        }

        AnimatedVisibility(
            visible = hud.duckHint && !session.paused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 100.dp, bottom = 38.dp),
        ) {
            HudChip(text = stringResource(R.string.dino_duck_hint), strong = true)
        }

        if (hud.countdown > 0 && !session.paused) {
            Text(
                text = hud.countdown.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x991D2B3A), CircleShape)
                    .padding(horizontal = 36.dp, vertical = 12.dp),
            )
        }

        Overlay(visible = hud.phase == DinoWorld.Phase.READY) {
            Panel(
                // a tap on the panel starts the game just like a tap next to it
                modifier = Modifier.pointerInput(world) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (canPlay()) world.pressJump()
                        waitForUpOrCancellation()
                        world.releaseJump()
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.dino_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dino_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dino_controls),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!usesRealTimetable) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.dino_sample_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.dino_tap_to_start),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Overlay(visible = session.paused) {
            Panel {
                Text(
                    text = stringResource(R.string.dino_paused_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dino_score, hud.score),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { session.restart() }) {
                        Text(stringResource(R.string.dino_restart))
                    }
                    Button(onClick = { session.resume() }) {
                        Text(stringResource(R.string.dino_resume))
                    }
                }
            }
        }

        Overlay(visible = hud.overVisible, pop = !reducedMotion) {
            Panel(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = stringResource(R.string.dino_game_over),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dino_score, hud.score),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (session.newBest) {
                    Text(
                        text = stringResource(R.string.dino_new_best),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.dino_best, best),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.dino_summary_full,
                        world.jumpedCancels,
                        world.jumpedObstacles,
                        world.stars,
                        world.daysDone,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (world.bestStreak >= 3) {
                    Text(
                        text = stringResource(R.string.dino_best_streak, world.bestStreak),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = { session.restart() }) {
                    Text(stringResource(R.string.dino_again))
                }
            }
        }
    }
}

/** Centers a panel and lets it scroll when the screen is too low for it (landscape phones). */
@Composable
private fun Overlay(visible: Boolean, pop: Boolean = false, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 56.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = if (pop) fadeIn() + scaleIn(initialScale = 0.9f) else fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .widthIn(max = 440.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun Hud(
    hud: HudState,
    best: Int,
    reducedMotion: Boolean,
    showPause: Boolean,
    onPause: () -> Unit,
) {
    val segment = hud.segment
    val minute = hud.minute
    val clock = remember(minute) { "%02d:%02d".format(minute / 60, minute % 60) }
    val lesson = segment?.lesson
    val now = when {
        segment == null -> ""
        segment.kind == SegmentKind.SCHOOL_START -> stringResource(R.string.dino_school_start)
        segment.kind == SegmentKind.SCHOOL_END -> stringResource(R.string.dino_school_end)
        segment.kind == SegmentKind.BREAK -> stringResource(R.string.dino_break)
        lesson != null && segment.cancelled -> stringResource(R.string.dino_cancelled, lesson.name)
        lesson != null -> lesson.name
        else -> ""
    }

    // the score chip bounces once for every bonus
    val pop = remember { Animatable(1f) }
    LaunchedEffect(hud.pulse) {
        if (hud.pulse > 0 && !reducedMotion) {
            pop.snapTo(1.22f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            HudChip(text = "${segment?.dayTitle.orEmpty()}  $clock".trim())
            if (now.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HudChip(text = now, strong = segment?.cancelled == true)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.End) {
                HudChip(
                    text = stringResource(R.string.dino_score, hud.score),
                    strong = true,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
                )
                Spacer(Modifier.height(4.dp))
                HudChip(text = stringResource(R.string.dino_best, best))
                if (hud.stars > 0) {
                    Spacer(Modifier.height(4.dp))
                    HudChip(text = "★ ${hud.stars}")
                }
                if (hud.streak >= 3) {
                    Spacer(Modifier.height(4.dp))
                    HudChip(text = stringResource(R.string.dino_streak, hud.streak), strong = true)
                }
            }
            if (showPause) {
                Spacer(Modifier.width(8.dp))
                PauseButton(onPause)
            }
        }
    }
}

@Composable
private fun HudChip(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        color = if (strong) scheme.inverseOnSurface else scheme.onSurface,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        fontSize = 14.sp,
        maxLines = 1,
        modifier = modifier
            .background(
                if (strong) scheme.inverseSurface.copy(alpha = 0.86f) else scheme.surface.copy(alpha = 0.84f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PauseButton(onPause: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.dino_pause_action)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(scheme.surface.copy(alpha = 0.84f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onPause)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val bar = scheme.onSurface
        Canvas(Modifier.size(18.dp)) {
            val w = size.width * 0.3f
            val r = CornerRadius(w / 3f, w / 3f)
            drawRoundRect(bar, Offset(size.width * 0.12f, 0f), Size(w, size.height), r)
            drawRoundRect(bar, Offset(size.width * 0.58f, 0f), Size(w, size.height), r)
        }
    }
}

/** Held down: the dino ducks. In the air it dives back to the ground. */
@Composable
private fun DuckButton(highlighted: Boolean, onDuck: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.dino_duck)
    val currentOnDuck by rememberUpdatedState(onDuck)
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) scheme.primaryContainer.copy(alpha = 0.95f) else scheme.surface.copy(alpha = 0.8f),
            )
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    currentOnDuck(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        // also when the button disappears while it is held
                        currentOnDuck(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.size(40.dp),
        )
    }
}
