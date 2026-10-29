package sh.lsx.untisx.ui.notebook

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notebook.NotebookItem

@RunWith(AndroidJUnit4::class)
class NotebookPinStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun notePinToggleExposesBothCheckedStatesAndLocalizedActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        compose.setContent {
            var pinned by remember { mutableStateOf(false) }

            MaterialTheme {
                NotebookItemRow(
                    item = NotebookItem(id = "note", title = "Note", pinnedAt = if (pinned) 1L else null),
                    today = LocalDate.now(), onClick = {}, onToggleCompleted = {},
                    onTogglePinned = { pinned = !pinned }, onArchive = {}, onDelete = {},
                    onConvert = {}, onJumpToDate = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.notebook_pin))
            .assertIsOff().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.notebook_unpin))
            .assertIsOn().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.notebook_pin)).assertIsOff()
    }

    @Test fun unpinnedStarHasAnActualTransparentCenterAndVisibleRim() {
        compose.setContent {
            Column {
                Icon(
                    imageVector = notebookPinIcon(true), contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(48.dp).background(Color.Magenta).testTag("filled-star"),
                )

                Icon(
                    imageVector = notebookPinIcon(false), contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(48.dp).background(Color.Magenta).testTag("hollow-star"),
                )
            }
        }

        val filled = compose.onNodeWithTag("filled-star").captureToImage().toPixelMap()
        val hollow = compose.onNodeWithTag("hollow-star").captureToImage().toPixelMap()
        assertEquals(Color.White, filled[filled.width / 2, filled.height / 2])
        assertEquals(Color.Magenta, hollow[hollow.width / 2, hollow.height / 2])
        assertEquals(Color.White, hollow[hollow.width / 2, hollow.height / 6])
    }
}
