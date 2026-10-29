package sh.lsx.untisx.ui.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.lsx.untisx.ui.nav.Screen

class NotebookNavigationTest {

    @Test
    fun `notebook-created editors return to the notebook`() {
        assertEquals(
            Screen.NOTEBOOK,
            notebookEditorReturnScreen(Screen.NOTEBOOK),
        )
    }

    @Test
    fun `lesson-created editors return to the same lesson details`() {
        assertEquals(
            Screen.LESSON_DETAILS,
            notebookEditorReturnScreen(Screen.LESSON_DETAILS),
        )
    }

    @Test
    fun `opening and cancelling an intermediate picker keeps the return destination`() {
        val session = NotebookEditorSession().openedFrom(Screen.LESSON_DETAILS)

        val pickingSource = session.picker(NotebookLessonTarget.ORIGIN)
        assertEquals(Screen.LESSON_DETAILS, pickingSource.returnScreen)
        assertEquals(NotebookLessonTarget.ORIGIN, pickingSource.pickerTarget)

        val cancelled = pickingSource.pickerClosed()
        assertEquals(Screen.LESSON_DETAILS, cancelled.returnScreen)
        assertNull(cancelled.pickerTarget)

        val pickingDue = session.picker(NotebookLessonTarget.DUE)
        assertEquals(Screen.LESSON_DETAILS, pickingDue.returnScreen)
        assertEquals(NotebookLessonTarget.DUE, pickingDue.pickerTarget)

        val completed = pickingDue.pickerClosed()
        assertEquals(Screen.LESSON_DETAILS, completed.returnScreen)
        assertNull(completed.pickerTarget)
    }

    @Test
    fun `reopening from the notebook replaces a previous lesson return destination`() {
        val session = NotebookEditorSession()
            .openedFrom(Screen.LESSON_DETAILS)
            .picker(NotebookLessonTarget.DUE)
            .openedFrom(Screen.NOTEBOOK)

        assertEquals(Screen.NOTEBOOK, session.returnScreen)
        assertNull(session.pickerTarget)
    }
}
