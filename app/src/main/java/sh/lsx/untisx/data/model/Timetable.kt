package sh.lsx.untisx.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@Serializable
data class TimetableElement(
    val id: Int = 0,
    val name: String = "",
    @SerialName("longName") val longName: String? = null,
    @SerialName("orgid") val orgId: Int? = null,
    @SerialName("orgname") val orgName: String? = null,
)

@Serializable
data class TimetableEntry(
    val id: Int = 0,
    val date: Int = 0,
    val startTime: Int = 0,
    val endTime: Int = 0,
    @SerialName("kl") val klasse: List<TimetableElement> = emptyList(),
    @SerialName("te") val teacher: List<TimetableElement> = emptyList(),
    @SerialName("su") val subject: List<TimetableElement> = emptyList(),
    @SerialName("ro") val room: List<TimetableElement> = emptyList(),
    val code: String? = null,
    val lstext: String? = null,
    val info: String? = null,
    val substText: String? = null,
    val lessonText: String? = null,
    val activityType: String? = null,
) {
    val cancelled: Boolean get() = code == "cancelled"
    val irregular: Boolean get() = code == "irregular"

    val isEvent: Boolean get() = subject.isEmpty()

    val eventTitle: String?
        get() = listOfNotNull(lstext, lessonText, info, substText)
            .firstOrNull { it.isNotBlank() }
}

@Serializable
data class TimegridUnit(
    val label: String? = null,
    val startTime: Int = 0,
    val endTime: Int = 0,
    val canLesson: Boolean? = null,
)

@Serializable
data class TimegridDay(
    val day: Int = 0,
    val name: String? = null,
    val timeUnits: List<TimegridUnit> = emptyList(),
)

@Serializable
data class MasterDataItem(
    val id: Int = 0,
    val name: String = "",
    @SerialName("longName") val longName: String = "",
    @SerialName("foreName") val foreName: String = "",
)

@Serializable
data class Holiday(
    val id: Int = 0,
    val name: String = "",
    @SerialName("longName") val longName: String = "",
    val startDate: Int = 0,
    val endDate: Int = 0,
)

@Serializable
data class DayMessage(
    val id: Int = 0,
    val subject: String = "",
    val body: String = "",
)

fun LocalDate.toUntisDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth

fun Int.toUntisLocalDate(): LocalDate =
    LocalDate.of(this / 10000, (this / 100) % 100, this % 100)

fun Int.toUntisLocalTime(): LocalTime = LocalTime.of(this / 100, this % 100)

fun LocalDate.toUntisWeekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun LocalDate.plusUntisWeeks(weeks: Long): LocalDate {
    val monday = toUntisWeekStart()
    return monday.plusWeeks(weeks)
}
