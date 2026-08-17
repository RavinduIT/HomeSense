package lk.ac.ucsc.scs3311.smarthome.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.CameraInfo
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.MULTI_SWITCH_SLOT_RANGE
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.Slot

data class PlanUiState(
    val floor: Floor? = null,
    val plan: FloorPlanSpec = PlanLibrary.blank,
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = true,
) {
    val occupiedCells: Set<IntCell>
        get() = devices.mapTo(mutableSetOf()) { IntCell(it.gridX, it.gridY) }
}

/** What the screen is currently asking the user about. */
sealed interface PlanDialog {
    data object None : PlanDialog

    /** Placing a new device on an empty cell. */
    data class AddDevice(val cell: IntCell) : PlanDialog

    /** Showing the controls for a device already on the plan. */
    data class DeviceSheet(val deviceId: String) : PlanDialog
}

class PlanViewModel(
    private val repository: HomeRepository,
    private val floorId: String,
) : ViewModel() {

    private val _dialog = MutableStateFlow<PlanDialog>(PlanDialog.None)
    val dialog: StateFlow<PlanDialog> = _dialog

    val uiState: StateFlow<PlanUiState> =
        combine(repository.floors, repository.devicesOnFloor(floorId)) { floors, devices ->
            val floor = floors.firstOrNull { it.id == floorId }
            PlanUiState(
                floor = floor,
                plan = PlanLibrary.byId(floor?.planAsset.orEmpty()),
                // Sorting keeps the draw order stable, so markers never flicker
                // between frames when an unrelated device reports in.
                devices = devices.sortedBy { it.id },
                isLoading = floor == null,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PlanUiState(),
        )

    fun onCellTapped(cell: IntCell) {
        _dialog.value = PlanDialog.AddDevice(cell)
    }

    fun onDeviceTapped(device: Device) {
        _dialog.value = PlanDialog.DeviceSheet(device.id)
    }

    fun dismissDialog() {
        _dialog.value = PlanDialog.None
    }

    /**
     * Creates a device on a cell.
     *
     * A multi-switch is created as **one** device with [slotCount] slots. There
     * is deliberately no code path that turns a gang box into several devices —
     * the specification requires it be a single entity, and the data model
     * makes any other shape unrepresentable.
     */
    /**
     * Creates a device on a cell.
     *
     * Each switch of a multi-switch unit is named at creation, rather than only
     * the first. Everything else about a slot — the appliance, its wattage,
     * whether it is a fire risk — is set afterwards from the device sheet,
     * which keeps this dialog to a manageable size while leaving every slot
     * fully configurable.
     */
    fun addDevice(
        cell: IntCell,
        name: String,
        kind: DeviceKind,
        /** One entry per switch. Its size determines the slot count. */
        slotLabels: List<String>,
        firstApplianceName: String,
        hazardous: Boolean,
        watts: Int?,
        maxOnDuration: Long,
    ) {
        val slots = when (kind) {
            DeviceKind.CAMERA -> emptyList()
            DeviceKind.OUTLET -> listOf(
                newSlot(
                    index = 0,
                    label = firstApplianceName.ifBlank { name },
                    appliance = Appliance(firstApplianceName, hazardous, watts),
                    maxOnDuration = maxOnDuration,
                ),
            )
            DeviceKind.MULTI_SWITCH -> {
                val labels = slotLabels
                    .take(MULTI_SWITCH_SLOT_RANGE.last)
                    .ifEmpty { List(MULTI_SWITCH_SLOT_RANGE.first) { "" } }
                labels.mapIndexed { i, label ->
                    newSlot(
                        index = i,
                        label = label.trim().ifBlank { "Switch ${i + 1}" },
                        // The appliance details in the dialog describe the first
                        // switch. The rest are named here and detailed from the
                        // device sheet, where every field is editable.
                        appliance = if (i == 0) {
                            Appliance(firstApplianceName, hazardous, watts)
                        } else {
                            Appliance()
                        },
                        maxOnDuration = if (i == 0) maxOnDuration else 0L,
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.saveDevice(
                Device(
                    floorId = floorId,
                    name = name.trim().ifBlank { kind.defaultName() },
                    gridX = cell.x,
                    gridY = cell.y,
                    kind = kind,
                    camera = if (kind == DeviceKind.CAMERA) {
                        CameraInfo(
                            snapshotUrl = "asset://cameras/generic.svg",
                            streamUrl = "mock://stream/${name.lowercase().replace(' ', '_')}",
                        )
                    } else {
                        null
                    },
                    slots = slots,
                ),
            )
            _dialog.value = PlanDialog.None
        }
    }

    private fun newSlot(index: Int, label: String, appliance: Appliance, maxOnDuration: Long) = Slot(
        id = "s${index + 1}",
        label = label,
        appliance = appliance,
        safety = Safety(maxOnDuration = maxOnDuration, autoCutoffEnabled = maxOnDuration > 0L),
        schedule = Schedule(),
    )

    fun moveDevice(deviceId: String, cell: IntCell) {
        viewModelScope.launch { repository.moveDevice(deviceId, cell.x, cell.y) }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            repository.deleteDevice(deviceId)
            _dialog.value = PlanDialog.None
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The floor id travels in the navigation arguments, not in a singleton. */
        fun factory(floorId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                PlanViewModel(app.container.repository, floorId)
            }
        }
    }
}

private fun DeviceKind.defaultName(): String = when (this) {
    DeviceKind.OUTLET -> "Outlet"
    DeviceKind.MULTI_SWITCH -> "Switch plate"
    DeviceKind.CAMERA -> "Camera"
}
