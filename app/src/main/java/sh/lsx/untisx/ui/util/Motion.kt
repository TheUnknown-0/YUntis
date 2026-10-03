package sh.lsx.untisx.ui.util

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Whether the app's own animations are enabled (Appearance setting).
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/**
 * Scopes for shared element transitions, provided by the app root.
 *
 * Screens and cards access them through these locals instead of threading the
 * scopes through every composable signature. When no transition scope is
 * available (previews, tests) [sharedElementBounds] silently falls back to a
 * plain modifier.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedContentScope = compositionLocalOf<AnimatedContentScope?> { null }

/**
 * Material 3 Expressive motion tokens.
 *
 * material3 1.4.0 does not expose [androidx.compose.material3.MotionScheme]
 * publicly, so the expressive spring specs are replicated here to give app
 * transitions the same springy feel.
 */
object AppMotion {
    /** Springy spec for movement: position, size, rotation. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f,
    )

    /** Fast, non-overshooting spec for color and opacity changes. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = 1600f,
    )
}

/** Springy spec for movement, or snap when animations are disabled. */
@Composable
fun <T> motionSpatial(): FiniteAnimationSpec<T> =
    if (LocalAnimationsEnabled.current) AppMotion.spatial() else snap()

/** Fast spec for color/opacity changes, or snap when animations are disabled. */
@Composable
fun <T> motionEffects(): FiniteAnimationSpec<T> =
    if (LocalAnimationsEnabled.current) AppMotion.effects() else snap()

/**
 * Marks an element as a shared element so it morphs between screens
 * (e.g. timetable card -> lesson details header).
 *
 * @param key stable key identifying the same content on both screens.
 */
@Composable
fun Modifier.sharedElementBounds(key: Any): Modifier {
    if (!LocalAnimationsEnabled.current) return this

    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val contentScope = LocalAnimatedContentScope.current ?: return this

    val base = this

    return with(sharedScope) {
        base.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = contentScope,
        )
    }
}

/**
 * Fades and lifts a list item into place the first time a screen appears.
 *
 * Items composed after [appeared] has flipped to true start fully visible, so
 * scrolling does not replay the entrance.
 */
@Composable
fun Modifier.entranceEffect(
    appeared: Boolean,
    index: Int = 0,
): Modifier {
    if (!LocalAnimationsEnabled.current) return this

    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = 260,
            delayMillis = index.coerceAtMost(12) * 25,
            easing = LinearOutSlowInEasing,
        ),
        label = "listItemEntrance",
    )

    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24.dp.toPx()
    }
}

/**
 * Item placement/insert/remove animation, disabled together with the app
 * animations setting.
 */
@Composable
fun LazyItemScope.animatedItem(): Modifier =
    if (LocalAnimationsEnabled.current) {
        Modifier.animateItem()
    } else {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    }
