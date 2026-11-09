package sh.lsx.untisx.ui.nav

internal enum class Screen {
    TIMETABLE,
    PROFILE,
    LESSON_DETAILS,
    TEACHER_INFO,
    SUBJECT_YEAR,
    RIGHTS,
    EVENT_DETAILS,
    EVENTS,
    EXAMS,
    MESSAGES,
    MESSAGE_DETAILS,
    NOTEBOOK,
    NOTEBOOK_EDITOR,
    HOMEWORK,
    ABSENCES,
    TEACHERS,
    ROOMS,
    STATS,
    CHANGES,
    SEARCH,
    DINO,
    SETTINGS,
    ABOUT,
}

internal fun Screen.depth(): Int = when (this) {
    Screen.TIMETABLE -> 0

    Screen.PROFILE,
    Screen.EVENTS,
    Screen.EXAMS,
    Screen.MESSAGES,
    Screen.NOTEBOOK,
    Screen.HOMEWORK,
    Screen.ABSENCES,
    Screen.TEACHERS,
    Screen.ROOMS,
    Screen.STATS,
    Screen.CHANGES,
    Screen.SEARCH,
    Screen.DINO,
    Screen.SETTINGS,
    Screen.ABOUT,
    -> 1

    Screen.LESSON_DETAILS,
    Screen.EVENT_DETAILS,
    Screen.MESSAGE_DETAILS,
    Screen.NOTEBOOK_EDITOR,
    Screen.SUBJECT_YEAR,
    Screen.RIGHTS,
    -> 2

    Screen.TEACHER_INFO -> 3
}
