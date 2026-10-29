package sh.lsx.untisx.ui.notebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.notebook.SubjectRef
import sh.lsx.untisx.data.subjectShortLabel

@Composable
internal fun NotebookItemRow(
    item: NotebookItem,
    today: LocalDate,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit,
    onTogglePinned: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onConvert: (NotebookKind) -> Unit,
    onJumpToDate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val todayInt = today.toUntisDate()
    val dueDate = item.due?.date
    val title = item.displayTitle.ifBlank { stringResource(R.string.notebook_untitled) }
    val subject = item.subject?.displayName?.takeIf { it.isNotBlank() }
    val bodyPreview = item.body.trim()
        .takeIf { it.isNotBlank() && item.title.isNotBlank() }
        ?.lineSequence()
        ?.take(2)
        ?.joinToString("\n")
        .orEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (item.isTask) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggleCompleted() },
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                IconToggleButton(
                    checked = item.isPinned,
                    onCheckedChange = { onTogglePinned() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = notebookPinIcon(item.isPinned),
                        contentDescription = stringResource(
                            if (item.isPinned) R.string.notebook_unpin else R.string.notebook_pin,
                        ),
                        tint = if (item.isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (bodyPreview.isNotBlank()) {
                    Text(
                        text = bodyPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    subject?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    val dueText = when {
                        date < todayInt -> stringResource(
                            R.string.notebook_due_overdue,
                            notebookDateText(date),
                        )

                        date == todayInt -> stringResource(R.string.notebook_due_today)
                        else -> stringResource(
                            R.string.notebook_due_date_value,
                            notebookDateText(date),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onJumpToDate(item.due?.lesson?.date ?: date) }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                date < todayInt -> MaterialTheme.colorScheme.error
                                date == todayInt -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                item.origin?.let { origin ->
                    Text(
                        text = stringResource(
                            R.string.notebook_origin_value,
                            notebookLessonTimeText(origin),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.notebook_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notebook_edit)) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        },
                    )

                    if (item.isTask) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notebook_convert_to_note)) },
                            onClick = {
                                menuExpanded = false
                                onConvert(NotebookKind.NOTE)
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notebook_convert_to_task)) },
                            onClick = {
                                menuExpanded = false
                                onConvert(NotebookKind.TASK)
                            },
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (item.isArchived) {
                                            R.string.notebook_unarchive
                                        } else {
                                            R.string.notebook_archive
                                        },
                                    ),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onArchive()
                            },
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notebook_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
