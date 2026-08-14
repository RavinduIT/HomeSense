package lk.ac.ucsc.scs3311.smarthome.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import androidx.compose.foundation.text.KeyboardOptions as TextKeyboardOptions

/**
 * Schedule editor for a slot.
 *
 * Times are stored as minutes from midnight, so nothing here has to reason
 * about dates, timezones or daylight saving — the worker's minute ticker
 * compares two integers. A window whose end is before its start simply wraps
 * past midnight, which is the common case for a porch light.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initial: Schedule,
    slotLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Schedule) -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var onAt by rememberSaveable { mutableIntStateOf(initial.onAtMinuteOfDay) }
    var offAt by rememberSaveable { mutableIntStateOf(initial.offAtMinuteOfDay) }
    var days by remember { mutableStateOf(initial.days.toSet()) }
    var editing by remember { mutableStateOf<TimeField?>(null) }

    val draft = Schedule(enabled, onAt, offAt, days.sorted())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule · ${slotLabel.ifBlank { "switch" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Run on a schedule", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { editing = TimeField.ON },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                    ) { Text("On at ${onAt.asHhMm()}") }
                    OutlinedButton(
                        onClick = { editing = TimeField.OFF },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                    ) { Text("Off at ${offAt.asHhMm()}") }
                }

                if (enabled && draft.wrapsMidnight) {
                    Text(
                        "This window crosses midnight — it stays on overnight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (enabled && onAt == offAt) {
                    Text(
                        "Start and end are the same, so the schedule will never run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text("Days", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_INITIALS.forEachIndexed { index, initialLetter ->
                        FilterChip(
                            selected = days.isEmpty() || index in days,
                            onClick = {
                                days = when {
                                    // "Empty means every day" is the storage
                                    // convention; the first tap makes it explicit.
                                    days.isEmpty() -> (0..6).toSet() - index
                                    index in days -> days - index
                                    else -> days + index
                                }
                            },
                            label = { Text(initialLetter) },
                            enabled = enabled,
                        )
                    }
                }
                Text(
                    if (days.isEmpty() || days.size == 7) "Every day" else "Selected days only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    Schedule(
                        enabled = enabled,
                        onAtMinuteOfDay = onAt,
                        offAtMinuteOfDay = offAt,
                        days = if (days.size == 7) emptyList() else days.sorted(),
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    editing?.let { field ->
        val current = if (field == TimeField.ON) onAt else offAt
        TimePickerDialog(
            initialMinuteOfDay = current,
            title = if (field == TimeField.ON) "Switch on at" else "Switch off at",
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                if (field == TimeField.ON) onAt = minutes else offAt = minutes
                editing = null
            },
        )
    }
}

private enum class TimeField { ON, OFF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinuteOfDay: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * `max_on_duration` editor.
 *
 * The wording is deliberate: the limit is enforced **by the server**, not by
 * this screen. A user who understands that will not be surprised when the iron
 * switches itself off with the phone in a pocket — which is the entire point of
 * the feature.
 */
@Composable
fun SafetyDialog(
    initial: Safety,
    slotLabel: String,
    hazardous: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Safety) -> Unit,
) {
    var enabled by rememberSaveable { mutableStateOf(initial.autoCutoffEnabled) }
    var seconds by rememberSaveable {
        mutableStateOf(if (initial.maxOnDuration > 0) initial.maxOnDuration.toString() else "30")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safety cut-off · ${slotLabel.ifBlank { "switch" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Switch off automatically", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                OutlinedTextField(
                    value = seconds,
                    onValueChange = { seconds = it.filter(Char::isDigit).take(6) },
                    label = { Text("max_on_duration (seconds)") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = TextKeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L, 120L, 300L, 900L).forEach { preset ->
                        FilterChip(
                            selected = seconds.toLongOrNull() == preset,
                            onClick = { seconds = preset.toString() },
                            label = { Text(preset.asClock()) },
                            enabled = enabled,
                        )
                    }
                }

                Text(
                    text = "Enforced on the server. The device is switched off even if " +
                        "this phone is closed, out of range, or switched off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hazardous) {
                    Text(
                        "This appliance is not marked as a fire hazard, but a cut-off " +
                            "can still be set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = seconds.toLongOrNull() ?: 0L
                onConfirm(
                    Safety(
                        maxOnDuration = value,
                        autoCutoffEnabled = enabled && value > 0L,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename switch") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun Int.asHhMm(): String =
    "${(this / 60).toString().padStart(2, '0')}:${(this % 60).toString().padStart(2, '0')}"

@Preview
@Composable
private fun SafetyDialogPreview() {
    HomeSenseTheme {
        Column(Modifier.padding(8.dp)) {
            SafetyDialog(
                initial = Safety(maxOnDuration = 30, autoCutoffEnabled = true),
                slotLabel = "Iron",
                hazardous = true,
                onDismiss = {},
                onConfirm = {},
            )
        }
    }
}
