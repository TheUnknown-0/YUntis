package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.ui.timetable.TIME_FORMAT
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_NAME_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.ENGLISH)

@Composable
fun LessonDetailsScreen(
    entry: TimetableEntry,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val subjectEl = entry.subject.firstOrNull()
    val subjectName = subjectEl
        ?.let { subjectsById[it.id]?.name?.takeIf { name -> name.isNotBlank() } ?: it.name }
        .orEmpty()

    val date = remember(entry.date) { entry.date.toUntisLocalDate() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Column {
                Text(
                    text = subjectName.ifBlank { entry.lstext?.takeIf { it.isNotBlank() } ?: "?" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )

                subjectEl?.let { it -> subjectsById[it.id]?.longName }
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.8f),
                        )
                    }

                Text(
                    text = formatLessonDate(date, entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.8f),
                )
            }
        }

        when {
            entry.cancelled -> Badge("Cancelled", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            entry.irregular -> Badge("Changed", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        }

        DetailCard {
            DetailRow("Time", AnnotatedString("${entry.startTime.toUntisLocalTime().format(TIME_FORMAT)} - ${entry.endTime.toUntisLocalTime().format(TIME_FORMAT)}"))
            ElementRow("Teacher", entry.teacher, teachersById)
            ElementRow("Room", entry.room, roomsById)
        }

        entry.lstext?.takeIf { it.isNotBlank() }?.let { info ->
            DetailCard {
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ElementRow(
    label: String,
    elements: List<TimetableElement>,
    data: Map<Int, MasterDataItem>?,
) {
    if (elements.isEmpty()) return

    val text = buildAnnotatedString {
        elements.forEachIndexed { index, el ->
            if (index > 0) append("  ")

            val current = data?.get(el.id)?.displayName() ?: el.name
            val orig = el.orgId?.takeIf { it > 0 && it != el.id }
                ?.let { data?.get(it)?.displayName() ?: el.orgName }

            if (orig.isNullOrBlank()) {
                append(current)
            } else {
                append(strikethroughChange(orig, current))
            }
        }
    }

    DetailRow(label, text)
}

private fun MasterDataItem.displayName(): String {
    val long = longName?.takeIf { it.isNotBlank() }
    val short = name.takeIf { it.isNotBlank() }

    return when {
        long != null && short != null && long != short -> "$long ($short)"
        long != null -> long
        else -> short.orEmpty()
    }
}

private fun strikethroughChange(orig: String, current: String): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            append(orig)
        }

        append(" → ")
        append(current)
    }

private fun formatLessonDate(date: LocalDate, entry: TimetableEntry): String {
    val dayName = DAY_NAME_FORMAT.format(date)

    return if (date == LocalDate.now()) "$dayName (Today)" else dayName
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, text: AnnotatedString) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
