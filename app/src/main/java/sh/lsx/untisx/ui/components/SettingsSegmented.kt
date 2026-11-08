package sh.lsx.untisx.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import sh.lsx.untisx.ui.theme.appCardEdge
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.Haptics

@Composable
fun SettingsSegmentedItem(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val morph by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "segmentedItemMorph",
    )

    val hapticOnClick = Haptics.haptic(onClick)

    val outerCorner = 16.dp
    val innerCorner = 4.dp

    val shape = RoundedCornerShape(
        topStart = lerp(if (count == 1 || index == 0) outerCorner else innerCorner, outerCorner, morph),
        topEnd = lerp(if (count == 1 || index == 0) outerCorner else innerCorner, outerCorner, morph),
        bottomStart = lerp(if (count == 1 || index == count - 1) outerCorner else innerCorner, outerCorner, morph),
        bottomEnd = lerp(if (count == 1 || index == count - 1) outerCorner else innerCorner, outerCorner, morph),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .appCardEdge(index)
            .clip(appCardShape(shape, index))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = hapticOnClick,
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SettingsSegmentedRow(
    icon: ImageVector,
    title: String,
    description: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSegmentedItem(
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier,
    ) {
        Spacer(Modifier.width(4.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )

        Spacer(Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
