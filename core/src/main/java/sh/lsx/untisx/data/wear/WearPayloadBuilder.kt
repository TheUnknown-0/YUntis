package sh.lsx.untisx.data.wear

import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.ExamsMatcher
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.PeriodExam
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WearPayloadBuilder {

    const val WINDOW_WEEKS = 4

    private val WINDOW_STEPS = intArrayOf(WINDOW_WEEKS, 2, 1)

    class Encoded(val payload: WearTimetablePayload, val bytes: ByteArray)

    fun build(
        data: TimetableCache.Data,
        accountId: String,
        displayName: String,
        server: String,
        subjectColors: Map<String, Int>,
        subjectAliases: Map<String, SubjectAlias>,
        defaultColor: Int,
        sentAt: Long = System.currentTimeMillis(),
        today: LocalDate = AppClock.today(),
        windowWeeks: Int = WINDOW_WEEKS,
        exams: List<ExamInfo> = emptyList(),
        requestId: String = "",
        refreshResult: WearRefreshResult = WearRefreshResult.CACHE,
    ): WearTimetablePayload {
        val maxDays = windowWeeks * 7L

        val subjectsById = data.subjects.associateBy { it.id }
        val teachersById = data.teachers.associateBy { it.id }
        val roomsById = data.rooms.associateBy { it.id }

        val weeks = data.weeks.filterKeys { key ->
            val date = key.toIntOrNull()?.toUntisLocalDateOrNull() ?: return@filterKeys false
            val distance = ChronoUnit.DAYS.between(today, date)

            distance in -maxDays..maxDays
        }.mapValues { (_, entries) ->
            entries.map { entry ->
                entry.copy(
                    subject = resolveElements(entry.subject, subjectsById),
                    teacher = resolveElements(entry.teacher, teachersById),
                    room = resolveElements(entry.room, roomsById),
                    exam = ExamsMatcher.best(entry, exams)?.let {
                        PeriodExam(it.id, it.examType, it.name, it.text)
                    } ?: entry.exam,
                )
            }
        }

        return WearTimetablePayload(
            accountId = accountId,
            displayName = displayName,
            server = server,
            fetchedAt = data.lastFetch,
            weekFetchedAt = data.weekFetchedAt.filterKeys { it in weeks },
            sentAt = sentAt,
            requestId = requestId,
            refreshResult = refreshResult,
            defaultColor = defaultColor,
            weeks = weeks,
            holidays = data.holidays,
            timegrid = data.timegrid,
            restrictedDays = data.restrictedDays,
            subjectColors = subjectColors,
            subjectAliases = subjectAliases,
        )
    }

    fun buildFitting(
        data: TimetableCache.Data,
        accountId: String,
        displayName: String,
        server: String,
        subjectColors: Map<String, Int>,
        subjectAliases: Map<String, SubjectAlias>,
        defaultColor: Int,
        sentAt: Long = System.currentTimeMillis(),
        today: LocalDate = AppClock.today(),
        exams: List<ExamInfo> = emptyList(),
        requestId: String = "",
        refreshResult: WearRefreshResult = WearRefreshResult.CACHE,
    ): Encoded {
        for (window in WINDOW_STEPS) {
            val payload = build(
                data = data,
                accountId = accountId,
                displayName = displayName,
                server = server,
                subjectColors = subjectColors,
                subjectAliases = subjectAliases,
                defaultColor = defaultColor,
                sentAt = sentAt,
                today = today,
                windowWeeks = window,
                exams = exams,
                requestId = requestId,
                refreshResult = refreshResult,
            )

            val encoded = Encoded(payload, WearPayloadCodec.encode(payload))

            if (encoded.bytes.size <= WearPayloadCodec.MAX_PAYLOAD_BYTES &&
                WearPayloadCodec.encodeJson(payload).toByteArray(Charsets.UTF_8).size <= WearPayloadCodec.MAX_JSON_BYTES
            ) return encoded
        }

        throw IllegalArgumentException("Timetable exceeds the Wear payload limit")
    }

    private fun resolveElements(
        elements: List<TimetableElement>,
        data: Map<Int, MasterDataItem>,
    ): List<TimetableElement> = elements.map { element ->
        if (element.name.isNotBlank()) {
            element
        } else {
            data[element.id]?.let { item ->
                element.copy(
                    name = item.name,
                    longName = element.longName ?: item.longName.takeIf { it.isNotBlank() },
                )
            } ?: element
        }
    }
}
