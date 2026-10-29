package sh.lsx.untisx.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookExportTest {
    private val export = NotebookExport(
        exportedAt = 123,
        appVersion = "test",
        items = listOf(
            task(
                id = "t1",
                title = "Task",
                body = "Body",
                subject = subject(),
                origin = lessonRef(1),
                due = DueRef(
                    date = 20260105,
                    lesson = lessonRef(1),
                    reminder = ReminderSpec(date = 20260104, minutesOfDay = 17 * 60),
                ),
            ),
            note(id = "n1", title = "Note", pinnedAt = 5),
        ),
    )

    @Test
    fun `encode and decode round trip`() {
        val decoded = NotebookExportCodec.decode(NotebookExportCodec.encode(export))

        assertTrue(decoded is NotebookDecodeResult.Valid)
        assertEquals(export, (decoded as NotebookDecodeResult.Valid).export)
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = """
            {
              "type": "untisx-notebook",
              "version": 1,
              "exportedAt": 1,
              "appVersion": "x",
              "unknownTop": true,
              "items": [
                {
                  "id": "n1",
                  "kind": "NOTE",
                  "title": "Note",
                  "unknownItem": 42
                }
              ]
            }
        """.trimIndent()

        val decoded = NotebookExportCodec.decode(text)
        assertTrue(decoded is NotebookDecodeResult.Valid)
        assertEquals("Note", (decoded as NotebookDecodeResult.Valid).export.items.single().title)
    }

    @Test
    fun `wrong type is rejected`() {
        val text = """{"type":"something-else","version":1,"items":[]}"""
        val decoded = NotebookExportCodec.decode(text)

        assertTrue(decoded is NotebookDecodeResult.Invalid)
    }

    @Test
    fun `future version is unsupported`() {
        val text = """{"type":"untisx-notebook","version":2,"items":[]}"""
        assertEquals(NotebookDecodeResult.UnsupportedVersion, NotebookExportCodec.decode(text))
    }

    @Test
    fun `oversized input is rejected before parsing`() {
        val text = "x".repeat(NotebookExportCodec.MAX_BYTES + 1)
        assertEquals(NotebookDecodeResult.TooLarge, NotebookExportCodec.decode(text))
    }

    @Test
    fun `duplicate ids and invalid dates are rejected`() {
        val duplicate = NotebookExport(
            items = listOf(note("same", title = "A"), note("same", title = "B")),
        )
        assertTrue(NotebookExportCodec.decode(NotebookExportCodec.encode(duplicate)) is NotebookDecodeResult.Invalid)

        val invalidDate = NotebookExport(
            items = listOf(
                task(
                    id = "bad",
                    title = "Bad",
                    due = DueRef(date = 20260230),
                ),
            ),
        )
        assertTrue(NotebookExportCodec.decode(NotebookExportCodec.encode(invalidDate)) is NotebookDecodeResult.Invalid)
    }

    @Test
    fun `invalid lesson times are rejected`() {
        val invalidTime = NotebookExport(
            items = listOf(
                note(
                    id = "bad",
                    title = "Bad",
                    origin = lessonRef(entryId = 1, startTime = 1000, endTime = 900),
                ),
            ),
        )
        assertTrue(NotebookExportCodec.decode(NotebookExportCodec.encode(invalidTime)) is NotebookDecodeResult.Invalid)
    }

    @Test
    fun `blank text is rejected`() {
        val blank = NotebookExport(items = listOf(note("blank")))
        assertTrue(NotebookExportCodec.decode(NotebookExportCodec.encode(blank)) is NotebookDecodeResult.Invalid)
    }
}
