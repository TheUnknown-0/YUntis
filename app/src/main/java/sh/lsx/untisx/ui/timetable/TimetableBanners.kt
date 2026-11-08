package sh.lsx.untisx.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled

@Composable
internal fun ErrorBanner(
    text: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(text, onRetry, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, modifier)
}

@Composable
internal fun InfoBanner(
    text: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(text, onRetry, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, modifier)
}

@Composable
private fun Banner(
    text: String?,
    onRetry: () -> Unit,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    var lastText by remember { mutableStateOf(text.orEmpty()) }
    val animations = LocalAnimationsEnabled.current

    if (text != null) {
        lastText = text
    }

    AnimatedVisibility(
        visible = text != null,
        enter = slideInVertically(
            animationSpec = if (animations) AppMotion.spatial() else snap(),
        ) { height -> -height } +
            fadeIn(animationSpec = if (animations) AppMotion.effects() else snap()),
        exit = slideOutVertically(
            animationSpec = if (animations) AppMotion.spatial() else snap(),
        ) { height -> -height } +
            fadeOut(animationSpec = if (animations) AppMotion.effects() else snap()),
        modifier = modifier,
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.9f),
            shape = appCardShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lastText,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                    modifier = Modifier.weight(1f),
                )

                TextButton(onClick = Haptics.haptic(onRetry)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
