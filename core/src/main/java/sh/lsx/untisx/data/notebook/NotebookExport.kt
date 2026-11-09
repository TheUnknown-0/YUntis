package sh.lsx.untisx.data.notebook

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NotebookExport(
    val type: String = NotebookExportCodec.TYPE,
    val version: Int = NotebookExportCodec.CURRENT_VERSION,
    val exportedAt: Long = 0,
    val appVersion: String = "",
    val items: List<NotebookItem> = emptyList(),
)

sealed interface NotebookDecodeResult {
    data class Valid(val export: NotebookExport) : NotebookDecodeResult
    data class Invalid(val issues: List<NotebookValidationIssue>) : NotebookDecodeResult
    data object UnsupportedVersion : NotebookDecodeResult
    data object TooLarge : NotebookDecodeResult
}

object NotebookExportCodec {
    const val TYPE = "untisx-notebook"
    const val CURRENT_VERSION = 1
    const val MAX_BYTES = 8 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(export: NotebookExport): String =
        json.encodeToString(export.copy(type = TYPE, version = CURRENT_VERSION))

    fun decode(text: String): NotebookDecodeResult {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return NotebookDecodeResult.TooLarge

        val export = runCatching { json.decodeFromString<NotebookExport>(text) }.getOrNull()
            ?: return NotebookDecodeResult.Invalid(
                listOf(NotebookValidationIssue(null, "export", "The file is not valid notebook JSON.")),
            )

        if (export.type != TYPE) {
            return NotebookDecodeResult.Invalid(
                listOf(NotebookValidationIssue(null, "type", "This is not a YUntis notebook export.")),
            )
        }

        if (export.version > CURRENT_VERSION) return NotebookDecodeResult.UnsupportedVersion

        if (export.version != CURRENT_VERSION) {
            return NotebookDecodeResult.Invalid(
                listOf(NotebookValidationIssue(null, "version", "Unsupported notebook export version.")),
            )
        }

        if (export.exportedAt < 0) {
            return NotebookDecodeResult.Invalid(
                listOf(NotebookValidationIssue(null, "exportedAt", "Invalid export timestamp.")),
            )
        }

        val issues = NotebookValidator.validateItems(export.items.map { NotebookValidator.normalize(it) })

        return if (issues.isEmpty()) {
            NotebookDecodeResult.Valid(export)
        } else {
            NotebookDecodeResult.Invalid(issues)
        }
    }
}

enum class NotebookImportChoice { SKIP, REPLACE, KEEP_BOTH }

enum class NotebookImportConflictReason {
    SAME_ID_DIFFERENT_CONTENT,
    SAME_CONTENT_DIFFERENT_ID,
    SAME_ID_OLDER,
}

data class NotebookImportConflict(
    val incoming: NotebookItem,
    val existing: NotebookItem,
    val reason: NotebookImportConflictReason,
)

data class NotebookImportPreview(
    val source: NotebookExport,
    val newItems: List<NotebookItem>,
    val identical: List<NotebookItem>,
    val conflicts: List<NotebookImportConflict>,
    val invalid: List<NotebookValidationIssue>,
    val localRevision: Long,
)

data class NotebookImportPlan(
    val add: List<NotebookItem> = emptyList(),
    val replace: List<NotebookItem> = emptyList(),
    val keepBoth: List<NotebookItem> = emptyList(),
    val localRevision: Long,
)

object NotebookImportPlanner {
    fun preview(export: NotebookExport, local: NotebookData): NotebookImportPreview {
        val normalized = export.items.map { NotebookValidator.normalize(it) }
        val issues = NotebookValidator.validateItems(normalized)

        val invalidIds = issues.mapNotNull { it.itemId }.toSet()
        val usable = if (issues.any { it.itemId == null }) {
            emptyList()
        } else {
            normalized.filter { it.id !in invalidIds && NotebookValidator.validate(it).isEmpty() }
        }

        val existingById = local.items.associateBy { it.id }
        val existingByFingerprint = local.items.groupBy { fingerprint(it) }

        val newItems = mutableListOf<NotebookItem>()
        val identical = mutableListOf<NotebookItem>()
        val conflicts = mutableListOf<NotebookImportConflict>()

        usable.forEach { incoming ->
            val sameId = existingById[incoming.id]

            if (sameId != null) {
                if (fingerprint(sameId) == fingerprint(incoming)) {
                    identical += incoming
                } else if (incoming.updatedAt < sameId.updatedAt) {
                    conflicts += NotebookImportConflict(incoming, sameId, NotebookImportConflictReason.SAME_ID_OLDER)
                } else {
                    conflicts += NotebookImportConflict(
                        incoming,
                        sameId,
                        NotebookImportConflictReason.SAME_ID_DIFFERENT_CONTENT,
                    )
                }
            } else {
                val sameContent = existingByFingerprint[fingerprint(incoming)]?.firstOrNull()

                if (sameContent != null) {
                    conflicts += NotebookImportConflict(
                        incoming,
                        sameContent,
                        NotebookImportConflictReason.SAME_CONTENT_DIFFERENT_ID,
                    )
                } else {
                    newItems += incoming
                }
            }
        }

        return NotebookImportPreview(
            source = export,
            newItems = newItems,
            identical = identical,
            conflicts = conflicts,
            invalid = issues,
            localRevision = local.revision,
        )
    }

    fun plan(
        preview: NotebookImportPreview,
        choices: Map<String, NotebookImportChoice>,
        newId: () -> String,
    ): NotebookImportPlan {
        val add = preview.newItems.toMutableList()
        val replace = mutableListOf<NotebookItem>()
        val keepBoth = mutableListOf<NotebookItem>()
        val used = (
            preview.newItems.map { it.id } +
                preview.identical.map { it.id } +
                preview.conflicts.map { it.existing.id } +
                preview.conflicts.map { it.incoming.id }
            ).toMutableSet()

        preview.conflicts.forEach { conflict ->
            when (choices[conflict.incoming.id] ?: NotebookImportChoice.SKIP) {
                NotebookImportChoice.SKIP -> Unit

                NotebookImportChoice.REPLACE -> {
                    if (conflict.reason != NotebookImportConflictReason.SAME_CONTENT_DIFFERENT_ID) {
                        replace += conflict.incoming
                    }
                }

                NotebookImportChoice.KEEP_BOTH -> {
                    val id = freshId(used, newId) ?: return@forEach

                    used += id
                    keepBoth += conflict.incoming.copy(id = id)
                }
            }
        }

        return NotebookImportPlan(
            add = add,
            replace = replace,
            keepBoth = keepBoth,
            localRevision = preview.localRevision,
        )
    }

    private fun freshId(used: MutableSet<String>, newId: () -> String): String? {
        repeat(10) {
            val candidate = newId().trim()

            if (candidate.isNotBlank() &&
                candidate.length <= NotebookValidator.MAX_ID &&
                candidate !in used
            ) {
                return candidate
            }
        }

        return null
    }

    private data class Fingerprint(
        val kind: NotebookKind,
        val title: String,
        val body: String,
        val subjectIds: List<Int>,
        val subjectName: String,
        val subjectLongName: String,
        val originEntryId: Int,
        val originDate: Int,
        val originStartTime: Int,
        val dueDate: Int,
        val dueLessonEntryId: Int,
        val dueLessonDate: Int,
        val dueLessonStartTime: Int,
        val reminderDate: Int,
        val reminderMinutesOfDay: Int,
        val completedAt: Long?,
        val pinnedAt: Long?,
        val archivedAt: Long?,
    )

    private fun fingerprint(item: NotebookItem): Fingerprint {
        val normalized = NotebookValidator.normalize(item)
        val subject = normalized.subject
        val origin = normalized.origin
        val due = normalized.due

        return Fingerprint(
            kind = normalized.kind,
            title = normalized.title,
            body = normalized.body,
            subjectIds = subject?.ids.orEmpty(),
            subjectName = subject?.name.orEmpty().lowercase(Locale.ROOT),
            subjectLongName = subject?.longName.orEmpty().lowercase(Locale.ROOT),
            originEntryId = origin?.entryId ?: 0,
            originDate = origin?.date ?: 0,
            originStartTime = origin?.startTime ?: 0,
            dueDate = due?.date ?: 0,
            dueLessonEntryId = due?.lesson?.entryId ?: 0,
            dueLessonDate = due?.lesson?.date ?: 0,
            dueLessonStartTime = due?.lesson?.startTime ?: 0,
            reminderDate = due?.reminder?.date ?: 0,
            reminderMinutesOfDay = due?.reminder?.minutesOfDay ?: 0,
            completedAt = normalized.completedAt,
            pinnedAt = normalized.pinnedAt,
            archivedAt = normalized.archivedAt,
        )
    }
}
