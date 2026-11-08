package sh.lsx.untisx.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.ui.theme.LocalThemeAppearance
import sh.lsx.untisx.ui.theme.notebookHighlighter
import sh.lsx.untisx.ui.theme.notebookPaperSeed
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
internal fun TimetableEventOverlay(
    span: EventSpan,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notebook = LocalThemeAppearance.current.style == ThemeStyle.NOTEBOOK
    val color = MaterialTheme.colorScheme.primary
    val eventLabel = span.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.event)

    val decoration = if (notebook) {
        Modifier.notebookHighlighter(
            seed = notebookPaperSeed(span.entry.id, span.entry.date, span.start),
            dark = LocalThemeAppearance.current.dark,
        )
    } else {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
    }

    Box(
        modifier = modifier
            .height(height)
            .then(decoration)
            .then(if (notebook) Modifier.semantics { contentDescription = eventLabel } else Modifier)
            .tapHaptic(onClick),
        contentAlignment = Alignment.TopStart,
    ) {
        if (!notebook && height >= 18.dp) {
            Text(
                text = span.title.orEmpty(),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}
