package sh.lsx.untisx.ui.absences

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.absences.AbsenceAssistant
import sh.lsx.untisx.data.absences.AbsenceItem
import sh.lsx.untisx.data.absences.OwnAbsence
import sh.lsx.untisx.data.absences.OwnAbsencesStore
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.tapHaptic
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

class AbsencesViewModel(app: Application) : AndroidViewModel(app) {

    /** What the school sent: fresh, from the cache, or nothing because it does not share it. */
    enum class SchoolStatus { LOADING, FRESH, CACHED, NOT_SHARED, FAILED }

    var status by mutableStateOf(SchoolStatus.LOADING)
        private set
    var items by mutableStateOf<List<AbsenceItem>>(emptyList())
        private set

    private var accountId: String? = null
    private var school: List<AbsenceItem> = emptyList()

    private fun publish() {
        val own = accountId?.let { OwnAbsencesStore.load(getApplication(), it) }.orEmpty().map(AbsenceItem::of)
        items = (school + own).sortedByDescending { it.date * 10000L + it.startTime }
    }

    fun load() {
        viewModelScope.launch {
            AccountStore.ensureLoaded(getApplication())
            val auth = AccountStore.active ?: return@launch
            val id = AccountPrefs.accountId(auth)
            accountId = id

            withContext(Dispatchers.IO) { OwnAbsencesStore.loadSchoolCache(getApplication(), id) }?.let { cached ->
                school = cached.map(AbsenceItem::of)
                status = SchoolStatus.CACHED
            }
            publish()

            status = try {
                val password = withContext(Dispatchers.IO) { PasswordVault.retrieve(getApplication(), id) }
                    ?: throw UntisException.BadCredentials
                val today = AppClock.today()
                val yearStart = StatsStore.fallbackYear(today).startDate.toUntisLocalDateOrNull() ?: today.minusMonths(6)
                val fetched = UntisClient.fetchAbsences(auth, password, yearStart, today)
                withContext(Dispatchers.IO) { OwnAbsencesStore.saveSchoolCache(getApplication(), id, fetched) }
                school = fetched.map(AbsenceItem::of)
                SchoolStatus.FRESH
            } catch (e: CancellationException) {
                throw e
            } catch (e: UntisException.Api) {
                Log.d("Absences", "not shared: ${e.code}")
                if (status == SchoolStatus.CACHED) SchoolStatus.CACHED else SchoolStatus.NOT_SHARED
            } catch (e: Exception) {
                Log.d("Absences", "failed: ${e.javaClass.simpleName}")
                if (status == SchoolStatus.CACHED) SchoolStatus.CACHED else SchoolStatus.FAILED
            }
            publish()
        }
    }

    fun save(absence: OwnAbsence) {
        val id = accountId ?: return
        OwnAbsencesStore.upsert(getApplication(), id, absence)
        publish()
    }

    fun delete(absence: OwnAbsence) {
        val id = accountId ?: return
        OwnAbsencesStore.delete(getApplication(), id, absence.id)
        publish()
    }
}

private val ExcusedColor = Color(0xFF43A047)
private val UnexcusedColor = Color(0xFFE53935)

@Composable
fun AbsencesScreen(
    lessons: List<TimetableEntry>,
    subjects: Map<Int, MasterDataItem>,
    modifier: Modifier = Modifier,
    onOpenDate: (Int) -> Unit = {},
    vm: AbsencesViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.load() }

    var editing by remember { mutableStateOf<OwnAbsence?>(null) }
    var excuseFor by remember { mutableStateOf<AbsenceItem?>(null) }

    Box(modifier.fillMaxSize()) {
        if (vm.status == AbsencesViewModel.SchoolStatus.LOADING && vm.items.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            AbsenceList(
                items = vm.items,
                status = vm.status,
                lessons = lessons,
                subjects = subjects,
                onRetry = vm::load,
                onEdit = { editing = it },
                onExcuse = { excuseFor = it },
                onOpenDate = onOpenDate,
            )
        }

        ExtendedFloatingActionButton(
            onClick = { editing = OwnAbsence(id = UUID.randomUUID().toString(), date = LocalDate.now().toUntisDate()) },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.absences_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { absence ->
        OwnAbsenceDialog(
            initial = absence,
            isNew = vm.items.none { it.own?.id == absence.id },
            onSave = {
                vm.save(it)
                editing = null
            },
            onDelete = {
                vm.delete(absence)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    excuseFor?.let { item ->
        ExcuseDialog(item, AbsenceAssistant.affectedLessons(item, lessons), subjects) { excuseFor = null }
    }
}

@Composable
private fun AbsenceList(
    items: List<AbsenceItem>,
    status: AbsencesViewModel.SchoolStatus,
    lessons: List<TimetableEntry>,
    subjects: Map<Int, MasterDataItem>,
    onRetry: () -> Unit,
    onEdit: (OwnAbsence) -> Unit,
    onExcuse: (AbsenceItem) -> Unit,
    onOpenDate: (Int) -> Unit,
) {
    val context = LocalContext.current
    val excused = items.filter { it.excused }
    val unexcused = items.filterNot { it.excused }
    val byMonth = items.groupBy { it.date.toUntisLocalDateOrNull()?.let(YearMonth::from) }
    var deadlineDays by remember { mutableIntStateOf(OwnAbsencesStore.deadlineDays(context)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(stringResource(R.string.absences_total), items.size, hours(items, lessons), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Tile(stringResource(R.string.absences_excused), excused.size, hours(excused, lessons), ExcusedColor, Modifier.weight(1f))
                Tile(stringResource(R.string.absences_unexcused), unexcused.size, hours(unexcused, lessons), UnexcusedColor, Modifier.weight(1f))
            }
        }

        val note = when (status) {
            AbsencesViewModel.SchoolStatus.NOT_SHARED -> R.string.absences_not_shared_own
            AbsencesViewModel.SchoolStatus.FAILED -> R.string.absences_failed_own
            AbsencesViewModel.SchoolStatus.CACHED -> R.string.absences_cached
            else -> null
        }
        if (note != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }

        if (unexcused.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.absences_deadline_setting, deadlineDays),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        deadlineDays = (deadlineDays - 1).coerceAtLeast(1)
                        OwnAbsencesStore.setDeadlineDays(context, deadlineDays)
                    }) { Text("−") }
                    TextButton(onClick = {
                        deadlineDays = (deadlineDays + 1).coerceAtMost(60)
                        OwnAbsencesStore.setDeadlineDays(context, deadlineDays)
                    }) { Text("+") }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.absences_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        byMonth.forEach { (month, list) ->
            item {
                Text(
                    text = month?.format(DateTimeFormatter.ofPattern("LLLL yyyy")).orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(list) { item ->
                AbsenceRow(
                    item = item,
                    affected = AbsenceAssistant.subjectNames(AbsenceAssistant.affectedLessons(item, lessons), subjects),
                    deadline = if (item.excused) null else AbsenceAssistant.deadline(item, deadlineDays),
                    onClick = { item.own?.let(onEdit) ?: onOpenDate(item.date) },
                    onExcuse = { onExcuse(item) },
                )
            }
        }
    }
}

/** Hours missed; a whole day counts the lessons that took place that day. */
private fun hours(list: List<AbsenceItem>, lessons: List<TimetableEntry>): Int = list.sumOf { item ->
    if (item.wholeDay) {
        AbsenceAssistant.affectedLessons(item, lessons).sumOf { (it.endTime / 100 * 60 + it.endTime % 100) - (it.startTime / 100 * 60 + it.startTime % 100) }
    } else {
        item.minutes
    }
} / 60

@Composable
private fun Tile(label: String, count: Int, hours: Int, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.absences_hours, hours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AbsenceRow(
    item: AbsenceItem,
    affected: List<String>,
    deadline: LocalDate?,
    onClick: () -> Unit,
    onExcuse: () -> Unit,
) {
    val dateFormat = rememberDateFormat("EEEdMMM")
    val timeFormat = rememberTimeFormat()
    val date = item.date.toUntisLocalDateOrNull()?.let(dateFormat::format).orEmpty()
    val time = if (item.wholeDay) {
        stringResource(R.string.absences_whole_day)
    } else {
        item.startTime.toUntisLocalTime().format(timeFormat) + " – " + item.endTime.toUntisLocalTime().format(timeFormat)
    }
    val color = if (item.excused) ExcusedColor else UnexcusedColor
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .tapHaptic(onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$date · $time", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (item.reason.isNotBlank()) {
                    Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (affected.isNotEmpty()) {
                    Text(
                        stringResource(R.string.absences_affected, affected.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.status.ifBlank {
                        stringResource(if (item.excused) R.string.absences_excused_one else R.string.absences_unexcused_one)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.background(color, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                if (item.own != null) {
                    Text(
                        stringResource(R.string.absences_own_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (deadline != null) {
            val overdue = deadline.isBefore(today)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    stringResource(
                        if (overdue) R.string.absences_deadline_over else R.string.absences_deadline,
                        rememberDateFormat("EEEdMMM").format(deadline),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue || !deadline.isAfter(today.plusDays(1))) UnexcusedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onExcuse) { Text(stringResource(R.string.absences_write_excuse)) }
            }
        }
    }
}

@Composable
private fun OwnAbsenceDialog(
    initial: OwnAbsence,
    isNew: Boolean,
    onSave: (OwnAbsence) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = rememberDateFormat("EEEEdMMMM")
    val timeFormat = rememberTimeFormat()
    var draft by remember { mutableStateOf(initial) }

    fun pickTime(current: Int, apply: (Int) -> Unit) {
        val start = if (current == 0) 800 else current
        TimePickerDialog(context, { _, h, m -> apply(h * 100 + m) }, start / 100, start % 100, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.absences_add else R.string.absences_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val day = draft.date.toUntisLocalDateOrNull() ?: LocalDate.now()
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, y, mo, d ->
                            draft = draft.copy(date = LocalDate.of(y, mo + 1, d).toUntisDate())
                        }, day.year, day.monthValue - 1, day.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(dateFormat.format(day)) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.wholeDay,
                        onCheckedChange = { whole ->
                            draft = if (whole) draft.copy(startTime = 0, endTime = 0) else draft.copy(startTime = 800, endTime = 1325)
                        },
                    )
                    Text(stringResource(R.string.absences_whole_day))
                }

                if (!draft.wholeDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickTime(draft.startTime) { draft = draft.copy(startTime = it) } }, modifier = Modifier.weight(1f)) {
                            Text(draft.startTime.toUntisLocalTime().format(timeFormat))
                        }
                        OutlinedButton(onClick = { pickTime(draft.endTime) { draft = draft.copy(endTime = it) } }, modifier = Modifier.weight(1f)) {
                            Text(draft.endTime.toUntisLocalTime().format(timeFormat))
                        }
                    }
                }

                OutlinedTextField(
                    value = draft.reason,
                    onValueChange = { draft = draft.copy(reason = it) },
                    label = { Text(stringResource(R.string.absences_reason)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft.excused, onCheckedChange = { draft = draft.copy(excused = it) })
                    Text(stringResource(R.string.absences_mark_excused))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.wholeDay || draft.endTime > draft.startTime,
                onClick = { onSave(draft) },
            ) { Text(stringResource(R.string.notebook_save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.notebook_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun ExcuseDialog(
    item: AbsenceItem,
    affected: List<TimetableEntry>,
    subjects: Map<Int, MasterDataItem>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = rememberDateFormat("EEEEdMMMMyyyy")
    val timeFormat = rememberTimeFormat()
    var name by remember { mutableStateOf(OwnAbsencesStore.excuseName(context)) }

    val day = item.date.toUntisLocalDateOrNull()?.let(dateFormat::format).orEmpty()
    val names = AbsenceAssistant.subjectNames(affected, subjects)
    val span = when {
        affected.isNotEmpty() -> " (" + affected.first().startTime.toUntisLocalTime().format(timeFormat) + " – " +
            affected.last().endTime.toUntisLocalTime().format(timeFormat) + ": " + names.joinToString(", ") + ")"
        !item.wholeDay -> " (" + item.startTime.toUntisLocalTime().format(timeFormat) + " – " + item.endTime.toUntisLocalTime().format(timeFormat) + ")"
        else -> ""
    }
    val reason = item.reason.takeIf { it.isNotBlank() }?.let { " " + stringResource(R.string.absences_excuse_reason, it) }.orEmpty()
    val template = stringResource(R.string.absences_excuse_template, day, span, reason, name.ifBlank { "…" })
    var text by remember(template) { mutableStateOf(template) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.absences_write_excuse)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        OwnAbsencesStore.setExcuseName(context, it)
                    },
                    label = { Text(stringResource(R.string.absences_excuse_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.absences_share)) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("excuse", text))
                    onDismiss()
                }) { Text(stringResource(R.string.absences_copy)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
