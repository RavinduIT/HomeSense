package lk.ac.ucsc.scs3311.smarthome.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import lk.ac.ucsc.scs3311.smarthome.data.remote.RemoteHomeSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.Slot
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEvent

/**
 * A hand-controlled stand-in for the cloud.
 *
 * Unlike the demo build's `FakeRemoteSource`, this one does nothing on its own:
 * the test decides exactly when a change "arrives from the cloud", via
 * [emitDevices] and [simulateExternalChange]. That is what makes the
 * propagation assertions deterministic rather than timing-dependent.
 */
class TestRemoteSource(
    initialFloors: List<Floor> = emptyList(),
    initialDevices: List<Device> = emptyList(),
) : RemoteHomeSource {

    private val floors = MutableStateFlow(initialFloors)
    private val devices = MutableStateFlow(initialDevices)
    private val alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val usage = MutableStateFlow<List<UsageEvent>>(emptyList())

    /** Everything the repository asked the cloud to do, in order. */
    val writes = mutableListOf<String>()
    val appendedEvents = mutableListOf<UsageEvent>()
    var signInCount = 0
        private set

    override fun observeFloors(homeId: String): Flow<List<Floor>> = floors
    override fun observeDevices(homeId: String): Flow<List<Device>> = devices
    override fun observeAlerts(homeId: String): Flow<List<Alert>> = alerts
    override fun observeUsage(homeId: String): Flow<List<UsageEvent>> = usage

    // ---- test controls ------------------------------------------------------

    fun emitDevices(list: List<Device>) {
        devices.value = list
    }

    fun emitFloors(list: List<Floor>) {
        floors.value = list
    }

    fun emitAlerts(list: List<Alert>) {
        alerts.value = list
    }

    fun emitUsage(list: List<UsageEvent>) {
        usage.value = list
    }

    /**
     * A change with no app involvement at all — the simulator flipping a relay,
     * or the worker declaring a fault. Nothing calls into the repository; the
     * value simply appears on the stream, exactly as a Firebase child listener
     * would deliver it.
     */
    fun simulateExternalChange(deviceId: String, slotId: String, transform: (Slot) -> Slot) {
        devices.update { list ->
            list.map { device ->
                if (device.id != deviceId) {
                    device
                } else {
                    device.copy(slots = device.slots.map { if (it.id == slotId) transform(it) else it })
                }
            }
        }
    }

    // ---- writes -------------------------------------------------------------

    override suspend fun saveFloor(homeId: String, floor: Floor): String {
        writes += "saveFloor(${floor.name})"
        val id = floor.id.ifBlank { "floor-${floors.value.size + 1}" }
        floors.update { current ->
            if (current.any { it.id == id }) current.map { if (it.id == id) floor.copy(id = id) else it }
            else current + floor.copy(id = id)
        }
        return id
    }

    override suspend fun deleteFloor(homeId: String, floorId: String) {
        writes += "deleteFloor($floorId)"
        floors.update { list -> list.filterNot { it.id == floorId } }
    }

    override suspend fun saveDevice(homeId: String, device: Device): String {
        writes += "saveDevice(${device.name})"
        val id = device.id.ifBlank { "device-${devices.value.size + 1}" }
        devices.update { current ->
            if (current.any { it.id == id }) current.map { if (it.id == id) device.copy(id = id) else it }
            else current + device.copy(id = id)
        }
        return id
    }

    override suspend fun deleteDevice(homeId: String, deviceId: String) {
        writes += "deleteDevice($deviceId)"
        devices.update { list -> list.filterNot { it.id == deviceId } }
    }

    override suspend fun moveDevice(homeId: String, deviceId: String, gridX: Int, gridY: Int) {
        writes += "moveDevice($deviceId,$gridX,$gridY)"
    }

    override suspend fun setDesiredState(
        homeId: String,
        deviceId: String,
        slotId: String,
        desired: Boolean,
    ) {
        writes += "setDesiredState($deviceId/$slotId=$desired)"
        // Only desiredState moves. status stays where it was, because in the
        // real system only the worker may change it.
        simulateExternalChange(deviceId, slotId) { it.copy(desiredState = desired) }
    }

    override suspend fun updateSlotLabel(homeId: String, deviceId: String, slotId: String, label: String) {
        writes += "updateSlotLabel($deviceId/$slotId=$label)"
    }

    override suspend fun updateAppliance(homeId: String, deviceId: String, slotId: String, appliance: Appliance) {
        writes += "updateAppliance($deviceId/$slotId=${appliance.name})"
    }

    override suspend fun updateSafety(homeId: String, deviceId: String, slotId: String, safety: Safety) {
        writes += "updateSafety($deviceId/$slotId=${safety.maxOnDuration})"
    }

    override suspend fun updateSchedule(homeId: String, deviceId: String, slotId: String, schedule: Schedule) {
        writes += "updateSchedule($deviceId/$slotId=${schedule.enabled})"
    }

    override suspend fun appendUsageEvent(homeId: String, event: UsageEvent) {
        val stored = event.copy(id = "usage-${appendedEvents.size + 1}")
        appendedEvents += stored
        usage.update { it + stored }
    }

    override suspend fun acknowledgeAlert(homeId: String, alertId: String) {
        writes += "acknowledgeAlert($alertId)"
        alerts.update { list -> list.map { if (it.id == alertId) it.copy(acknowledged = true) else it } }
    }

    override suspend fun ensureSignedIn(): String {
        signInCount++
        return "test-uid"
    }

    companion object {
        /** Convenience: a slot that the worker currently reports as OFF. */
        fun slot(id: String, label: String = id, status: SlotStatus = SlotStatus.OFF) =
            Slot(id = id, label = label, status = status)
    }
}
