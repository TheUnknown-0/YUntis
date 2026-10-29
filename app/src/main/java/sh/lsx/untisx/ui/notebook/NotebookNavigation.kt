package sh.lsx.untisx.ui.notebook

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import sh.lsx.untisx.ui.nav.Screen

internal enum class NotebookLessonTarget { ORIGIN, DUE }

internal data class NotebookEditorSession(
    val returnScreen: Screen = Screen.NOTEBOOK,
    val pickerTarget: NotebookLessonTarget? = null,
) {
    fun openedFrom(screen: Screen): NotebookEditorSession = copy(
        returnScreen = notebookEditorReturnScreen(screen),
        pickerTarget = null,
    )

    fun picker(target: NotebookLessonTarget): NotebookEditorSession = copy(pickerTarget = target)

    fun pickerClosed(): NotebookEditorSession = copy(pickerTarget = null)
}

internal fun notebookEditorReturnScreen(from: Screen): Screen = when (from) {
    Screen.LESSON_DETAILS -> Screen.LESSON_DETAILS
    else -> Screen.NOTEBOOK
}

internal val NotebookEditorSessionSaver: Saver<NotebookEditorSession, Any> =
    listSaver<NotebookEditorSession, String>(
        save = { session ->
            listOf(
                session.returnScreen.name,
                session.pickerTarget?.name.orEmpty(),
            )
        },
        restore = { values ->
            val screen = values.getOrNull(0)
                ?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
                ?: Screen.NOTEBOOK
            val target = values.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { NotebookLessonTarget.valueOf(it) }.getOrNull() }

            NotebookEditorSession(returnScreen = screen, pickerTarget = target)
        },
    )
