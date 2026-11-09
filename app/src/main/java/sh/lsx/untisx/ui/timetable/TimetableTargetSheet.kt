package sh.lsx.untisx.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.ui.util.tapHaptic

/** Lets the user switch to the timetable of a class or student the school shares with the account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableTargetSheet(
    current: TimetableTarget?,
    ownClassId: Int,
    classes: List<TimetableTarget>,
    students: List<TimetableTarget>,
    loading: Boolean,
    onPick: (TimetableTarget?) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shownClasses = classes.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.longName.lowercase().contains(q) }
    val shownStudents = students.filter { q.isEmpty() || it.name.lowercase().contains(q) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.target_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.target_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.target_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                TargetRow(
                    title = stringResource(R.string.target_own),
                    subtitle = null,
                    selected = current == null,
                    onClick = { onPick(null) },
                )
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (shownClasses.isNotEmpty()) {
                item { Section(stringResource(R.string.target_classes, classes.size)) }
                items(shownClasses, key = { "c-${it.id}" }) { target ->
                    TargetRow(
                        title = target.name,
                        subtitle = listOfNotNull(
                            target.longName.takeIf { it.isNotBlank() && it != target.name },
                            if (target.id == ownClassId) stringResource(R.string.target_own_class) else null,
                        ).joinToString(" · ").ifBlank { null },
                        selected = current == target,
                        onClick = { onPick(target) },
                    )
                }
            }

            if (shownStudents.isNotEmpty()) {
                item { Section(stringResource(R.string.target_students, students.size)) }
                items(shownStudents, key = { "s-${it.id}" }) { target ->
                    TargetRow(title = target.name, subtitle = null, selected = current == target, onClick = { onPick(target) })
                }
            }

            if (!loading && classes.isEmpty() && students.isEmpty()) {
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.target_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            } else if (!loading && students.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.target_no_students),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun TargetRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            )
            .tapHaptic(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
