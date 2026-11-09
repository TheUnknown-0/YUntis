package sh.lsx.untisx.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesParserTest {
    @Test
    fun `headings, tagged bullets, links and paragraphs are recognised`() {
        val blocks = ReleaseNotesParser.parse(
            """
            Kurze Einleitung.

            ### Neu
            * [Widgets] Drei **neue** Widgets
            - ohne Tag mit [Link](https://example.org)
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                NoteBlock.Paragraph("Kurze Einleitung."),
                NoteBlock.Heading("Neu", 3),
                NoteBlock.Bullet("Widgets", "Drei **neue** Widgets"),
                NoteBlock.Bullet(null, "ohne Tag mit Link"),
            ),
            blocks,
        )
    }
}
