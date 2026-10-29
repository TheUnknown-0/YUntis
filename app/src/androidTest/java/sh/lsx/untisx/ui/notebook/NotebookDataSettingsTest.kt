package sh.lsx.untisx.ui.notebook

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.screens.settings.GeneralSettingsScreen

@RunWith(AndroidJUnit4::class)
class NotebookDataSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun generalShowsSeparateSettingsBackupAndDataActionsWithPrivacyHint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var exports = 0
        var imports = 0

        compose.setContent {
            MaterialTheme {
                GeneralSettingsScreen(
                    hapticsEnabled = false, onHapticsChange = {},
                    showLastRefreshed = false, onShowLastRefreshedChange = {},
                    dataTransferContent = {
                        NotebookDataTransferRows(onExport = { exports++ }, onImport = { imports++ })
                    },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_backup)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.notebook_data_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.notebook_data_description)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.notebook_export)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.notebook_import)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, exports)
            assertEquals(1, imports)
        }
    }
}
