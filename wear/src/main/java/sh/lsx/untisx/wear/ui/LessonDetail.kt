package sh.lsx.untisx.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.data.wear.WearTimetablePayload
import sh.lsx.untisx.wear.R

@Composable
fun LessonDetail(entry: TimetableEntry, payload: WearTimetablePayload, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    val listState = rememberTransformingLazyColumnState()

    val subjectName = entry.subject.firstOrNull()?.name.orEmpty()
    val label = if (entry.isEvent) entry.eventTitle.orEmpty() else subjectLongLabel(payload.subjectAliases, subjectName)
    val color = if (entry.isEvent) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color(payload.subjectColors[subjectName] ?: payload.defaultColor)
    }
    val room = entry.room.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }.joinToString(", ")
    val teacher = entry.teacher.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }.joinToString(", ")

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onClose,
                modifier = Modifier.scrollable(
                    listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    overscrollEffect = rememberOverscrollEffect(),
                ),
            ) {
                Text(
                    text = stringResource(R.string.wear_back),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = timeRange(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.cancelled) {
                item {
                    StatusChip(
                        stringResource(R.string.wear_cancelled),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (entry.irregular) {
                item {
                    StatusChip(
                        stringResource(R.string.wear_irregular),
                        contentColor = EXAM_COLOR,
                    )
                }
            }

            entry.exam?.let { exam ->
                val name = listOf(exam.name, exam.examtype)
                    .filterNotNull()
                    .firstOrNull { it.isNotBlank() }
                val text = exam.text.clean()?.takeIf { it != name }

                item {
                    InfoCard(border = BorderStroke(1.dp, EXAM_COLOR)) {
                        InfoText(
                            label = stringResource(R.string.wear_exam),
                            value = name ?: stringResource(R.string.wear_exam),
                            labelColor = EXAM_COLOR,
                        )

                        if (text != null) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (room.isNotBlank() || teacher.isNotBlank()) {
                item {
                    InfoCard {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (room.isNotBlank()) {
                                InfoColumn(
                                    label = stringResource(R.string.wear_room),
                                    value = room,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (teacher.isNotBlank()) {
                                InfoColumn(
                                    label = stringResource(R.string.wear_teacher),
                                    value = teacher,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            entry.substText.clean()?.let { text ->
                item {
                    InfoCard {
                        InfoText(label = stringResource(R.string.wear_substitution), value = text)
                    }
                }
            }

            entry.info.clean()?.let { text ->
                item {
                    InfoCard {
                        InfoText(label = stringResource(R.string.wear_info), value = text)
                    }
                }
            }

            entry.lstext.clean()?.let { text ->
                item {
                    InfoCard {
                        InfoText(label = stringResource(R.string.wear_topic), value = text)
                    }
                }
            }

            entry.lessonText.clean()?.let { text ->
                item {
                    InfoCard {
                        InfoText(label = stringResource(R.string.wear_topic), value = text)
                    }
                }
            }
        }
    }
}

private fun String?.clean(): String? =
    this?.trim()?.takeIf { it.isNotBlank() && it !in setOf("-", "–", "—") }
