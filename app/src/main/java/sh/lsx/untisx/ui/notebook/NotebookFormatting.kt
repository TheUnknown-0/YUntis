package sh.lsx.untisx.ui.notebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import sh.lsx.untisx.R
import sh.lsx.untisx.data.StatsData
import sh.lsx.untisx.data.StatsStore
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.model.toUntisLocalTime
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.LessonBlock
import sh.lsx.untisx.data.notebook.LessonKey
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookDraft
import sh.lsx.untisx.data.notebook.NotebookData
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookLessonCache
import sh.lsx.untisx.data.notebook.NotebookLessonLinks
import sh.lsx.untisx.data.notebook.NotebookLessonResolver
import sh.lsx.untisx.data.notebook.NotebookLessonSummary
import sh.lsx.untisx.data.notebook.NotebookReadFailure
import sh.lsx.untisx.data.notebook.NotebookSearch
import sh.lsx.untisx.data.notebook.NotebookStorageFailure
import sh.lsx.untisx.data.notebook.NotebookValidationIssue
import sh.lsx.untisx.data.notebook.NotebookValidator
import sh.lsx.untisx.data.notebook.SubjectRef
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.rememberTimeFormat

enum class NotebookTab { TASKS, NOTES }

enum class NotebookTaskGroup { OVERDUE, TODAY, UPCOMING, NO_DATE }

data class NotebookTaskSection(
    val group: NotebookTaskGroup,
    val items: List<NotebookItem>,
)

enum class NotebookLessonSectionKind { SOURCE, DUE, PINNED }

data class NotebookLessonSection(
    val kind: NotebookLessonSectionKind,
    val items: List<NotebookItem>,
)

enum class NotebookLessonWarning { MISSING, AMBIGUOUS, CANCELLED }

fun notebookTaskSections(items: List<NotebookItem>, today: LocalDate): List<NotebookTaskSection> {
    val todayInt = today.toUntisDate()
    val open = NotebookSearch.sort(items.filter { it.isTask && !it.isCompleted })

    val overdue = open.filter { item -> item.due?.date?.let { it < todayInt } == true }
    val dueToday = open.filter { it.due?.date == todayInt }
    val upcoming = open.filter { item -> item.due?.date?.let { it > todayInt } == true }
    val noDate = open.filter { it.due == null }

    return listOf(
        NotebookTaskSection(NotebookTaskGroup.OVERDUE, overdue),
        NotebookTaskSection(NotebookTaskGroup.TODAY, dueToday),
        NotebookTaskSection(NotebookTaskGroup.UPCOMING, upcoming),
        NotebookTaskSection(NotebookTaskGroup.NO_DATE, noDate),
    ).filter { it.items.isNotEmpty() }
}

fun notebookOpenTaskCount(items: List<NotebookItem>): Int =
    items.count { it.isTask && !it.isCompleted }

fun notebookNotes(items: List<NotebookItem>): List<NotebookItem> =
    NotebookSearch.sort(items.filter { !it.isTask })

fun notebookFilterWithoutSubject(items: List<NotebookItem>): List<NotebookItem> =
    items.filter { item -> item.subject?.isBlank != false }

/** Affiliation follows exact lesson links, with the source taking precedence. */
internal fun notebookLessonSubject(form: NotebookDraft): SubjectRef? =
    (form.origin?.subject ?: form.due?.lesson?.subject)?.normalized()?.takeIf { !it.isBlank }

sealed interface NotebookSubjectChoices {
    data object Loading : NotebookSubjectChoices

    data class Ready(
        val subjects: List<SubjectRef>,
        val yearName: String,
        val estimatedYear: Boolean,
    ) : NotebookSubjectChoices

    data class Empty(
        val yearName: String,
        val estimatedYear: Boolean,
    ) : NotebookSubjectChoices
}

data class NotebookSubjectSnapshot(
    val subjects: List<SubjectRef>,
    val yearName: String,
    val estimatedYear: Boolean,
)

fun notebookCurrentSchoolYearSubjects(
    cache: TimetableCache.Data?,
    stats: StatsData?,
    today: LocalDate,
): NotebookSubjectSnapshot {
    val todayInt = today.toUntisDate()
    val storedYear = stats?.years?.values
        ?.asSequence()
        ?.filter { it.startDate > 0 && it.endDate > 0 }
        ?.filter { todayInt in it.startDate..it.endDate }
        ?.maxByOrNull { it.startDate }
    val fallback = StatsStore.fallbackYear(today)
    val startDate = storedYear?.startDate ?: fallback.startDate
    val endDate = storedYear?.endDate ?: fallback.endDate
    val yearName = storedYear?.name?.takeIf { it.isNotBlank() } ?: fallback.name
    val estimatedYear = storedYear == null

    val names = LinkedHashMap<Int, MasterDataItem>()

    fun rememberSubject(id: Int, name: String, longName: String) {
        if (id <= 0) return
        if (name.isBlank() && longName.isBlank()) return

        names.putIfAbsent(id, MasterDataItem(id = id, name = name, longName = longName))
    }

    cache?.subjects.orEmpty().forEach { item ->
        rememberSubject(item.id, item.name, item.longName)
    }

    val ids = LinkedHashSet<Int>()

    storedYear?.days?.forEach { (dateKey, day) ->
        val date = dateKey.toIntOrNull() ?: return@forEach
        if (date < startDate || date > endDate) return@forEach

        day.subjectCounts.keys
            .filter { it > 0 }
            .forEach(ids::add)
    }

    cache?.weeks?.values.orEmpty().flatten().forEach { entry ->
        if (entry.isEvent || entry.cancelled) return@forEach
        if (entry.date < startDate || entry.date > endDate) return@forEach

        for (element in entry.subject) {
            if (element.id <= 0) continue

            ids.add(element.id)
            rememberSubject(
                id = element.id,
                name = element.name,
                longName = element.longName.orEmpty(),
            )
        }
    }

    val subjects = ids.mapNotNull { id ->
        val item = names[id] ?: return@mapNotNull null
        val name = item.name.ifBlank { item.longName }

        if (name.isBlank()) {
            null
        } else {
            SubjectRef(ids = listOf(id), name = name, longName = item.longName)
        }
    }.sortedBy { it.displayName.lowercase(Locale.ROOT) }

    return NotebookSubjectSnapshot(
        subjects = subjects,
        yearName = yearName,
        estimatedYear = estimatedYear,
    )
}

fun notebookSubjectPickerChoices(
    subjects: List<SubjectRef>,
    selected: SubjectRef?,
): List<SubjectRef> {
    val result = LinkedHashMap<String, SubjectRef>()

    fun add(ref: SubjectRef?) {
        val normalized = ref?.normalized() ?: return

        if (normalized.isBlank) return

        val key = if (normalized.ids.isNotEmpty()) {
            "id:${normalized.primaryId}"
        } else {
            "name:${normalized.name.lowercase(Locale.ROOT)}"
        }

        if (!result.containsKey(key)) result[key] = normalized
    }

    subjects.forEach { add(it) }
    val selectedNormalized = selected?.normalized()?.takeIf { !it.isBlank }
    val selectedFreeText = selectedNormalized?.takeIf { it.ids.isEmpty() }
    val duplicateFreeText = selectedFreeText
        ?.let { free -> subjects.any { free.matches(it) } }
        ?: false

    if (!duplicateFreeText) add(selectedNormalized)

    return result.values.sortedBy { it.displayName.lowercase(Locale.ROOT) }
}

fun notebookSearchSubjects(
    choices: List<SubjectRef>,
    query: String,
    aliases: Map<String, SubjectAlias>,
): List<SubjectRef> {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return choices

    return choices.filter { subject ->
        val alias = aliases[subject.displayName]
        val fields = listOfNotNull(subject.name, subject.longName, alias?.short, alias?.long) +
            subject.ids.map(Int::toString)
        terms.all { term -> fields.any { it.contains(term, ignoreCase = true) } }
    }
}

fun notebookLessonRef(cache: NotebookLessonCache, entry: TimetableEntry): LessonRef {
    val blocks = NotebookLessonResolver.blocks(cache)
    val entrySubject = SubjectRef.from(entry.subject)
    val exact = blocks.firstOrNull { block ->
        block.date == entry.date && block.entry.id == entry.id
    }
    val containing = blocks.firstOrNull { block ->
        block.date == entry.date &&
            entry.startTime >= block.startTime &&
            entry.startTime < block.endTime &&
            (
                entrySubject == null ||
                    block.subject == null ||
                    entrySubject.matches(block.subject)
                )
    }

    return (exact ?: containing)?.toRef() ?: notebookEntryRef(entry)
}


fun notebookEntryRef(entry: TimetableEntry): LessonRef = LessonRef(
    entryId = entry.id,
    date = entry.date,
    startTime = entry.startTime,
    endTime = entry.endTime,
    subject = SubjectRef.from(entry.subject),
    teacher = entry.teacher.firstOrNull()?.name.orEmpty(),
    room = entry.room.firstOrNull()?.name.orEmpty(),
)

fun notebookLessonSections(links: NotebookLessonLinks): List<NotebookLessonSection> {
    val seen = HashSet<String>()

    fun take(items: List<NotebookItem>): List<NotebookItem> =
        items.filter { item -> seen.add(item.id) }

    return listOf(
        NotebookLessonSection(NotebookLessonSectionKind.SOURCE, take(links.sourced)),
        NotebookLessonSection(NotebookLessonSectionKind.DUE, take(links.due)),
        NotebookLessonSection(NotebookLessonSectionKind.PINNED, take(links.pinned)),
    ).filter { it.items.isNotEmpty() }
}

internal fun notebookLessonJumpDate(item: NotebookItem, section: NotebookLessonSectionKind): Int? =
    when (section) {
        NotebookLessonSectionKind.DUE -> item.origin?.date
        NotebookLessonSectionKind.SOURCE -> item.due?.lesson?.date ?: item.due?.date
        NotebookLessonSectionKind.PINNED -> item.origin?.date
    }

fun notebookLessonWarning(cache: NotebookLessonCache, ref: LessonRef): NotebookLessonWarning? {
    val cancelled = cache.weeks.values
        .asSequence()
        .flatten()
        .any { entry ->
            entry.cancelled &&
                entry.date == ref.date &&
                (ref.entryId <= 0 || entry.id == ref.entryId) &&
                (
                    ref.startTime == entry.startTime ||
                        (ref.startTime >= entry.startTime && ref.startTime < entry.endTime)
                    )
        }

    if (cancelled) return NotebookLessonWarning.CANCELLED

    return when (NotebookLessonResolver.resolve(cache, ref)) {
        is sh.lsx.untisx.data.notebook.LessonResolution.Missing -> NotebookLessonWarning.MISSING
        is sh.lsx.untisx.data.notebook.LessonResolution.Ambiguous -> NotebookLessonWarning.AMBIGUOUS
        is sh.lsx.untisx.data.notebook.LessonResolution.Found -> null
    }
}

fun notebookNextLesson(
    cache: NotebookLessonCache,
    now: ZonedDateTime,
    subject: SubjectRef?,
    dueLesson: LessonRef?,
    origin: LessonRef?,
): LessonBlock? {
    val effectiveSubject = subject ?: dueLesson?.subject ?: origin?.subject
    val after = dueLesson?.key ?: origin?.key

    if (effectiveSubject == null && after == null) return null

    return NotebookLessonResolver.nextBlock(
        cache = cache,
        now = now,
        after = after,
        subject = effectiveSubject,
    )
}

fun notebookBadgeMap(
    entries: Iterable<TimetableEntry>,
    items: List<NotebookItem>,
): Map<LessonKey, NotebookLessonSummary> {
    val badges = LinkedHashMap<LessonKey, NotebookLessonSummary>()

    entries.forEach { entry ->
        if (entry.isEvent || entry.subject.isEmpty()) return@forEach

        val ref = notebookEntryRef(entry)
        val summary = NotebookSearch.summarize(items, ref)

        if (summary.badgeCount > 0) badges[ref.key] = summary
    }

    return badges
}

fun notebookPreviousDay(date: Int): Int =
    date.toUntisLocalDateOrNull()?.minusDays(1)?.toUntisDate() ?: date

fun notebookHasContent(title: String, body: String): Boolean =
    title.isNotBlank() || body.isNotBlank()

fun notebookEditorCanSave(state: NotebookEditorState.Ready): Boolean =
    !state.readOnly &&
        !state.saving &&
        notebookHasContent(state.draft.title, state.draft.body)

fun notebookEditorCanMutate(state: NotebookEditorState.Ready): Boolean =
    !state.readOnly && !state.saving

fun notebookHasUnsavedChanges(draft: NotebookDraft, baseline: NotebookItem?): Boolean {
    val normalized = NotebookValidator.normalizeDraft(draft)

    if (baseline == null) {
        return normalized.title.isNotBlank() ||
            normalized.body.isNotBlank() ||
            normalized.subject != null ||
            normalized.origin != null ||
            normalized.due != null
    }

    return normalized.kind != baseline.kind ||
        normalized.title != baseline.title ||
        normalized.body != baseline.body ||
        normalized.subject != baseline.subject ||
        normalized.origin != baseline.origin ||
        normalized.due != baseline.due
}

fun notebookItemContentChanged(left: NotebookItem, right: NotebookItem): Boolean =
    left.kind != right.kind ||
        left.title != right.title ||
        left.body != right.body ||
        left.subject != right.subject ||
        left.origin != right.origin ||
        left.due != right.due

fun notebookFindSavedItem(data: NotebookData, draft: NotebookDraft): NotebookItem? {
    draft.itemId?.let { id ->
        data.items.firstOrNull { it.id == id }?.let { return it }
    }

    val title = draft.title.trim()

    return data.items.firstOrNull { item ->
        item.kind == draft.kind &&
            item.title == title &&
            item.body == draft.body &&
            item.updatedAt == data.updatedAt
    }
}

@Composable
internal fun notebookDateText(date: Int): String {
    val formatter = rememberDateFormat("EEE, d. MMM")

    return remember(date, formatter) {
        date.toUntisLocalDateOrNull()?.let { formatter.format(it) }.orEmpty()
    }
}

@Composable
internal fun notebookTimeText(time: Int): String {
    val formatter = rememberTimeFormat()

    return remember(time, formatter) {
        runCatching { time.toUntisLocalTime().format(formatter) }.getOrDefault("")
    }
}

@Composable
internal fun notebookLessonTimeText(ref: LessonRef): String = stringResource(
    R.string.notebook_lesson_time_range,
    notebookDateText(ref.date),
    notebookTimeText(ref.startTime),
    notebookTimeText(ref.endTime),
)

@Composable
internal fun notebookReadFailureText(reason: NotebookReadFailure): String = stringResource(
    when (reason) {
        NotebookReadFailure.IO -> R.string.notebook_read_failure_io
        NotebookReadFailure.KEY_LOST -> R.string.notebook_read_failure_key_lost
        NotebookReadFailure.TRANSIENT -> R.string.notebook_read_failure_transient
        NotebookReadFailure.NOT_ENCRYPTED -> R.string.notebook_read_failure_not_encrypted
        NotebookReadFailure.CORRUPT -> R.string.notebook_read_failure_corrupt
        NotebookReadFailure.UNSUPPORTED_VERSION -> R.string.notebook_read_failure_version
    },
)

@Composable
internal fun notebookStorageFailureText(reason: NotebookStorageFailure): String = stringResource(
    when (reason) {
        NotebookStorageFailure.IO -> R.string.notebook_storage_failure_io
        NotebookStorageFailure.KEY_UNAVAILABLE -> R.string.notebook_storage_failure_key
    },
)

@Composable
internal fun notebookValidationText(issue: NotebookValidationIssue): String = when {
    issue.field == "text" -> stringResource(R.string.notebook_error_content)
    issue.field.startsWith("title") -> stringResource(R.string.notebook_error_title_too_long)
    issue.field.startsWith("body") -> stringResource(R.string.notebook_error_body_too_long)
    issue.field.startsWith("subject") -> stringResource(R.string.notebook_error_subject_too_long)
    else -> stringResource(R.string.notebook_error_generic)
}

@Composable
internal fun notebookNoticeText(notice: NotebookNotice): String = stringResource(
    when (notice) {
        NotebookNotice.SAVED -> R.string.notebook_saved
        NotebookNotice.DELETED -> R.string.notebook_deleted
        NotebookNotice.ITEM_MISSING -> R.string.notebook_item_missing
        NotebookNotice.READ_ONLY -> R.string.notebook_error_read_only
        NotebookNotice.SAVE_FAILED -> R.string.notebook_save_failed
        NotebookNotice.DELETE_FAILED -> R.string.notebook_delete_failed
        NotebookNotice.TOGGLE_FAILED -> R.string.notebook_toggle_failed
        NotebookNotice.CONVERSION_DONE -> R.string.notebook_conversion_done
        NotebookNotice.CONVERSION_FAILED -> R.string.notebook_conversion_failed
        NotebookNotice.IMPORT_APPLIED -> R.string.notebook_import_done
        NotebookNotice.IMPORT_EMPTY -> R.string.notebook_import_empty
        NotebookNotice.IMPORT_STALE -> R.string.notebook_import_stale
        NotebookNotice.IMPORT_FAILED -> R.string.notebook_import_failed
        NotebookNotice.IMPORT_READ_FAILED -> R.string.notebook_import_read_failed
        NotebookNotice.IMPORT_TOO_LARGE -> R.string.notebook_import_too_large
        NotebookNotice.EXPORT_DONE -> R.string.notebook_export_done
        NotebookNotice.EXPORT_FAILED -> R.string.notebook_export_failed
        NotebookNotice.EXPORT_EMPTY -> R.string.notebook_export_empty
        NotebookNotice.EXPORT_TOO_LARGE -> R.string.notebook_export_too_large
    },
)
