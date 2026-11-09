package sh.lsx.untisx.data.notebook

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class NotebookField { DUE, REMINDER, COMPLETION, PINNED, ARCHIVE }

data class NotebookConversionWarning(
    val from: NotebookKind,
    val to: NotebookKind,
    val lostFields: Set<NotebookField>,
)

object NotebookConversion {
    fun warning(item: NotebookItem, target: NotebookKind): NotebookConversionWarning? {
        if (item.kind == target) return null

        val lost = mutableSetOf<NotebookField>()

        when (item.kind) {
            NotebookKind.NOTE -> {
                if (item.pinnedAt != null) lost += NotebookField.PINNED
                if (item.archivedAt != null) lost += NotebookField.ARCHIVE
            }

            NotebookKind.TASK -> {
                val due = item.due
                if (due != null) {
                    lost += NotebookField.DUE
                    if (due.reminder != null) lost += NotebookField.REMINDER
                }
                if (item.completedAt != null) lost += NotebookField.COMPLETION
            }
        }

        return if (lost.isEmpty()) {
            null
        } else {
            NotebookConversionWarning(item.kind, target, lost)
        }
    }

    fun convert(item: NotebookItem, target: NotebookKind, now: Long): NotebookItem {
        if (item.kind == target) return item

        return when (target) {
            NotebookKind.TASK -> item.copy(
                kind = NotebookKind.TASK,
                pinnedAt = null,
                archivedAt = null,
                updatedAt = now,
            )

            NotebookKind.NOTE -> item.copy(
                kind = NotebookKind.NOTE,
                due = null,
                completedAt = null,
                updatedAt = now,
            )
        }
    }
}

class NotebookRepository(
    context: Context,
    val accountId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val appContext = context.applicationContext ?: context
    private val lock = NotebookAccountLocks.lock(accountId)

    suspend fun load(): NotebookReadResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookReadResult.Missing

        NotebookStore.load(appContext, accountId)
    }

    suspend fun commitDraft(
        draft: NotebookDraft,
        expectedRevision: Long? = null,
        cleanupDraft: Boolean = true,
        completed: Boolean? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null,
    ): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val normalizedDraft = NotebookValidator.normalizeDraft(draft)
        val draftIssues = NotebookValidator.validateDraft(normalizedDraft)
        if (draftIssues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(draftIssues)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        if (expectedRevision != null && expectedRevision != current.revision) {
            return@withLock NotebookWriteResult.Conflict(current)
        }

        val draftItemId = normalizedDraft.itemId
        val existing = draftItemId?.let { id -> current.items.firstOrNull { it.id == id } }
        if (draftItemId != null && existing == null) {
            return@withLock NotebookWriteResult.NotFound(draftItemId)
        }

        if (existing != null) {
            if (normalizedDraft.baseUpdatedAt != null && normalizedDraft.baseUpdatedAt != existing.updatedAt) {
                return@withLock NotebookWriteResult.Conflict(current)
            }
            if (normalizedDraft.kind != existing.kind) {
                return@withLock NotebookWriteResult.Invalid(
                    listOf(
                        NotebookValidationIssue(
                            existing.id,
                            "kind",
                            "Use the conversion action to change a note into a task or vice versa.",
                        ),
                    ),
                )
            }
        }

        val now = clock()
        val itemId = existing?.id ?: generateId(current.items.mapTo(mutableSetOf<String>()) { it.id })
            ?: return@withLock NotebookWriteResult.Invalid(
                listOf(NotebookValidationIssue(null, "id", "Could not allocate a unique item ID.")),
            )

        val candidate = NotebookValidator.normalize(
            NotebookItem(
                id = itemId,
                kind = normalizedDraft.kind,
                title = normalizedDraft.title,
                body = normalizedDraft.body,
                subject = normalizedDraft.subject,
                origin = normalizedDraft.origin,
                due = normalizedDraft.due,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                completedAt = when (completed) {
                    true -> existing?.completedAt ?: now
                    false -> null
                    null -> existing?.completedAt
                },
                pinnedAt = when (pinned) {
                    true -> existing?.pinnedAt ?: now
                    false -> null
                    null -> existing?.pinnedAt
                },
                archivedAt = when (archived) {
                    true -> existing?.archivedAt ?: now
                    false -> null
                    null -> existing?.archivedAt
                },
            ),
        )

        val issues = NotebookValidator.validate(candidate)
        if (issues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(issues)

        val items = if (existing == null) {
            current.items + candidate
        } else {
            current.items.map { if (it.id == existing.id) candidate else it }
        }

        if (items.size > NotebookValidator.MAX_ITEMS) {
            return@withLock NotebookWriteResult.Invalid(
                listOf(NotebookValidationIssue(null, "items", "At most ${NotebookValidator.MAX_ITEMS} items are supported.")),
            )
        }

        when (val written = writeLocked(current, items, now)) {
            is NotebookWriteResult.Success -> {
                val cleanupFailed = if (cleanupDraft && normalizedDraft.id.isNotBlank()) {
                    deleteDraftLocked(normalizedDraft.id) !is NotebookDraftWriteResult.Success
                } else {
                    false
                }

                written.copy(draftCleanupFailed = cleanupFailed)
            }

            else -> written
        }
    }

    suspend fun delete(itemId: String, cleanupDrafts: Boolean = true): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        if (current.items.none { it.id == itemId }) {
            return@withLock NotebookWriteResult.NotFound(itemId)
        }

        val items = current.items.filterNot { it.id == itemId }
        val now = clock()

        when (val written = writeLocked(current, items, now)) {
            is NotebookWriteResult.Success -> {
                written.copy(draftCleanupFailed = cleanupDrafts && deleteDraftsForItemLocked(itemId))
            }

            else -> written
        }
    }

    /** Removes several items in one write (e.g. into the trash); unknown ids are ignored. */
    suspend fun removeAll(itemIds: Set<String>): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        val items = current.items.filterNot { it.id in itemIds }
        if (items.size == current.items.size) return@withLock NotebookWriteResult.Success(current)

        val written = writeLocked(current, items, clock())
        if (written is NotebookWriteResult.Success) itemIds.forEach { deleteDraftsForItemLocked(it) }
        written
    }

    /** Puts items back as they were (from the trash); an id taken in the meantime gets a new one. */
    suspend fun restore(restored: List<NotebookItem>): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        val used = current.items.mapTo(mutableSetOf()) { it.id }
        val added = restored.map { item ->
            val id = if (item.id.isNotBlank() && item.id !in used) {
                item.id
            } else {
                generateId(used) ?: return@withLock NotebookWriteResult.Invalid(
                    listOf(NotebookValidationIssue(null, "id", "Could not allocate a unique item ID.")),
                )
            }
            used += id
            NotebookValidator.normalize(item.copy(id = id))
        }
        val items = current.items + added
        if (items.size > NotebookValidator.MAX_ITEMS) {
            return@withLock NotebookWriteResult.Invalid(
                listOf(NotebookValidationIssue(null, "items", "At most ${NotebookValidator.MAX_ITEMS} items are supported.")),
            )
        }
        writeLocked(current, items, clock())
    }

    suspend fun setCompleted(itemId: String, completed: Boolean): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        val item = current.items.firstOrNull { it.id == itemId }
            ?: return@withLock NotebookWriteResult.NotFound(itemId)

        if (!item.isTask) {
            return@withLock NotebookWriteResult.Invalid(
                listOf(NotebookValidationIssue(itemId, "kind", "Only tasks can be completed.")),
            )
        }

        if (item.isCompleted == completed) return@withLock NotebookWriteResult.Success(current)

        val now = clock()
        val updated = item.copy(completedAt = if (completed) now else null, updatedAt = now)

        writeItemLocked(current, updated, now)
    }

    suspend fun setPinned(itemId: String, pinned: Boolean): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        val item = current.items.firstOrNull { it.id == itemId }
            ?: return@withLock NotebookWriteResult.NotFound(itemId)

        if (item.isTask) {
            return@withLock NotebookWriteResult.Invalid(
                listOf(NotebookValidationIssue(itemId, "kind", "Only notes can be pinned.")),
            )
        }

        if (item.isPinned == pinned) return@withLock NotebookWriteResult.Success(current)

        val now = clock()
        val updated = item.copy(pinnedAt = if (pinned) now else null, updatedAt = now)

        writeItemLocked(current, updated, now)
    }

    suspend fun setArchived(itemId: String, archived: Boolean): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        val item = current.items.firstOrNull { it.id == itemId }
            ?: return@withLock NotebookWriteResult.NotFound(itemId)

        if (item.isTask) {
            return@withLock NotebookWriteResult.Invalid(
                listOf(NotebookValidationIssue(itemId, "kind", "Only notes can be archived.")),
            )
        }

        if (item.isArchived == archived) return@withLock NotebookWriteResult.Success(current)

        val now = clock()
        val updated = item.copy(archivedAt = if (archived) now else null, updatedAt = now)

        writeItemLocked(current, updated, now)
    }

    suspend fun convert(
        itemId: String,
        target: NotebookKind,
        confirmed: Boolean = false,
    ): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        val item = current.items.firstOrNull { it.id == itemId }
            ?: return@withLock NotebookWriteResult.NotFound(itemId)

        if (item.kind == target) return@withLock NotebookWriteResult.Success(current)

        val warning = NotebookConversion.warning(item, target)
        if (warning != null && !confirmed) {
            return@withLock NotebookWriteResult.ConfirmationRequired(warning)
        }

        val now = clock()
        val converted = NotebookValidator.normalize(NotebookConversion.convert(item, target, now))
        val issues = NotebookValidator.validate(converted)

        if (issues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(issues)

        writeItemLocked(current, converted, now)
    }

    suspend fun applyImport(plan: NotebookImportPlan): NotebookWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadCurrentLocked()) {
            is Current.Ok -> loaded.data
            is Current.Failed -> return@withLock NotebookWriteResult.ReadFailed(loaded.reason)
        }

        if (plan.localRevision != current.revision) {
            return@withLock NotebookWriteResult.Conflict(current)
        }

        if (plan.add.isEmpty() && plan.replace.isEmpty() && plan.keepBoth.isEmpty()) {
            return@withLock NotebookWriteResult.Success(current)
        }

        val originalIds = current.items.mapTo(mutableSetOf()) { it.id }
        val items = LinkedHashMap<String, NotebookItem>()
        current.items.forEach { items[it.id] = it }

        for (incoming in plan.add) {
            val normalized = NotebookValidator.normalize(incoming)
            val issues = NotebookValidator.validate(normalized)
            if (issues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(issues)
            if (normalized.id in items) {
                return@withLock NotebookWriteResult.Invalid(
                    listOf(NotebookValidationIssue(normalized.id, "id", "Imported item ID already exists.")),
                )
            }
            items[normalized.id] = normalized
        }

        for (incoming in plan.replace) {
            val normalized = NotebookValidator.normalize(incoming)
            val issues = NotebookValidator.validate(normalized)
            if (issues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(issues)
            if (normalized.id !in originalIds) {
                return@withLock NotebookWriteResult.Invalid(
                    listOf(NotebookValidationIssue(normalized.id, "id", "Replacement item does not exist.")),
                )
            }
            items[normalized.id] = normalized
        }

        for (incoming in plan.keepBoth) {
            var normalized = NotebookValidator.normalize(incoming)

            if (normalized.id.isBlank() ||
                normalized.id.length > NotebookValidator.MAX_ID ||
                normalized.id in items
            ) {
                val id = generateId(items.keys)
                    ?: return@withLock NotebookWriteResult.Invalid(
                        listOf(NotebookValidationIssue(null, "id", "Could not allocate a unique item ID.")),
                    )
                normalized = normalized.copy(id = id)
            }

            val issues = NotebookValidator.validate(normalized)
            if (issues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(issues)

            items[normalized.id] = normalized
        }

        val now = clock()
        val data = current.copy(
            schema = NotebookSchema.CURRENT,
            revision = current.revision + 1,
            items = items.values.toList(),
            updatedAt = now,
        )
        val issues = NotebookValidator.validate(data)

        if (issues.isNotEmpty()) return@withLock NotebookWriteResult.Invalid(issues)

        NotebookStore.write(appContext, accountId, data)
    }

    suspend fun loadDrafts(): NotebookDraftReadResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookDraftReadResult.Missing

        NotebookDraftStore.load(appContext, accountId)
    }

    suspend fun saveDraft(draft: NotebookDraft): NotebookDraftWriteResult = lock.withLock {
        if (accountBlocked()) return@withLock NotebookDraftWriteResult.ReadFailed(NotebookReadFailure.IO)

        val current = when (val loaded = loadDraftsLocked()) {
            is DraftCurrent.Ok -> loaded.data
            is DraftCurrent.Failed -> return@withLock NotebookDraftWriteResult.ReadFailed(loaded.reason)
        }

        val normalized = NotebookValidator.normalizeDraft(draft).copy(updatedAt = clock())
        val issues = NotebookValidator.validateDraft(normalized)

        if (issues.isNotEmpty()) return@withLock NotebookDraftWriteResult.Invalid(issues)

        if (normalized.id !in current.drafts && current.drafts.size >= NotebookValidator.MAX_DRAFTS) {
            return@withLock NotebookDraftWriteResult.Invalid(
                listOf(
                    NotebookValidationIssue(
                        normalized.id,
                        "drafts",
                        "At most ${NotebookValidator.MAX_DRAFTS} drafts are supported.",
                    ),
                ),
            )
        }

        val data = current.copy(
            schema = NotebookSchema.CURRENT,
            revision = current.revision + 1,
            drafts = current.drafts + (normalized.id to normalized),
        )

        NotebookDraftStore.write(appContext, accountId, data)
    }

    private suspend fun loadCurrentLocked(): Current {
        return when (val loaded = NotebookStore.load(appContext, accountId)) {
            is NotebookReadResult.Loaded -> Current.Ok(loaded.data)
            NotebookReadResult.Missing -> Current.Ok(NotebookData())
            is NotebookReadResult.Failed -> Current.Failed(loaded.reason)
        }
    }

    private suspend fun loadDraftsLocked(): DraftCurrent {
        return when (val loaded = NotebookDraftStore.load(appContext, accountId)) {
            is NotebookDraftReadResult.Loaded -> DraftCurrent.Ok(loaded.data)
            NotebookDraftReadResult.Missing -> DraftCurrent.Ok(NotebookDraftData())
            is NotebookDraftReadResult.Failed -> DraftCurrent.Failed(loaded.reason)
        }
    }

    private suspend fun writeItemLocked(
        current: NotebookData,
        item: NotebookItem,
        now: Long,
    ): NotebookWriteResult {
        val items = current.items.map { if (it.id == item.id) item else it }

        return writeLocked(current, items, now)
    }

    private suspend fun writeLocked(
        current: NotebookData,
        items: List<NotebookItem>,
        now: Long,
    ): NotebookWriteResult {
        val data = current.copy(
            schema = NotebookSchema.CURRENT,
            revision = current.revision + 1,
            items = items,
            updatedAt = now,
        )

        return NotebookStore.write(appContext, accountId, data)
    }

    private suspend fun deleteDraftLocked(draftId: String): NotebookDraftWriteResult {
        val current = when (val loaded = loadDraftsLocked()) {
            is DraftCurrent.Ok -> loaded.data
            is DraftCurrent.Failed -> return NotebookDraftWriteResult.ReadFailed(loaded.reason)
        }

        if (draftId !in current.drafts) return NotebookDraftWriteResult.Success(current)

        val data = current.copy(
            schema = NotebookSchema.CURRENT,
            revision = current.revision + 1,
            drafts = current.drafts - draftId,
        )

        return NotebookDraftStore.write(appContext, accountId, data)
    }

    private suspend fun deleteDraftsForItemLocked(itemId: String): Boolean {
        val current = when (val loaded = loadDraftsLocked()) {
            is DraftCurrent.Ok -> loaded.data
            is DraftCurrent.Failed -> return true
        }

        val remaining = current.drafts.filterValues { it.itemId != itemId }
        if (remaining.size == current.drafts.size) return false

        val data = current.copy(
            schema = NotebookSchema.CURRENT,
            revision = current.revision + 1,
            drafts = remaining,
        )

        return when (NotebookDraftStore.write(appContext, accountId, data)) {
            is NotebookDraftWriteResult.Success -> false
            else -> true
        }
    }

    private suspend fun accountBlocked(): Boolean = withContext(Dispatchers.IO) {
        NotebookAccountLifecycle.isBlocked(appContext, accountId)
    }

    private fun generateId(used: Collection<String>): String? {
        repeat(10) {
            val candidate = newId().trim()

            if (candidate.isNotBlank() && candidate.length <= NotebookValidator.MAX_ID && candidate !in used) {
                return candidate
            }
        }

        return null
    }

    private sealed interface Current {
        data class Ok(val data: NotebookData) : Current
        data class Failed(val reason: NotebookReadFailure) : Current
    }

    private sealed interface DraftCurrent {
        data class Ok(val data: NotebookDraftData) : DraftCurrent
        data class Failed(val reason: NotebookReadFailure) : DraftCurrent
    }

}
