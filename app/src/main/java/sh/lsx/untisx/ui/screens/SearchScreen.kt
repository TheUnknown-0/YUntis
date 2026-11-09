package sh.lsx.untisx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.ui.theme.AppCard
import sh.lsx.untisx.ui.theme.appCardShape
import sh.lsx.untisx.ui.util.rememberDateFormat
import sh.lsx.untisx.ui.util.tapHaptic
import java.text.Normalizer

/** Where a search hit leads. */
internal sealed interface SearchTarget {
    data class Subject(val id: Int) : SearchTarget
    data class Teacher(val id: Int) : SearchTarget
    data object Rooms : SearchTarget
    data class Note(val itemId: String) : SearchTarget
    data class Date(val date: Int) : SearchTarget
    data class Event(val entry: TimetableEntry) : SearchTarget
    data class Lesson(val entry: TimetableEntry) : SearchTarget
    data class Message(val message: DirectMessage) : SearchTarget
}

internal enum class SearchKind(val titleRes: Int) {
    SUBJECT(R.string.search_kind_subjects),
    TEACHER(R.string.search_kind_teachers),
    ROOM(R.string.search_kind_rooms),
    EXAM(R.string.search_kind_exams),
    EVENT(R.string.search_kind_events),
    NOTE(R.string.search_kind_notes),
    MESSAGE(R.string.search_kind_messages),
    LESSON(R.string.search_kind_lessons),
}

internal data class SearchHit(val kind: SearchKind, val title: String, val subtitle: String, val target: SearchTarget, val date: Int = 0)

internal data class SearchData(
    val subjects: Map<Int, MasterDataItem> = emptyMap(),
    val teachers: Map<Int, MasterDataItem> = emptyMap(),
    val rooms: Map<Int, MasterDataItem> = emptyMap(),
    val exams: List<ExamInfo> = emptyList(),
    val events: List<TimetableEntry> = emptyList(),
    val notebook: List<NotebookItem> = emptyList(),
    val messages: List<DirectMessage> = emptyList(),
    val lessons: List<TimetableEntry> = emptyList(),
)

internal object Search {
    /** Lower case without accents, so "muller" finds "Müller". */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace("ß", "ss")

    fun run(query: String, data: SearchData, limitPerKind: Int = 8): List<SearchHit> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        fun matches(vararg fields: String?): Boolean {
            val haystack = fold(fields.filterNotNull().joinToString(" "))
            return words.all { it in haystack }
        }

        fun subjectName(id: Int) = data.subjects[id]?.let { it.longName.ifBlank { it.name } }.orEmpty()

        val hits = ArrayList<SearchHit>()

        data.subjects.values.filter { matches(it.name, it.longName) }.sortedBy { it.name }.take(limitPerKind).forEach {
            hits += SearchHit(SearchKind.SUBJECT, it.longName.ifBlank { it.name }, it.name, SearchTarget.Subject(it.id))
        }
        data.teachers.values.filter { matches(it.name, it.longName, it.foreName) }.sortedBy { it.longName }.take(limitPerKind).forEach {
            hits += SearchHit(SearchKind.TEACHER, listOf(it.foreName, it.longName).filter(String::isNotBlank).joinToString(" ").ifBlank { it.name }, it.name, SearchTarget.Teacher(it.id))
        }
        data.rooms.values.filter { matches(it.name, it.longName) }.sortedBy { it.name }.take(limitPerKind).forEach {
            hits += SearchHit(SearchKind.ROOM, it.name, it.longName, SearchTarget.Rooms)
        }
        data.exams.filter { matches(it.examType, it.name, it.text, it.subjectName, subjectName(it.subjectId)) }
            .sortedByDescending { it.date }.take(limitPerKind).forEach {
                hits += SearchHit(
                    SearchKind.EXAM,
                    listOf(it.examType, subjectName(it.subjectId).ifBlank { it.subjectName }).filter(String::isNotBlank).joinToString(" "),
                    listOf(it.name, it.text).filter(String::isNotBlank).joinToString(" · "),
                    SearchTarget.Date(it.date),
                    it.date,
                )
            }
        data.events.filter { it.isEvent && matches(it.eventTitle, it.info, it.substText) }
            .distinctBy { it.date to it.eventTitle }.sortedByDescending { it.date }.take(limitPerKind).forEach {
                hits += SearchHit(SearchKind.EVENT, it.eventTitle.orEmpty(), it.info.orEmpty(), SearchTarget.Event(it), it.date)
            }
        data.notebook.filter { matches(it.title, it.body, it.subject?.displayName) }
            .sortedByDescending { it.updatedAt }.take(limitPerKind).forEach {
                hits += SearchHit(SearchKind.NOTE, it.displayTitle, it.subject?.displayName.orEmpty(), SearchTarget.Note(it.id), it.due?.date ?: 0)
            }
        data.messages.filter { matches(it.subject, it.content, it.contentPreview, it.sender?.displayName) }
            .take(limitPerKind).forEach {
                hits += SearchHit(SearchKind.MESSAGE, it.subject, it.sender?.displayName.orEmpty(), SearchTarget.Message(it))
            }
        data.lessons.filter { it.subject.isNotEmpty() && matches(it.lstext, it.info, it.substText, it.lessonText) }
            .distinctBy { Triple(it.date, it.startTime, it.subject.first().id) }
            .sortedByDescending { it.date }.take(limitPerKind).forEach {
                val text = listOfNotNull(it.lstext, it.info, it.substText, it.lessonText).filter(String::isNotBlank).distinct().joinToString(" · ")
                hits += SearchHit(SearchKind.LESSON, subjectName(it.subject.first().id).ifBlank { it.subject.first().name }, text, SearchTarget.Lesson(it), it.date)
            }

        return hits
    }
}

@Composable
internal fun SearchScreen(
    data: SearchData,
    onOpen: (SearchTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val hits = remember(query, data) { Search.run(query, data) }
    val dateFormat = rememberDateFormat("EEEdMMMyyyy")

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }

        if (query.isNotBlank() && hits.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        hits.groupBy { it.kind }.forEach { (kind, list) ->
            item(key = "k${kind.name}") {
                Text(
                    stringResource(kind.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(list) { hit ->
                AppCard(
                    modifier = Modifier.fillMaxWidth().tapHaptic { onOpen(hit.target) },
                    shape = appCardShape(12.dp),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(hit.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            hit.date.takeIf { it > 0 }?.toUntisLocalDateOrNull()?.let(dateFormat::format),
                            hit.subtitle.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
