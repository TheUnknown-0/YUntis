package sh.lsx.untisx.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntOffset
import sh.lsx.untisx.ui.util.LocalAnimatedContentScope
import sh.lsx.untisx.ui.util.LocalSharedTransitionScope
import sh.lsx.untisx.ui.util.motionEffects
import sh.lsx.untisx.ui.util.motionSpatial

@Composable
internal fun AnimatedScreenHost(
    screen: Screen,
    content: @Composable (Screen) -> Unit,
) {
    val spatial = motionSpatial<IntOffset>()
    val effects = motionEffects<Float>()

    SharedTransitionLayout {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { screenTransition(initialState, targetState, spatial, effects) },
            label = "screen",
        ) { target ->
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedContentScope provides this@AnimatedContent,
            ) {
                content(target)
            }
        }
    }
}

private fun screenTransition(
    initial: Screen,
    target: Screen,
    spatial: FiniteAnimationSpec<IntOffset>,
    effects: FiniteAnimationSpec<Float>,
): ContentTransform {
    val forward = target.depth() >= initial.depth()

    val enter = slideInHorizontally(animationSpec = spatial) { width ->
        if (forward) width / 5 else -width / 8
    } + fadeIn(animationSpec = effects)

    val exit = slideOutHorizontally(animationSpec = spatial) { width ->
        if (forward) -width / 8 else width / 5
    } + fadeOut(animationSpec = effects)

    return enter togetherWith exit
}
