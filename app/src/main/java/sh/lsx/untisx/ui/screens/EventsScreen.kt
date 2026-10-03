package sh.lsx.untisx.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDate
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.ui.timetable.TIME_FORMAT
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.tapHaptic
import sh.lsx.untisx.ui.timetable.rememberToday
import sh.lsx.untisx.ui.timetable.resolveNames
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun Int.tryLocalDate(): LocalDate? = runCatching { toUntisLocalDate() }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    events: List<TimetableEntry>,
    holidays: List<Holiday>,
    messages: List<DayMessage>,
    roomsById: Map<Int, MasterDataItem>,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onEventClick: (TimetableEntry) -> Unit = {},
    exams: List<ExamInfo> = emptyList(),
    subjectsById: Map<Int, MasterDataItem> = emptyMap(),
    teachersById: Map<Int, MasterDataItem> = emptyMap(),
) {
    val today = rememberToday()

    val upcomingEvents = remember(events, today) {
        events
            .filter { (it.date.tryLocalDate() ?: return@filter false) >= today }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    val upcomingExams = remember(exams, today) {
        exams
            .filter { (it.date.tryLocalDate() ?: return@filter false) >= today }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    val upcomingHolidays = remember(holidays, today) {
        holidays
            .filter { (it.endDate.tryLocalDate() ?: return@filter false) >= today }
            .sortedBy { it.startDate }
            .take(5)
    }

    PullToRefreshBox(
        isRefreshing = loading && (upcomingEvents.isNotEmpty() || upcomingHolidays.isNotEmpty() || messages.isNotEmpty()),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        val isEmpty = upcomingEvents.isEmpty() && upcomingHolidays.isEmpty() && messages.isEmpty() &&
            upcomingExams.isEmpty()

        when {
            isEmpty && loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            isEmpty -> EventsEmptyState()

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (messages.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.events_messages))
                        }

                        itemsIndexed(messages, key = { index, message -> "message-${message.id}:$index" }) { _, message ->
                            MessageCard(message = message)
                        }
                    }

                    if (upcomingEvents.isNotEmpty()) {
                        item {
                            SectionHeader(
                                text = stringResource(R.string.events_upcoming),
                                modifier = Modifier.padding(top = if (messages.isNotEmpty()) 16.dp else 0.dp),
                            )
                        }

                        val byDate = upcomingEvents.groupBy { it.date }

                        byDate.forEach { (date, dayEvents) ->
                            item {
                                DayLabel(date.tryLocalDate(), today)
                            }

                            items(dayEvents) { event ->
                                EventCard(
                                    event = event,
                                    rooms = resolveNames(event.room, roomsById),
                                    onClick = { onEventClick(event) },
                                )
                            }
                        }
                    }

                    if (upcomingExams.isNotEmpty()) {
                        item {
                            SectionHeader(
                                text = stringResource(R.string.exams_title),
                                modifier = Modifier.padding(
                                    top = if (upcomingEvents.isNotEmpty() || messages.isNotEmpty()) 16.dp else 0.dp,
                                ),
                            )
                        }

                        val examsByDate = upcomingExams.groupBy { it.date }

                        examsByDate.forEach { (date, dayExams) ->
                            item {
                                DayLabel(date.tryLocalDate(), today)
                            }

                            itemsIndexed(
                                dayExams,
                                key = { index, exam -> "exam-${exam.id}-${exam.date}-${exam.startTime}:$index" },
                            ) { _, exam ->
                                ExamCard(
                                    exam = exam,
                                    subjectsById = subjectsById,
                                    teachersById = teachersById,
                                    roomsById = roomsById,
                                )
                            }
                        }
                    }

                    if (upcomingHolidays.isNotEmpty()) {
                        item {
                            SectionHeader(
                                text = stringResource(R.string.events_holidays),
                                modifier = Modifier.padding(top = if (upcomingEvents.isNotEmpty() || messages.isNotEmpty()) 16.dp else 0.dp),
                            )
                        }

                        items(upcomingHolidays) { holiday ->
                            HolidayCard(holiday = holiday, today = today)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun MessageCard(
    message: DayMessage,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (message.subject.isNotBlank()) {
                    Text(
                        text = message.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DayLabel(
    date: LocalDate?,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val dayFormat = rememberDateFormat("EEE, d. MMM")

    val text = when (date) {
        null -> "?"
        today -> stringResource(R.string.timetable_today)
        today.plusDays(1) -> stringResource(R.string.events_tomorrow)
        else -> dayFormat.format(date)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun EventCard(
    event: TimetableEntry,
    rooms: String,
    onClick: () -> Unit,
) {
    val timed = event.startTime != 0 && event.endTime != 0

    val timeText = if (timed) {
        "${event.startTime.toUntisLocalTime().format(TIME_FORMAT)} – ${event.endTime.toUntisLocalTime().format(TIME_FORMAT)}"
    } else {
        stringResource(R.string.events_all_day)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tapHaptic(onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = event.eventTitle ?: stringResource(R.string.events_untitled),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (rooms.isNotBlank()) {
                    Text(
                        text = rooms,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ExamCard(
    exam: ExamInfo,
    subjectsById: Map<Int, MasterDataItem>,
    teachersById: Map<Int, MasterDataItem>,
    roomsById: Map<Int, MasterDataItem>,
) {
    val type = exam.examType.takeIf { it.isNotBlank() } ?: stringResource(R.string.lesson_exam)
    val subject = subjectsById[exam.subjectId]?.name?.takeIf { it.isNotBlank() }
    val rooms = exam.roomIds.mapNotNull { roomsById[it]?.name?.takeIf { name -> name.isNotBlank() } }.distinct()
    val teachers = exam.teacherIds.mapNotNull { teachersById[it]?.name?.takeIf { name -> name.isNotBlank() } }.distinct()
    val timed = exam.startTime != 0 || exam.endTime != 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = listOfNotNull(type, subject).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (exam.name.isNotBlank()) {
                    Text(
                        text = exam.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (exam.text.isNotBlank()) {
                    Text(
                        text = exam.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (teachers.isNotEmpty()) {
                    Text(
                        text = teachers.joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (rooms.isNotEmpty()) {
                    Text(
                        text = rooms.joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (timed) {
                Text(
                    text = "${exam.startTime.toUntisLocalTime().format(TIME_FORMAT)} – " +
                        exam.endTime.toUntisLocalTime().format(TIME_FORMAT),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun HolidayCard(
    holiday: Holiday,
    today: LocalDate,
) {
    val start = holiday.startDate.tryLocalDate()
    val end = holiday.endDate.tryLocalDate()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = holiday.longName.ifBlank { holiday.name },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (start != null && end != null) {
                    Text(
                        text = stringResource(
                            R.string.events_date_range,
                            rememberDateFormat("d. MMM").format(start),
                            rememberDateFormat("d. MMM").format(end),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (start != null && end != null) {
                Text(
                    text = holidayCountdown(start, end, today),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun holidayCountdown(
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
): String {
    val days = ChronoUnit.DAYS.between(today, start)
    val dateFormat = rememberDateFormat("d. MMM")

    return when {
        days <= 0L -> stringResource(R.string.events_holiday_running, dateFormat.format(end))
        days == 1L -> stringResource(R.string.events_holiday_tomorrow)
        else -> pluralStringResource(R.plurals.events_holiday_days, days.toInt(), days)
    }
}

@Composable
private fun EventsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.events_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.events_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
