package sh.lsx.untisx.ui.notebook

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookImportFileTest {

    @Test
    fun `reads text below the byte cap`() {
        val result = readNotebookText(
            ByteArrayInputStream("notebook".toByteArray()),
            maxBytes = 64,
        )

        assertEquals("notebook", (result as NotebookFileReadResult.Text).text)
    }

    @Test
    fun `rejects text above the byte cap`() {
        val result = readNotebookText(
            ByteArrayInputStream("notebook".toByteArray()),
            maxBytes = 4,
        )

        assertEquals(NotebookFileReadResult.TooLarge, result)
    }

    @Test
    fun `returns failed for an unreadable stream`() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IllegalStateException("broken")
        }

        assertEquals(NotebookFileReadResult.Failed, readNotebookText(broken))
    }

    @Test
    fun `accepts exactly the byte cap`() {
        val result = readNotebookText(
            ByteArrayInputStream("1234".toByteArray()),
            maxBytes = 4,
        )

        assertTrue(result is NotebookFileReadResult.Text)
    }
}
