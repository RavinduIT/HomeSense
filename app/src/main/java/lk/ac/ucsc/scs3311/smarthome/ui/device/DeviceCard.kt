package lk.ac.ucsc.scs3311.smarthome.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.CameraInfo
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.Slot
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * Dispatches to the right card for the device's profile.
 *
 * The three profiles differ in *shape*, not in kind of thing: an outlet is one
 * slot, a gang box is N slots behind one master, a camera is no slots at all.
 * Scheduling and `max_on_duration` are properties of a slot, so both an outlet
 * and any channel of a gang box can carry them — which is exactly the realistic
 * case of an iron in a socket and a timed bulb on switch 2.
 */
@Composable
fun DeviceCard(
    device: Device,
    nowMillis: Long,
    onToggleSlot: (slotId: String, desired: Boolean) -> Unit,
    onToggleAll: (desired: Boolean) -> Unit,
    onEditSchedule: (slotId: String, Schedule) -> Unit,
    onEditSafety: (slotId: String, Safety) -> Unit,
    onRename: (slotId: String, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    pendingSlotIds: Set<String> = emptySet(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            DeviceHeader(device = device, onDelete = onDelete)
            Spacer(Modifier.height(12.dp))

            when (device.kind) {
                DeviceKind.OUTLET -> device.slots.forEach { slot ->
                    SlotRow(
                        slot = slot,
                        nowMillis = nowMillis,
                        pending = slot.id in pendingSlotIds,
                        onToggle = { onToggleSlot(slot.id, it) },
                        onEditSchedule = { onEditSchedule(slot.id, it) },
                        onEditSafety = { onEditSafety(slot.id, it) },
                        onRename = { onRename(slot.id, it) },
                    )
                }

                DeviceKind.MULTI_SWITCH -> MultiSwitchBody(
                    device = device,
                    nowMillis = nowMillis,
                    pendingSlotIds = pendingSlotIds,
                    onToggleSlot = onToggleSlot,
                    onToggleAll = onToggleAll,
                    onEditSchedule = onEditSchedule,
                    onEditSafety = onEditSafety,
                    onRename = onRename,
                )

                DeviceKind.CAMERA -> CameraBody(
                    device = device,
                    nowMillis = nowMillis,
                )
            }
        }
    }
}

@Composable
private fun DeviceHeader(device: Device, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(device.kind.description())
                    if (device.kind == DeviceKind.MULTI_SWITCH) {
                        append(" · ${device.slots.size} switches")
                    }
                    append(" · cell ${device.gridX},${device.gridY}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (device.link == LinkState.DISCONNECTED) {
            StatusPill(SlotStatus.DISCONNECTED, compact = true)
            Spacer(Modifier.width(4.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Device options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from plan") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

private fun DeviceKind.description(): String = when (this) {
    DeviceKind.OUTLET -> "Power outlet"
    DeviceKind.MULTI_SWITCH -> "Gang box"
    DeviceKind.CAMERA -> "Security camera"
}

// ---- multi-switch ----------------------------------------------------------

/**
 * One gang box. One card, one entity, one grid cell — with N independently
 * addressable switches inside it and a master that addresses each of them in
 * turn. There is no code path anywhere that presents this as N devices.
 */
@Composable
private fun MultiSwitchBody(
    device: Device,
    nowMillis: Long,
    pendingSlotIds: Set<String>,
    onToggleSlot: (String, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onEditSchedule: (String, Schedule) -> Unit,
    onEditSafety: (String, Safety) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val anyControllable = device.slots.any { it.status.allowsToggle }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("All switches", fontWeight = FontWeight.Medium)
            Text(
                text = "${device.slots.count { it.status == SlotStatus.ON }} of " +
                    "${device.slots.size} on",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = device.allSlotsOn,
            onCheckedChange = onToggleAll,
            enabled = anyControllable,
        )
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse switches" else "Expand switches",
            )
        }
    }

    AnimatedVisibility(visible = expanded) {
        Column {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            device.slots.forEachIndexed { index, slot ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SlotRow(
                    slot = slot,
                    nowMillis = nowMillis,
                    pending = slot.id in pendingSlotIds,
                    onToggle = { onToggleSlot(slot.id, it) },
                    onEditSchedule = { onEditSchedule(slot.id, it) },
                    onEditSafety = { onEditSafety(slot.id, it) },
                    onRename = { onRename(slot.id, it) },
                )
            }
        }
    }
}

// ---- a single slot ---------------------------------------------------------

@Composable
fun SlotRow(
    slot: Slot,
    nowMillis: Long,
    onToggle: (Boolean) -> Unit,
    onEditSchedule: (Schedule) -> Unit,
    onEditSafety: (Safety) -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
) {
    var showSchedule by remember { mutableStateOf(false) }
    var showSafety by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = slot.label.ifBlank { "Unnamed switch" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (slot.appliance.hazardous) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = "Fire hazard appliance",
                            tint = StatusColors.warning,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                if (slot.appliance.name.isNotBlank()) {
                    Text(
                        text = slot.appliance.name +
                            (slot.appliance.watts?.let { " · ${it} W" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(slot.status, compact = true)
                    if (slot.schedule.enabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                slot.schedule.summary(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            slot.cutoffProgress(nowMillis)?.let { progress ->
                CountdownRing(
                    progress = progress,
                    remainingSeconds = (slot.safety.maxOnDuration - slot.onDurationSeconds(nowMillis))
                        .coerceAtLeast(0L),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            Switch(
                checked = if (pending) !slot.reportedState else slot.desiredState,
                onCheckedChange = onToggle,
                enabled = slot.status.allowsToggle,
            )

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${slot.label}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Schedule…") },
                        onClick = { menuOpen = false; showSchedule = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Safety cut-off…") },
                        onClick = { menuOpen = false; showSafety = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename…") },
                        onClick = { menuOpen = false; showRename = true },
                    )
                }
            }
        }

        if (slot.status == SlotStatus.ERROR) {
            Text(
                text = "The hub asked for ${if (slot.desiredState) "ON" else "OFF"} " +
                    "but the device reports ${if (slot.reportedState) "ON" else "OFF"}.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusColors.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (showSchedule) {
        ScheduleDialog(
            initial = slot.schedule,
            slotLabel = slot.label,
            onDismiss = { showSchedule = false },
            onConfirm = { onEditSchedule(it); showSchedule = false },
        )
    }
    if (showSafety) {
        SafetyDialog(
            initial = slot.safety,
            slotLabel = slot.label,
            hazardous = slot.appliance.hazardous,
            onDismiss = { showSafety = false },
            onConfirm = { onEditSafety(it); showSafety = false },
        )
    }
    if (showRename) {
        RenameDialog(
            initial = slot.label,
            onDismiss = { showRename = false },
            onConfirm = { onRename(it); showRename = false },
        )
    }
}

/**
 * The `max_on_duration` countdown.
 *
 * The ring drains as the permitted on-time is consumed and turns amber under
 * 20% remaining, so the cut-off is visibly *about to happen* rather than
 * arriving as a surprise. It is a readout of state the server owns, not a timer
 * the phone is running — closing the app does not stop it.
 */
@Composable
fun CountdownRing(
    progress: Float,
    remainingSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(targetValue = progress, label = "cutoff")
    val nearingLimit = progress >= 0.8f
    val colour = if (nearingLimit) StatusColors.warning else StatusColors.on
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(44.dp)) {
        ComposeCanvas(Modifier.size(44.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke.width,
                    size.height - stroke.width,
                ),
                style = stroke,
            )
            drawArc(
                color = colour,
                startAngle = -90f,
                // Drains clockwise: a full ring means the whole allowance is left.
                sweepAngle = 360f * (1f - animated),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke.width,
                    size.height - stroke.width,
                ),
                style = stroke,
            )
        }
        Text(
            text = remainingSeconds.asClock(),
            style = MaterialTheme.typography.labelSmall,
            color = colour,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun Long.asClock(): String {
    val minutes = this / 60
    val seconds = this % 60
    return if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "${seconds}s"
}

fun Schedule.summary(): String {
    fun Int.hhmm() = "${(this / 60).toString().padStart(2, '0')}:" +
        (this % 60).toString().padStart(2, '0')
    val days = when {
        days.isEmpty() -> "daily"
        days.size == 7 -> "daily"
        else -> days.sorted().joinToString("") { DAY_INITIALS[it] }
    }
    return "${onAtMinuteOfDay.hhmm()}–${offAtMinuteOfDay.hhmm()} $days"
}

val DAY_INITIALS = listOf("S", "M", "T", "W", "T", "F", "S")

// ---- previews --------------------------------------------------------------

private fun outlet(status: SlotStatus, onSince: Long? = null) = Device(
    id = "d1",
    name = "Utility outlet",
    gridX = 1,
    gridY = 1,
    kind = DeviceKind.OUTLET,
    link = if (status == SlotStatus.DISCONNECTED) LinkState.DISCONNECTED else LinkState.ONLINE,
    slots = listOf(
        Slot(
            id = "s1",
            label = "Iron",
            appliance = Appliance("Steam iron", hazardous = true, watts = 1200),
            desiredState = status == SlotStatus.ON,
            reportedState = status == SlotStatus.ON,
            status = status,
            onSince = onSince,
            safety = Safety(maxOnDuration = 30L, autoCutoffEnabled = true),
        ),
    ),
)

@Preview(showBackground = true, widthDp = 380, name = "Outlet — all four states")
@Composable
private fun OutletCardPreview() {
    val now = 1_700_000_000_000L
    HomeSenseTheme {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                SlotStatus.ON to now - 24_000L,
                SlotStatus.OFF to null,
                SlotStatus.ERROR to null,
                SlotStatus.DISCONNECTED to null,
            ).forEach { (status, since) ->
                DeviceCard(
                    device = outlet(status, since),
                    nowMillis = now,
                    onToggleSlot = { _, _ -> },
                    onToggleAll = {},
                    onEditSchedule = { _, _ -> },
                    onEditSafety = { _, _ -> },
                    onRename = { _, _ -> },
                    onDelete = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, name = "Gang box — one entity, 3 slots")
@Composable
private fun MultiSwitchCardPreview() {
    HomeSenseTheme {
        DeviceCard(
            device = Device(
                id = "d2",
                name = "Hall gang box",
                gridX = 4,
                gridY = 2,
                kind = DeviceKind.MULTI_SWITCH,
                link = LinkState.ONLINE,
                slots = listOf(
                    Slot(
                        id = "s1",
                        label = "Ceiling light",
                        appliance = Appliance("LED panel", watts = 24),
                        desiredState = true,
                        reportedState = true,
                        status = SlotStatus.ON,
                        schedule = Schedule(true, 18 * 60, 23 * 60),
                    ),
                    Slot(id = "s2", label = "Porch light", status = SlotStatus.OFF),
                    Slot(
                        id = "s3",
                        label = "Ceiling fan",
                        desiredState = true,
                        reportedState = false,
                        status = SlotStatus.ERROR,
                    ),
                ),
            ),
            nowMillis = 1_700_000_000_000L,
            onToggleSlot = { _, _ -> },
            onToggleAll = {},
            onEditSchedule = { _, _ -> },
            onEditSafety = { _, _ -> },
            onRename = { _, _ -> },
            onDelete = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 380, name = "Camera")
@Composable
private fun CameraCardPreview() {
    HomeSenseTheme {
        DeviceCard(
            device = Device(
                id = "d3",
                name = "Front door camera",
                gridX = 0,
                gridY = 4,
                kind = DeviceKind.CAMERA,
                link = LinkState.ONLINE,
                camera = CameraInfo(
                    snapshotUrl = "asset://cameras/front_door.svg",
                    streamUrl = "mock://stream/front_door",
                    lastFrameAt = 1_700_000_000_000L,
                ),
            ),
            nowMillis = 1_700_000_000_000L,
            onToggleSlot = { _, _ -> },
            onToggleAll = {},
            onEditSchedule = { _, _ -> },
            onEditSafety = { _, _ -> },
            onRename = { _, _ -> },
            onDelete = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(showBackground = true, name = "Countdown ring")
@Composable
private fun CountdownRingPreview() {
    HomeSenseTheme {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountdownRing(progress = 0.1f, remainingSeconds = 27)
            CountdownRing(progress = 0.5f, remainingSeconds = 15)
            CountdownRing(progress = 0.85f, remainingSeconds = 4)
            CountdownRing(progress = 0.98f, remainingSeconds = 1)
        }
    }
}

/** Kept for previews that need a neutral colour without pulling in the theme. */
internal val PreviewNeutral = Color(0xFF6B7280)
