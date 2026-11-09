package sh.lsx.untisx.ui.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.notebook.ReminderRule

private val ONE_HOUR_BEFORE = ReminderRule(minutesBefore = 60)

/** Quick choices for a reminder that follows the due date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderPresets(
    current: ReminderRule?,
    defaultRule: ReminderRule,
    enabled: Boolean,
    onRule: (ReminderRule) -> Unit,
    onFixed: () -> Unit,
) {
    var askBefore by remember { mutableStateOf(false) }
    val customBefore = current != null && current.atMinutes == null && current != ONE_HOUR_BEFORE

    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = current == defaultRule,
                onClick = { onRule(defaultRule) },
                enabled = enabled,
                label = { Text(reminderRuleText(defaultRule)) },
            )
            FilterChip(
                selected = current == ONE_HOUR_BEFORE,
                onClick = { onRule(ONE_HOUR_BEFORE) },
                enabled = enabled,
                label = { Text(reminderRuleText(ONE_HOUR_BEFORE)) },
            )
            FilterChip(
                selected = customBefore,
                onClick = { askBefore = true },
                enabled = enabled,
                label = {
                    Text(if (customBefore && current != null) reminderRuleText(current) else stringResource(R.string.homework_reminder_before_custom))
                },
            )
            FilterChip(
                selected = current == null,
                onClick = onFixed,
                enabled = enabled,
                label = { Text(stringResource(R.string.homework_reminder_fixed)) },
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(
                if (current != null) R.string.homework_reminder_follows else R.string.homework_reminder_fixed_hint,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (askBefore) {
        BeforeDialog(
            initial = current?.takeIf { it.atMinutes == null }?.minutesBefore ?: 120,
            onConfirm = { minutes ->
                onRule(ReminderRule(minutesBefore = minutes))
                askBefore = false
            },
            onDismiss = { askBefore = false },
        )
    }
}

@Composable
internal fun reminderRuleText(rule: ReminderRule): String {
    val at = rule.atMinutes
    if (at != null) {
        val time = "%02d:%02d".format(at / 60, at % 60)
        return when (rule.daysBefore) {
            0 -> stringResource(R.string.homework_reminder_same_day, time)
            1 -> stringResource(R.string.homework_reminder_day_before, time)
            else -> stringResource(R.string.homework_reminder_days_before, rule.daysBefore, time)
        }
    }

    val minutes = rule.minutesBefore
    return when {
        minutes % (24 * 60) == 0 && minutes > 0 ->
            pluralStringResource(R.plurals.homework_before_days, minutes / (24 * 60), minutes / (24 * 60))
        minutes % 60 == 0 && minutes > 0 ->
            pluralStringResource(R.plurals.homework_before_hours, minutes / 60, minutes / 60)
        else -> pluralStringResource(R.plurals.homework_before_minutes, minutes, minutes)
    }
}

private enum class BeforeUnit(val minutes: Int, val label: Int) {
    MINUTES(1, R.string.homework_unit_minutes),
    HOURS(60, R.string.homework_unit_hours),
    DAYS(24 * 60, R.string.homework_unit_days),
}

@Composable
private fun BeforeDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val startUnit = when {
        initial % (24 * 60) == 0 -> BeforeUnit.DAYS
        initial % 60 == 0 -> BeforeUnit.HOURS
        else -> BeforeUnit.MINUTES
    }
    var unit by remember { mutableStateOf(startUnit) }
    var amount by remember { mutableStateOf((initial / startUnit.minutes).toString()) }
    val value = amount.toIntOrNull()
    val total = value?.let { it * unit.minutes }
    val valid = total != null && total in 1..(60 * 24 * 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.homework_reminder_before_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.homework_reminder_amount)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BeforeUnit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = unit == option,
                            onClick = { unit = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = BeforeUnit.entries.size),
                        ) { Text(stringResource(option.label)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.homework_reminder_before_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { total?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
