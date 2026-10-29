package sh.lsx.untisx.ui.notebook

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookData
import sh.lsx.untisx.data.notebook.NotebookDraft
import sh.lsx.untisx.data.notebook.NotebookDraftReadResult
import sh.lsx.untisx.data.notebook.NotebookDraftStore
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.notebook.NotebookStore
import sh.lsx.untisx.data.notebook.ReminderSpec
import sh.lsx.untisx.data.notebook.SubjectRef
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.FakeCryptor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookViewModelTest {

    private lateinit var app: Application
    private lateinit var dispatcher: TestDispatcher
    private lateinit var repository: NotebookRepository
    private lateinit var reminders: FakeReminderSink

    private val accountId = "notebook-viewmodel-test"
    private val viewModels = mutableListOf<NotebookViewModel>()
    private var now = 10_000L
    private var nextId = 0

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        NotebookStore.clear(app, accountId)
        NotebookDraftStore.clear(app, accountId)

        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)

        repository = NotebookRepository(
            context = app,
            accountId = accountId,
            clock = { now++ },
            newId = { "generated-${nextId++}" },
        )
        reminders = FakeReminderSink()
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()

        NotebookStore.clear(app, accountId)
        NotebookDraftStore.clear(app, accountId)
        AccountStore.resetForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun `dirty conversion requires an explicit decision and save-first keeps edits`() = runBlocking {
        seed(task("t1", title = "Original", due = DueRef(20261029)))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }

        vm.updateDraft { it.copy(title = "Edited") }
        vm.requestConversion("t1", NotebookKind.NOTE)
        awaitCondition { vm.pendingConversion.value != null }

        val request = vm.pendingConversion.value!!
        assertTrue(request.requiresEditorDecision)
        assertEquals(NotebookKind.TASK, items().first { it.id == "t1" }.kind)
        assertEquals("Edited", (vm.editorState.value as NotebookEditorState.Ready).draft.title)

        vm.saveThenConvert("t1", NotebookKind.NOTE)
        awaitCondition {
            (vm.listState.value as? NotebookListState.Ready)
                ?.items
                ?.firstOrNull { it.id == "t1" }
                ?.kind == NotebookKind.NOTE
        }

        val converted = items().first { it.id == "t1" }
        assertEquals(NotebookKind.NOTE, converted.kind)
        assertEquals("Edited", converted.title)
        assertTrue(drafts().isEmpty())
    }

    @Test
    fun `dirty conversion can explicitly discard changes`() = runBlocking {
        seed(task("t1", title = "Original", due = DueRef(20261029)))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(title = "Edited") }

        vm.requestConversion("t1", NotebookKind.NOTE)
        awaitCondition { vm.pendingConversion.value?.requiresEditorDecision == true }

        vm.discardChangesAndConvert("t1", NotebookKind.NOTE)
        awaitCondition {
            (vm.listState.value as? NotebookListState.Ready)
                ?.items
                ?.firstOrNull { it.id == "t1" }
                ?.kind == NotebookKind.NOTE
        }

        val converted = items().first { it.id == "t1" }
        assertEquals(NotebookKind.NOTE, converted.kind)
        assertEquals("Original", converted.title)
        assertTrue(drafts().isEmpty())
    }

    @Test
    fun `editing and replacing a form never autosaves to the filesystem`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)

        vm.openNew(NotebookKind.TASK)
        awaitEditor(vm) { it.isNew }

        vm.updateDraft { it.copy(title = "Unsaved title") }
        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.advanceUntilIdle()
        vm.reconcileOnResume()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(NotebookDraftStore.file(app, accountId).exists())
        vm.openNew(NotebookKind.NOTE)
        awaitEditor(vm) { it.isNew && it.draft.kind == NotebookKind.NOTE }

        assertTrue(drafts().isEmpty())
        assertTrue(items().isEmpty())
    }

    @Test
    fun `explicit save commits a valid form without creating a draft`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)

        vm.openNew(NotebookKind.TASK)
        awaitEditor(vm) { it.isNew }
        vm.updateDraft { it.copy(title = "Saved title") }
        vm.save()
        awaitCondition { vm.editorState.value == null }

        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(drafts().isEmpty())
        assertEquals("Saved title", items().single().title)
        assertFalse(NotebookDraftStore.file(app, accountId).exists())
    }

    @Test
    fun `delete remains explicit and cannot recreate transient edits`() = runBlocking {
        seed(task("t1", title = "Original"))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(title = "Edited") }

        vm.delete("t1")
        awaitCondition {
            (vm.listState.value as? NotebookListState.Ready)?.items?.none { it.id == "t1" } == true
        }

        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(drafts().isEmpty())
    }

    @Test
    fun `dirty cancel waits for confirmation and discard changes no saved item`() = runBlocking {
        seed(task("t1", title = "Original"))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(title = "Discarded") }
        vm.updateEditorFlags { it.copy(completedAt = 100L) }
        vm.requestEditorExit()
        assertTrue((vm.editorState.value as NotebookEditorState.Ready).isDirty)
        assertEquals("Original", items().single().title)
        assertFalse(items().single().isCompleted)

        vm.closeEditor()
        awaitCondition { vm.editorState.value == null }

        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(drafts().isEmpty())
        assertEquals("Original", items().single().title)
        assertFalse(items().single().isCompleted)
    }

    @Test
    fun `editor completion is staged and committed atomically with explicit save`() = runBlocking {
        seed(task("t1", title = "Original"))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(title = "Edited") }

        vm.updateEditorFlags { it.copy(completedAt = 100L) }
        awaitEditor(vm) { it.item?.isCompleted == true }
        assertEquals("Edited", (vm.editorState.value as NotebookEditorState.Ready).draft.title)
        assertEquals("Original", items().single().title)
        assertFalse(items().single().isCompleted)

        vm.save()
        awaitCondition { vm.editorState.value == null }

        val stored = items().first { it.id == "t1" }
        assertEquals("Edited", stored.title)
        assertTrue(stored.isCompleted)
    }

    @Test
    fun `legacy draft files are not read restored or rewritten by editing or save`() = runBlocking {
        seed(task("t1", title = "Original"))
        NotebookDraftStore.file(app, accountId).writeText("plaintext-draft")

        val vm = viewModel()
        awaitList(vm)
        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(title = "Edited") }

        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull((vm.editorState.value as NotebookEditorState.Ready).error)
        assertFalse((vm.editorState.value as NotebookEditorState.Ready).readOnly)
        assertEquals("plaintext-draft", NotebookDraftStore.file(app, accountId).readText())

        vm.save()
        awaitCondition { vm.editorState.value == null }

        assertEquals("Edited", items().first { it.id == "t1" }.title)
        assertEquals(NotebookNotice.SAVED, vm.notice.value)
        assertEquals("plaintext-draft", NotebookDraftStore.file(app, accountId).readText())
    }

    @Test
    fun `resume reconcile updates completion without overwriting a dirty draft`() = runBlocking {
        seed(task("t1", title = "Original"))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(title = "Edited") }

        repository.setCompleted("t1", true)
        vm.reconcileOnResume()
        awaitEditor(vm) { it.item?.isCompleted == true }

        val editor = vm.editorState.value as NotebookEditorState.Ready
        assertEquals("Edited", editor.draft.title)
        assertFalse(editor.error is NotebookEditorError.Conflict)
        assertEquals(editor.item!!.updatedAt, editor.draft.baseUpdatedAt ?: -1L)

        vm.save()
        awaitCondition { vm.editorState.value == null }

        val stored = items().first { it.id == "t1" }
        assertEquals("Edited", stored.title)
        assertTrue(stored.isCompleted)
    }

    @Test
    fun `list conversion leaves dormant legacy drafts untouched`() = runBlocking {
        val original = task("t1", title = "Original", due = DueRef(20261029))
        seed(original)
        repository.saveDraft(
            NotebookDraft(
                id = "draft-1",
                itemId = "t1",
                kind = NotebookKind.TASK,
                title = "Draft title",
                due = original.due,
                baseUpdatedAt = original.updatedAt,
            ),
        )

        val vm = viewModel()
        awaitList(vm)
        vm.requestConversion("t1", NotebookKind.NOTE)
        awaitCondition { vm.pendingConversion.value != null }

        vm.confirmConversion("t1", NotebookKind.NOTE)
        awaitCondition {
            (vm.listState.value as? NotebookListState.Ready)
                ?.items
                ?.firstOrNull { it.id == "t1" }
                ?.kind == NotebookKind.NOTE
        }

        val converted = items().first { it.id == "t1" }
        val draft = drafts().single { it.id == "draft-1" }

        assertEquals(NotebookKind.TASK, draft.kind)
        assertEquals("Draft title", draft.title)
        assertEquals(original.due, draft.due)
        assertEquals(original.updatedAt, draft.baseUpdatedAt ?: -1L)
        assertEquals(NotebookKind.NOTE, converted.kind)
    }

    @Test
    fun `selecting another source derives its subject and preserves live text and due metadata`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)

        vm.openNew(
            kind = NotebookKind.TASK,
            origin = LessonRef(entryId = 2, date = 20261028, startTime = 800, endTime = 845,
                subject = SubjectRef(ids = listOf(2), name = "DE")),
        )
        awaitEditor(vm) { it.isNew }

        vm.updateDraft {
            it.copy(
                title = "Draft title",
                body = "Draft body",
                due = DueRef(
                    date = 20261030,
                    reminder = ReminderSpec(date = 20261030, minutesOfDay = 600),
                ),
            )
        }
        val draftId = (vm.editorState.value as NotebookEditorState.Ready).draft.id
        val lesson = LessonRef(
            entryId = 5,
            date = 20261029,
            startTime = 800,
            endTime = 845,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )

        vm.applyOriginLesson(lesson)
        val kept = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(lesson.normalized(), kept.draft.origin)
        assertEquals(listOf(1), kept.draft.subject?.ids ?: emptyList<Int>())
        assertEquals("Draft title", kept.draft.title)
        assertEquals("Draft body", kept.draft.body)
        assertEquals(20261030, kept.draft.due?.date ?: -1)
        assertEquals(600, kept.draft.due?.reminder?.minutesOfDay ?: -1)
        assertEquals(draftId, kept.draft.id)
    }

    @Test
    fun `legacy standalone subject is retained until an explicit lesson selection and save`() = runBlocking {
        val legacy = task("t1", "Original").copy(subject = SubjectRef(ids = listOf(2), name = "DE"))
        seed(legacy)
        val vm = viewModel()
        awaitList(vm)

        vm.openItem(legacy.id)
        awaitEditor(vm) { it.draft.itemId == legacy.id }

        vm.updateDraft { it.copy(title = "Draft title", body = "Draft body") }
        assertEquals(legacy.subject, (vm.editorState.value as NotebookEditorState.Ready).draft.subject)
        val lesson = LessonRef(
            entryId = 5,
            date = 20261029,
            startTime = 800,
            endTime = 845,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )

        vm.applyOriginLesson(lesson)
        val editor = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(lesson.normalized(), editor.draft.origin)
        assertEquals(listOf(1), editor.draft.subject?.ids ?: emptyList<Int>())
        assertEquals("Draft title", editor.draft.title)
        assertEquals("Draft body", editor.draft.body)
        assertEquals(legacy.subject, items().single().subject)
        vm.save()
        awaitCondition { vm.editorState.value == null }
        assertEquals(lesson.subject, items().single().subject)
    }

    @Test
    fun `clearing the only source of a new item clears its hidden subject`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)

        vm.openNew(
            kind = NotebookKind.NOTE,
        )
        awaitEditor(vm) { it.isNew }

        vm.updateDraft { it.copy(title = "Draft title", body = "Draft body") }
        val lesson = LessonRef(
            entryId = 5,
            date = 20261029,
            startTime = 800,
            endTime = 845,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )

        vm.applyOriginLesson(lesson)

        val editor = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(lesson.normalized(), editor.draft.origin)
        assertEquals(listOf(1), editor.draft.subject?.ids ?: emptyList<Int>())
        vm.updateDraft { it.copy(origin = null) }
        assertNull((vm.editorState.value as NotebookEditorState.Ready).draft.subject)
    }

    @Test
    fun `explicit source selection upgrades a legacy free-text subject to lesson stable ids`() = runBlocking {
        seed(task("t1", "Original").copy(subject = SubjectRef.freeText("MA")))
        val vm = viewModel()
        awaitList(vm)

        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }

        vm.updateDraft { it.copy(title = "Draft title", body = "Draft body") }
        val lesson = LessonRef(
            entryId = 5,
            date = 20261029,
            startTime = 800,
            endTime = 845,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )

        vm.applyOriginLesson(lesson)

        val editor = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(lesson.normalized(), editor.draft.origin)
        assertEquals(listOf(1), editor.draft.subject?.ids ?: emptyList<Int>())
        assertEquals("MA", editor.draft.subject?.name)
    }

    @Test
    fun `due lesson with a different date requires confirmation and keeps the date until accepted`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)

        vm.openNew(NotebookKind.TASK)
        awaitEditor(vm) { it.isNew }
        vm.updateDraft {
            it.copy(
                title = "Draft title",
                body = "Draft body",
                due = DueRef(
                    date = 20261030,
                    reminder = ReminderSpec(date = 20261030, minutesOfDay = 600),
                ),
            )
        }
        val draftId = (vm.editorState.value as NotebookEditorState.Ready).draft.id
        val lesson = LessonRef(
            entryId = 7,
            date = 20261029,
            startTime = 900,
            endTime = 945,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )

        vm.applyDueLesson(lesson)
        awaitCondition { vm.pendingDueLesson.value != null }

        val pending = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(20261030, pending.draft.due?.date ?: -1)
        assertNull(pending.draft.due?.lesson)
        assertEquals(600, pending.draft.due?.reminder?.minutesOfDay ?: -1)
        assertEquals("Draft title", pending.draft.title)
        assertEquals(draftId, pending.draft.id)

        vm.cancelPendingDueLesson()

        val cancelled = vm.editorState.value as NotebookEditorState.Ready
        assertNull(vm.pendingDueLesson.value)
        assertEquals(20261030, cancelled.draft.due?.date ?: -1)
        assertNull(cancelled.draft.due?.lesson)
        assertEquals(600, cancelled.draft.due?.reminder?.minutesOfDay ?: -1)

        vm.applyDueLesson(lesson)
        awaitCondition { vm.pendingDueLesson.value != null }
        vm.confirmPendingDueLesson(useLessonDate = true)

        val accepted = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(20261029, accepted.draft.due?.date ?: -1)
        assertEquals(lesson.normalized(), accepted.draft.due?.lesson)
        assertEquals(600, accepted.draft.due?.reminder?.minutesOfDay ?: -1)
        assertEquals("Draft title", accepted.draft.title)
        assertEquals(draftId, accepted.draft.id)
    }

    @Test
    fun `due lesson can keep the existing date while attaching the target lesson`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)

        vm.openNew(NotebookKind.TASK)
        awaitEditor(vm) { it.isNew }
        vm.updateDraft {
            it.copy(
                title = "Draft title",
                body = "Draft body",
                due = DueRef(
                    date = 20261030,
                    reminder = ReminderSpec(date = 20261030, minutesOfDay = 600),
                ),
            )
        }
        val lesson = LessonRef(
            entryId = 7,
            date = 20261029,
            startTime = 900,
            endTime = 945,
            subject = SubjectRef(ids = listOf(1), name = "MA"),
        )

        vm.applyDueLesson(lesson)
        awaitCondition { vm.pendingDueLesson.value != null }
        vm.confirmPendingDueLesson(useLessonDate = false)

        val editor = vm.editorState.value as NotebookEditorState.Ready
        assertEquals(20261030, editor.draft.due?.date ?: -1)
        assertEquals(lesson.normalized(), editor.draft.due?.lesson)
        assertEquals(600, editor.draft.due?.reminder?.minutesOfDay ?: -1)
        assertEquals("Draft title", editor.draft.title)
    }

    @Test
    fun `live form survives timetable selection and cancellation without any disk draft`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)
        vm.openNew(NotebookKind.TASK)
        awaitEditor(vm) { it.isNew }
        vm.updateDraft { it.copy(title = "Live title", body = "Live body", due = DueRef(20261030)) }
        val before = (vm.editorState.value as NotebookEditorState.Ready).draft
        var session = NotebookEditorSession().openedFrom(sh.lsx.untisx.ui.nav.Screen.LESSON_DETAILS)
        session = session.picker(NotebookLessonTarget.ORIGIN)
        vm.reconcileOnResume()
        dispatcher.scheduler.advanceUntilIdle()
        session = session.pickerClosed()
        assertEquals(before, (vm.editorState.value as NotebookEditorState.Ready).draft)
        assertEquals(sh.lsx.untisx.ui.nav.Screen.LESSON_DETAILS, session.returnScreen)

        vm.applyTimetableLesson(
            NotebookLessonTarget.ORIGIN,
            sh.lsx.untisx.data.model.TimetableEntry(id = 7, date = 20261029, startTime = 900, endTime = 945),
        )
        val after = (vm.editorState.value as NotebookEditorState.Ready).draft
        assertEquals(before.id, after.id)
        assertEquals(before.title, after.title)
        assertEquals(before.body, after.body)
        assertEquals(before.due, after.due)
        assertEquals(7, after.origin?.entryId ?: -1)
        assertTrue(items().isEmpty())
        assertFalse(NotebookDraftStore.file(app, accountId).exists())
    }

    @Test
    fun `legacy saved form is never resumed or removed by edit cancel or delete`() = runBlocking {
        val original = task("t1", "Original")
        seed(original)
        repository.saveDraft(NotebookDraft(
            id = "legacy", itemId = original.id, kind = original.kind,
            title = "Old unsaved edits", baseUpdatedAt = original.updatedAt,
        ))
        val file = NotebookDraftStore.file(app, accountId)
        val legacyBytes = file.readBytes()
        val vm = viewModel()
        awaitList(vm)
        vm.openItem(original.id)
        awaitEditor(vm) { it.draft.itemId == original.id }
        assertEquals("Original", (vm.editorState.value as NotebookEditorState.Ready).draft.title)
        vm.updateDraft { it.copy(title = "New unsaved edits") }
        vm.closeEditor()
        vm.delete(original.id)
        awaitCondition { (vm.listState.value as? NotebookListState.Ready)?.items?.isEmpty() == true }
        assertTrue(file.readBytes().contentEquals(legacyBytes))
    }

    @Test
    fun `conversion warning includes due metadata added only in the live form`() = runBlocking {
        seed(task("t1", "Original"))
        val vm = viewModel()
        awaitList(vm)
        vm.openItem("t1")
        awaitEditor(vm) { it.draft.itemId == "t1" }
        vm.updateDraft { it.copy(due = DueRef(20261030)) }
        vm.requestConversion("t1", NotebookKind.NOTE)
        awaitCondition { vm.pendingConversion.value != null }
        assertTrue(vm.pendingConversion.value!!.requiresEditorDecision)
        assertTrue(vm.pendingConversion.value!!.warning!!.lostFields.contains(
            sh.lsx.untisx.data.notebook.NotebookField.DUE,
        ))
        assertNull(items().single().due)
        assertFalse(NotebookDraftStore.file(app, accountId).exists())
    }

    @Test
    fun `affiliation follows source then target then none without losing exact lesson links`() = runBlocking {
        val vm = viewModel()
        awaitList(vm)
        vm.openNew(NotebookKind.TASK)
        awaitEditor(vm) { it.isNew }
        vm.updateDraft { it.copy(title = "Task", subject = SubjectRef.freeText("Not a lesson")) }
        assertNull((vm.editorState.value as NotebookEditorState.Ready).draft.subject)
        val target = LessonRef(entryId = 20, date = 20261030, startTime = 900, endTime = 945,
            subject = SubjectRef(ids = listOf(2), name = "DE"))
        val source = target.copy(entryId = 10, date = 20261029,
            subject = SubjectRef(ids = listOf(1), name = "MA"))
        vm.applyDueLesson(target)
        assertEquals(target.subject, (vm.editorState.value as NotebookEditorState.Ready).draft.subject)
        vm.applyOriginLesson(source)
        val linked = (vm.editorState.value as NotebookEditorState.Ready).draft
        assertEquals(source.subject, linked.subject)
        assertEquals(target, linked.due?.lesson)
        vm.updateDraft { it.copy(origin = null) }
        assertEquals(target.subject, (vm.editorState.value as NotebookEditorState.Ready).draft.subject)
        vm.updateDraft { it.copy(due = it.due?.copy(lesson = null)) }
        val cleared = (vm.editorState.value as NotebookEditorState.Ready).draft
        assertNull(cleared.subject)
        assertEquals(20261030, cleared.due?.date ?: -1)
        vm.save()
        awaitCondition { vm.editorState.value == null }
        assertNull(items().single().subject)
    }

    @Test
    fun `text-only editing preserves an imported standalone subject without migration`() = runBlocking {
        val subject = SubjectRef.freeText("Imported subject")
        seed(task("legacy", "Original").copy(subject = subject))
        val vm = viewModel()
        awaitList(vm)
        vm.openItem("legacy")
        awaitEditor(vm) { it.draft.itemId == "legacy" }
        vm.updateDraft { it.copy(body = "Edited text") }
        vm.save()
        awaitCondition { vm.editorState.value == null }
        val saved = items().single()
        assertEquals(subject, saved.subject)
        assertNull(saved.origin)
        assertNull(saved.due)
    }

    private fun viewModel(): NotebookViewModel =
        NotebookViewModel(app, repository, accountId, reminders).also(viewModels::add)

    private suspend fun seed(item: NotebookItem) {
        NotebookStore.write(
            app,
            accountId,
            NotebookData(revision = 1, items = listOf(item), updatedAt = item.updatedAt),
        )
    }

    private suspend fun awaitList(vm: NotebookViewModel) {
        awaitCondition { vm.listState.value is NotebookListState.Ready }
    }

    private suspend fun awaitEditor(
        vm: NotebookViewModel,
        predicate: (NotebookEditorState.Ready) -> Boolean,
    ) {
        awaitCondition {
            val editor = vm.editorState.value

            editor is NotebookEditorState.Ready && predicate(editor)
        }
    }

    private suspend fun awaitCondition(
        timeoutMillis: Long = 5_000,
        condition: () -> Boolean,
    ) {
        withTimeout(timeoutMillis) {
            while (!condition()) delay(10)
        }
    }

    private suspend fun items(): List<NotebookItem> =
        (repository.load() as? NotebookReadResult.Loaded)?.data?.items.orEmpty()

    private suspend fun drafts(): List<NotebookDraft> =
        (repository.loadDrafts() as? NotebookDraftReadResult.Loaded)
            ?.data
            ?.drafts
            ?.values
            ?.toList()
            .orEmpty()

    private fun task(
        id: String,
        title: String,
        due: DueRef? = null,
    ): NotebookItem = NotebookItem(
        id = id,
        kind = NotebookKind.TASK,
        title = title,
        due = due,
        createdAt = 1,
        updatedAt = 1,
    )

    private class FakeReminderSink : NotebookReminderSink {
        val scheduled = mutableListOf<NotebookItem>()
        val cancelled = mutableListOf<String>()
        var rescheduleCount = 0

        override fun schedule(context: Context, accountId: String, item: NotebookItem) {
            scheduled += item
        }

        override fun cancel(context: Context, accountId: String, itemId: String) {
            cancelled += itemId
        }

        override fun rescheduleAll(context: Context) {
            rescheduleCount++
        }
    }
}
