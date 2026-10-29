package sh.lsx.untisx.ui.notebook

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookConversion
import sh.lsx.untisx.data.notebook.NotebookConversionWarning
import sh.lsx.untisx.data.notebook.NotebookData
import sh.lsx.untisx.data.notebook.NotebookDecodeResult
import sh.lsx.untisx.data.notebook.NotebookDraft
import sh.lsx.untisx.data.notebook.NotebookExport
import sh.lsx.untisx.data.notebook.NotebookExportCodec
import sh.lsx.untisx.data.notebook.NotebookImportChoice
import sh.lsx.untisx.data.notebook.NotebookImportPlan
import sh.lsx.untisx.data.notebook.NotebookImportPlanner
import sh.lsx.untisx.data.notebook.NotebookImportPreview
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookLessonCache
import sh.lsx.untisx.data.notebook.NotebookQuery
import sh.lsx.untisx.data.notebook.NotebookReadFailure
import sh.lsx.untisx.data.notebook.NotebookReadResult
import sh.lsx.untisx.data.notebook.NotebookRepository
import sh.lsx.untisx.data.notebook.NotebookStatusFilter
import sh.lsx.untisx.data.notebook.NotebookStorageFailure
import sh.lsx.untisx.data.notebook.NotebookValidationIssue
import sh.lsx.untisx.data.notebook.NotebookWriteResult
import sh.lsx.untisx.data.notebook.SubjectRef
import sh.lsx.untisx.data.notifications.NotebookReminderScheduler
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore

sealed interface NotebookListState {
    data object Loading : NotebookListState

    data class Ready(
        val items: List<NotebookItem>,
        val revision: Long,
    ) : NotebookListState

    data class Failed(val reason: NotebookReadFailure) : NotebookListState
}

sealed interface NotebookEditorError {
    data class ReadFailed(val reason: NotebookReadFailure) : NotebookEditorError
    data class StorageFailed(val reason: NotebookStorageFailure) : NotebookEditorError
    data class Invalid(val issues: List<NotebookValidationIssue>) : NotebookEditorError
    data object Conflict : NotebookEditorError
    data object NotFound : NotebookEditorError
    data object SaveFailed : NotebookEditorError
}

sealed interface NotebookEditorState {
    data object Loading : NotebookEditorState

    data class Ready(
        val draft: NotebookDraft,
        val lessonCache: NotebookLessonCache,
        val isNew: Boolean,
        val isDirty: Boolean,
        val saving: Boolean,
        val error: NotebookEditorError?,
        val readOnly: Boolean = false,
        val item: NotebookItem? = null,
    ) : NotebookEditorState

    data class Failed(val reason: NotebookReadFailure) : NotebookEditorState
}

sealed interface NotebookUndo {
    val itemId: String

    data class Completed(override val itemId: String, val previous: Boolean) : NotebookUndo

    data class Pinned(override val itemId: String, val previous: Boolean) : NotebookUndo

    data class Archived(override val itemId: String, val previous: Boolean) : NotebookUndo
}

data class NotebookConversionRequest(
    val itemId: String,
    val target: NotebookKind,
    val warning: NotebookConversionWarning?,
    val requiresEditorDecision: Boolean = false,
)

sealed interface NotebookImportPreviewResult {
    data class Ready(val preview: NotebookImportPreview) : NotebookImportPreviewResult

    data class Invalid(val issues: List<NotebookValidationIssue>) : NotebookImportPreviewResult

    data object UnsupportedVersion : NotebookImportPreviewResult

    data object TooLarge : NotebookImportPreviewResult
}

enum class NotebookNotice {
    SAVED,
    DELETED,
    ITEM_MISSING,
    READ_ONLY,
    SAVE_FAILED,
    DELETE_FAILED,
    TOGGLE_FAILED,
    CONVERSION_DONE,
    CONVERSION_FAILED,
    IMPORT_APPLIED,
    IMPORT_EMPTY,
    IMPORT_STALE,
    IMPORT_FAILED,
    IMPORT_READ_FAILED,
    IMPORT_TOO_LARGE,
    EXPORT_DONE,
    EXPORT_FAILED,
    EXPORT_EMPTY,
    EXPORT_TOO_LARGE,
}

enum class NotebookFocusNotice { ACCOUNT_MISSING, ITEM_MISSING }

class NotebookViewModel internal constructor(
    app: Application,
    private val repository: NotebookRepository,
    val accountId: String,
    private val reminders: NotebookReminderSink = DefaultNotebookReminderSink,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app = app,
        repository = NotebookRepository(app.applicationContext ?: app, resolveAccountId(app)),
        accountId = resolveAccountId(app),
    )

    private val context = app.applicationContext ?: app
    private val draftMutex = Mutex()

    private var data: NotebookData? = null
    private var baseline: NotebookItem? = null
    private var lessonCache = NotebookLessonCache()
    private var editorGeneration: Long = 0

    private var loadJob: Job? = null
    private var editorJob: Job? = null
    private var saveJob: Job? = null

    private val _listState = MutableStateFlow<NotebookListState>(NotebookListState.Loading)
    val listState: StateFlow<NotebookListState> = _listState.asStateFlow()

    private val _editorState = MutableStateFlow<NotebookEditorState?>(null)
    val editorState: StateFlow<NotebookEditorState?> = _editorState.asStateFlow()

    private val _pendingUndo = MutableStateFlow<NotebookUndo?>(null)
    val pendingUndo: StateFlow<NotebookUndo?> = _pendingUndo.asStateFlow()

    private val _notice = MutableStateFlow<NotebookNotice?>(null)
    val notice: StateFlow<NotebookNotice?> = _notice.asStateFlow()

    private val _pendingConversion = MutableStateFlow<NotebookConversionRequest?>(null)
    val pendingConversion: StateFlow<NotebookConversionRequest?> = _pendingConversion.asStateFlow()

    private val _importPreview = MutableStateFlow<NotebookImportPreviewResult?>(null)
    val importPreview: StateFlow<NotebookImportPreviewResult?> = _importPreview.asStateFlow()

    private val _openEditorNonce = MutableStateFlow(0)
    val openEditorNonce: StateFlow<Int> = _openEditorNonce.asStateFlow()

    private val _exitRequest = MutableStateFlow(0)
    val exitRequest: StateFlow<Int> = _exitRequest.asStateFlow()

    private val _subjectChoices = MutableStateFlow<NotebookSubjectChoices>(NotebookSubjectChoices.Loading)
    val subjectChoices: StateFlow<NotebookSubjectChoices> = _subjectChoices.asStateFlow()

    private val _pendingDueLesson = MutableStateFlow<LessonRef?>(null)
    val pendingDueLesson: StateFlow<LessonRef?> = _pendingDueLesson.asStateFlow()

    var selectedTab by mutableStateOf(NotebookTab.TASKS)
        private set

    var taskStatusFilter by mutableStateOf(NotebookStatusFilter.OPEN)
        private set

    var noteStatusFilter by mutableStateOf(NotebookStatusFilter.OPEN)
        private set

    var subjectFilter by mutableStateOf<SubjectRef?>(null)
        private set

    var withoutSubject by mutableStateOf(false)
        private set

    var query by mutableStateOf(
        NotebookQuery(kinds = setOf(NotebookKind.TASK), status = NotebookStatusFilter.OPEN),
    )
        private set

    var taskScrollIndex: Int = 0
        private set

    var noteScrollIndex: Int = 0
        private set

    init {
        refresh()
        loadLessonCache()
    }

    fun refresh() {
        if (accountId.isBlank()) {
            _listState.value = NotebookListState.Failed(NotebookReadFailure.IO)
            return
        }

        if (loadJob?.isActive == true) return

        if (data == null) _listState.value = NotebookListState.Loading

        loadJob = viewModelScope.launch {
            when (val result = repository.load()) {
                is NotebookReadResult.Loaded -> applyLoaded(result.data)
                NotebookReadResult.Missing -> applyLoaded(NotebookData())
                is NotebookReadResult.Failed -> _listState.value = NotebookListState.Failed(result.reason)
            }

        }
    }

    fun setTab(tab: NotebookTab) {
        selectedTab = tab
        rebuildQuery()
    }

    fun updateTaskStatusFilter(status: NotebookStatusFilter) {
        taskStatusFilter = status

        if (selectedTab == NotebookTab.TASKS) rebuildQuery()
    }

    fun updateNoteStatusFilter(status: NotebookStatusFilter) {
        noteStatusFilter = status

        if (selectedTab == NotebookTab.NOTES) rebuildQuery()
    }

    fun setSearchText(text: String) {
        if (query.text == text) return

        rebuildQuery(text)
    }

    fun updateSubjectFilter(subject: SubjectRef?) {
        subjectFilter = subject
        withoutSubject = false
        rebuildQuery()
    }

    fun setWithoutSubject() {
        subjectFilter = null
        withoutSubject = true
        rebuildQuery()
    }

    fun clearSubjectFilter() {
        subjectFilter = null
        withoutSubject = false
        rebuildQuery()
    }

    fun setScrollIndex(tab: NotebookTab, index: Int) {
        if (tab == NotebookTab.TASKS) {
            taskScrollIndex = index.coerceAtLeast(0)
        } else {
            noteScrollIndex = index.coerceAtLeast(0)
        }
    }

    fun openNew(kind: NotebookKind, origin: LessonRef? = null) {
        editorJob?.cancel()
        editorJob = viewModelScope.launch {
            if (accountId.isBlank()) {
                _editorState.value = NotebookEditorState.Failed(NotebookReadFailure.IO)
                return@launch
            }

            if (_listState.value is NotebookListState.Failed) {
                val failed = _listState.value as NotebookListState.Failed
                _editorState.value = NotebookEditorState.Failed(failed.reason)
                _openEditorNonce.value += 1
                return@launch
            }

            editorJob?.cancel()
            editorGeneration++
            baseline = null
            clearLessonPicks()
            loadLessonCache()

            val draft = NotebookDraft(
                id = UUID.randomUUID().toString(),
                itemId = null,
                kind = kind,
                subject = origin?.subject?.normalized()?.takeIf { !it.isBlank },
                origin = origin?.normalized(),
                due = null,
            )

            _editorState.value = NotebookEditorState.Ready(
                draft = draft,
                lessonCache = lessonCache,
                isNew = true,
                isDirty = false,
                saving = false,
                error = null,
                readOnly = false,
            )

            _openEditorNonce.value += 1
        }
    }

    fun openItem(itemId: String) {
        openItemInternal(itemId, flushFirst = true)
    }

    private fun openItemInternal(itemId: String, flushFirst: Boolean) {
        if (itemId.isBlank()) {
            _notice.value = NotebookNotice.ITEM_MISSING
            return
        }

        val failed = _listState.value as? NotebookListState.Failed

        if (failed != null) {
            _editorState.value = NotebookEditorState.Failed(failed.reason)
            _openEditorNonce.value += 1
            return
        }

        editorJob?.cancel()
        editorJob = viewModelScope.launch {
            editorGeneration++
            clearLessonPicks()
            loadLessonCache()

            val current = data ?: loadDataForEditor() ?: return@launch
            val item = current.items.firstOrNull { it.id == itemId }

            if (item == null) {
                _notice.value = NotebookNotice.ITEM_MISSING
                return@launch
            }

            val draft = item.toDraft()

            baseline = item

            _editorState.value = NotebookEditorState.Ready(
                draft = draft,
                lessonCache = lessonCache,
                isNew = false,
                isDirty = false,
                saving = false,
                error = null,
                readOnly = false,
                item = item,
            )

            _openEditorNonce.value += 1
        }
    }

    fun updateDraft(transform: (NotebookDraft) -> NotebookDraft) {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return

        if (current.readOnly || current.saving) return

        val candidate = transform(current.draft).copy(updatedAt = System.currentTimeMillis())
        val linksChanged = candidate.origin != current.draft.origin ||
            candidate.due?.lesson != current.draft.due?.lesson

        val updated = if (current.isNew || linksChanged) {
            candidate.copy(subject = notebookLessonSubject(candidate))
        } else candidate
        val dirty = notebookHasUnsavedChanges(updated, baseline) || editorFlagsChanged(current.item)

        _editorState.value = current.copy(
            draft = updated,
            isDirty = dirty,
            error = null,
        )

    }

    fun applyOriginLesson(lesson: LessonRef) {
        val normalizedLesson = lesson.normalized()
        updateLessonLinks { it.copy(origin = normalizedLesson) }
    }

    private fun updateLessonLinks(transform: (NotebookDraft) -> NotebookDraft) {
        updateDraft { form ->
            val updated = transform(form)
            updated.copy(subject = notebookLessonSubject(updated))
        }
    }

    fun applyDueLesson(lesson: LessonRef) {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return

        if (current.readOnly || current.saving) return

        val normalizedLesson = lesson.normalized()
        val due = current.draft.due

        when {
            due == null -> updateLessonLinks {
                it.copy(due = DueRef(date = normalizedLesson.date, lesson = normalizedLesson))
            }

            due.date == normalizedLesson.date -> updateLessonLinks {
                it.copy(due = due.copy(lesson = normalizedLesson))
            }

            else -> {
                _pendingDueLesson.value = normalizedLesson
            }
        }
    }

    fun confirmPendingDueLesson(useLessonDate: Boolean) {
        val lesson = _pendingDueLesson.value ?: return

        _pendingDueLesson.value = null
        updateLessonLinks { draft ->
            val due = draft.due

            when {
                due == null -> draft.copy(
                    due = DueRef(date = lesson.date, lesson = lesson),
                )

                useLessonDate -> draft.copy(
                    due = due.copy(date = lesson.date, lesson = lesson),
                )

                else -> draft.copy(
                    due = due.copy(lesson = lesson),
                )
            }
        }
    }

    fun cancelPendingDueLesson() {
        _pendingDueLesson.value = null
    }

    internal fun applyTimetableLesson(target: NotebookLessonTarget, entry: TimetableEntry) {
        val lesson = notebookLessonRef(lessonCache, entry)

        when (target) {
            NotebookLessonTarget.ORIGIN -> applyOriginLesson(lesson)
            NotebookLessonTarget.DUE -> applyDueLesson(lesson)
        }
    }

    private fun clearLessonPicks() {
        _pendingDueLesson.value = null
    }

    fun save(overwrite: Boolean = false) {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return

        if (current.readOnly || current.saving) return

        if (!notebookHasContent(current.draft.title, current.draft.body)) {
            _editorState.value = current.copy(
                error = NotebookEditorError.Invalid(
                    listOf(NotebookValidationIssue(current.draft.id, "text", "")),
                ),
            )
            return
        }

        _editorState.value = current.copy(saving = true, error = null)

        saveJob = viewModelScope.launch {
            editorGeneration++

            val draft = if (overwrite) current.draft.copy(baseUpdatedAt = null) else current.draft
            val result = commitEditorForm(current, draft)

            when (result) {
                is NotebookWriteResult.Success -> {
                    val saved = notebookFindSavedItem(result.data, draft)

                    applyLoaded(result.data)
                    clearLessonPicks()
                    _editorState.value = null

                    if (saved != null) {
                        reminders.schedule(context, accountId, saved)
                    }

                    _notice.value = NotebookNotice.SAVED
                }

                is NotebookWriteResult.Conflict -> {
                    applyLoaded(result.current)
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.Conflict,
                    )
                }

                is NotebookWriteResult.NotFound -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.NotFound,
                    )
                }

                is NotebookWriteResult.Invalid -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.Invalid(result.issues),
                    )
                }

                is NotebookWriteResult.ReadFailed -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.ReadFailed(result.reason),
                        readOnly = true,
                    )
                }

                is NotebookWriteResult.StorageFailed -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.StorageFailed(result.reason),
                    )
                }

                is NotebookWriteResult.ConfirmationRequired -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.SaveFailed,
                    )
                }
            }
        }
    }

    private suspend fun commitEditorForm(
        current: NotebookEditorState.Ready,
        form: NotebookDraft,
    ): NotebookWriteResult = draftMutex.withLock {
        repository.commitDraft(
            form,
            cleanupDraft = false,
            completed = current.item?.isCompleted,
            pinned = current.item?.isPinned,
            archived = current.item?.isArchived,
        )
    }

    private fun editorFlagsChanged(item: NotebookItem?): Boolean = item != null &&
        (item.completedAt != baseline?.completedAt || item.pinnedAt != baseline?.pinnedAt ||
            item.archivedAt != baseline?.archivedAt)

    fun updateEditorFlags(transform: (NotebookItem) -> NotebookItem) {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return
        if (current.readOnly || current.saving) return
        val item = current.item?.let(transform) ?: return
        _editorState.value = current.copy(
            item = item,
            isDirty = notebookHasUnsavedChanges(current.draft, baseline) || editorFlagsChanged(item),
        )
    }

    fun requestEditorExit() {
        if ((_editorState.value as? NotebookEditorState.Ready)?.saving == true) return
        _exitRequest.value += 1
    }

    fun discardDraftAndClose() {
        closeEditor()
    }

    fun closeEditor() {
        editorJob?.cancel()
        editorGeneration++
        baseline = null
        clearLessonPicks()
        _pendingConversion.value = null
        _editorState.value = null
    }

    fun reloadEditorFromItem() {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return
        val itemId = current.draft.itemId ?: return
        viewModelScope.launch {
            editorGeneration++
            openItemInternal(itemId, flushFirst = false)
        }
    }

    fun reviveDraftAsNew() {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return
        val draft = current.draft.copy(itemId = null, baseUpdatedAt = null)

        editorGeneration++
        baseline = null
        clearLessonPicks()

        _editorState.value = current.copy(
            draft = draft,
            isNew = true,
            isDirty = notebookHasUnsavedChanges(draft, null),
            error = null,
            item = null,
        )

    }

    fun clearEditorError() {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return

        if (current.error is NotebookEditorError.Conflict ||
            current.error is NotebookEditorError.NotFound ||
            current.error is NotebookEditorError.Invalid
        ) {
            _editorState.value = current.copy(error = null)
        }
    }

    fun delete(itemId: String) {
        viewModelScope.launch {
            editorGeneration++

            when (val result = draftMutex.withLock { repository.delete(itemId, cleanupDrafts = false) }) {
                is NotebookWriteResult.Success -> {
                    applyLoaded(result.data)
                    reminders.cancel(context, accountId, itemId)

                    val editor = _editorState.value as? NotebookEditorState.Ready

                    if (editor?.draft?.itemId == itemId) {
                        clearLessonPicks()
                        _editorState.value = null
                    }

                    _notice.value = NotebookNotice.DELETED
                }

                is NotebookWriteResult.ReadFailed -> {
                    applyEditorFailure(NotebookEditorError.ReadFailed(result.reason), readOnly = true)
                    _notice.value = NotebookNotice.READ_ONLY
                }

                is NotebookWriteResult.StorageFailed -> {
                    applyEditorFailure(NotebookEditorError.StorageFailed(result.reason))
                    _notice.value = NotebookNotice.DELETE_FAILED
                }

                is NotebookWriteResult.NotFound -> _notice.value = NotebookNotice.ITEM_MISSING
                else -> _notice.value = NotebookNotice.DELETE_FAILED
            }
        }
    }

    fun toggleCompleted(itemId: String) {
        val item = currentItem(itemId) ?: return

        if (!item.isTask) return

        viewModelScope.launch {
            when (val result = repository.setCompleted(itemId, !item.isCompleted)) {
                is NotebookWriteResult.Success ->
                    applyWrite(result, NotebookUndo.Completed(itemId, item.isCompleted), itemId)

                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                else -> _notice.value = NotebookNotice.TOGGLE_FAILED
            }
        }
    }

    fun togglePinned(itemId: String) {
        val item = currentItem(itemId) ?: return

        if (item.isTask) return

        viewModelScope.launch {
            when (val result = repository.setPinned(itemId, !item.isPinned)) {
                is NotebookWriteResult.Success ->
                    applyWrite(result, NotebookUndo.Pinned(itemId, item.isPinned), itemId)

                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                else -> _notice.value = NotebookNotice.TOGGLE_FAILED
            }
        }
    }

    fun toggleArchived(itemId: String) {
        val item = currentItem(itemId) ?: return

        if (item.isTask) return

        viewModelScope.launch {
            when (val result = repository.setArchived(itemId, !item.isArchived)) {
                is NotebookWriteResult.Success ->
                    applyWrite(result, NotebookUndo.Archived(itemId, item.isArchived), itemId)

                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                else -> _notice.value = NotebookNotice.TOGGLE_FAILED
            }
        }
    }

    fun undoLastAction() {
        val undo = _pendingUndo.value ?: return

        _pendingUndo.value = null

        viewModelScope.launch {
            val result = when (undo) {
                is NotebookUndo.Completed -> repository.setCompleted(undo.itemId, undo.previous)
                is NotebookUndo.Pinned -> repository.setPinned(undo.itemId, undo.previous)
                is NotebookUndo.Archived -> repository.setArchived(undo.itemId, undo.previous)
            }

            if (result is NotebookWriteResult.Success) {
                applyWrite(result, undo = null, itemId = undo.itemId)
            } else {
                _notice.value = NotebookNotice.TOGGLE_FAILED
            }
        }
    }

    fun clearPendingUndoIf(undo: NotebookUndo) {
        if (_pendingUndo.value == undo) _pendingUndo.value = null
    }

    fun requestConversion(itemId: String, target: NotebookKind) {
        viewModelScope.launch {
            val editor = _editorState.value as? NotebookEditorState.Ready

            if (editor?.draft?.itemId == itemId && editor.isDirty) {
                val item = (editor.item ?: currentItem(itemId))?.copy(due = editor.draft.due)

                _pendingConversion.value = NotebookConversionRequest(
                    itemId = itemId,
                    target = target,
                    warning = item?.let { NotebookConversion.warning(it, target) },
                    requiresEditorDecision = true,
                )
                return@launch
            }

            when (val result = repository.convert(itemId, target, confirmed = false)) {
                is NotebookWriteResult.ConfirmationRequired -> _pendingConversion.value =
                    NotebookConversionRequest(itemId, target, result.warning)

                is NotebookWriteResult.Success -> applyConversionSuccess(result, itemId, target)
                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                is NotebookWriteResult.NotFound -> _notice.value = NotebookNotice.ITEM_MISSING
                else -> _notice.value = NotebookNotice.CONVERSION_FAILED
            }
        }
    }

    fun confirmConversion(itemId: String, target: NotebookKind) {
        _pendingConversion.value = null

        viewModelScope.launch {
            when (val result = repository.convert(itemId, target, confirmed = true)) {
                is NotebookWriteResult.Success -> applyConversionSuccess(result, itemId, target)
                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                is NotebookWriteResult.NotFound -> _notice.value = NotebookNotice.ITEM_MISSING
                else -> _notice.value = NotebookNotice.CONVERSION_FAILED
            }
        }
    }

    fun saveThenConvert(itemId: String, target: NotebookKind) {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return

        if (current.readOnly || current.saving) return

        if (!notebookHasContent(current.draft.title, current.draft.body)) {
            _editorState.value = current.copy(
                error = NotebookEditorError.Invalid(
                    listOf(NotebookValidationIssue(current.draft.id, "text", "")),
                ),
            )
            return
        }

        _pendingConversion.value = null
        _editorState.value = current.copy(saving = true, error = null)

        saveJob = viewModelScope.launch {
            editorGeneration++

            val draft = current.draft
            val commit = commitEditorForm(current, draft)

            when (commit) {
                is NotebookWriteResult.Success -> {
                    val saved = notebookFindSavedItem(commit.data, draft)

                    applyLoaded(commit.data)
                    clearLessonPicks()
                    _editorState.value = null

                    if (saved != null) {
                        reminders.schedule(context, accountId, saved)
                    }

                    when (val converted = draftMutex.withLock {
                        repository.convert(itemId, target, confirmed = true)
                    }) {
                        is NotebookWriteResult.Success -> applyConversionSuccess(converted, itemId, target)
                        is NotebookWriteResult.ReadFailed -> {
                            _editorState.value = null
                            _notice.value = NotebookNotice.READ_ONLY
                        }

                        else -> {
                            _editorState.value = null
                            _notice.value = NotebookNotice.CONVERSION_FAILED
                        }
                    }
                }

                is NotebookWriteResult.Conflict -> {
                    applyLoaded(commit.current)
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.Conflict,
                    )
                }

                is NotebookWriteResult.NotFound -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.NotFound,
                    )
                }

                is NotebookWriteResult.Invalid -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.Invalid(commit.issues),
                    )
                }

                is NotebookWriteResult.ReadFailed -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.ReadFailed(commit.reason),
                        readOnly = true,
                    )
                }

                is NotebookWriteResult.StorageFailed -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.StorageFailed(commit.reason),
                    )
                }

                is NotebookWriteResult.ConfirmationRequired -> {
                    _editorState.value = current.copy(
                        saving = false,
                        error = NotebookEditorError.SaveFailed,
                    )
                }
            }
        }
    }

    fun discardChangesAndConvert(itemId: String, target: NotebookKind) {
        val current = _editorState.value as? NotebookEditorState.Ready ?: return

        _pendingConversion.value = null

        viewModelScope.launch {
            editorGeneration++

            clearLessonPicks()
            _editorState.value = null

            when (val result = repository.convert(itemId, target, confirmed = true)) {
                is NotebookWriteResult.Success -> applyConversionSuccess(result, itemId, target)
                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                is NotebookWriteResult.NotFound -> _notice.value = NotebookNotice.ITEM_MISSING
                else -> _notice.value = NotebookNotice.CONVERSION_FAILED
            }
        }
    }

    fun clearPendingConversion() {
        _pendingConversion.value = null
    }

    fun previewImport(text: String) {
        viewModelScope.launch {
            if (_listState.value is NotebookListState.Failed) {
                _notice.value = NotebookNotice.READ_ONLY
                return@launch
            }

            val current = data ?: loadDataForOperation() ?: return@launch

            _importPreview.value = when (val decoded = NotebookExportCodec.decode(text)) {
                is NotebookDecodeResult.Valid ->
                    NotebookImportPreviewResult.Ready(NotebookImportPlanner.preview(decoded.export, current))

                is NotebookDecodeResult.Invalid -> NotebookImportPreviewResult.Invalid(decoded.issues)
                NotebookDecodeResult.UnsupportedVersion -> NotebookImportPreviewResult.UnsupportedVersion
                NotebookDecodeResult.TooLarge -> NotebookImportPreviewResult.TooLarge
            }
        }
    }

    fun applyImport(
        preview: NotebookImportPreview,
        choices: Map<String, NotebookImportChoice>,
    ) {
        viewModelScope.launch {
            val plan: NotebookImportPlan = NotebookImportPlanner.plan(preview, choices) {
                UUID.randomUUID().toString()
            }

            if (plan.add.isEmpty() && plan.replace.isEmpty() && plan.keepBoth.isEmpty()) {
                _importPreview.value = null
                _notice.value = NotebookNotice.IMPORT_EMPTY
                return@launch
            }

            when (val result = repository.applyImport(plan)) {
                is NotebookWriteResult.Success -> {
                    applyLoaded(result.data)
                    _importPreview.value = null
                    _notice.value = NotebookNotice.IMPORT_APPLIED
                    reminders.rescheduleAll(context)
                }

                is NotebookWriteResult.Conflict -> {
                    applyLoaded(result.current)
                    _importPreview.value = null
                    _notice.value = NotebookNotice.IMPORT_STALE
                }

                is NotebookWriteResult.ReadFailed -> _notice.value = NotebookNotice.READ_ONLY
                else -> _notice.value = NotebookNotice.IMPORT_FAILED
            }
        }
    }

    fun clearImportPreview() {
        _importPreview.value = null
    }

    fun exportText(): String? {
        val current = data ?: return null

        return NotebookExportCodec.encode(
            NotebookExport(
                exportedAt = System.currentTimeMillis(),
                items = current.items,
            ),
        )
    }

    fun exportItemCount(): Int = data?.items?.size ?: 0

    fun postNotice(notice: NotebookNotice) {
        _notice.value = notice
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun applyLoaded(loaded: NotebookData) {
        data = loaded
        _listState.value = NotebookListState.Ready(loaded.items, loaded.revision)
    }

    private suspend fun loadDataForEditor(): NotebookData? {
        if (data != null) return data

        return when (val result = repository.load()) {
            is NotebookReadResult.Loaded -> {
                applyLoaded(result.data)
                result.data
            }

            NotebookReadResult.Missing -> {
                val empty = NotebookData()
                applyLoaded(empty)
                empty
            }

            is NotebookReadResult.Failed -> {
                _editorState.value = NotebookEditorState.Failed(result.reason)
                null
            }
        }
    }

    private suspend fun loadDataForOperation(): NotebookData? {
        if (data != null) return data

        return when (val result = repository.load()) {
            is NotebookReadResult.Loaded -> {
                applyLoaded(result.data)
                result.data
            }

            NotebookReadResult.Missing -> {
                val empty = NotebookData()
                applyLoaded(empty)
                empty
            }

            is NotebookReadResult.Failed -> {
                _notice.value = NotebookNotice.READ_ONLY
                null
            }
        }
    }

    fun reconcileOnResume() {
        if (accountId.isBlank()) return

        viewModelScope.launch {
            when (val result = repository.load()) {
                is NotebookReadResult.Loaded -> applyReconciled(result.data)
                NotebookReadResult.Missing -> applyReconciled(NotebookData())
                is NotebookReadResult.Failed -> {
                    _listState.value = NotebookListState.Failed(result.reason)

                    val editor = _editorState.value as? NotebookEditorState.Ready

                    if (editor != null) {
                        _editorState.value = editor.copy(
                            error = NotebookEditorError.ReadFailed(result.reason),
                            readOnly = true,
                        )
                    }
                }
            }

            _pendingUndo.value = null
        }
    }

    private fun applyReconciled(loaded: NotebookData) {
        val previousBaseline = baseline
        val flagsChanged = editorFlagsChanged((_editorState.value as? NotebookEditorState.Ready)?.item)

        applyLoaded(loaded)

        val editor = _editorState.value as? NotebookEditorState.Ready ?: return
        val itemId = editor.draft.itemId ?: return
        val latest = loaded.items.firstOrNull { it.id == itemId }

        if (latest == null) {
            _editorState.value = editor.copy(
                item = null,
                error = NotebookEditorError.NotFound,
            )
            return
        }

        if (!editor.isDirty) {
            val refreshed = editor.draft.copy(
                kind = latest.kind,
                title = latest.title,
                body = latest.body,
                subject = latest.subject,
                origin = latest.origin,
                due = latest.due,
                baseUpdatedAt = latest.updatedAt,
            )

            baseline = latest

            _editorState.value = editor.copy(
                draft = refreshed,
                item = latest,
                isDirty = false,
                error = null,
                readOnly = false,
            )
            return
        }

        val contentChanged = previousBaseline == null ||
            notebookItemContentChanged(previousBaseline, latest)

        if (contentChanged) {
            _editorState.value = editor.copy(
                item = latest,
                error = NotebookEditorError.Conflict,
            )
            return
        }

        val draft = editor.draft.copy(baseUpdatedAt = latest.updatedAt)

        baseline = latest

        _editorState.value = editor.copy(
            draft = draft,
            item = if (flagsChanged) editor.item else latest,
            isDirty = notebookHasUnsavedChanges(draft, latest) || flagsChanged,
            error = null,
            readOnly = false,
        )
    }

    private fun applyWrite(
        result: NotebookWriteResult.Success,
        undo: NotebookUndo?,
        itemId: String,
    ) {
        val before = data?.items?.firstOrNull { it.id == itemId }
        val updated = result.data.items.firstOrNull { it.id == itemId }

        applyLoaded(result.data)

        if (updated != null) {
            reminders.schedule(context, accountId, updated)
        }

        val changed = before != updated

        if (undo != null && changed) _pendingUndo.value = undo

        val editor = _editorState.value as? NotebookEditorState.Ready

        if (editor != null && editor.draft.itemId == itemId && updated != null) {
            val draft = editor.draft.copy(baseUpdatedAt = updated.updatedAt)

            baseline = updated

            _editorState.value = editor.copy(
                draft = draft,
                item = updated,
                isDirty = notebookHasUnsavedChanges(draft, updated),
            )
        }
    }

    private suspend fun applyConversionSuccess(
        result: NotebookWriteResult.Success,
        itemId: String,
        target: NotebookKind,
    ) {
        editorGeneration++
        applyLoaded(result.data)

        val updated = result.data.items.firstOrNull { it.id == itemId }

        if (target == NotebookKind.NOTE) {
            reminders.cancel(context, accountId, itemId)
        } else if (updated != null) {
            reminders.schedule(context, accountId, updated)
        }

        val editor = _editorState.value as? NotebookEditorState.Ready

        if (editor != null && editor.draft.itemId == itemId && !editor.isDirty) {
            clearLessonPicks()
            _editorState.value = null
        }

        _notice.value = NotebookNotice.CONVERSION_DONE
    }

    private fun applyEditorFailure(error: NotebookEditorError, readOnly: Boolean = false) {
        val editor = _editorState.value as? NotebookEditorState.Ready ?: return

        _editorState.value = editor.copy(
            saving = false,
            error = error,
            readOnly = readOnly || editor.readOnly,
        )
    }

    private fun currentItem(itemId: String): NotebookItem? =
        data?.items?.firstOrNull { it.id == itemId }

    private fun rebuildQuery(text: String = query.text) {
        val kind = if (selectedTab == NotebookTab.TASKS) NotebookKind.TASK else NotebookKind.NOTE
        val status = if (selectedTab == NotebookTab.TASKS) taskStatusFilter else noteStatusFilter

        query = NotebookQuery(
            text = text,
            kinds = setOf(kind),
            status = status,
            subject = subjectFilter,
        )
    }

    private fun loadLessonCache() {
        viewModelScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    if (accountId.isBlank()) {
                        null
                    } else {
                        val cache = TimetableCache.load(context, accountId)
                        val stats = StatsStore.load(context, accountId)

                        cache to stats
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            lessonCache = NotebookLessonCache.from(loaded?.first)
            _subjectChoices.value = loaded?.let { (cache, stats) ->
                val snapshot = notebookCurrentSchoolYearSubjects(
                    cache = cache,
                    stats = stats,
                    today = AppClock.today(),
                )

                if (snapshot.subjects.isEmpty()) {
                    NotebookSubjectChoices.Empty(
                        yearName = snapshot.yearName,
                        estimatedYear = snapshot.estimatedYear,
                    )
                } else {
                    NotebookSubjectChoices.Ready(
                        subjects = snapshot.subjects,
                        yearName = snapshot.yearName,
                        estimatedYear = snapshot.estimatedYear,
                    )
                }
            } ?: NotebookSubjectChoices.Empty(yearName = "", estimatedYear = false)

            val editor = _editorState.value as? NotebookEditorState.Ready

            if (editor != null) _editorState.value = editor.copy(lessonCache = lessonCache)
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        editorJob?.cancel()
        saveJob?.cancel()
        super.onCleared()
    }

    private fun NotebookItem.toDraft(): NotebookDraft = NotebookDraft(
        id = UUID.randomUUID().toString(),
        itemId = id,
        kind = kind,
        title = title,
        body = body,
        subject = subject,
        origin = origin,
        due = due,
        baseUpdatedAt = updatedAt,
        updatedAt = updatedAt,
    )

    companion object {
        private fun resolveAccountId(app: Application): String {
            val context = app.applicationContext ?: app

            return AccountStore.active
                ?.let { AccountPrefs.accountId(it) }
                ?: AccountStore.getActiveIdSync(context).orEmpty()
        }
    }
}

internal interface NotebookReminderSink {
    fun schedule(context: Context, accountId: String, item: NotebookItem)

    fun cancel(context: Context, accountId: String, itemId: String)

    fun rescheduleAll(context: Context)
}

internal object DefaultNotebookReminderSink : NotebookReminderSink {
    override fun schedule(context: Context, accountId: String, item: NotebookItem) {
        NotebookReminderScheduler.schedule(context, accountId, item)
    }

    override fun cancel(context: Context, accountId: String, itemId: String) {
        NotebookReminderScheduler.cancel(context, accountId, itemId)
    }

    override fun rescheduleAll(context: Context) {
        NotebookReminderScheduler.rescheduleAll(context)
    }
}
