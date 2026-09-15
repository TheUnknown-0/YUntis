package sh.lsx.untisx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.ui.components.SettingsSegmentedRow

@Composable
fun SettingsOverviewScreen(
    onOpenCategory: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = SettingsCategory.entries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
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
    }
}
