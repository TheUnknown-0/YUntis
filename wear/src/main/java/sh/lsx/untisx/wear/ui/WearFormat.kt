package sh.lsx.untisx.wear.ui

import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.TimetableEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.getDefault())
private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun timeLabel(value: Int): String = "%02d:%02d".format(value / 100, value % 100)

fun timeRange(entry: TimetableEntry): String = "${timeLabel(entry.startTime)}–${timeLabel(entry.endTime)}"

fun clockLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter)

fun updateLabel(epochMillis: Long): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return if (dateTime.toLocalDate() == AppClock.today()) clockLabel(epochMillis)
    else dateTime.format(DateTimeFormatter.ofPattern("d. MMM, HH:mm", Locale.getDefault()))
}

fun dayTitle(date: LocalDate): String = date.format(dayFormatter)
