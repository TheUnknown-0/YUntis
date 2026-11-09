package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SchoolYearStats
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.planner.SubjectDayState
import sh.lsx.untisx.data.planner.SubjectYear
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.tapHaptic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
internal fun SubjectYearScreen(
    subjectId: Int,
    subjectsById: Map<Int, MasterDataItem>,
    stats: SchoolYearStats?,
    yearStart: LocalDate?,
    yearEnd: LocalDate?,
    weeks: Map<Int, List<TimetableEntry>>,
    holidays: List<Holiday>,
    exams: List<ExamInfo>,
    notebook: List<NotebookItem>,
    color: Color,
    onOpenDate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val subject = subjectsById[subjectId]
    val name = subject?.longName?.takeIf { it.isNotBlank() } ?: subject?.name ?: "#$subjectId"
    val lessons = remember(weeks) { weeks.values.flatten() }
    val states = remember(subjectId, stats, lessons) { SubjectYear.dayStates(subjectId, stats, lessons, today) }

    val totals = remember(stats, subjectId) {
        stats?.days?.values?.mapNotNull { it.subjectDays[subjectId] }.orEmpty()
    }
    val planned = totals.sumOf { it.planned }
    val cancelled = totals.sumOf { it.cancelled }
    val substituted = totals.sumOf { it.substituted }

    val subjectExams = exams.filter { it.subjectId == subjectId || (it.subjectId == 0 && it.subjectName.equals(subject?.name, true)) }
    val examDays = subjectExams.map { it.date }.toSet()
    val noteDays = notebook.filter { item -> item.subject?.ids?.contains(subjectId) == true }
        .mapNotNull { it.due?.date ?: it.origin?.date }
        .toSet()

    val start = yearStart ?: today.withDayOfMonth(1)
    val end = yearEnd ?: today.plusMonths(6)
    val left = remember(subjectId, weeks, holidays, end) { SubjectYear.lessonsLeft(subjectId, weeks, holidays, today, end) }
    val months = remember(start, end) {
        generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(end)) }.toList()
    }
    val dateFormat = rememberDateFormat("dMMMMyyyy")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth(), shape = appCardShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stringResource(R.string.subject_year_counts, planned, planned - cancelled - substituted, substituted, cancelled),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.subject_year_left, left, dateFormat.format(end)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Legend(StatHeldColor, stringResource(R.string.stats_legend_held))
                        Legend(StatSubstituteColor, stringResource(R.string.stats_legend_substituted))
                        Legend(StatCancelledColor, stringResource(R.string.stats_legend_cancelled))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Legend(MaterialTheme.colorScheme.error, stringResource(R.string.subject_year_exam), ring = true)
                        Legend(MaterialTheme.colorScheme.tertiary, stringResource(R.string.subject_year_note), dot = true)
                    }
                }
            }
        }

        months.forEach { month ->
            item(key = month.toString()) {
                MonthGrid(month, states, examDays, noteDays, holidays, today, onOpenDate)
            }
        }

        if (subjectExams.isNotEmpty()) {
            item {
                Text(stringResource(R.string.exams_title), style = MaterialTheme.typography.titleMedium)
            }
            subjectExams.sortedBy { it.date }.forEach { exam ->
                item(key = "exam${exam.id}-${exam.date}-${exam.own}") {
                    val day = exam.date.toUntisLocalDateOrNull()
                    Text(
                        listOfNotNull(day?.let(dateFormat::format), exam.examType.ifBlank { null }, exam.name.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (day != null && day.isBefore(today)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().tapHaptic { onOpenDate(exam.date) }.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, ring: Boolean = false, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = if (dot) CircleShape else RoundedCornerShape(3.dp)
        Box(
            Modifier
                .size(if (dot) 6.dp else 10.dp)
                .then(if (ring) Modifier.border(2.dp, color, shape) else Modifier.background(color, shape)),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    states: Map<Int, SubjectDayState>,
    examDays: Set<Int>,
    noteDays: Set<Int>,
    holidays: List<Holiday>,
    today: LocalDate,
    onOpenDate: (Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val cells = (0 until offset).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 7).forEach { index ->
                    val day = week.getOrNull(index)
                    Box(Modifier.weight(1f).aspectRatio(1.4f)) {
                        if (day != null) DayCell(day, states, examDays, noteDays, holidays, today, onOpenDate)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    states: Map<Int, SubjectDayState>,
    examDays: Set<Int>,
    noteDays: Set<Int>,
    holidays: List<Holiday>,
    today: LocalDate,
    onOpenDate: (Int) -> Unit,
) {
    val date = day.toUntisDate()
    val state = states[date]
    val holiday = holidays.any { date in it.startDate..it.endDate }
    val background = when (state) {
        SubjectDayState.HELD -> StatHeldColor
        SubjectDayState.SUBSTITUTED -> StatSubstituteColor
        SubjectDayState.PARTLY_CANCELLED -> StatCancelledColor.copy(alpha = 0.55f)
        SubjectDayState.CANCELLED -> StatCancelledColor
        SubjectDayState.PLANNED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        null -> if (holiday) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else Color.Transparent
    }
    val ink = when (state) {
        SubjectDayState.HELD, SubjectDayState.CANCELLED, SubjectDayState.SUBSTITUTED -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(background)
            .then(if (date in examDays) Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape) else Modifier)
            .then(if (day == today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .tapHaptic { onOpenDate(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            fontSize = 11.sp,
            color = if (state == null && day.dayOfWeek.value > 5) MaterialTheme.colorScheme.onSurfaceVariant else ink,
            fontWeight = if (state != null) FontWeight.Bold else FontWeight.Normal,
        )
        if (date in noteDays) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}
