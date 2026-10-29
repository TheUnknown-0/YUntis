package sh.lsx.untisx.ui.notebook

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notebook.DueRef
import sh.lsx.untisx.data.notebook.LessonRef
import sh.lsx.untisx.data.notebook.NotebookItem
import sh.lsx.untisx.data.notebook.NotebookKind
import sh.lsx.untisx.data.notebook.NotebookLessonLinks

@RunWith(AndroidJUnit4::class)
class NotebookLessonEntriesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lessonLinksNavigateToOppositeDays() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val origin = LessonRef(entryId = 1, date = 20261104, startTime = 800, endTime = 845)
        val target = origin.copy(entryId = 2, date = 20261111)
        val task = NotebookItem(
            id = "due-task", title = "Linked task", kind = NotebookKind.TASK,
            origin = origin, due = DueRef(date = target.date, lesson = target),
        )
        var jumped = 0
        compose.setContent {
            MaterialTheme {
                NotebookLessonSection(
                    links = NotebookLessonLinks(
                        sourced = listOf(task.copy(id = "source-task")),
                        due = listOf(task),
                    ),
                    today = LocalDate.of(2026, 11, 11),
                    onItemClick = {}, onCreateNote = {}, onCreateTask = {},
                    onJumpToDate = { jumped = it },
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(
            R.string.notebook_show_origin_in_timetable, notebookDateText(origin.date),
        )).performClick()
        compose.runOnIdle { assertEquals(origin.date, jumped) }
        compose.onNodeWithContentDescription(context.getString(
            R.string.notebook_show_due_in_timetable, notebookDateText(target.date),
        )).performClick()
        compose.runOnIdle { assertEquals(target.date, jumped) }
    }

    @Test fun dueMetadataDoesNotCreateAnExtraTouchRowAndActionsStayCloseToEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val note = NotebookItem(id = "note", title = "Short note")
        val task = NotebookItem(id = "task", title = "Short task", kind = NotebookKind.TASK,
            due = DueRef(20261111))
        var opened = ""
        var jumped = 0
        var dueAction = ""

        compose.setContent {
            MaterialTheme {
                dueAction = stringResource(R.string.notebook_show_due_in_timetable, notebookDateText(20261111))
                NotebookLessonSection(
                    links = NotebookLessonLinks(sourced = listOf(note, task)),
                    today = LocalDate.of(2026, 11, 11),
                    onItemClick = { opened = it }, onCreateNote = {}, onCreateTask = {},
                    onJumpToDate = { jumped = it },
                )
            }
        }

        val noteNode = compose.onNodeWithText(note.title).assertHasClickAction()
        val taskNode = compose.onNodeWithText(task.title).assertHasClickAction()
        val noteBounds = noteNode.getUnclippedBoundsInRoot()
        val taskBounds = taskNode.getUnclippedBoundsInRoot()
        val noteHeight = noteBounds.bottom - noteBounds.top
        val taskHeight = taskBounds.bottom - taskBounds.top

        assertTrue(noteHeight >= 48.dp)
        assertTrue(taskHeight - noteHeight < 32.dp)

        val actionBounds = compose.onNodeWithText(context.getString(R.string.notebook_create_note_for_lesson))
            .getUnclippedBoundsInRoot()

        assertTrue(actionBounds.top - taskBounds.bottom <= 12.dp)
        taskNode.performClick()
        compose.onNodeWithContentDescription(dueAction).performClick()
        compose.runOnIdle {
            assertEquals(task.id, opened)
            assertEquals(20261111, jumped)
        }
    }
}
