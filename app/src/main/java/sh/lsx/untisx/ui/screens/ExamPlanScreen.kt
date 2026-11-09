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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.planner.PlannerPrefs
import sh.lsx.untisx.data.planner.StudyPlan
import sh.lsx.untisx.data.planner.StudySlotKind
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.tapHaptic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle

@Composable
internal fun ExamPlanScreen(
    exams: List<ExamInfo>,
    weeks: Map<Int, List<TimetableEntry>>,
    holidays: List<Holiday>,
    subjectsById: Map<Int, MasterDataItem>,
    subjectAliases: Map<String, SubjectAlias>,
    colorFor: (String) -> Color,
    onOpenDate: (Int) -> Unit,
    onLoadWeeksUntil: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onShare: ((ExamInfo) -> Unit)? = null,
) {
    val now = remember { LocalDateTime.now() }
    val today = now.toLocalDate()
    val upcoming = remember(exams) {
        exams.filter { exam -> exam.date.toUntisLocalDateOrNull()?.let { !it.isBefore(today) } == true }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .distinctBy { Triple(it.date, it.startTime, it.subjectId to it.subjectName) }
    }
    val lessons = remember(weeks) { weeks.values.flatten() }

    LaunchedEffect(upcoming) {
        // the next weeks are needed to count the remaining lessons and to find free periods
        upcoming.mapNotNull { it.date.toUntisLocalDateOrNull() }
            .filter { it.isBefore(today.plusWeeks(8)) }
            .maxOrNull()
            ?.let(onLoadWeeksUntil)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (upcoming.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.exam_plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(upcoming, key = { PlannerPrefs.examKey(it.id, it.date, it.startTime, it.own) }) { exam ->
            ExamPlanCard(
                exam = exam,
                now = now,
                lessons = lessons,
                loadedWeeks = weeks.keys,
                holidays = holidays,
                otherExamDays = upcoming.filter { it !== exam }.map { it.date }.toSet(),
                subjectsById = subjectsById,
                subjectAliases = subjectAliases,
                colorFor = colorFor,
                onOpenDate = onOpenDate,
                onShare = onShare?.let { share -> { share(exam) } },
            )
        }
    }
}

@Composable
private fun ExamPlanCard(
    exam: ExamInfo,
    now: LocalDateTime,
    lessons: List<TimetableEntry>,
    loadedWeeks: Set<Int>,
    holidays: List<Holiday>,
    otherExamDays: Set<Int>,
    subjectsById: Map<Int, MasterDataItem>,
    subjectAliases: Map<String, SubjectAlias>,
    colorFor: (String) -> Color,
    onOpenDate: (Int) -> Unit,
    onShare: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = rememberDateFormat("EEEEdMMMM")
    val shortDate = rememberDateFormat("dMMM")
    val timeFormat = rememberTimeFormat()

    val key = PlannerPrefs.examKey(exam.id, exam.date, exam.startTime, exam.own)
    var sessions by remember(key) { mutableIntStateOf(PlannerPrefs.sessions(context, key)) }
    var timesVersion by remember(key) { mutableIntStateOf(0) }
    var done by remember(key) { mutableStateOf(PlannerPrefs.doneSlots(context, key)) }
    // the study timer checks blocks off in its own screen; read them again when coming back
    androidx.lifecycle.compose.LifecycleResumeEffect(key) {
        done = PlannerPrefs.doneSlots(context, key)
        timesVersion++
        onPauseOrDispose { }
    }

    val short = subjectsById[exam.subjectId]?.name?.takeIf { it.isNotBlank() } ?: exam.subjectName
    val subject = subjectLongLabel(subjectAliases, short).ifBlank { short }
    val type = exam.examType.ifBlank { stringResource(R.string.lesson_exam) }
    val countdown = remember(exam, lessons, loadedWeeks) { StudyPlan.countdown(exam, now, lessons, loadedWeeks) }
    val slots = remember(exam, lessons, sessions, holidays, timesVersion) {
        val suggested = StudyPlan.suggest(exam, now.toLocalDate(), lessons, holidays, sessions, otherExamDays = otherExamDays, earliestToday = now.toLocalTime())
        PlannerPrefs.withOwnTimes(context, key, suggested)
    }
    var editing by remember(key) { mutableStateOf<sh.lsx.untisx.data.planner.StudySlot?>(null) }
    fun studyNow(slot: sh.lsx.untisx.data.planner.StudySlot?) {
        val minutes = slot?.let { java.time.Duration.between(it.start, it.end).toMinutes() } ?: 0L
        context.startActivity(
            sh.lsx.untisx.ui.planner.PomodoroActivity.intent(context, "$type $subject", key, slot?.key.orEmpty(), minutes),
        )
    }

    AppCard(modifier = Modifier.fillMaxWidth(), shape = appCardShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).tapHaptic { onOpenDate(exam.date) },
            ) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colorFor(short)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("$type $subject", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val day = exam.date.toUntisLocalDateOrNull()
                    Text(
                        listOfNotNull(
                            day?.let(dateFormat::format),
                            exam.startTime.takeIf { it > 0 }?.toUntisLocalTime()?.format(timeFormat),
                        ).joinToString(", ") + exam.name.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                countdown?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (it.days == 0L) stringResource(R.string.exam_plan_today) else it.days.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (it.days <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        if (it.days > 0) {
                            Text(
                                pluralStringResource(R.plurals.exam_plan_days, it.days.toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(Modifier.align(Alignment.End)) {
                // the next open block is checked off when the timer is done
                TextButton(onClick = { studyNow(slots.firstOrNull { it.key !in done }) }) {
                    Text(stringResource(R.string.pomodoro_study_now))
                }
                if (onShare != null) {
                    TextButton(onClick = onShare) {
                        Text(stringResource(R.string.share_title))
                    }
                }
            }

            countdown?.let {
                Text(
                    if (it.lessonsComplete) {
                        pluralStringResource(R.plurals.exam_plan_lessons_left, it.lessonsLeft, it.lessonsLeft, short)
                    } else {
                        pluralStringResource(R.plurals.exam_plan_lessons_left_min, it.lessonsLeft, it.lessonsLeft, short)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.exam_plan_sessions, sessions),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = sessions > 0,
                    onClick = {
                        sessions--
                        PlannerPrefs.setSessions(context, key, sessions)
                    },
                ) { Text("−") }
                TextButton(
                    enabled = sessions < 10,
                    onClick = {
                        sessions++
                        PlannerPrefs.setSessions(context, key, sessions)
                    },
                ) { Text("+") }
            }

            if (sessions > 0 && slots.isEmpty()) {
                Text(
                    stringResource(R.string.exam_plan_no_slots),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            slots.forEach { slot ->
                val checked = slot.key in done
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            PlannerPrefs.setSlotDone(context, key, slot.key, it)
                            done = PlannerPrefs.doneSlots(context, key)
                        },
                    )
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).tapHaptic { editing = slot }) {
                        Text(
                            slot.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + shortDate.format(slot.date) +
                                ", " + slot.start.format(timeFormat) + " – " + slot.end.format(timeFormat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(
                                when (slot.kind) {
                                    StudySlotKind.FREE_PERIOD -> R.string.exam_plan_slot_free
                                    StudySlotKind.AFTERNOON -> R.string.exam_plan_slot_afternoon
                                    StudySlotKind.DAY_OFF -> R.string.exam_plan_slot_day_off
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!checked) {
                        TextButton(onClick = { studyNow(slot) }) { Text("▶") }
                    }
                }
            }
        }
    }

    editing?.let { slot ->
        SlotTimeDialog(
            slot = slot,
            own = PlannerPrefs.slotTime(context, key, slot.key) != null,
            onSave = { start, end ->
                PlannerPrefs.setSlotTime(context, key, slot.key, start, end)
                timesVersion++
                editing = null
            },
            onReset = {
                PlannerPrefs.setSlotTime(context, key, slot.key, null, null)
                timesVersion++
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Change start and end of a study block; the time pickers are the system ones. */
@Composable
private fun SlotTimeDialog(
    slot: sh.lsx.untisx.data.planner.StudySlot,
    own: Boolean,
    onSave: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableIntStateOf(slot.start.hour * 60 + slot.start.minute) }
    var end by remember { mutableIntStateOf(slot.end.hour * 60 + slot.end.minute) }
    val dateFormat = rememberDateFormat("EEEEdMMMM")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_plan_edit_slot)) },
        text = {
            Column {
                Text(dateFormat.format(slot.date), style = MaterialTheme.typography.bodyMedium)
                sh.lsx.untisx.ui.components.SettingsTimeRow(label = stringResource(R.string.exam_plan_slot_start), minute = start, onPick = { start = it })
                sh.lsx.untisx.ui.components.SettingsTimeRow(label = stringResource(R.string.exam_plan_slot_end), minute = end, onPick = { end = it })
                if (end <= start) {
                    Text(stringResource(R.string.exam_plan_slot_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = end > start, onClick = { onSave(start, end) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (own) TextButton(onClick = onReset) { Text(stringResource(R.string.exam_plan_slot_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
