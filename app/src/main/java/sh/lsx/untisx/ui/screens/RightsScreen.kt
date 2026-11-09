package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.rights.ProbeKey
import sh.lsx.untisx.data.rights.ProbeResult
import sh.lsx.untisx.data.rights.ProbeStatus
import sh.lsx.untisx.data.rights.RightsProbe
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import java.text.DateFormat
import java.util.Date

private fun labelOf(key: ProbeKey): Pair<Int, Int> = when (key) {
    ProbeKey.TIMETABLE_REST -> R.string.rights_timetable_rest to R.string.rights_timetable_rest_desc
    ProbeKey.TIMETABLE_INTERNAL -> R.string.rights_timetable_internal to R.string.rights_timetable_internal_desc
    ProbeKey.TIMETABLE_LEGACY -> R.string.rights_timetable_legacy to R.string.rights_timetable_legacy_desc
    ProbeKey.OWN_CLASS -> R.string.rights_own_class to R.string.rights_own_class_desc
    ProbeKey.OTHER_CLASSES -> R.string.rights_other_classes to R.string.rights_other_classes_desc
    ProbeKey.EXAMS -> R.string.rights_exams to R.string.rights_exams_desc
    ProbeKey.ABSENCES -> R.string.rights_absences to R.string.rights_absences_desc
    ProbeKey.MESSAGES -> R.string.rights_messages to R.string.rights_messages_desc
    ProbeKey.DAY_MESSAGES -> R.string.rights_day_messages to R.string.rights_day_messages_desc
    ProbeKey.LESSON_NOTES -> R.string.rights_lesson_notes to R.string.rights_lesson_notes_desc
    ProbeKey.TEACHERS -> R.string.rights_teachers to R.string.rights_teachers_desc
    ProbeKey.STUDENTS -> R.string.rights_students to R.string.rights_students_desc
    ProbeKey.IMPORT_TIME -> R.string.rights_import_time to R.string.rights_import_time_desc
}

/** "What my school allows": runs every check once and shows the answers. */
@Composable
internal fun RightsScreen(auth: AuthData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountId = AccountPrefs.accountId(auth)
    var saved by remember { mutableStateOf(RightsProbe.load(context, accountId)) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rights_intro), style = MaterialTheme.typography.bodyMedium)
                Button(
                    enabled = !running,
                    onClick = {
                        running = true
                        scope.launch {
                            RightsProbe.run(context, auth) { done, all -> progress = done.toFloat() / all }
                            saved = RightsProbe.load(context, accountId)
                            running = false
                        }
                    },
                ) { Text(stringResource(if (saved == null) R.string.rights_run else R.string.rights_run_again)) }
                if (running) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                saved?.let {
                    Text(
                        stringResource(R.string.rights_checked_at, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.at))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val results = saved?.results.orEmpty()
        items(ProbeKey.entries.mapNotNull { key -> results.firstOrNull { it.key == key.name }?.let { key to it } }) { (key, result) ->
            RightsRow(key, result)
        }
    }
}

@Composable
private fun RightsRow(key: ProbeKey, result: ProbeResult) {
    val (title, desc) = labelOf(key)
    val (color, label) = when (result.status) {
        ProbeStatus.ALLOWED -> Color(0xFF43A047) to stringResource(R.string.rights_allowed)
        ProbeStatus.EMPTY -> Color(0xFF7E8B99) to stringResource(R.string.rights_empty)
        ProbeStatus.REFUSED -> Color(0xFFE53935) to stringResource(R.string.rights_refused)
        ProbeStatus.ERROR -> Color(0xFFF9A825) to stringResource(R.string.rights_error)
    }

    AppCard(modifier = Modifier.fillMaxWidth(), shape = appCardShape(14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
