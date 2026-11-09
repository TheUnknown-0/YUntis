package sh.lsx.untisx.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.components.SettingsFocus
import sh.lsx.untisx.ui.components.SettingsSegmentedRow
import sh.lsx.untisx.ui.util.tapHaptic
import java.text.Normalizer

/** One findable setting: its title (and description) and the page it is on. */
internal data class SettingsSearchEntry(
    val category: SettingsCategory,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int = 0,
)

internal object SettingsSearch {
    /** Lower case, accents removed, so "uberschrift" also finds "Überschrift". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace("ß", "ss")

    /** Entries whose title, description or page contain every word of [query]; title hits first. */
    fun <T> search(query: String, entries: List<T>, title: (T) -> String, rest: (T) -> String): List<T> {
        val words = normalize(query).split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        return entries
            .map { entry -> entry to normalize(title(entry)) }
            .filter { (entry, t) -> val all = t + " " + normalize(rest(entry)); words.all { it in all } }
            .sortedBy { (_, t) -> if (words.all { it in t }) 0 else 1 }
            .map { it.first }
    }
}

@Composable
fun SettingsOverviewScreen(
    onOpenCategory: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val categories = SettingsCategory.entries
    var query by rememberSaveable { mutableStateOf("") }
    // back on the overview: a jump that found no row must not light one up later
    androidx.compose.runtime.LaunchedEffect(Unit) { SettingsFocus.target = null }

    // the texts in the app's current language, once
    val resolved = remember(resources, configuration) {
        settingsSearchIndex.map { entry ->
            Resolved(
                entry = entry,
                title = resources.getString(entry.title).trim(),
                description = if (entry.description != 0) resources.getString(entry.description) else "",
                page = resources.getString(entry.category.titleRes),
            )
        }.distinctBy { it.entry.category to it.title }
    }
    val results = remember(query, resolved) {
        SettingsSearch.search(query, resolved, { it.title }, { it.description + " " + it.page })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.settings_search_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.size(12.dp))

        if (query.isBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                categories.forEachIndexed { index, category ->
                    SettingsSegmentedRow(
                        icon = category.icon,
                        title = stringResource(category.titleRes),
                        description = stringResource(category.descriptionRes),
                        index = index,
                        count = categories.size,
                        onClick = { onOpenCategory(category) },
                    )
                }
            }
        } else if (results.isEmpty()) {
            Text(
                stringResource(R.string.settings_search_none, query.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                results.forEach { hit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .tapHaptic {
                                SettingsFocus.target = hit.title
                                onOpenCategory(hit.entry.category)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            hit.entry.category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hit.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOf(hit.page, hit.description).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Resolved(
    val entry: SettingsSearchEntry,
    val title: String,
    val description: String,
    val page: String,
)
