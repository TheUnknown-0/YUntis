package sh.lsx.untisx.data.notebook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.FakeCryptor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookDraftStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountId = "notebook-draft-account"
    private val file get() = NotebookDraftStore.file(context, accountId)

    @Before
    fun setUp() {
        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        clear()
    }

    @After
    fun tearDown() {
        clear()
        AccountStore.resetForTests()
    }

    @Test
    fun `missing drafts are not empty data`() = runBlocking {
        assertEquals(NotebookDraftReadResult.Missing, NotebookDraftStore.load(context, accountId))
        assertFalse(file.exists())
    }

    @Test
    fun `draft file is encrypted at rest`() = runBlocking {
        val data = NotebookDraftData(
            revision = 1,
            drafts = mapOf("d1" to NotebookDraft(id = "d1", title = "Secret draft", body = "Secret body")),
        )

        val result = NotebookDraftStore.write(context, accountId, data)
        assertTrue(result is NotebookDraftWriteResult.Success)

        val raw = file.readText()
        assertFalse(raw.contains("Secret draft"))
        assertFalse(raw.contains("Secret body"))

        val loaded = NotebookDraftStore.load(context, accountId)
        assertTrue(loaded is NotebookDraftReadResult.Loaded)
        assertEquals("Secret draft", (loaded as NotebookDraftReadResult.Loaded).data.drafts.getValue("d1").title)
    }

    @Test
    fun `corrupt draft file blocks overwrite`() = runBlocking {
        assertTrue(AtomicJsonFile.writeEncrypted(file, "{bad draft"))
        val before = file.readText()

        assertEquals(
            NotebookDraftReadResult.Failed(NotebookReadFailure.CORRUPT),
            NotebookDraftStore.load(context, accountId),
        )

        val write = NotebookDraftStore.write(
            context,
            accountId,
            NotebookDraftData(drafts = mapOf("d1" to NotebookDraft(id = "d1", title = "New"))),
        )
        assertEquals(NotebookDraftWriteResult.ReadFailed(NotebookReadFailure.CORRUPT), write)
        assertEquals(before, file.readText())
    }

    @Test
    fun `future draft schema is unsupported and blocks overwrite`() = runBlocking {
        assertTrue(AtomicJsonFile.writeEncrypted(file, """{"schema":2,"revision":1,"drafts":{}}"""))
        val before = file.readText()

        assertEquals(
            NotebookDraftReadResult.Failed(NotebookReadFailure.UNSUPPORTED_VERSION),
            NotebookDraftStore.load(context, accountId),
        )

        val write = NotebookDraftStore.write(
            context,
            accountId,
            NotebookDraftData(drafts = mapOf("d1" to NotebookDraft(id = "d1", title = "New"))),
        )
        assertEquals(NotebookDraftWriteResult.ReadFailed(NotebookReadFailure.UNSUPPORTED_VERSION), write)
        assertEquals(before, file.readText())
    }

    private fun clear() {
        NotebookDraftStore.clear(context, accountId)
    }
}
