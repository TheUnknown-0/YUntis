package sh.lsx.untisx.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectTotals
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import java.util.Locale

/** Fixed traffic-light colors, as asked: held green, substitution yellow, cancelled red. */
internal val StatHeldColor = Color(0xFF43A047)
internal val StatSubstituteColor = Color(0xFFF9A825)
internal val StatCancelledColor = Color(0xFFE53935)

@Composable
internal fun SubjectStatsCard(
    totals: List<SubjectTotals>,
    nameFor: (Int) -> String,
    locale: Locale,
    olderDaysMissing: Boolean,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_by_subject),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(StatHeldColor, stringResource(R.string.stats_legend_held))
                Legend(StatSubstituteColor, stringResource(R.string.stats_legend_substituted))
                Legend(StatCancelledColor, stringResource(R.string.stats_legend_cancelled))
            }

            Text(
                text = stringResource(R.string.stats_by_subject_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )

            if (olderDaysMissing) {
                Text(
                    text = stringResource(R.string.stats_by_subject_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatSubstituteColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            totals.forEachIndexed { index, total ->
                SubjectRow(total, nameFor(total.subjectId), locale, index)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SubjectRow(total: SubjectTotals, name: String, locale: Locale, index: Int) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animations = LocalAnimationsEnabled.current
    val grow by animateFloatAsState(
        targetValue = if (started || !animations) 1f else 0f,
        animationSpec = tween(500, delayMillis = index.coerceAtMost(10) * 40, easing = LinearOutSlowInEasing),
        label = "subjectBar",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_cancel_rate_short, percent(total.cancelRate, locale)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (total.cancelled > 0) StatCancelledColor else StatHeldColor,
            )
        }

        // stacked bar: held | substituted | cancelled, each relative to this subject's planned lessons
        Row(
            modifier = Modifier
                .fillMaxWidth(grow.coerceIn(0.001f, 1f))
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (total.held > 0) Box(Modifier.weight(total.held.toFloat()).fillMaxHeight().background(StatHeldColor))
            if (total.substituted > 0) Box(Modifier.weight(total.substituted.toFloat()).fillMaxHeight().background(StatSubstituteColor))
            if (total.cancelled > 0) Box(Modifier.weight(total.cancelled.toFloat()).fillMaxHeight().background(StatCancelledColor))
        }

        Text(
            text = stringResource(
                R.string.stats_subject_line,
                total.planned,
                total.held,
                total.substituted,
                total.cancelled,
                duration(total.cancelledMinutes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun percent(value: Float, locale: Locale): String = String.format(locale, "%.0f %%", value * 100f)

@Composable
private fun duration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        minutes == 0 -> stringResource(R.string.stats_duration_none)
        h == 0 -> stringResource(R.string.stats_duration_minutes, m)
        m == 0 -> stringResource(R.string.stats_duration_hours, h)
        else -> stringResource(R.string.stats_duration_hours_minutes, h, m)
    }
}
