package sh.lsx.untisx.data.notebook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import sh.lsx.untisx.data.CorePrefs
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountRemovalFailure
import sh.lsx.untisx.data.prefs.AccountRemovalResult
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.FakeCryptor
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountStoreNotebookCleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val authA = account("cleanup-a")
    private val authB = account("cleanup-b")
    private val accountA get() = AccountPrefs.accountId(authA)
    private val accountB get() = AccountPrefs.accountId(authB)

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        clear(accountA)
        clear(accountB)
    }

    @After
    fun tearDown() {
        clear(accountA)
        clear(accountB)
        prefs().edit().clear().commit()
        AccountStore.resetForTests()
    }

    @Test
    fun `remove account deletes notebook and drafts for that account only`() = runBlocking {
        AccountStore.addAccount(context, authA, "pw-a")
        AccountStore.addAccount(context, authB, "pw-b")

        assertTrue(
            NotebookStore.write(
                context,
                accountA,
                NotebookData(items = listOf(note("a", title = "A"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookStore.write(
                context,
                accountB,
                NotebookData(items = listOf(note("b", title = "B"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountA,
                NotebookDraftData(drafts = mapOf("da" to NotebookDraft(id = "da", title = "A draft"))),
            ) is NotebookDraftWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountB,
                NotebookDraftData(drafts = mapOf("db" to NotebookDraft(id = "db", title = "B draft"))),
            ) is NotebookDraftWriteResult.Success,
        )

        val result = AccountStore.removeAccountTransactional(context, accountA)

        assertEquals(AccountRemovalResult.Removed, result)
        assertFalse(NotebookStore.file(context, accountA).exists())
        assertFalse(File(NotebookStore.file(context, accountA).path + ".bak").exists())
        assertFalse(NotebookDraftStore.file(context, accountA).exists())
        assertTrue(NotebookAccountLifecycle.isBlocked(context, accountA))

        assertTrue(NotebookStore.file(context, accountB).exists())
        assertTrue(NotebookDraftStore.file(context, accountB).exists())
        assertFalse(NotebookAccountLifecycle.isBlocked(context, accountB))

        assertEquals(listOf(authB), AccountStore.accounts.value)

        val remaining = NotebookStore.load(context, accountB)
        assertTrue(remaining is NotebookReadResult.Loaded)
        assertEquals("B", (remaining as NotebookReadResult.Loaded).data.items.single().title)
    }

    @Test
    fun `cleanup failure reports pending and blocks stale writes`() = runBlocking {
        AccountStore.addAccount(context, authA, "pw-a")

        assertTrue(
            NotebookStore.write(
                context,
                accountA,
                NotebookData(items = listOf(note("a", title = "A"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountA,
                NotebookDraftData(drafts = mapOf("da" to NotebookDraft(id = "da", title = "A draft"))),
            ) is NotebookDraftWriteResult.Success,
        )

        val notebook = NotebookStore.file(context, accountA)
        notebook.delete()
        notebook.mkdirs()
        File(notebook, "child").writeText("x")

        val result = AccountStore.removeAccountTransactional(context, accountA)

        assertTrue(result is AccountRemovalResult.RemovedCleanupPending)
        assertTrue(AccountStore.accounts.value.isEmpty())
        assertTrue(NotebookAccountLifecycle.isBlocked(context, accountA))
        assertTrue(notebook.exists())
        assertFalse(NotebookDraftStore.file(context, accountA).exists())

        val repository = NotebookRepository(context, accountA)
        assertTrue(repository.load() is NotebookReadResult.Missing)
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "post-remove", title = "No resurrection")) is
                NotebookWriteResult.ReadFailed,
        )
        assertTrue(
            repository.saveDraft(NotebookDraft(id = "post-remove-draft", title = "No draft")) is
                NotebookDraftWriteResult.ReadFailed,
        )
    }

    @Test
    fun `removing an absent id cleans orphan data without account persistence`() = runBlocking {
        assertTrue(
            NotebookStore.write(
                context,
                accountA,
                NotebookData(items = listOf(note("a", title = "A"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountA,
                NotebookDraftData(drafts = mapOf("da" to NotebookDraft(id = "da", title = "A draft"))),
            ) is NotebookDraftWriteResult.Success,
        )

        val result = AccountStore.removeAccountTransactional(context, accountA)

        assertEquals(AccountRemovalResult.Removed, result)
        assertTrue(AccountStore.accounts.value.isEmpty())
        assertFalse(NotebookStore.file(context, accountA).exists())
        assertFalse(NotebookDraftStore.file(context, accountA).exists())
        assertTrue(NotebookAccountLifecycle.isBlocked(context, accountA))
    }

    @Test
    fun `account persistence failure retains account and notebook data`() = runBlocking {
        AccountStore.addAccount(context, authA, "pw-a")

        assertTrue(
            NotebookStore.write(
                context,
                accountA,
                NotebookData(items = listOf(note("a", title = "A"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountA,
                NotebookDraftData(drafts = mapOf("da" to NotebookDraft(id = "da", title = "A draft"))),
            ) is NotebookDraftWriteResult.Success,
        )

        AccountStore.cryptor = EncryptFailingCryptor(FakeCryptor())

        val result = AccountStore.removeAccountTransactional(context, accountA)

        assertTrue(result is AccountRemovalResult.Retained)
        assertEquals(
            AccountRemovalFailure.ACCOUNT_PERSISTENCE_FAILED,
            (result as AccountRemovalResult.Retained).failure,
        )
        assertEquals(listOf(authA), AccountStore.accounts.value)
        assertFalse(NotebookAccountLifecycle.isBlocked(context, accountA))

        AccountStore.cryptor = FakeCryptor()

        val notebook = NotebookStore.load(context, accountA)
        assertTrue(notebook is NotebookReadResult.Loaded)
        assertEquals("A", (notebook as NotebookReadResult.Loaded).data.items.single().title)

        val drafts = NotebookDraftStore.load(context, accountA)
        assertTrue(drafts is NotebookDraftReadResult.Loaded)
        assertEquals("A draft", (drafts as NotebookDraftReadResult.Loaded).data.drafts.getValue("da").title)

        val repository = NotebookRepository(context, accountA)
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "after-failure", title = "Still writable")) is
                NotebookWriteResult.Success,
        )
    }

    @Test
    fun `concurrent and stale repository writes cannot resurrect removed account`() = runBlocking {
        AccountStore.addAccount(context, authA, "pw-a")

        val repository = NotebookRepository(context, accountA)
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "before", title = "Before removal")) is
                NotebookWriteResult.Success,
        )

        val (removal, writes) = coroutineScope {
            val removal = async(Dispatchers.Default) {
                AccountStore.removeAccountTransactional(context, accountA)
            }
            val writes = (0 until 10).map { index ->
                async(Dispatchers.Default) {
                    repository.commitDraft(NotebookDraft(id = "race-$index", title = "Race $index"))
                }
            }

            removal to writes.awaitAll()
        }

        assertEquals(AccountRemovalResult.Removed, removal.await())
        assertTrue(
            writes.all { it is NotebookWriteResult.Success || it is NotebookWriteResult.ReadFailed },
        )
        assertFalse(NotebookStore.file(context, accountA).exists())
        assertTrue(NotebookAccountLifecycle.isBlocked(context, accountA))
        assertTrue(repository.load() is NotebookReadResult.Missing)
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "stale", title = "Stale")) is
                NotebookWriteResult.ReadFailed,
        )
    }

    @Test
    fun `readding the same account activates a fresh notebook`() = runBlocking {
        AccountStore.addAccount(context, authA, "pw-a")

        val repository = NotebookRepository(context, accountA)
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "old", title = "Old item")) is
                NotebookWriteResult.Success,
        )
        assertEquals(AccountRemovalResult.Removed, AccountStore.removeAccountTransactional(context, accountA))
        assertTrue(AccountStore.accounts.value.isEmpty())
        assertTrue(NotebookAccountLifecycle.isBlocked(context, accountA))

        AccountStore.addAccount(context, authA, "pw-a2")

        assertEquals(listOf(authA), AccountStore.accounts.value)
        assertFalse(NotebookAccountLifecycle.isBlocked(context, accountA))
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "new", title = "New item")) is
                NotebookWriteResult.Success,
        )

        val loaded = repository.load()
        assertTrue(loaded is NotebookReadResult.Loaded)
        val items = (loaded as NotebookReadResult.Loaded).data.items
        assertEquals(1, items.size)
        assertEquals("New item", items.single().title)
    }

    @Test
    fun `prepared deletion marker finalizes after restart when account is absent`() = runBlocking {
        AccountStore.addAccount(context, authB, "pw-b")

        assertTrue(
            NotebookStore.write(
                context,
                accountB,
                NotebookData(items = listOf(note("b", title = "B"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookStore.write(
                context,
                accountA,
                NotebookData(items = listOf(note("a", title = "A"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountA,
                NotebookDraftData(drafts = mapOf("da" to NotebookDraft(id = "da", title = "A draft"))),
            ) is NotebookDraftWriteResult.Success,
        )
        assertTrue(NotebookAccountLifecycle.prepare(context, accountA))

        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        AccountStore.refresh(context)

        assertEquals(listOf(authB), AccountStore.accounts.value)
        assertFalse(NotebookStore.file(context, accountA).exists())
        assertFalse(NotebookDraftStore.file(context, accountA).exists())
        assertTrue(NotebookAccountLifecycle.isBlocked(context, accountA))
        assertTrue(NotebookStore.file(context, accountB).exists())
    }

    @Test
    fun `prepared deletion marker aborts after restart when account persisted`() = runBlocking {
        AccountStore.addAccount(context, authA, "pw-a")

        assertTrue(
            NotebookStore.write(
                context,
                accountA,
                NotebookData(items = listOf(note("a", title = "A"))),
            ) is NotebookWriteResult.Success,
        )
        assertTrue(
            NotebookDraftStore.write(
                context,
                accountA,
                NotebookDraftData(drafts = mapOf("da" to NotebookDraft(id = "da", title = "A draft"))),
            ) is NotebookDraftWriteResult.Success,
        )
        assertTrue(NotebookAccountLifecycle.prepare(context, accountA))

        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        AccountStore.refresh(context)

        assertEquals(listOf(authA), AccountStore.accounts.value)
        assertFalse(NotebookAccountLifecycle.isBlocked(context, accountA))
        assertTrue(NotebookStore.file(context, accountA).exists())
        assertTrue(NotebookDraftStore.file(context, accountA).exists())

        val repository = NotebookRepository(context, accountA)
        assertTrue(
            repository.commitDraft(NotebookDraft(id = "after-abort", title = "Still writable")) is
                NotebookWriteResult.Success,
        )
    }

    private fun account(user: String) = AuthData(
        server = "https://school.example",
        loginName = "school",
        displayName = "School",
        user = user,
        session = AuthResult(sessionId = "session-$user"),
    )

    private fun prefs() =
        context.getSharedPreferences(CorePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    private fun clear(accountId: String) {
        val notebook = NotebookStore.file(context, accountId)
        val drafts = NotebookDraftStore.file(context, accountId)

        if (notebook.isDirectory) notebook.deleteRecursively()
        if (drafts.isDirectory) drafts.deleteRecursively()

        NotebookStore.clear(context, accountId)
        NotebookDraftStore.clear(context, accountId)
        NotebookAccountLifecycle.activate(context, accountId)
    }

    private class EncryptFailingCryptor(private val delegate: Cryptor) : Cryptor {
        override fun encrypt(plain: String): String = error("encrypt failed")
        override fun decrypt(encoded: String): DecryptResult = delegate.decrypt(encoded)
    }
}
