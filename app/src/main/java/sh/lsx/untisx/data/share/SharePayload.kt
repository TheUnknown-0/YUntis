package sh.lsx.untisx.data.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.automation.SchoolDayPlan
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Something one student hands to another, as a QR code or link. No server involved. */
@Serializable
sealed interface SharePayload {
    val from: String

    @Serializable
    @SerialName("hw")
    data class Homework(
        override val from: String = "",
        val title: String,
        val body: String = "",
        val subject: String = "",
        val due: Int = 0,
    ) : SharePayload

    @Serializable
    @SerialName("exam")
    data class Exam(
        override val from: String = "",
        val type: String = "",
        val subject: String = "",
        val date: Int,
        val start: Int = 0,
        val end: Int = 0,
        val name: String = "",
        val text: String = "",
    ) : SharePayload

    /** Lesson stretches of a week (Monday [monday]), to find common free time. Only times, no subjects. */
    @Serializable
    @SerialName("week")
    data class Week(
        override val from: String = "",
        val monday: Int,
        val busy: List<Busy>,
    ) : SharePayload
}

@Serializable
data class Busy(val date: Int, val start: Int, val end: Int)

/** A stretch of time on one day when both are free. */
data class FreeTogether(val date: LocalDate, val start: LocalTime, val end: LocalTime) {
    val minutes: Long get() = java.time.Duration.between(start, end).toMinutes()
}

object ShareCodec {
    const val SCHEME = "yuntis"
    const val HOST = "share"
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }

    /** "yuntis://share?v=1&d=..." with the payload compressed and base64url encoded. */
    fun encode(payload: SharePayload): String {
        val raw = json.encodeToString(SharePayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(raw); finish() }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        val data = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return "$SCHEME://$HOST?v=$VERSION&d=$data"
    }

    /** Reads a link made by [encode]; null for anything else or a damaged code. */
    fun decode(link: String): SharePayload? = runCatching {
        val trimmed = link.trim()
        if (!trimmed.startsWith("$SCHEME://$HOST?")) return null
        val params = trimmed.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        if ((params["v"]?.toIntOrNull() ?: 0) > VERSION) return null
        val bytes = Base64.getUrlDecoder().decode(params["d"] ?: return null)
        val inflater = Inflater(true).apply { setInput(bytes) }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        json.decodeFromString(SharePayload.serializer(), out.toString(Charsets.UTF_8.name()))
    }.getOrNull()

    /** The own lesson stretches of the week starting [monday] (Monday to Friday). */
    fun weekOf(from: String, anyDay: LocalDate, lessons: List<TimetableEntry>): SharePayload.Week {
        val monday = anyDay.with(DayOfWeek.MONDAY)
        val busy = (0..4).flatMap { offset ->
            val day = monday.plusDays(offset.toLong())
            SchoolDayPlan.quietIntervals(lessons, day, mergeGapMinutes = 0).map {
                Busy(day.toUntisDate(), it.start.hour * 100 + it.start.minute, it.end.hour * 100 + it.end.minute)
            }
        }
        return SharePayload.Week(from, monday.toUntisDate(), busy)
    }

    /**
     * Times on school days from the first lesson of either side until [dayEnd] when neither has a
     * lesson, at least [minMinutes] long: free periods both share, and the afternoon.
     */
    fun freeTogether(
        mine: List<Busy>,
        theirs: List<Busy>,
        dayStart: LocalTime = LocalTime.of(7, 30),
        dayEnd: LocalTime = LocalTime.of(17, 0),
        minMinutes: Long = 30,
    ): List<FreeTogether> {
        fun time(v: Int) = LocalTime.of((v / 100).coerceIn(0, 23), (v % 100).coerceIn(0, 59))
        val days = (mine + theirs).map { it.date }.distinct().sorted()
        return days.flatMap { date ->
            val day = LocalDate.of(date / 10000, date / 100 % 100, date % 100)
            val blocks = (mine + theirs).filter { it.date == date }.map { time(it.start) to time(it.end) }.sortedBy { it.first }
            val free = ArrayList<FreeTogether>()
            // the morning before anyone's first lesson is not shared free time
            var cursor = blocks.firstOrNull()?.first?.let { maxOf(it, dayStart) } ?: dayStart
            blocks.forEach { (start, end) ->
                if (start.isAfter(cursor)) free += FreeTogether(day, cursor, minOf(start, dayEnd))
                if (end.isAfter(cursor)) cursor = end
            }
            if (dayEnd.isAfter(cursor)) free += FreeTogether(day, cursor, dayEnd)
            free.filter { it.minutes >= minMinutes }
        }
    }
}
