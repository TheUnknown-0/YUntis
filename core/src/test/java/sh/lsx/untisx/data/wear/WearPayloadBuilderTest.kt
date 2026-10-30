package sh.lsx.untisx.data.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.PeriodExam
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import java.time.LocalDate
import kotlin.random.Random

class WearPayloadBuilderTest {

    private val today = LocalDate.of(2026, 10, 7)

    private fun entry(date: LocalDate, text: String? = null) = TimetableEntry(
        id = 1,
        date = date.toUntisDate(),
        startTime = 800,
        endTime = 845,
        substText = text,
    )

    private fun week(date: LocalDate, entries: List<TimetableEntry> = listOf(entry(date))) =
        date.toUntisDate().toString() to entries

    private fun build(
        data: TimetableCache.Data,
        windowWeeks: Int = WearPayloadBuilder.WINDOW_WEEKS,
        exams: List<ExamInfo> = emptyList(),
    ) = WearPayloadBuilder.build(
        data = data,
        accountId = "school/user/person",
        displayName = "Max",
        server = "school",
        subjectColors = mapOf("MA" to 0xFF123456.toInt()),
        subjectAliases = mapOf("MA" to SubjectAlias(short = "Mathe")),
        defaultColor = 7,
        sentAt = 99L,
        today = today,
        windowWeeks = windowWeeks,
        exams = exams,
    )

    @Test
    fun `keeps only weeks within the window`() {
        val data = TimetableCache.Data(
            weeks = mapOf(
                week(today),
                week(today.minusDays(14)),
                week(today.plusDays(14)),
                week(today.minusDays(35)),
                week(today.plusDays(35)),
            ),
            lastFetch = 123L,
        )

        val payload = build(data)

        assertEquals(
            setOf(
                today.toUntisDate().toString(),
                today.minusDays(14).toUntisDate().toString(),
                today.plusDays(14).toUntisDate().toString(),
            ),
            payload.weeks.keys,
        )
    }

    @Test fun `matches separately stored exams while preserving embedded fallback`() {
        val lesson = entry(today).copy(subject = listOf(TimetableElement(id = 7)), exam = PeriodExam(id = 1))
        val data = TimetableCache.Data(weeks = mapOf(week(today, listOf(lesson))))
        val exam = ExamInfo(id = 2, date = today.toUntisDate(), subjectId = 7,
            startTime = 800, endTime = 845, name = "Math test", text = "Chapter 2")
        val matched = build(data, exams = listOf(exam)).weeks.values.single().single().exam

        assertEquals(2, matched?.id)
        assertEquals("Math test", matched?.name)
        assertEquals("Chapter 2", matched?.text)
        assertEquals(1, build(data, exams = listOf(exam.copy(subjectId = 8))).weeks.values.single().single().exam?.id)
    }

    @Test
    fun `carries display data without account session`() {
        val data = TimetableCache.Data(
            weeks = mapOf(week(today)),
            lastFetch = 123L,
        )

        val payload = build(data)

        assertEquals("school/user/person", payload.accountId)
        assertEquals("Max", payload.displayName)
        assertEquals("school", payload.server)
        assertEquals(123L, payload.fetchedAt)
        assertEquals(99L, payload.sentAt)
        assertEquals(7, payload.defaultColor)
        assertEquals(mapOf("MA" to 0xFF123456.toInt()), payload.subjectColors)
        assertEquals(mapOf("MA" to SubjectAlias(short = "Mathe")), payload.subjectAliases)
        assertEquals(WearTimetablePayload.VERSION, payload.version)
    }

    @Test
    fun `resolves element names from master data`() {
        val data = TimetableCache.Data(
            weeks = mapOf(
                week(
                    today,
                    listOf(
                        TimetableEntry(
                            id = 1,
                            date = today.toUntisDate(),
                            startTime = 800,
                            endTime = 845,
                            subject = listOf(TimetableElement(id = 3)),
                            room = listOf(TimetableElement(id = 9)),
                        ),
                    ),
                ),
            ),
            subjects = listOf(MasterDataItem(id = 3, name = "MA")),
            rooms = listOf(MasterDataItem(id = 9, name = "R. 205")),
        )

        val entry = build(data).weeks.values.single().single()

        assertEquals("MA", entry.subject.single().name)
        assertEquals("R. 205", entry.room.single().name)
    }

    @Test
    fun `carries only included weeks freshness`() {
        val key = today.toUntisDate().toString()
        val data = TimetableCache.Data(
            weeks = mapOf(week(today)),
            weekFetchedAt = mapOf(key to 123L, "20200101" to 456L),
        )
        assertEquals(mapOf(key to 123L), build(data).weekFetchedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects payload that cannot fit even the smallest window`() {
        WearPayloadBuilder.buildFitting(
            data = TimetableCache.Data(), accountId = "account", displayName = "x".repeat(WearPayloadCodec.MAX_JSON_BYTES),
            server = "school", subjectColors = emptyMap(), subjectAliases = emptyMap(), defaultColor = 0,
        )
    }

    @Test
    fun `build fitting shrinks the window when the payload is too large`() {
        val random = Random(42)

        fun randomText() = buildString {
            repeat(4000) { append('a' + random.nextInt(26)) }
        }

        val weeks = (-3..3).associate { offset ->
            val monday = today.plusWeeks(offset.toLong())
            val entries = (0 until 10).map { entry(monday.plusDays(1), randomText()) }

            monday.toUntisDate().toString() to entries
        }

        val data = TimetableCache.Data(weeks = weeks)

        val wide = WearPayloadBuilder.buildFitting(
            data = data,
            accountId = "school/user/person",
            displayName = "Max",
            server = "school",
            subjectColors = emptyMap(),
            subjectAliases = emptyMap(),
            defaultColor = 0,
            sentAt = 99L,
            today = today,
        )

        assertTrue(wide.bytes.size <= WearPayloadCodec.MAX_PAYLOAD_BYTES)
        assertTrue(wide.payload.weeks.size < weeks.size)
    }
}
