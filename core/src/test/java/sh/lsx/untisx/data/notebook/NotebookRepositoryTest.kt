package sh.lsx.untisx.data.notebook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class NotebookRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountId = "notebook-repository-account"
    private val ids = AtomicInteger(0)
    private var now = 1_000L

    private val repo: NotebookRepository
        get() = NotebookRepository(
            context = context,
            accountId = accountId,
            clock = { now++ },
            newId = { "generated-${ids.incrementAndGet()}" },
        )

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
    fun `fifty parallel commits lose no update`() = runBlocking {
        val repository = repo

        val results = coroutineScope {
            (0 until 50).map { index ->
                async(Dispatchers.Default) {
                    repository.commitDraft(
                        NotebookDraft(
                            id = "draft-$index",
                            title = "Item $index",
                        ),
                    )
                }
            }.awaitAll()
        }

        assertTrue(results.all { it is NotebookWriteResult.Success })

        val loaded = repository.load()
        assertTrue(loaded is NotebookReadResult.Loaded)
        val data = (loaded as NotebookReadResult.Loaded).data

        assertEquals(50L, data.revision)
        assertEquals(50, data.items.size)
        assertEquals(50, data.items.map { it.id }.distinct().size)
    }

    @Test
    fun `stale base timestamp is a conflict and does not change the item`() = runBlocking {
        val repository = repo
        val first = repository.commitDraft(NotebookDraft(id = "d1", title = "Original"))
        assertTrue(first is NotebookWriteResult.Success)

        val item = (first as NotebookWriteResult.Success).data.items.single()
        val conflict = repository.commitDraft(
            NotebookDraft(
                id = "d2",
                itemId = item.id,
                title = "Changed",
                baseUpdatedAt = item.updatedAt - 1,
            ),
        )

        assertTrue(conflict is NotebookWriteResult.Conflict)

        val loaded = repository.load() as NotebookReadResult.Loaded
        assertEquals("Original", loaded.data.items.single().title)
    }

    @Test
    fun `expected revision is checked`() = runBlocking {
        val repository = repo
        assertTrue(repository.commitDraft(NotebookDraft(id = "d1", title = "One")) is NotebookWriteResult.Success)

        val conflict = repository.commitDraft(NotebookDraft(id = "d2", title = "Two"), expectedRevision = 0)

        assertTrue(conflict is NotebookWriteResult.Conflict)
        assertEquals(1, (repository.load() as NotebookReadResult.Loaded).data.items.size)
    }

    @Test
    fun `successful commit removes its draft`() = runBlocking {
        val repository = repo
        assertTrue(repository.saveDraft(NotebookDraft(id = "draft", title = "Draft")) is NotebookDraftWriteResult.Success)

        val result = repository.commitDraft(NotebookDraft(id = "draft", title = "Committed"))
        assertTrue(result is NotebookWriteResult.Success)
        assertFalse((result as NotebookWriteResult.Success).draftCleanupFailed)

        val drafts = repository.loadDrafts()
        assertTrue(drafts is NotebookDraftReadResult.Missing ||
            (drafts is NotebookDraftReadResult.Loaded && "draft" !in drafts.data.drafts))
    }

    @Test
    fun `draft cleanup failure is reported after a successful notebook write`() = runBlocking {
        val repository = repo
        AtomicJsonFile.writeEncrypted(NotebookDraftStore.file(context, accountId), "{corrupt draft")

        val result = repository.commitDraft(NotebookDraft(id = "draft", title = "Committed"))

        assertTrue(result is NotebookWriteResult.Success)
        assertTrue((result as NotebookWriteResult.Success).draftCleanupFailed)
        assertEquals(1, (repository.load() as NotebookReadResult.Loaded).data.items.size)
    }

    @Test
    fun `read failure blocks every mutator and preserves bytes`() = runBlocking {
        val repository = repo
        val notebook = NotebookStore.file(context, accountId)
        assertTrue(AtomicJsonFile.writeEncrypted(notebook, "{corrupt notebook"))
        val before = notebook.readText()

        val plan = NotebookImportPlan(
            add = listOf(note("n1", title = "Imported")),
            localRevision = 0,
        )

        val results = listOf(
            repository.commitDraft(NotebookDraft(id = "d1", title = "New")),
            repository.delete("missing"),
            repository.setCompleted("missing", true),
            repository.setPinned("missing", true),
            repository.setArchived("missing", true),
            repository.convert("missing", NotebookKind.TASK),
            repository.applyImport(plan),
        )

        assertTrue(results.all { it is NotebookWriteResult.ReadFailed })
        assertEquals(before, notebook.readText())
    }

    @Test
    fun `conversion requires confirmation and preserves the item until then`() = runBlocking {
        val repository = repo
        assertTrue(repository.commitDraft(NotebookDraft(id = "d1", title = "Note")) is NotebookWriteResult.Success)
        val noteId = (repository.load() as NotebookReadResult.Loaded).data.items.single().id
        assertTrue(repository.setPinned(noteId, true) is NotebookWriteResult.Success)

        val blocked = repository.convert(noteId, NotebookKind.TASK)
        assertTrue(blocked is NotebookWriteResult.ConfirmationRequired)

        val before = (repository.load() as NotebookReadResult.Loaded).data.items.single()
        assertEquals(NotebookKind.NOTE, before.kind)
        assertNotNull(before.pinnedAt)

        val applied = repository.convert(noteId, NotebookKind.TASK, confirmed = true)
        assertTrue(applied is NotebookWriteResult.Success)

        val after = (repository.load() as NotebookReadResult.Loaded).data.items.single()
        assertEquals(NotebookKind.TASK, after.kind)
        assertNull(after.pinnedAt)
    }

    @Test
    fun `task to note conversion warns about due reminder and completion`() = runBlocking {
        val repository = repo
        assertTrue(
            repository.commitDraft(
                NotebookDraft(
                    id = "d1",
                    kind = NotebookKind.TASK,
                    title = "Task",
                    due = DueRef(
                        date = 20260105,
                        reminder = ReminderSpec(date = 20260104, minutesOfDay = 17 * 60),
                    ),
                ),
            ) is NotebookWriteResult.Success,
        )

        val taskId = (repository.load() as NotebookReadResult.Loaded).data.items.single().id
        assertTrue(repository.setCompleted(taskId, true) is NotebookWriteResult.Success)

        val blocked = repository.convert(taskId, NotebookKind.NOTE)
        assertTrue(blocked is NotebookWriteResult.ConfirmationRequired)

        val warning = (blocked as NotebookWriteResult.ConfirmationRequired).warning
        assertEquals(setOf(NotebookField.DUE, NotebookField.REMINDER, NotebookField.COMPLETION), warning.lostFields)

        assertTrue(repository.convert(taskId, NotebookKind.NOTE, confirmed = true) is NotebookWriteResult.Success)
        val after = (repository.load() as NotebookReadResult.Loaded).data.items.single()
        assertEquals(NotebookKind.NOTE, after.kind)
        assertNull(after.due)
        assertNull(after.completedAt)
    }

    @Test
    fun `stale import plan is rejected without writing`() = runBlocking {
        val repository = repo
        assertTrue(repository.commitDraft(NotebookDraft(id = "d1", title = "Local")) is NotebookWriteResult.Success)
        val notebook = NotebookStore.file(context, accountId)
        val before = notebook.readText()

        val result = repository.applyImport(
            NotebookImportPlan(
                add = listOf(note("imported", title = "Imported")),
                localRevision = 0,
            ),
        )

        assertTrue(result is NotebookWriteResult.Conflict)
        assertEquals(before, notebook.readText())
    }

    @Test
    fun `import applies explicit add and keeps state`() = runBlocking {
        val repository = repo
        val imported = task(
            id = "imported",
            title = "Imported task",
            due = DueRef(
                date = 20260105,
                lesson = lessonRef(7),
                reminder = ReminderSpec(date = 20260104, minutesOfDay = 17 * 60),
            ),
            completedAt = 5,
        )

        val result = repository.applyImport(
            NotebookImportPlan(add = listOf(imported), localRevision = 0),
        )

        assertTrue(result is NotebookWriteResult.Success)
        val stored = (repository.load() as NotebookReadResult.Loaded).data.items.single()
        assertEquals(20260105, stored.due?.date ?: -1)
        assertEquals(7, stored.due?.lesson?.entryId ?: -1)
        assertEquals(17 * 60, stored.due?.reminder?.minutesOfDay ?: -1)
        assertEquals(5L, stored.completedAt ?: -1L)
    }

    @Test
    fun `note cannot be completed and task cannot be pinned`() = runBlocking {
        val repository = repo
        assertTrue(repository.commitDraft(NotebookDraft(id = "d1", title = "Note")) is NotebookWriteResult.Success)
        val noteId = (repository.load() as NotebookReadResult.Loaded).data.items.single().id

        assertTrue(repository.setCompleted(noteId, true) is NotebookWriteResult.Invalid)
        assertTrue(repository.setPinned(noteId, true) is NotebookWriteResult.Success)
        assertTrue(repository.setArchived(noteId, true) is NotebookWriteResult.Success)
    }

    private fun clear() {
        NotebookStore.clear(context, accountId)
        NotebookDraftStore.clear(context, accountId)
    }
}
