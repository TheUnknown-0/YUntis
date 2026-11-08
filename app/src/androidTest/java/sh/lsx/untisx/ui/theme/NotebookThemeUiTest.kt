package sh.lsx.untisx.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.data.AppTheme
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.ui.screens.settings.AppearanceSettingsScreen
import sh.lsx.untisx.ui.timetable.LessonCard
import sh.lsx.untisx.ui.timetable.LessonDisplay
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.LocalHapticsEnabled

@RunWith(AndroidJUnit4::class)
class NotebookThemeUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeSelectionSwitchesBothWaysWithoutChangingAccentOrMode() {
        val theme = mutableStateOf(AppTheme(mode = ThemeMode.LIGHT, preset = ThemePreset.BLUE))
        var contentMounts = 0

        compose.setContent {
            CompositionLocalProvider(LocalAnimationsEnabled provides false, LocalHapticsEnabled provides false) {
                UntisXTheme(mode = theme.value.mode, preset = theme.value.preset, style = theme.value.style) {
                    remember { contentMounts++ }
                    SettingsPage(theme.value, { theme.value = it })
                }
            }
        }

        compose.onNodeWithText("Default").assertIsSelected()
        compose.onNodeWithText("Notebook").performClick()
        compose.onNodeWithText("Notebook").assertIsSelected()

        compose.runOnIdle {
            assertEquals(ThemePreset.BLUE, theme.value.preset)
            assertEquals(ThemeMode.LIGHT, theme.value.mode)
        }

        compose.onNodeWithText("Default").performClick()
        compose.onNodeWithText("Default").assertIsSelected()

        compose.runOnIdle {
            assertEquals(ThemeStyle.DEFAULT, theme.value.style)
            assertEquals("Switching styles must not recreate screen state", 1, contentMounts)
        }
    }

    @Test
    fun darkNotebookSettingsSupportLargerType() {
        compose.setContent {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                UntisXTheme(mode = ThemeMode.DARK, style = ThemeStyle.NOTEBOOK) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                        SettingsPage(AppTheme(mode = ThemeMode.DARK, style = ThemeStyle.NOTEBOOK), {})
                    }
                }
            }
        }

        compose.onNodeWithText("Notebook").assertIsSelected()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(sh.lsx.untisx.R.string.settings_animations))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lightCompactLessonsFitLargerType() = lessonSamples(ThemeMode.LIGHT)

    @Test
    fun darkCompactLessonsFitLargerType() = lessonSamples(ThemeMode.DARK)

    private fun lessonSamples(mode: ThemeMode) {
        compose.setContent {
            val density = LocalDensity.current

            CompositionLocalProvider(
                LocalAnimationsEnabled provides false,
                LocalDensity provides Density(density.density, 1.5f),
            ) {
                UntisXTheme(mode = mode, style = ThemeStyle.NOTEBOOK) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (height in listOf(54.dp, 104.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (width in listOf(48.dp, 72.dp, 120.dp)) {
                                    LessonCard(
                                        lesson = LessonDisplay(
                                            entry = TimetableEntry(id = width.value.toInt()),
                                            subject = "MA_G2",
                                            rooms = "R. 205",
                                            teachers = "Müller",
                                        ),
                                        color = Color(0xFF558FE0),
                                        textColor = Color.Black,
                                        sharedElement = false,
                                        modifier = Modifier.size(width, height),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val rooms = compose.onAllNodesWithText("R. 205", useUnmergedTree = true)
        val roomCount = rooms.fetchSemanticsNodes().size

        assertEquals(6, roomCount)

        repeat(roomCount) { index ->
            rooms[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                action(layouts)

                assertTrue(layouts.isNotEmpty())
                layouts.forEach {
                    assertFalse(
                        "Room $index must fit: size=${it.size}, naturalWidth=${it.multiParagraph.maxIntrinsicWidth}, " +
                            "fontSize=${it.layoutInput.style.fontSize}, widthOverflow=${it.didOverflowWidth}, " +
                            "heightOverflow=${it.didOverflowHeight}",
                        it.hasVisualOverflow,
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingsPage(theme: AppTheme, onChange: (AppTheme) -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxSize().appBackground(),
            containerColor = appScreenContainerColor(),
        ) { insets ->
            AppearanceSettingsScreen(
                theme = theme,
                onThemeChange = onChange,
                animationsEnabled = false,
                onAnimationsChange = {},
                modifier = Modifier.padding(insets),
            )
        }
    }
}
