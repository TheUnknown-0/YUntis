package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.SubjectTotals
import sh.lsx.untisx.data.TeacherTotals
import androidx.compose.material3.LinearProgressIndicator
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.data.subjectShortLabel
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat
import sh.lsx.untisx.ui.util.tapHaptic
import java.time.LocalDateTime
import java.time.format.TextStyle

/** Full name of a teacher: "first name surname", falling back to the abbreviation. */
internal fun teacherFullName(teacher: MasterDataItem?, fallback: String): String {
    val surname = teacher?.longName?.takeIf { it.isNotBlank() }
    val first = teacher?.foreName?.takeIf { it.isNotBlank() && surname?.contains(it) != true }

    return listOfNotNull(first, surname).joinToString(" ")
        .ifBlank { teacher?.name?.takeIf { it.isNotBlank() } ?: fallback }
}

@Composable
internal fun TeacherInfoScreen(
    teacherId: Int,
    teachersById: Map<Int, MasterDataItem>,
    subjectsById: Map<Int, MasterDataItem>,
    subjectAliases: Map<String, SubjectAlias>,
    ownEntries: List<TimetableEntry>,
    subjectTotals: List<SubjectTotals>,
    colorFor: (String) -> Color,
    textColorFor: (String) -> Color,
    onLessonClick: (TimetableEntry) -> Unit,
    onShowTimetable: (TimetableTarget) -> Unit,
    modifier: Modifier = Modifier,
    ownAbsence: TeacherTotals? = null,
    classAbsence: TeacherTotals? = null,
    className: String = "",
    classLoading: Boolean = false,
    classProgress: Pair<Int, Int>? = null,
) {
    val teacher = teachersById[teacherId]
    val short = teacher?.name.orEmpty()
    val fullName = teacherFullName(teacher, "#$teacherId")

    val info = remember(teacherId, ownEntries) {
        val now = LocalDateTime.now()
        teacherInfo(teacherId, ownEntries, now.toLocalDate().toUntisDate(), now.hour * 100 + now.minute)
    }

    fun subjectName(id: Int) = subjectsById[id]?.name?.takeIf { it.isNotBlank() } ?: "#$id"

    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = rememberDateFormat("dMMM")
    val timeFormat = rememberTimeFormat()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoCard {
            Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            if (short.isNotBlank() && short != fullName) {
                Text(short, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = {
                    onShowTimetable(
                        TimetableTarget(TimetableTarget.TEACHER, teacherId, short.ifBlank { fullName }, fullName),
                    )
                },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.teacher_show_timetable))
            }
        }

        InfoCard {
            SectionTitle(stringResource(R.string.teacher_subjects))

            if (info.subjectIds.isEmpty()) {
                Hint(stringResource(R.string.teacher_subjects_none))
            } else {
                info.subjectIds.forEach { id ->
                    val name = subjectName(id)
                    val long = subjectLongLabel(subjectAliases, name).takeIf { it != name }
                        ?: subjectsById[id]?.longName?.takeIf { it.isNotBlank() && it != name }
                        ?: ""

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectChip(subjectShortLabel(subjectAliases, name), colorFor(name), textColorFor(name))
                        Spacer(Modifier.width(10.dp))
                        Text(long, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        InfoCard {
            SectionTitle(stringResource(R.string.teacher_upcoming))

            if (info.upcoming.isEmpty()) {
                Hint(stringResource(R.string.teacher_upcoming_none))
            } else {
                info.upcoming.forEach { entry ->
                    val name = entry.subject.firstOrNull()?.id?.let(::subjectName) ?: "?"
                    val day = entry.date.toUntisLocalDateOrNull()?.let {
                        it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + dateFormat.format(it)
                    }.orEmpty()
                    val time = entry.startTime.toUntisLocalTime().format(timeFormat)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .tapHaptic { onLessonClick(entry) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectChip(subjectShortLabel(subjectAliases, name), colorFor(name), textColorFor(name), entry.cancelled)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("$day · $time", style = MaterialTheme.typography.bodyMedium)

                            if (entry.cancelled) {
                                Text(
                                    stringResource(R.string.lesson_cancelled),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        InfoCard {
            SectionTitle(stringResource(R.string.teacher_absence))

            AbsenceLine(stringResource(R.string.teacher_absence_own), ownAbsence, locale)
            AbsenceLine(
                if (className.isBlank()) stringResource(R.string.teacher_absence_class_unnamed)
                else stringResource(R.string.teacher_absence_class, className),
                classAbsence,
                locale,
            )

            if (classLoading) {
                val (done, all) = classProgress ?: (0 to 0)
                LinearProgressIndicator(
                    progress = { if (all == 0) 0f else done.toFloat() / all },
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(stringResource(R.string.teacher_absence_loading, done, all))
            }

            Hint(stringResource(R.string.teacher_absence_hint))
        }

        val totals = subjectTotals.filter { it.subjectId in info.subjectIds }

        if (totals.isNotEmpty()) {
            SubjectStatsCard(
                totals = totals,
                nameFor = ::subjectName,
                locale = locale,
                olderDaysMissing = false,
            )
        }
    }
}

@Composable
private fun AbsenceLine(label: String, totals: TeacherTotals?, locale: java.util.Locale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))

        if (totals == null || totals.planned == 0) {
            Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format(locale, "%.0f %%", totals.absentRate * 100f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (totals.absentRate >= 0.15f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.teacher_absence_counts, totals.absent, totals.planned, totals.cancelled, totals.substituted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SubjectChip(label: String, color: Color, textColor: Color, cancelled: Boolean = false) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
        )
    }
}
