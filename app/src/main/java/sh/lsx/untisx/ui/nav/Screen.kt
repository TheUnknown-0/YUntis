package sh.lsx.untisx.ui.nav

internal enum class Screen {
    TIMETABLE,
    PROFILE,
    LESSON_DETAILS,
    EVENT_DETAILS,
    EVENTS,
    MESSAGES,
    MESSAGE_DETAILS,
    NOTEBOOK,
    NOTEBOOK_EDITOR,
    TEACHERS,
    ROOMS,
    STATS,
    SETTINGS,
    ABOUT,
}

internal fun Screen.depth(): Int = when (this) {
    Screen.TIMETABLE -> 0

    Screen.PROFILE,
    Screen.EVENTS,
    Screen.MESSAGES,
    Screen.NOTEBOOK,
    Screen.TEACHERS,
    Screen.ROOMS,
    Screen.STATS,
    Screen.SETTINGS,
    Screen.ABOUT,
    -> 1

    Screen.LESSON_DETAILS,
    Screen.EVENT_DETAILS,
    Screen.MESSAGE_DETAILS,
    Screen.NOTEBOOK_EDITOR,
    -> 2
}
