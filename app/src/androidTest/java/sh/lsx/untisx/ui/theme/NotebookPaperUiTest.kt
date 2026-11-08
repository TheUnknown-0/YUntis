package sh.lsx.untisx.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.data.model.PeriodExam
import sh.lsx.untisx.data.model.TimetableElement
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.ui.screens.LessonDetailsScreen
import sh.lsx.untisx.ui.timetable.EventSpan
import sh.lsx.untisx.ui.timetable.LessonCard
import sh.lsx.untisx.ui.timetable.LessonDisplay
import sh.lsx.untisx.ui.timetable.TimetableEventOverlay
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.LocalHapticsEnabled

@RunWith(AndroidJUnit4::class)
class NotebookPaperUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cancelledPaperActuallyRemovesLetteringAndRecompositionDoesNotMoveTear() {
        val cancelled = mutableStateOf(true)
        val revision = mutableStateOf(0)
        val backing = Color(0xFF172F40)

        compose.setContent {
            TestTheme(ThemeMode.LIGHT) {
                Column {
                    Text("Revision ${revision.value}")

                    Box(Modifier.size(180.dp, 120.dp).background(backing).testTag("note")) {
                        SampleLesson(7, cancelled.value, Modifier.fillMaxSize())
                    }
                }
            }
        }

        compose.onNodeWithText("Mathematik").assertExists()

        val torn = capture("note")
        val backgroundPixel = backing.toArgb()

        for (fraction in listOf(0.15f, 0.5f, 0.85f)) {
            assertEquals(backgroundPixel, torn.getPixel((torn.width * fraction).toInt(), (torn.height * 0.90f).toInt()))
        }

        assertFalse(backgroundPixel == torn.getPixel(torn.width / 2, torn.height / 5))
        compose.runOnIdle { revision.value++ }
        assertTrue("Tear must not flicker on recomposition", torn.sameAs(capture("note")))
        compose.runOnIdle { cancelled.value = false }
        val intact = capture("note")
        assertFalse("Normal lesson retains its lower half", backgroundPixel == intact.getPixel(intact.width / 2, (intact.height * 0.90f).toInt()))
    }

    @Test
    fun disablingCancellationDecorationKeepsTheWholeNote() {
        val backing = Color(0xFF172F40)

        compose.setContent {
            TestTheme(ThemeMode.DARK) {
                Box(Modifier.size(180.dp, 120.dp).background(backing).testTag("note")) {
                    LessonCard(
                        lesson = LessonDisplay(
                            entry = TimetableEntry(id = 7, code = "cancelled"),
                            subject = "Mathematik",
                            rooms = "R. 205",
                            teachers = "Müller",
                        ),
                        color = Color(0xFF81B8E0),
                        textColor = Color.Black,
                        strike = false,
                        sharedElement = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val image = capture("note")

        assertFalse(backing.toArgb() == image.getPixel(image.width / 2, (image.height * 0.9f).toInt()))
    }

    @Test
    fun lightCardsAndEventsRemainInteractive() = materialSamples(ThemeMode.LIGHT)

    @Test
    fun darkCardsAndEventsRemainInteractive() = materialSamples(ThemeMode.DARK)

    @Test
    fun lightLessonDetailsShowExam() = detailSample(ThemeMode.LIGHT)

    @Test
    fun darkLessonDetailsShowExam() = detailSample(ThemeMode.DARK)

    @Test
    fun defaultEventsKeepTheirVisibleTitle() {
        compose.setContent {
            UntisXTheme(style = ThemeStyle.DEFAULT) {
                TimetableEventOverlay(
                    span = EventSpan(900, 1200, "Projekttag", TimetableEntry(id = 70)),
                    height = 120.dp,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        compose.onNodeWithText("Projekttag").assertIsDisplayed()
    }

    private fun materialSamples(mode: ThemeMode) {
        var eventClicks = 0
        var cardClicks = 0

        compose.setContent {
            TestTheme(mode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppCard(
                        onClick = { cardClicks++ },
                        modifier = Modifier.testTag("paper-card"),
                    ) {
                        Text("Notizen", Modifier.padding(18.dp))
                    }

                    TimetableEventOverlay(
                        span = EventSpan(900, 1200, "Projekttag", TimetableEntry(id = 70)),
                        height = 120.dp,
                        onClick = { eventClicks++ },
                        modifier = Modifier.fillMaxWidth().testTag("event"),
                    )
                }
            }
        }

        compose.onNodeWithTag("paper-card").performClick()
        compose.onNodeWithText("Projekttag").assertDoesNotExist()
        compose.onNodeWithTag("event").assertContentDescriptionEquals("Projekttag")
        compose.onNodeWithTag("event").performClick()

        compose.runOnIdle {
            assertEquals(1, cardClicks)
            assertEquals(1, eventClicks)
        }
    }

    private fun detailSample(mode: ThemeMode) {
        compose.setContent {
            TestTheme(mode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().appBackground(),
                    containerColor = appScreenContainerColor(),
                ) { insets ->
                    LessonDetailsScreen(
                        entry = TimetableEntry(
                            id = 55,
                            date = 20261109,
                            startTime = 800,
                            endTime = 845,
                            subject = listOf(TimetableElement(id = 1, name = "Mathematik")),
                            exam = PeriodExam(examtype = "Exam", name = "Analysis"),
                        ),
                        subjectsById = emptyMap(),
                        teachersById = emptyMap(),
                        roomsById = emptyMap(),
                        color = Color(0xFF81B8E0),
                        textColor = Color.Black,
                        modifier = Modifier.padding(insets),
                    )
                }
            }
        }

        compose.onNodeWithText("Mathematik").assertIsDisplayed()
        compose.onAllNodesWithText("Exam")[0].assertIsDisplayed()
    }

    @Composable
    private fun SampleLesson(id: Int, cancelled: Boolean, modifier: Modifier) {
        LessonCard(
            lesson = LessonDisplay(
                entry = TimetableEntry(id = id, date = 20261109, startTime = 800, code = if (cancelled) "cancelled" else null),
                subject = "Mathematik",
                rooms = "R. 205",
                teachers = "Müller",
            ),
            color = listOf(Color(0xFF81B8E0), Color(0xFFEF8A91), Color(0xFF70C6A5), Color(0xFFD999D9))[id % 4],
            textColor = Color.Black,
            sharedElement = false,
            modifier = modifier,
        )
    }

    @Composable
    private fun TestTheme(mode: ThemeMode, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAnimationsEnabled provides false, LocalHapticsEnabled provides false) {
            UntisXTheme(mode = mode, style = ThemeStyle.NOTEBOOK, content = content)
        }
    }

    private fun capture(tag: String) = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
}
