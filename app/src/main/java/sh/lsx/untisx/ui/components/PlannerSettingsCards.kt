package sh.lsx.untisx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.planner.PlannerPrefs
import sh.lsx.untisx.data.planner.PlannerScheduler
import sh.lsx.untisx.ui.util.Haptics
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Settings of the weekly preview and of the get-up / leave notice. */
@Composable
fun PlannerSettingsCards() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    var weekEnabled by remember { mutableStateOf(PlannerPrefs.weekEnabled(context)) }
    var weekDay by remember { mutableStateOf(PlannerPrefs.weekDay(context)) }
    var weekMinute by remember { mutableIntStateOf(PlannerPrefs.weekMinute(context)) }
    var commuteEnabled by remember { mutableStateOf(PlannerPrefs.commuteEnabled(context)) }
    var onlyChanges by remember { mutableStateOf(PlannerPrefs.commuteOnlyChanges(context)) }
    var commuteMinute by remember { mutableIntStateOf(PlannerPrefs.commuteMinute(context)) }
    var way by remember { mutableIntStateOf(PlannerPrefs.wayMinutes(context)) }
    var prep by remember { mutableIntStateOf(PlannerPrefs.prepMinutes(context)) }

    fun reschedule() = PlannerScheduler.ensureAll(context)

    SettingsSectionCard(title = stringResource(R.string.week_preview)) {
        SettingsSwitchRow(
            title = stringResource(R.string.week_preview_enable),
            description = stringResource(R.string.week_preview_enable_desc),
            checked = weekEnabled,
            onCheckedChange = {
                weekEnabled = it
                PlannerPrefs.setWeekEnabled(context, it)
                reschedule()
            },
        )

        if (weekEnabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
                    FilterChip(
                        selected = weekDay == day,
                        onClick = Haptics.haptic {
                            weekDay = day
                            PlannerPrefs.setWeekDay(context, day)
                            reschedule()
                        },
                        label = { Text(day.getDisplayName(TextStyle.FULL, locale)) },
                    )
                }
            }

            SettingsTimeRow(
                label = stringResource(R.string.week_preview_time),
                minute = weekMinute,
                onPick = {
                    weekMinute = it
                    PlannerPrefs.setWeekMinute(context, it)
                    reschedule()
                },
            )

            TextButton(onClick = Haptics.haptic { PlannerScheduler.runNow(context, PlannerScheduler.KIND_WEEK) }) {
                Text(stringResource(R.string.planner_try_now))
            }
        }
    }

    SettingsSectionCard(title = stringResource(R.string.commute)) {
        SettingsSwitchRow(
            title = stringResource(R.string.commute_enable),
            description = stringResource(R.string.commute_enable_desc),
            checked = commuteEnabled,
            onCheckedChange = {
                commuteEnabled = it
                PlannerPrefs.setCommuteEnabled(context, it)
                reschedule()
            },
        )

        if (commuteEnabled) {
            SettingsSwitchRow(
                title = stringResource(R.string.commute_only_changes),
                description = stringResource(R.string.commute_only_changes_desc),
                checked = onlyChanges,
                onCheckedChange = {
                    onlyChanges = it
                    PlannerPrefs.setCommuteOnlyChanges(context, it)
                },
            )

            SettingsTimeRow(
                label = stringResource(R.string.commute_time),
                minute = commuteMinute,
                onPick = {
                    commuteMinute = it
                    PlannerPrefs.setCommuteMinute(context, it)
                    reschedule()
                },
            )

            SettingsSliderRow(
                title = stringResource(R.string.commute_way),
                value = way.toFloat(),
                valueLabel = stringResource(R.string.minutes_short, way),
                valueRange = 5f..90f,
                steps = 16,
                onValueChange = {
                    way = it.toInt()
                    PlannerPrefs.setWayMinutes(context, way)
                },
            )

            SettingsSliderRow(
                title = stringResource(R.string.commute_prep),
                value = prep.toFloat(),
                valueLabel = stringResource(R.string.minutes_short, prep),
                valueRange = 15f..120f,
                steps = 20,
                onValueChange = {
                    prep = it.toInt()
                    PlannerPrefs.setPrepMinutes(context, prep)
                },
            )

            TextButton(onClick = Haptics.haptic { PlannerScheduler.runNow(context, PlannerScheduler.KIND_COMMUTE) }) {
                Text(stringResource(R.string.planner_try_now))
            }
        }
    }
}
