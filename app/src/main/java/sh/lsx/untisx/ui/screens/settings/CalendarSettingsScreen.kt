package sh.lsx.untisx.ui.screens.settings

import android.Manifest
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.lsx.untisx.R
import sh.lsx.untisx.data.ExamsStore
import sh.lsx.untisx.data.OwnExamsStore
import sh.lsx.untisx.data.calendar.CalendarCategory
import sh.lsx.untisx.data.calendar.CalendarExportPrefs
import sh.lsx.untisx.data.calendar.CalendarExportWorker
import sh.lsx.untisx.data.calendar.CalendarItems
import sh.lsx.untisx.data.calendar.CalendarSync
import sh.lsx.untisx.data.calendar.DeviceCalendar
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.ui.components.SettingsSectionCard
import sh.lsx.untisx.ui.components.SettingsSwitchRow
import sh.lsx.untisx.ui.util.Haptics
import java.text.DateFormat
import java.util.Date

@Composable
fun CalendarSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(CalendarExportPrefs.isEnabled(context)) }
    var target by remember { mutableLongStateOf(CalendarExportPrefs.target(context)) }
    var categories by remember { mutableStateOf(CalendarExportPrefs.categories(context)) }
    var excludedTypes by remember { mutableStateOf(CalendarExportPrefs.excludedExamTypes(context)) }
    var interval by remember { mutableIntStateOf(CalendarExportPrefs.intervalHours(context)) }
    var lastRun by remember { mutableLongStateOf(CalendarExportPrefs.lastRun(context)) }
    var lastResult by remember { mutableStateOf(CalendarExportPrefs.lastResult(context)) }
    var running by remember { mutableStateOf(CalendarExportPrefs.isRunning(context)) }
    var permitted by remember { mutableStateOf(CalendarSync.hasPermission(context)) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    var examTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var accountToggle by remember { mutableIntStateOf(0) }
    var removedCount by remember { mutableStateOf<Int?>(null) }

    val accounts by AccountStore.accounts.collectAsStateWithLifecycle()

    fun reschedule() = CalendarExportWorker.ensure(context)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = CalendarSync.hasPermission(context)
        if (permitted) {
            calendars = CalendarSync.writableCalendars(context)
            if (enabled) CalendarExportWorker.runNow(context)
        }
    }

    fun requestPermission() = permissionLauncher.launch(
        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
    )

    LifecycleResumeEffect(Unit) {
        permitted = CalendarSync.hasPermission(context)
        if (permitted) calendars = CalendarSync.writableCalendars(context)
        onPauseOrDispose { }
    }

    DisposableEffect(Unit) {
        val prefs = CalendarExportPrefs.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            lastRun = CalendarExportPrefs.lastRun(context)
            lastResult = CalendarExportPrefs.lastResult(context)
            running = CalendarExportPrefs.isRunning(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(accounts, lastRun) {
        // exam types the school (or the user) used so far, so each can be switched off
        examTypes = withContext(Dispatchers.IO) {
            accounts.flatMap { auth ->
                val id = AccountPrefs.accountId(auth)
                ExamsStore.load(context, id)?.exams.orEmpty() + OwnExamsStore.load(context, id)
            }
                .map { it.examType.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { CalendarItems.examTypeKey(it) }
                .sortedBy { it.lowercase() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionCard(title = stringResource(R.string.calendar_export)) {
            SettingsSwitchRow(
                title = stringResource(R.string.calendar_export_enable),
                description = stringResource(R.string.calendar_export_enable_desc),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    CalendarExportPrefs.setEnabled(context, it)
                    reschedule()
                    if (it && !permitted) requestPermission()
                    if (it && permitted) CalendarExportWorker.runNow(context)
                },
            )

            if (enabled && !permitted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.calendar_permission_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = Haptics.haptic { requestPermission() }) {
                    Text(stringResource(R.string.calendar_permission_grant))
                }
            }
        }

        if (!enabled) return@Column

        SettingsSectionCard(
            title = stringResource(R.string.calendar_target),
            subtitle = stringResource(R.string.calendar_target_desc),
        ) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceRow(
                    selected = target == CalendarExportPrefs.OWN_CALENDAR,
                    title = stringResource(R.string.calendar_target_own),
                    subtitle = stringResource(R.string.calendar_target_own_desc),
                    color = Color(0xFFF65D31),
                ) {
                    target = CalendarExportPrefs.OWN_CALENDAR
                    CalendarExportPrefs.setTarget(context, target)
                }

                calendars.forEach { calendar ->
                    ChoiceRow(
                        selected = target == calendar.id,
                        title = calendar.name,
                        subtitle = calendar.account.takeIf { it != calendar.name }.orEmpty(),
                        color = Color(calendar.color),
                    ) {
                        target = calendar.id
                        CalendarExportPrefs.setTarget(context, target)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.calendar_target_change_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSectionCard(title = stringResource(R.string.calendar_what)) {
            fun toggle(category: CalendarCategory, on: Boolean) {
                CalendarExportPrefs.setCategory(context, category, on)
                categories = CalendarExportPrefs.categories(context)
            }

            listOf(
                CalendarCategory.EXAMS to (R.string.calendar_cat_exams to R.string.calendar_cat_exams_desc),
                CalendarCategory.OWN_EXAMS to (R.string.calendar_cat_own_exams to R.string.calendar_cat_own_exams_desc),
                CalendarCategory.EVENTS to (R.string.calendar_cat_events to R.string.calendar_cat_events_desc),
                CalendarCategory.HOLIDAYS to (R.string.calendar_cat_holidays to R.string.calendar_cat_holidays_desc),
                CalendarCategory.HOMEWORK to (R.string.calendar_cat_homework to R.string.calendar_cat_homework_desc),
                CalendarCategory.CANCELLED to (R.string.calendar_cat_cancelled to R.string.calendar_cat_cancelled_desc),
                CalendarCategory.SUBSTITUTED to (R.string.calendar_cat_substituted to R.string.calendar_cat_substituted_desc),
            ).forEach { (category, texts) ->
                SettingsSwitchRow(
                    title = stringResource(texts.first),
                    description = stringResource(texts.second),
                    checked = category in categories,
                    onCheckedChange = { toggle(category, it) },
                )

                if (category == CalendarCategory.EXAMS && category in categories && examTypes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.calendar_exam_types),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WrapRow {
                        examTypes.forEach { type ->
                            val on = CalendarItems.examTypeKey(type) !in excludedTypes
                            FilterChip(
                                selected = on,
                                onClick = Haptics.haptic {
                                    CalendarExportPrefs.setExamType(context, type, !on)
                                    excludedTypes = CalendarExportPrefs.excludedExamTypes(context)
                                },
                                label = { Text(type) },
                            )
                        }
                    }
                }
            }
        }

        if (accounts.size > 1) {
            SettingsSectionCard(
                title = stringResource(R.string.calendar_accounts),
                subtitle = stringResource(R.string.calendar_accounts_desc),
            ) {
                accountToggle.let { _ ->
                    accounts.forEach { auth ->
                        val id = AccountPrefs.accountId(auth)
                        SettingsSwitchRow(
                            title = auth.user,
                            description = auth.displayName,
                            checked = CalendarExportPrefs.isAccountEnabled(context, id),
                            onCheckedChange = {
                                CalendarExportPrefs.setAccountEnabled(context, id, it)
                                accountToggle++
                            },
                        )
                    }
                }
            }
        }

        SettingsSectionCard(title = stringResource(R.string.calendar_interval)) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CalendarExportPrefs.INTERVALS.forEach { hours ->
                    ChoiceRow(
                        selected = interval == hours,
                        title = if (hours == 0) {
                            stringResource(R.string.calendar_interval_manual)
                        } else {
                            pluralStringResource(R.plurals.calendar_interval_hours, hours, hours)
                        },
                    ) {
                        interval = hours
                        CalendarExportPrefs.setIntervalHours(context, hours)
                        reschedule()
                    }
                }
            }
        }

        SettingsSectionCard(title = stringResource(R.string.calendar_now)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = permitted && !running,
                    onClick = Haptics.haptic {
                        removedCount = null
                        CalendarExportWorker.runNow(context)
                    },
                ) { Text(stringResource(R.string.calendar_now_button)) }

                if (running) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (lastRun == 0L) {
                    stringResource(R.string.calendar_last_never)
                } else {
                    stringResource(
                        R.string.calendar_last,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastRun)),
                        lastResult,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            removedCount?.let {
                Text(stringResource(R.string.calendar_removed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TextButton(
                enabled = permitted && !running,
                onClick = Haptics.haptic {
                    scope.launch {
                        val removed = withContext(Dispatchers.IO) { CalendarSync.removeAll(context) }
                        removedCount = removed
                    }
                },
            ) { Text(stringResource(R.string.calendar_remove_all)) }
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String = "",
    color: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .selectable(selected = selected, role = Role.RadioButton, onClick = Haptics.haptic(onClick))
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))

        if (color != null) {
            Spacer(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 1f)),
            )
            Spacer(Modifier.width(10.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WrapRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
