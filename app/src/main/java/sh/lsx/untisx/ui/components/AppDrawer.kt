package sh.lsx.untisx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.lsx.untisx.R
import sh.lsx.untisx.ui.nav.Screen
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
internal fun AppDrawer(
    drawerState: DrawerState,
    open: Boolean,
    messagesBadge: String?,
    eventsBadge: String?,
    notebookBadge: String?,
    homeworkBadge: String? = null,
    teachersBadge: String,
    statsBadge: String?,
    onSelect: (Screen) -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun close() = scope.launch { drawerState.close() }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.85f),
    ) {
        // the list is taller than small screens, so it scrolls
        Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = stringResource(R.string.untisx_logo),
                modifier = Modifier.size(40.dp),
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "YUntis",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .tapHaptic { close() }
                    .padding(2.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        SidebarSection(stringResource(R.string.nav_information))

        SidebarItem(
            icon = Icons.Outlined.Email,
            label = stringResource(R.string.messages),
            badge = messagesBadge,
            visible = open,
            index = 0,
            onClick = {
                close()
                onSelect(Screen.MESSAGES)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.DateRange,
            label = stringResource(R.string.events),
            badge = eventsBadge,
            visible = open,
            index = 1,
            onClick = {
                close()
                onSelect(Screen.EVENTS)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.Star,
            label = stringResource(R.string.exam_plan_title),
            visible = open,
            index = 1,
            onClick = {
                close()
                onSelect(Screen.EXAMS)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.CheckCircle,
            label = stringResource(R.string.homework_title),
            badge = homeworkBadge,
            visible = open,
            index = 2,
            onClick = {
                close()
                onSelect(Screen.HOMEWORK)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.notebook_title),
            badge = notebookBadge,
            visible = open,
            index = 3,
            onClick = {
                close()
                onSelect(Screen.NOTEBOOK)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.Person,
            label = stringResource(R.string.teachers),
            badge = teachersBadge,
            visible = open,
            index = 4,
            onClick = {
                close()
                onSelect(Screen.TEACHERS)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.Warning,
            label = stringResource(R.string.absences_title),
            visible = open,
            index = 5,
            onClick = {
                close()
                onSelect(Screen.ABSENCES)
            },
        )

        SidebarItem(
            icon = Icons.AutoMirrored.Outlined.List,
            label = stringResource(R.string.stats),
            badge = statsBadge,
            visible = open,
            index = 6,
            onClick = {
                close()
                onSelect(Screen.STATS)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.Refresh,
            label = stringResource(R.string.history_title),
            visible = open,
            index = 7,
            onClick = {
                close()
                onSelect(Screen.CHANGES)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.PlayArrow,
            label = stringResource(R.string.dino_title),
            visible = open,
            index = 8,
            onClick = {
                close()
                onSelect(Screen.DINO)
            },
        )

        Spacer(Modifier.height(16.dp))
        SidebarSection(stringResource(R.string.nav_other))

        SidebarItem(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.settings),
            visible = open,
            index = 8,
            onClick = {
                close()
                onSelect(Screen.SETTINGS)
            },
        )

        SidebarItem(
            icon = Icons.Outlined.Info,
            label = stringResource(R.string.about),
            visible = open,
            index = 9,
            onClick = {
                close()
                onSelect(Screen.ABOUT)
            },
        )
            }
    }
}

@Composable
private fun SidebarSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    visible: Boolean = true,
    index: Int = 0,
    onClick: () -> Unit,
) {
    val animations = LocalAnimationsEnabled.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = if (animations) {
                tween(durationMillis = 180, delayMillis = index * 35)
            } else {
                snap()
            },
        ) + slideInHorizontally(
            animationSpec = if (animations) {
                tween(durationMillis = 220, delayMillis = index * 35)
            } else {
                snap()
            },
        ) { width -> -width / 4 },
        exit = fadeOut(
            animationSpec = if (animations) tween(durationMillis = 90) else snap(),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .tapHaptic(onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(20.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
