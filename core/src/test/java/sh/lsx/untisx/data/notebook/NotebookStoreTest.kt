package sh.lsx.untisx.data.notebook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountId = "notebook-store-account"
    private val file get() = NotebookStore.file(context, accountId)

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
    fun `missing is not empty and creates no file`() = runBlocking {
        assertEquals(NotebookReadResult.Missing, NotebookStore.load(context, accountId))
        assertFalse(file.exists())
    }

    @Test
    fun `notebook is encrypted at rest`() = runBlocking {
        val data = NotebookData(
            revision = 1,
            items = listOf(note("n1", title = "Secret title", body = "Secret body")),
            updatedAt = 1,
        )

        val result = NotebookStore.write(context, accountId, data)
        assertTrue(result is NotebookWriteResult.Success)

        val raw = file.readText()
        assertFalse(raw.contains("Secret title"))
        assertFalse(raw.contains("Secret body"))

        val loaded = NotebookStore.load(context, accountId)
        assertTrue(loaded is NotebookReadResult.Loaded)
        assertEquals("Secret title", (loaded as NotebookReadResult.Loaded).data.items.single().title)
    }

    @Test
    fun `corrupt json blocks every overwrite`() = runBlocking {
        assertTrue(AtomicJsonFile.writeEncrypted(file, "{not valid json"))
        val before = file.readText()

        assertEquals(NotebookReadResult.Failed(NotebookReadFailure.CORRUPT), NotebookStore.load(context, accountId))

        val write = NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "New"))))
        assertEquals(NotebookWriteResult.ReadFailed(NotebookReadFailure.CORRUPT), write)
        assertEquals(before, file.readText())
    }

    @Test
    fun `plaintext is not migrated and blocks writes`() = runBlocking {
        file.writeText("""{"schema":1,"revision":0,"items":[],"updatedAt":0}""")
        val before = file.readText()

        assertEquals(
            NotebookReadResult.Failed(NotebookReadFailure.NOT_ENCRYPTED),
            NotebookStore.load(context, accountId),
        )

        val write = NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "New"))))
        assertEquals(NotebookWriteResult.ReadFailed(NotebookReadFailure.NOT_ENCRYPTED), write)
        assertEquals(before, file.readText())
    }

    @Test
    fun `key loss blocks writes and preserves bytes`() = runBlocking {
        assertTrue(NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "Old")))) is NotebookWriteResult.Success)
        val before = file.readText()

        AccountStore.cryptor = object : Cryptor {
            override fun encrypt(plain: String): String = error("key unavailable")
            override fun decrypt(encoded: String): DecryptResult = DecryptResult.KeyLost
        }

        assertEquals(NotebookReadResult.Failed(NotebookReadFailure.KEY_LOST), NotebookStore.load(context, accountId))

        val write = NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "New"))))
        assertEquals(NotebookWriteResult.ReadFailed(NotebookReadFailure.KEY_LOST), write)
        assertEquals(before, file.readText())
    }

    @Test
    fun `transient read failure blocks writes`() = runBlocking {
        assertTrue(NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "Old")))) is NotebookWriteResult.Success)
        val before = file.readText()

        AccountStore.cryptor = object : Cryptor {
            override fun encrypt(plain: String): String = error("key unavailable")
            override fun decrypt(encoded: String): DecryptResult = DecryptResult.Transient
        }

        assertEquals(NotebookReadResult.Failed(NotebookReadFailure.TRANSIENT), NotebookStore.load(context, accountId))

        val write = NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "New"))))
        assertEquals(NotebookWriteResult.ReadFailed(NotebookReadFailure.TRANSIENT), write)
        assertEquals(before, file.readText())
    }

    @Test
    fun `future schema blocks writes`() = runBlocking {
        assertTrue(AtomicJsonFile.writeEncrypted(file, """{"schema":2,"revision":1,"items":[],"updatedAt":0}"""))
        val before = file.readText()

        assertEquals(
            NotebookReadResult.Failed(NotebookReadFailure.UNSUPPORTED_VERSION),
            NotebookStore.load(context, accountId),
        )

        val write = NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "New"))))
        assertEquals(NotebookWriteResult.ReadFailed(NotebookReadFailure.UNSUPPORTED_VERSION), write)
        assertEquals(before, file.readText())
    }

    @Test
    fun `a backup without a base file is not missing`() = runBlocking {
        val backup = File(file.path + ".bak")
        backup.writeText("recoverable")

        assertEquals(NotebookReadResult.Failed(NotebookReadFailure.IO), NotebookStore.load(context, accountId))

        val write = NotebookStore.write(context, accountId, NotebookData(items = listOf(note("n1", title = "New"))))
        assertEquals(NotebookWriteResult.ReadFailed(NotebookReadFailure.IO), write)
        assertTrue(backup.exists())
    }

    @Test
    fun `invalid data is rejected before writing`() = runBlocking {
        val result = NotebookStore.write(
            context,
            accountId,
            NotebookData(
                items = listOf(
                    note("n1", title = "Note", origin = lessonRef(1)).copy(due = DueRef(20260105)),
                ),
            ),
        )

        assertTrue(result is NotebookWriteResult.Invalid)
        assertFalse(file.exists())
    }

    private fun clear() {
        NotebookStore.clear(context, accountId)
        NotebookDraftStore.clear(context, accountId)
    }
}
