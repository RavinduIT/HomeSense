package lk.ac.ucsc.scs3311.smarthome.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState
import lk.ac.ucsc.scs3311.smarthome.domain.model.MULTI_SWITCH_SLOT_RANGE
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Slot
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors
import androidx.compose.foundation.shape.RoundedCornerShape as Rounded
import androidx.compose.foundation.text.KeyboardOptions as TextKeyboardOptions

/**
 * One floor: its plan, the abstract grid over it, and the devices on the cells.
 *
 * Tap an empty cell to place a device; tap a device to open its controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    floorId: String,
    onBack: () -> Unit,
    deviceSheet: @Composable (deviceId: String, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    // Keyed by floor. Separate navigation entries usually give each floor its
    // own ViewModel store, but keying does not depend on that holding.
    viewModel: PlanViewModel = viewModel(
        key = floorId,
        factory = PlanViewModel.factory(floorId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.floor?.name ?: "Floor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val floor = state.floor
        if (state.isLoading || floor == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            PlanLegend(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            FloorPlanCanvas(
                plan = state.plan,
                cols = floor.gridCols,
                rows = floor.gridRows,
                devices = state.devices,
                style = planStyle(),
                modifier = Modifier.weight(1f).padding(12.dp),
                onCellTap = viewModel::onCellTapped,
                onDeviceTap = viewModel::onDeviceTapped,
            )

            Text(
                text = "${state.devices.size} device(s) placed · " +
                    "tap an empty cell to add one",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    when (val current = dialog) {
        is PlanDialog.AddDevice -> AddDeviceDialog(
            cell = current.cell,
            onDismiss = viewModel::dismissDialog,
            onConfirm = { name, kind, labels, appliance, hazardous, watts, maxOn ->
                viewModel.addDevice(
                    cell = current.cell,
                    name = name,
                    kind = kind,
                    slotLabels = labels,
                    firstApplianceName = appliance,
                    hazardous = hazardous,
                    watts = watts,
                    maxOnDuration = maxOn,
                )
            },
        )

        is PlanDialog.DeviceSheet -> deviceSheet(current.deviceId, viewModel::dismissDialog)

        PlanDialog.None -> Unit
    }
}

@Composable
private fun planStyle() = PlanStyle(
    roomFill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    roomStroke = MaterialTheme.colorScheme.outline,
    wallStroke = MaterialTheme.colorScheme.outlineVariant,
    gridLine = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    gridLineStrong = MaterialTheme.colorScheme.outline,
    label = MaterialTheme.colorScheme.onSurfaceVariant,
    selection = MaterialTheme.colorScheme.primary,
    onSurface = MaterialTheme.colorScheme.onSurface,
)

/**
 * The marker key. Status is conveyed by shape *and* colour, so the plan is
 * still readable on a washed-out projector or by a colour-blind examiner.
 */
@Composable
private fun PlanLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendDot("On", StatusColors.on, filled = true)
        LegendDot("Off", StatusColors.off, filled = false)
        LegendDot("Error", StatusColors.error, filled = false)
        LegendDot("Offline", StatusColors.disconnected, filled = false, dashed = true)
    }
}

@Composable
private fun LegendDot(label: String, color: Color, filled: Boolean, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(Rounded(5.dp))
                .background(if (filled) color else Color.Transparent)
                .then(
                    if (filled) {
                        Modifier
                    } else {
                        Modifier.background(color.copy(alpha = if (dashed) 0.25f else 0.4f))
                    },
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ---- add device ------------------------------------------------------------

@Composable
private fun AddDeviceDialog(
    cell: IntCell,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        kind: DeviceKind,
        slotLabels: List<String>,
        appliance: String,
        hazardous: Boolean,
        watts: Int?,
        maxOnDuration: Long,
    ) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(DeviceKind.OUTLET) }
    var slotCount by rememberSaveable { mutableIntStateOf(3) }
    // A snapshot list rather than a State<List<...>>, so an edit to one field
    // does not rebuild the whole list, and so it survives rotation.
    val slotLabels = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { List(MULTI_SWITCH_SLOT_RANGE.last) { "" }.toMutableStateList() }
    var appliance by rememberSaveable { mutableStateOf("") }
    var hazardous by rememberSaveable { mutableStateOf(false) }
    var watts by rememberSaveable { mutableStateOf("") }
    var maxOn by rememberSaveable { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place a device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("Cell ${cell.x}, ${cell.y}") },
                )

                Text("Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.label()) },
                            modifier = Modifier.selectable(selected = kind == option) { kind = option },
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device name") },
                    placeholder = { Text(kind.placeholder()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (kind == DeviceKind.MULTI_SWITCH) {
                    Text(
                        "Switches on this plate: $slotCount",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "One gang box is one device with $slotCount independently " +
                            "addressable switches — not $slotCount devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MULTI_SWITCH_SLOT_RANGE.forEach { count ->
                            FilterChip(
                                selected = slotCount == count,
                                onClick = { slotCount = count },
                                label = { Text("$count") },
                            )
                        }
                    }

                    // Every switch is named here, not only the first. Leaving
                    // one blank falls back to "Switch n" rather than refusing
                    // to continue.
                    Spacer(Modifier.height(4.dp))
                    (0 until slotCount).forEach { index ->
                        OutlinedTextField(
                            value = slotLabels.getOrElse(index) { "" },
                            onValueChange = { value ->
                                while (slotLabels.size <= index) slotLabels.add("")
                                slotLabels[index] = value
                            },
                            label = { Text("Switch ${index + 1}") },
                            placeholder = { Text(SWITCH_PLACEHOLDERS.getOrElse(index) { "Switch ${index + 1}" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (kind != DeviceKind.CAMERA) {
                    OutlinedTextField(
                        value = appliance,
                        onValueChange = { appliance = it },
                        label = { Text("Appliance on the first slot") },
                        placeholder = { Text("Iron") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = watts,
                        onValueChange = { watts = it.filter(Char::isDigit) },
                        label = { Text("Watts (optional, for the energy estimate)") },
                        singleLine = true,
                        keyboardOptions = TextKeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Fire hazard", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Arms an automatic cut-off after a maximum on-time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = hazardous, onCheckedChange = { hazardous = it })
                    }
                    if (hazardous) {
                        OutlinedTextField(
                            value = maxOn,
                            onValueChange = { maxOn = it.filter(Char::isDigit) },
                            label = { Text("max_on_duration (seconds)") },
                            supportingText = { Text("The server switches it off after this long.") },
                            singleLine = true,
                            keyboardOptions = TextKeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name,
                        kind,
                        slotLabels.take(slotCount),
                        appliance,
                        hazardous,
                        watts.toIntOrNull(),
                        if (hazardous) maxOn.toLongOrNull() ?: 0L else 0L,
                    )
                },
            ) { Text("Place") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Suggestions, so the fields are not a column of identical empty boxes. */
private val SWITCH_PLACEHOLDERS = listOf(
    "Ceiling light",
    "Porch light",
    "Ceiling fan",
    "Wall light",
    "Extractor",
    "Spare",
)

private fun DeviceKind.label(): String = when (this) {
    DeviceKind.OUTLET -> "Outlet"
    DeviceKind.MULTI_SWITCH -> "Gang box"
    DeviceKind.CAMERA -> "Camera"
}

private fun DeviceKind.placeholder(): String = when (this) {
    DeviceKind.OUTLET -> "Utility outlet"
    DeviceKind.MULTI_SWITCH -> "Hall gang box"
    DeviceKind.CAMERA -> "Front door camera"
}

// ---- previews --------------------------------------------------------------

private fun previewDevices() = listOf(
    Device(
        id = "d1",
        name = "Utility outlet",
        gridX = 1,
        gridY = 1,
        kind = DeviceKind.OUTLET,
        link = LinkState.ONLINE,
        slots = listOf(
            Slot(
                id = "s1",
                label = "Iron",
                appliance = Appliance("Steam iron", hazardous = true, watts = 1200),
                status = SlotStatus.ON,
                safety = Safety(30, true),
            ),
        ),
    ),
    Device(
        id = "d2",
        name = "Hall gang box",
        gridX = 4,
        gridY = 2,
        kind = DeviceKind.MULTI_SWITCH,
        link = LinkState.ONLINE,
        slots = listOf(
            Slot(id = "s1", label = "Ceiling", status = SlotStatus.ON),
            Slot(id = "s2", label = "Porch", status = SlotStatus.OFF),
            Slot(id = "s3", label = "Fan", status = SlotStatus.OFF),
        ),
    ),
    Device(
        id = "d3",
        name = "Kitchen outlet",
        gridX = 6,
        gridY = 4,
        kind = DeviceKind.OUTLET,
        slots = listOf(Slot(id = "s1", label = "Kettle", status = SlotStatus.ERROR)),
    ),
    Device(
        id = "d4",
        name = "Front camera",
        gridX = 0,
        gridY = 4,
        kind = DeviceKind.CAMERA,
        link = LinkState.DISCONNECTED,
    ),
)

@Preview(showBackground = true, widthDp = 400, heightDp = 420)
@Composable
private fun GroundFloorPlanPreview() {
    HomeSenseTheme {
        Column {
            PlanLegend(Modifier.padding(12.dp))
            FloorPlanCanvas(
                plan = PlanLibrary.groundFloor,
                cols = 8,
                rows = 6,
                devices = previewDevices(),
                style = planStyle(),
                modifier = Modifier.height(340.dp).padding(12.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 380)
@Composable
private fun FirstFloorPlanPreview() {
    HomeSenseTheme {
        FloorPlanCanvas(
            plan = PlanLibrary.firstFloor,
            cols = 8,
            rows = 6,
            devices = listOf(
                Device(
                    id = "d5",
                    name = "Bedroom gang box",
                    gridX = 2,
                    gridY = 2,
                    kind = DeviceKind.MULTI_SWITCH,
                    link = LinkState.ONLINE,
                    slots = listOf(
                        Slot(id = "s1", label = "Bed light", status = SlotStatus.ON),
                        Slot(id = "s2", label = "Lamp", status = SlotStatus.DISCONNECTED),
                    ),
                ),
            ),
            style = planStyle(),
            selectedCell = IntCell(5, 3),
            modifier = Modifier.fillMaxSize().padding(12.dp),
        )
    }
}
