package lk.ac.ucsc.scs3311.smarthome.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.repository.HomeRepository
import lk.ac.ucsc.scs3311.smarthome.ui.common.launchWrite
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus

data class DeviceUiState(
    val device: Device? = null,
    /**
     * Slot ids with a command in flight. The switch shows the requested
     * position while the hardware is still catching up.
     */
    val pendingSlotIds: Set<String> = emptySet(),
    val message: String? = null,
    /** Ticks once a second so countdown rings animate without recomposing the world. */
    val nowMillis: Long = System.currentTimeMillis(),
)

/**
 * Controls for a single device.
 *
 * ### Optimistic toggles, honestly reconciled
 * Tapping a switch flips it immediately — waiting for a network round trip
 * feels broken. But the app only ever writes `desiredState`; it cannot make the
 * relay move. So the optimistic position is held for at most
 * [RECONCILE_TIMEOUT_MS], and if `reportedState` has not followed by then, the
 * switch snaps back and the user is told. That is the difference between an
 * optimistic UI and a dishonest one.
 */
class DeviceViewModel(
    private val repository: HomeRepository,
    private val deviceId: String,
) : ViewModel() {

    private val pending = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    /** One tick a second, only while something is collecting this ViewModel. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<DeviceUiState> =
        combine(
            repository.device(deviceId),
            pending,
            message,
            clock,
        ) { device, pendingIds, msg, now ->
            DeviceUiState(
                device = device,
                pendingSlotIds = pendingIds,
                message = msg,
                nowMillis = now,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DeviceUiState(),
        )

    fun toggleSlot(slotId: String, desired: Boolean) {
        viewModelScope.launch {
            val before = repository.device(deviceId).first()
            val wasUnreachable = before?.link == LinkState.DISCONNECTED ||
                before?.slot(slotId)?.status == SlotStatus.DISCONNECTED

            pending.update { it + slotId }
            runCatching { repository.setSlotDesiredState(deviceId, slotId, desired) }
                .onFailure {
                    pending.update { ids -> ids - slotId }
                    message.value = "Could not reach the hub. Nothing was changed."
                    return@launch
                }

            val label = before?.slot(slotId)?.label.orEmpty().ifBlank { "The device" }

            // A device that has never reported, or has stopped reporting, is not
            // going to answer within the reconciliation window. Waiting for it
            // would produce a misleading "did not respond" for what is in fact a
            // correctly recorded intention, so the command is reported as queued
            // and the wait is skipped.
            if (wasUnreachable) {
                pending.update { it - slotId }
                message.value =
                    "$label is offline. The request is saved and will be applied " +
                    "when it reconnects."
                return@launch
            }

            // Wait for the hardware to report back, via the same stream the UI
            // renders from. No polling, no callback.
            val settled = withTimeoutOrNull(RECONCILE_TIMEOUT_MS) {
                repository.device(deviceId).first { current ->
                    current?.slot(slotId)?.reportedState == desired
                }
            }

            pending.update { it - slotId }
            if (settled == null) {
                message.value = "$label did not respond. It is now showing an error."
            }
        }
    }

    /** Master switch on a gang box: every slot, one command each. */
    fun toggleAll(desired: Boolean) {
        viewModelScope.launch {
            val device = repository.device(deviceId).first() ?: return@launch
            val ids = device.slots.map { it.id }.toSet()
            pending.update { it + ids }
            runCatching { repository.setAllSlots(deviceId, desired) }
                .onFailure {
                    // Nothing was written, so nothing will report back. Waiting
                    // out the reconciliation window would leave every slot
                    // spinning for six seconds after a failure already known.
                    pending.update { current -> current - ids }
                    message.value = "Could not reach the hub. Nothing was changed."
                    return@launch
                }

            withTimeoutOrNull(RECONCILE_TIMEOUT_MS) {
                repository.device(deviceId).first { current ->
                    current != null && current.slots.all { it.reportedState == desired }
                }
            }
            pending.update { it - ids }
        }
    }

    fun updateSchedule(slotId: String, schedule: Schedule) {
        viewModelScope.launchWrite(message) { repository.updateSchedule(deviceId, slotId, schedule) }
    }

    fun updateSafety(slotId: String, safety: Safety) {
        viewModelScope.launchWrite(message) { repository.updateSafety(deviceId, slotId, safety) }
    }

    /**
     * Saves what a slot controls: its label and the appliance attached to it.
     *
     * Both are written, because they are edited together. The appliance carries
     * the wattage the energy estimate uses and the flag marking it a fire risk,
     * neither of which had any way of being set after a device was created.
     */
    fun editSlot(slotId: String, label: String, appliance: Appliance) {
        viewModelScope.launchWrite(message) {
            repository.updateSlotLabel(deviceId, slotId, label.trim())
            repository.updateAppliance(deviceId, slotId, appliance)
        }
    }

    fun deleteDevice() {
        viewModelScope.launchWrite(message) { repository.deleteDevice(deviceId) }
    }

    fun dismissMessage() {
        message.value = null
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 1_000L

        /**
         * How long the hardware gets to obey before the optimistic switch is
         * rolled back. Comfortably longer than a Realtime Database round trip
         * plus the simulator's own actuation delay, and comfortably shorter
         * than the worker's 10-second ERROR threshold — so the user is told
         * something is wrong before the badge turns red, not after.
         */
        private const val RECONCILE_TIMEOUT_MS = 4_000L

        fun factory(deviceId: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as HomeSenseApp
                DeviceViewModel(app.container.repository, deviceId)
            }
        }
    }
}

/**
 * Whether the control accepts a command.
 *
 * Always. Disabling the switch for a disconnected device seems defensible —
 * one cannot operate what one cannot reach — but it is wrong here, for two
 * reasons.
 *
 * First, the application writes `desiredState`, which is an *intention*, not an
 * instruction that has taken effect. Recording an intention for a device that
 * is currently unreachable is exactly what the field is for: the write is
 * queued, and the relay applies it when it reports back.
 *
 * Second, and decisively, a newly created device is `DISCONNECTED` until the
 * hardware has reported at least once. Disabling the control in that state left
 * every freshly placed device permanently unusable, with nothing the user could
 * do to recover.
 *
 * What the interface must not do is imply the command took effect. That is
 * handled separately: `status` continues to show `DISCONNECTED`, and the
 * ViewModel reports the command as queued rather than confirmed.
 */
val SlotStatus.allowsToggle: Boolean get() = true
