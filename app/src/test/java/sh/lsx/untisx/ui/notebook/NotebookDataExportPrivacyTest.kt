package sh.lsx.untisx.ui.notebook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import sh.lsx.untisx.data.notebook.NotebookExport
import sh.lsx.untisx.data.notebook.NotebookExportCodec
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind

class NotebookDataExportPrivacyTest {
    @Test fun `data export schema contains only personal entries and format metadata`() {
        val export = NotebookExport(items = listOf(
            NotebookItem(id = "note", kind = NotebookKind.NOTE, body = "Personal note"),
            NotebookItem(id = "task", kind = NotebookKind.TASK, title = "Personal task"),
        ))

        val root = Json.parseToJsonElement(NotebookExportCodec.encode(export)).jsonObject
        assertEquals(setOf("type", "version", "exportedAt", "appVersion", "items"), root.keys)
        assertEquals(2, export.items.size)
    }
}
