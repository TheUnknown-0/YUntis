package sh.lsx.untisx.ui.homework

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.ui.timetable.rememberToday
import sh.lsx.untisx.ui.util.tapHaptic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Groups of open homework, in display order. */
internal enum class HomeworkGroup(val title: Int) {
    OVERDUE(R.string.homework_group_overdue),
    TODAY(R.string.homework_group_today),
    TOMORROW(R.string.homework_group_tomorrow),
    THIS_WEEK(R.string.homework_group_week),
    LATER(R.string.homework_group_later),
    NO_DATE(R.string.homework_group_no_date),
}

internal fun homeworkGroup(item: NotebookItem, today: LocalDate): HomeworkGroup {
    val due = item.due?.date?.toUntisLocalDateOrNull() ?: return HomeworkGroup.NO_DATE
    val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return when {
        due.isBefore(today) -> HomeworkGroup.OVERDUE
        due == today -> HomeworkGroup.TODAY
        due == today.plusDays(1) -> HomeworkGroup.TOMORROW
        !due.isAfter(endOfWeek) -> HomeworkGroup.THIS_WEEK
        else -> HomeworkGroup.LATER
    }
}

/** Open homework sorted by due date and lesson start, grouped for the overview. */
internal fun groupOpenHomework(items: List<NotebookItem>, today: LocalDate): Map<HomeworkGroup, List<NotebookItem>> =
    items
        .filter { it.isTask && !it.isCompleted }
        .sortedWith(
            compareBy<NotebookItem>(
                { it.due?.date ?: Int.MAX_VALUE },
                { it.due?.lesson?.startTime ?: 0 },
                { it.createdAt },
            ),
        )
        .groupBy { homeworkGroup(it, today) }
        .toSortedMap(compareBy { it.ordinal })

@Composable
fun HomeworkScreen(
    items: List<NotebookItem>,
    subjectColors: Map<String, Int>,
    defaultColor: Int,
    subjectAliases: Map<String, SubjectAlias>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = rememberToday()
    val groups = remember(items, today) { groupOpenHomework(items, today) }
    val done = remember(items) {
        items.filter { it.isTask && it.isCompleted }.sortedByDescending { it.completedAt ?: 0L }
    }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val openCount = groups.values.sumOf { it.size }
    val overdue = groups[HomeworkGroup.OVERDUE].orEmpty().size

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Summary(openCount = openCount, overdue = overdue, done = done.size)
            }

            if (openCount == 0) {
                item {
                    Text(
                        text = stringResource(R.string.homework_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            groups.forEach { (group, list) ->
                item(key = "group-${group.name}") {
                    Text(
                        text = stringResource(group.title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (group == HomeworkGroup.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { "open-${it.id}" }) { item ->
                    HomeworkRow(
                        item = item,
                        overdue = group == HomeworkGroup.OVERDUE,
                        color = subjectColor(item, subjectColors, defaultColor),
                        subjectAliases = subjectAliases,
                        onToggle = { onToggle(item.id) },
                        onOpen = { onOpen(item.id) },
                    )
                }
            }

            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    TextButton(onClick = { showDone = !showDone }) {
                        Text(
                            stringResource(
                                if (showDone) R.string.homework_hide_done else R.string.homework_show_done,
                                done.size,
                            ),
                        )
                    }
                }
                if (showDone) {
                    items(done.take(50), key = { "done-${it.id}" }) { item ->
                        HomeworkRow(
                            item = item,
                            overdue = false,
                            color = subjectColor(item, subjectColors, defaultColor),
                            subjectAliases = subjectAliases,
                            onToggle = { onToggle(item.id) },
                            onOpen = { onOpen(item.id) },
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.homework_add)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

private fun subjectColor(item: NotebookItem, colors: Map<String, Int>, default: Int): Color {
    val name = item.subject?.name?.takeIf { it.isNotBlank() } ?: item.due?.lesson?.subject?.name
    return Color(name?.let { colors[it] } ?: default)
}

@Composable
private fun Summary(openCount: Int, overdue: Int, done: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryChip(pluralStringResource(R.plurals.homework_open_count, openCount, openCount), MaterialTheme.colorScheme.primary)
        if (overdue > 0) {
            SummaryChip(pluralStringResource(R.plurals.homework_overdue_count, overdue, overdue), MaterialTheme.colorScheme.error)
        }
        if (done > 0) {
            SummaryChip(pluralStringResource(R.plurals.homework_done_count, done, done), Color(0xFF2E7D32))
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun HomeworkRow(
    item: NotebookItem,
    overdue: Boolean,
    color: Color,
    subjectAliases: Map<String, SubjectAlias>,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val subjectName = item.subject?.name?.takeIf { it.isNotBlank() } ?: item.due?.lesson?.subject?.name.orEmpty()
    val subject = subjectName.takeIf { it.isNotBlank() }?.let { name ->
        subjectLongLabel(subjectAliases, name).let { if (it == name) item.subject?.longName?.takeIf(String::isNotBlank) ?: name else it }
    }
    val due = dueText(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .tapHaptic(onOpen)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(64.dp)
                .background(color),
        )

        Checkbox(checked = item.isCompleted, onCheckedChange = { onToggle() })

        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = item.displayTitle.ifBlank { stringResource(R.string.homework_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )

            val meta = listOfNotNull(subject, due).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (item.due?.reminder != null && !item.isCompleted) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.notebook_reminder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp).size(18.dp),
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun dueText(item: NotebookItem): String? {
    val due = item.due ?: return null
    val date = due.date.toUntisLocalDateOrNull() ?: return null
    val day = date.format(DateTimeFormatter.ofPattern("EEE d.M."))
    val lesson = due.lesson?.takeIf { it.date == due.date }
    return if (lesson != null) {
        stringResource(R.string.homework_due_lesson, day, "%02d:%02d".format(lesson.startTime / 100, lesson.startTime % 100))
    } else {
        stringResource(R.string.homework_due_day, day)
    }
}
