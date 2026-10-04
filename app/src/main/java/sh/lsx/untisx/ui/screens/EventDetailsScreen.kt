package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.ui.timetable.TimetableViewModel
import sh.lsx.untisx.ui.timetable.resolveNames
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.sharedElementBounds

@Composable
fun EventDetailsScreen(
    entry: TimetableEntry,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
    modifier: Modifier = Modifier,
    notes: TimetableViewModel.NotesState? = null,
) {
    val title = entry.eventTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.events_untitled)

    val timeFormat = rememberTimeFormat()
    val date = remember(entry.date) { entry.date.toUntisLocalDateOrNull() }
    val rooms = remember(entry.room, roomsById) { resolveNames(entry.room, roomsById) }
    val teachers = remember(entry.teacher, teachersById) { resolveNames(entry.teacher, teachersById) }

    val inlineTexts = remember(entry) {
        listOfNotNull(entry.lessonText, entry.lstext, entry.info, entry.substText)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != title }
            .distinct()
    }

    val loaded = notes as? TimetableViewModel.NotesState.Loaded
    val hasLoadedDetails = loaded != null && (
        !loaded.info.isNullOrBlank() ||
            !loaded.topic.isNullOrBlank() ||
            loaded.notes.isNotEmpty()
        )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .sharedElementBounds("event-${entry.id}-${entry.date}-${entry.startTime}")
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                date?.let {
                    Text(
                        text = rememberDateFormat("EEEEdMMMMyyyy").format(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }

                Text(
                    text = if (entry.startTime != 0 || entry.endTime != 0) {
                        "${entry.startTime.toUntisLocalTime().format(timeFormat)} - " +
                            entry.endTime.toUntisLocalTime().format(timeFormat)
                    } else {
                        stringResource(R.string.events_all_day)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )

                if (rooms.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.lesson_room) + ": " + rooms,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        if (inlineTexts.isNotEmpty()) {
            DetailCard {
                Text(
                    text = stringResource(R.string.event_description),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                inlineTexts.forEach { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (teachers.isNotBlank()) {
            DetailCard {
                Text(
                    text = stringResource(R.string.lesson_teacher),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = teachers,
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
                notes.info?.takeIf { it.isNotBlank() }?.let { info ->
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

                notes.topic?.takeIf { it.isNotBlank() }?.let { topic ->
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

        if (inlineTexts.isEmpty() && !hasLoadedDetails && notes !is TimetableViewModel.NotesState.Loading) {
            DetailCard {
                Text(
                    text = stringResource(R.string.event_no_details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
