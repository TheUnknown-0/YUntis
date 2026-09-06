package sh.lsx.untisx.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

val LocalHapticsEnabled = compositionLocalOf { true }

object Haptics {

    @Composable
    fun haptic(onClick: () -> Unit): () -> Unit {
        val hapticsOn = LocalHapticsEnabled.current
        val haptic = LocalHapticFeedback.current
        val current by rememberUpdatedState(onClick)

        return {
            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            current()
        }
    }
}

/** Clickable with haptic feedback (respects the "Haptic feedback" setting). */
@Composable
fun Modifier.tapHaptic(onClick: () -> Unit): Modifier = tapHaptic(interactive = true, onClick = onClick)

/** Clickable with haptic feedback that can be disabled via [interactive]. */
@Composable
fun Modifier.tapHaptic(
    interactive: Boolean,
    onClick: () -> Unit,
): Modifier = composed {
    val hapticsOn = LocalHapticsEnabled.current && interactive
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)

    this.then(
        Modifier.clickable(enabled = interactive) {
            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnClick()
        },
    )
}
