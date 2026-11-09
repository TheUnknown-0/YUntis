package sh.lsx.untisx.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import sh.lsx.untisx.R

enum class SettingsCategory(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    GENERAL(R.string.settings_general, R.string.settings_general_desc, Icons.Outlined.Settings),
    APPEARANCE(R.string.settings_appearance, R.string.settings_appearance_desc, Icons.Outlined.Star),
    NOTIFICATIONS(
        R.string.settings_notifications,
        R.string.settings_notifications_desc,
        Icons.Outlined.Notifications,
    ),
    TIMETABLE(R.string.timetable, R.string.settings_timetable_desc, Icons.Outlined.DateRange),
    CALENDAR(R.string.settings_calendar, R.string.settings_calendar_desc, Icons.Outlined.Share),
}
