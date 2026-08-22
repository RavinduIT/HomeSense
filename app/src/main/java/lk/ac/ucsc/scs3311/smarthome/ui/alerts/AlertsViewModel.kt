package lk.ac.ucsc.scs3311.smarthome.ui.alerts

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
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.ui.common.launchWrite

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    /** Device id → display name, so an alert reads as a place, not an id. */
    val deviceNames: Map<String, String> = emptyMap(),
) {
    val unacknowledgedCount: Int get() = alerts.count { !it.acknowledged }
}

class AlertsViewModel(private val repository: HomeRepository) : ViewModel() {

    /** Reported when the server refuses a write, rather than crashing. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun dismissError() {
        _error.value = null
    }

    val uiState: StateFlow<AlertsUiState> =
        combine(repository.alerts, repository.devices) { alerts, devices ->
            AlertsUiState(
                alerts = alerts,
                deviceNames = devices.associate { it.id to it.name },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AlertsUiState(),
        )

    /**
     * The only field of an alert a client may write. The database rules reject
     * a write that changes the message, the timestamp or the severity.
     */
    fun acknowledge(alertId: String) {
        viewModelScope.launchWrite(_error) { repository.acknowledgeAlert(alertId) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                AlertsViewModel(app.container.repository)
            }
        }
    }
}
