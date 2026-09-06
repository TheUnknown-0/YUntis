package sh.lsx.untisx.ui.timetable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.ui.util.Haptics

@Composable
internal fun ErrorBanner(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(text, onRetry, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, modifier)
}

@Composable
internal fun InfoBanner(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(text, onRetry, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, modifier)
}

@Composable
private fun Banner(
    text: String,
    onRetry: () -> Unit,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer,
                modifier = Modifier.weight(1f),
            )

            TextButton(onClick = Haptics.haptic(onRetry)) {
                Text("Retry")
            }
        }
    }
}
