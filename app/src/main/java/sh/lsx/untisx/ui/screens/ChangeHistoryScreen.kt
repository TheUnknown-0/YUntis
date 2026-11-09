package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.history.ChangeHistory
import sh.lsx.untisx.data.history.ChangeRecord
import sh.lsx.untisx.data.notifications.ChangeType
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.tapHaptic
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle

@Composable
internal fun ChangeHistoryScreen(
    accountId: String,
    onOpenDate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<ChangeRecord>?>(null) }
    var subjectFilter by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(accountId) {
        records = withContext(Dispatchers.IO) { ChangeHistory.load(context, accountId) }
    }

    val all = records ?: return
    val subjects = remember(all) { all.map { it.subject }.distinct().sorted() }
    val shown = all.filter { subjectFilter == null || it.subject == subjectFilter }

    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = rememberDateFormat("EEEEdMMMM")
    val shortDate = rememberDateFormat("dMMM")
    val timeFormat = rememberTimeFormat()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (all.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }

        if (subjects.size > 1) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = subjectFilter == null,
                        onClick = { subjectFilter = null },
                        label = { Text(stringResource(R.string.history_all)) },
                    )
                    subjects.forEach { subject ->
                        FilterChip(
                            selected = subjectFilter == subject,
                            onClick = { subjectFilter = if (subjectFilter == subject) null else subject },
                            label = { Text(subject) },
                        )
                    }
                }
            }
        }

        // grouped by the day the change was noticed
        shown.groupBy { Instant.ofEpochMilli(it.detectedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .forEach { (day, dayRecords) ->
                item(key = "h$day") {
                    Text(
                        stringResource(R.string.history_noticed, dayFormat.format(day)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(dayRecords, key = { it.identity + it.detectedAt }) { record ->
                    val type = ChangeHistory.typeOf(record)
                    val lessonDay = record.date.toUntisLocalDateOrNull()
                    val lessonText = listOfNotNull(
                        lessonDay?.let { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + shortDate.format(it) },
                        record.startTime.toUntisLocalTime().format(timeFormat),
                    ).joinToString(", ")
                    val noticed = Instant.ofEpochMilli(record.detectedAt).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)

                    val (label, color) = when (type) {
                        ChangeType.CANCELLED -> stringResource(R.string.history_cancelled) to MaterialTheme.colorScheme.error
                        ChangeType.ROOM -> stringResource(R.string.history_room) to MaterialTheme.colorScheme.tertiary
                        ChangeType.TEACHER -> stringResource(R.string.history_teacher) to MaterialTheme.colorScheme.secondary
                        null -> record.type to MaterialTheme.colorScheme.outline
                    }

                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(appCardShape(14.dp))
                            .tapHaptic { onOpenDate(record.date) },
                        shape = appCardShape(14.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${record.subject} · $label",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(lessonText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (type != ChangeType.CANCELLED && (record.previous != null || record.current != null)) {
                                    Text(
                                        "${record.previous.orEmpty().ifBlank { "?" }} → ${record.current.orEmpty().ifBlank { "?" }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Text(noticed, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
    }
}
