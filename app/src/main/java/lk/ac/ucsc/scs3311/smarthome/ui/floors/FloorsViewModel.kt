package lk.ac.ucsc.scs3311.smarthome.ui.floors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.ui.plan.PlanLibrary
import lk.ac.ucsc.scs3311.smarthome.ui.plan.planStatus

/** One row of the floor list, with the summary counts shown on its card. */
data class FloorSummary(
    val floor: Floor,
    val deviceCount: Int,
    val onCount: Int,
    val problemCount: Int,
) {
    val planName: String get() = PlanLibrary.byId(floor.planAsset).displayName
}

data class FloorsUiState(
    val floors: List<FloorSummary> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Owns the floor list. Holds no Firebase or Room type — only the repository —
 * which is what lets the same ViewModel serve the demo and live flavours.
 */
class FloorsViewModel(private val repository: HomeRepository) : ViewModel() {

    val uiState: StateFlow<FloorsUiState> =
        combine(repository.floors, repository.devices) { floors, devices ->
            FloorsUiState(
                floors = floors
                    .sortedBy { it.level }
                    .map { floor -> floor.summarise(devices) },
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = FloorsUiState(),
        )

    private fun Floor.summarise(devices: List<Device>): FloorSummary {
        val onFloor = devices.filter { it.floorId == id }
        return FloorSummary(
            floor = this,
            deviceCount = onFloor.size,
            onCount = onFloor.count { it.planStatus() == SlotStatus.ON },
            problemCount = onFloor.count {
                it.planStatus() == SlotStatus.ERROR || it.planStatus() == SlotStatus.DISCONNECTED
            },
        )
    }

    fun addFloor(name: String, level: Int, planId: String, cols: Int, rows: Int) {
        viewModelScope.launch {
            repository.saveFloor(
                Floor(
                    name = name.trim().ifBlank { "Floor $level" },
                    level = level,
                    planAsset = planId,
                    gridCols = cols.coerceIn(Floor.COL_RANGE),
                    gridRows = rows.coerceIn(Floor.ROW_RANGE),
                ),
            )
        }
    }

    fun updateFloor(floor: Floor) {
        viewModelScope.launch { repository.saveFloor(floor) }
    }

    /** Also removes the devices standing on it — see the repository. */
    fun deleteFloor(floorId: String) {
        viewModelScope.launch { repository.deleteFloor(floorId) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                FloorsViewModel(app.container.repository)
            }
        }
    }
}
