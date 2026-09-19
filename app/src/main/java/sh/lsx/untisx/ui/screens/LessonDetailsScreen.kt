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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.ui.timetable.TIME_FORMAT
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_NAME_FORMAT = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.getDefault())

@Composable
fun LessonDetailsScreen(
    entry: TimetableEntry,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    notes: TimetableViewModel.NotesState? = null,
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
            entry.cancelled -> Badge(stringResource(R.string.lesson_cancelled), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            entry.irregular -> Badge(stringResource(R.string.lesson_changed), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        }

        DetailCard {
            DetailRow(stringResource(R.string.lesson_time), AnnotatedString("${entry.startTime.toUntisLocalTime().format(TIME_FORMAT)} - ${entry.endTime.toUntisLocalTime().format(TIME_FORMAT)}"))
            ElementRow(stringResource(R.string.lesson_teacher), entry.teacher, teachersById)
            ElementRow(stringResource(R.string.lesson_room), entry.room, roomsById)
        }

        val loaded = notes as? TimetableViewModel.NotesState.Loaded

        (loaded?.info ?: entry.lessonText)?.takeIf { it.isNotBlank() }?.let { info ->
            DetailCard {
                Text(
                    text = stringResource(R.string.lesson_info),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        (loaded?.substitution ?: entry.lstext)?.takeIf { it.isNotBlank() }?.let { info ->
            DetailCard {
                Text(
                    text = stringResource(R.string.lesson_substitution),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        when (notes) {
            is TimetableViewModel.NotesState.Loading -> {
                DetailCard {
                    Text(
                        text = stringResource(R.string.lesson_notes_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is TimetableViewModel.NotesState.Loaded -> {
                notes.topic?.let { topic ->
                    DetailCard {
                        Text(
                            text = stringResource(R.string.lesson_topic),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = topic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (notes.notes.isNotEmpty()) {
                    DetailCard {
                        Text(
                            text = stringResource(R.string.lesson_notes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        notes.notes.forEach { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            else -> Unit
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

@Composable
private fun formatLessonDate(date: LocalDate, entry: TimetableEntry): String {
    val dayName = DAY_NAME_FORMAT.format(date)

    return if (date == LocalDate.now()) {
        stringResource(R.string.lesson_date_today, dayName)
    } else {
        dayName
    }
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
