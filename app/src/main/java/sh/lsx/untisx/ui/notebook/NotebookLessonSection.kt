package sh.lsx.untisx.ui.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import java.time.LocalDate
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookLessonLinks

@Composable
internal fun NotebookLessonSection(
    links: NotebookLessonLinks?,
    today: LocalDate,
    onItemClick: (String) -> Unit,
    onCreateNote: () -> Unit,
    onCreateTask: () -> Unit,
    onJumpToDate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links == null) return

    val sections = notebookLessonSections(links)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notebook_lesson_personal),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (sections.isEmpty()) {
                Text(
                    text = stringResource(R.string.notebook_lesson_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            sections.forEach { section ->
                Text(
                    text = when (section.kind) {
                        NotebookLessonSectionKind.SOURCE -> stringResource(R.string.notebook_lesson_source)
                        NotebookLessonSectionKind.DUE -> stringResource(R.string.notebook_lesson_due)
                        NotebookLessonSectionKind.PINNED -> stringResource(R.string.notebook_lesson_notes)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                section.items.forEach { item ->
                    NotebookLessonRow(
                        item = item,
                        today = today,
                        onClick = { onItemClick(item.id) },
                        onJumpToDate = onJumpToDate,
                        sectionKind = section.kind,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCreateNote) {
                    Text(stringResource(R.string.notebook_create_note_for_lesson))
                }
                TextButton(onClick = onCreateTask) {
                    Text(stringResource(R.string.notebook_create_task_for_lesson))
                }
            }
        }
    }
}

@Composable
internal fun NotebookLessonRow(
    item: NotebookItem,
    today: LocalDate,
    onClick: () -> Unit,
    onJumpToDate: (Int) -> Unit,
    sectionKind: NotebookLessonSectionKind = NotebookLessonSectionKind.SOURCE,
) {
    val todayInt = today.toUntisDate()
    val dueDate = item.due?.date
    val jumpDate = notebookLessonJumpDate(item, sectionKind)
    val title = item.displayTitle.ifBlank { stringResource(R.string.notebook_untitled) }
    val subject = item.subject?.displayName?.takeIf { it.isNotBlank() }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(end = if (jumpDate != null) 48.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.isCompleted) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!item.isTask) {
                    Icon(
                        imageVector = notebookPinIcon(item.isPinned),
                        contentDescription = null,
                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = stringResource(
                        if (item.kind == NotebookKind.TASK) {
                            R.string.notebook_kind_task
                        } else {
                            R.string.notebook_kind_note
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                subject?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (item.isCompleted) {
                    Text(
                        text = stringResource(R.string.notebook_completed_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (item.isArchived) {
                    Text(
                        text = stringResource(R.string.notebook_archived_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            dueDate?.let { date ->
                Text(
                    text = when {
                        date < todayInt -> stringResource(
                            R.string.notebook_due_overdue,
                            notebookDateText(date),
                        )

                        date == todayInt -> stringResource(R.string.notebook_due_today)
                        else -> stringResource(
                            R.string.notebook_due_date_value,
                            notebookDateText(date),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        date < todayInt -> MaterialTheme.colorScheme.error
                        date == todayInt -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
            jumpDate?.let { date ->
                IconButton(
                    onClick = { onJumpToDate(date) },
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = stringResource(
                            if (sectionKind == NotebookLessonSectionKind.DUE) {
                                R.string.notebook_show_origin_in_timetable
                            } else {
                                R.string.notebook_show_due_in_timetable
                            },
                            notebookDateText(date),
                        ),
                    )
                }
            }
        }
    }
}
