package sh.lsx.untisx.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The setting a search hit points to: its page scrolls there and lights it up briefly. */
object SettingsFocus {
    var target by mutableStateOf<String?>(null)
}

/** Scrolls this row into view and highlights it when it is the [SettingsFocus.target]. */
@Composable
fun Modifier.settingsFocus(title: String): Modifier {
    val target = SettingsFocus.target
    if (target == null || target != title.trim()) return this

    val requester = remember { BringIntoViewRequester() }
    var lit by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(if (lit) 0.16f else 0f, tween(600), label = "settingsFocus")
    val color = MaterialTheme.colorScheme.primary

    LaunchedEffect(title) {
        // let the page lay out first
        delay(250)
        requester.bringIntoView()
        delay(1800)
        lit = false
        delay(700)
        if (SettingsFocus.target == title.trim()) SettingsFocus.target = null
    }
    return this
        .bringIntoViewRequester(requester)
        .background(color.copy(alpha = alpha), RoundedCornerShape(12.dp))
}
