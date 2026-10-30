package sh.lsx.untisx.data.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry

class WearPayloadCodecTest {

    private val payload = WearTimetablePayload(
        accountId = "school/user/person",
        displayName = "Max Mustermann",
        server = "school.example",
        fetchedAt = 123L,
        sentAt = 456L,
        defaultColor = 0xFF78A8CF.toInt(),
        weeks = mapOf(
            "20261005" to listOf(
                TimetableEntry(
                    id = 1,
                    date = 20261005,
                    startTime = 800,
                    endTime = 845,
                    subject = listOf(TimetableElement(id = 3, name = "MA")),
                    substText = "Raumänderung",
                ),
            ),
        ),
        holidays = listOf(Holiday(id = 1, name = "Herbstferien", startDate = 20261012, endDate = 20261024)),
        restrictedDays = setOf(20261005),
        subjectColors = mapOf("MA" to 0xFF123456.toInt()),
        subjectAliases = mapOf("MA" to SubjectAlias(short = "Mathe")),
    )

    @Test
    fun `round trip preserves payload`() {
        val decoded = WearPayloadCodec.decode(WearPayloadCodec.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun `decode rejects garbage`() {
        assertNull(WearPayloadCodec.decode(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `rejects unsupported protocol version`() {
        assertNull(WearPayloadCodec.decode(WearPayloadCodec.encode(payload.copy(version = 99))))
    }

    @Test
    fun `rejects oversized compressed input`() {
        assertNull(WearPayloadCodec.decode(ByteArray(WearPayloadCodec.MAX_PAYLOAD_BYTES + 1)))
    }

    @Test
    fun `rejects excessive decompressed size`() {
        val huge = payload.copy(displayName = "a".repeat(WearPayloadCodec.MAX_JSON_BYTES + 1))
        assertNull(WearPayloadCodec.decode(WearPayloadCodec.encode(huge)))
    }

    @Test
    fun `encode produces gzip stream`() {
        val bytes = WearPayloadCodec.encode(payload)

        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
    }
}
