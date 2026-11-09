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
    val exam: PeriodExam? = null,
    val layoutStartPosition: Int = 0,
    val layoutWidth: Int = 1000,
    val backColor: String? = null,
    val foreColor: String? = null,
) {
    val cancelled: Boolean get() = code == "cancelled"
    val irregular: Boolean get() = code == "irregular"

    val isEvent: Boolean get() = subject.isEmpty()

    val eventTitle: String?
        get() = listOfNotNull(lstext, lessonText, info, substText)
            .firstOrNull { it.isNotBlank() }
}

@Serializable
data class PeriodExam(
    val id: Int = 0,
    val examtype: String? = null,
    val name: String? = null,
    val text: String? = null,
)

@Serializable
data class ExamInfo(
    val id: Int = 0,
    val examType: String = "",
    val name: String = "",
    val text: String = "",
    val date: Int = 0,
    val startTime: Int = 0,
    val endTime: Int = 0,
    val subjectId: Int = 0,
    val roomIds: List<Int> = emptyList(),
    val teacherIds: List<Int> = emptyList(),
    val klasseIds: List<Int> = emptyList(),
    /** Added by the user in the app, not by the school. */
    val own: Boolean = false,
    /** Subject short name, kept for own exams in case the subject id is unknown. */
    val subjectName: String = "",
)

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
    val backColor: String? = null,
    val foreColor: String? = null,
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

@Serializable
data class SchoolYear(
    val id: Int = 0,
    val name: String = "",
    val startDate: Int = 0,
    val endDate: Int = 0,
)

fun LocalDate.toUntisDate(): Int = year * 10000 + monthValue * 100 + dayOfMonth

fun Int.toUntisLocalDate(): LocalDate =
    LocalDate.of(this / 10000, (this / 100) % 100, this % 100)

fun Int.toUntisLocalDateOrNull(): LocalDate? =
    runCatching { toUntisLocalDate() }.getOrNull()

fun Int.toUntisLocalTime(): LocalTime {
    val hour = (this / 100).coerceIn(0, 23)
    val minute = (this % 100).coerceIn(0, 59)

    return LocalTime.of(hour, minute)
}

fun LocalDate.toUntisWeekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun LocalDate.toVisibleWeekStart(): LocalDate {
    val monday = toUntisWeekStart()

    return if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
        monday.plusWeeks(1)
    } else {
        monday
    }
}

fun LocalDate.plusUntisWeeks(weeks: Long): LocalDate {
    val monday = toUntisWeekStart()
    return monday.plusWeeks(weeks)
}

/**
 * Another timetable than the account's own one: a class or a student.
 * [type] uses Untis element types (1 = class, 5 = student).
 */
@Serializable
data class TimetableTarget(
    val type: Int,
    val id: Int,
    val name: String,
    val longName: String = "",
) {
    companion object {
        const val CLASS = 1
        const val TEACHER = 2
        const val STUDENT = 5
    }
}

/** One absence of the student. Times use Untis format (date YYYYMMDD, time HHMM). */
@Serializable
data class Absence(
    val id: Int = 0,
    val date: Int = 0,
    val startTime: Int = 0,
    val endDate: Int = 0,
    val endTime: Int = 0,
    val reason: String = "",
    val text: String = "",
    val excused: Boolean = false,
    val excuseStatus: String = "",
) {
    /** Length in minutes if the absence lies within one day, otherwise 0. */
    val minutes: Int
        get() = if (endDate != date || endTime <= startTime) 0
        else (endTime / 100 * 60 + endTime % 100) - (startTime / 100 * 60 + startTime % 100)
}
