package lk.ac.ucsc.scs3311.smarthome.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.AlertSeverity
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.CameraInfo
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.EventSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.Slot
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEvent
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEventType
import java.util.concurrent.atomic.AtomicLong

/**
 * An in-memory stand-in for the cloud, used by the `demo` product flavour and
 * by the repository tests.
 *
 * It is not a stub that returns canned data. It plays **all three** roles that
 * the real system splits across processes:
 *
 *  - the *simulator*, by following `desiredState` into `reportedState` after a
 *    short delay, exactly as a relay would;
 *  - the *worker*, by deriving `status`, stamping `onSince`, enforcing
 *    `max_on_duration` and raising alerts;
 *  - the *cloud*, by pushing every change out through its flows.
 *
 * That means the demo build exercises the real repository, the real ViewModels
 * and the real UI — including a genuine safety cutoff — with no network. It is
 * insurance against campus Wi-Fi on presentation day, not a mock-up.
 *
 * [injectFault], [unplug] and [replug] mirror the simulator's chaos buttons.
 */
class FakeRemoteSource(
    private val scope: CoroutineScope,
    seed: Boolean = true,
    /** How long the "hardware" takes to obey a command. */
    private val actuationDelayMs: Long = 350L,
) : RemoteHomeSource {

    private val floorState = MutableStateFlow<List<Floor>>(emptyList())
    private val deviceState = MutableStateFlow<List<Device>>(emptyList())
    private val alertState = MutableStateFlow<List<Alert>>(emptyList())
    private val usageState = MutableStateFlow<List<UsageEvent>>(emptyList())

    /** Devices whose "hardware" has been told to misbehave. */
    private val faulted = mutableSetOf<String>()
    private val unplugged = mutableSetOf<String>()

    private val ids = AtomicLong(1_000)

    val devices = deviceState.asStateFlow()

    init {
        if (seed) seedDemoHome()
        scope.launch { runSafetyLoop() }
    }

    // ---- observation --------------------------------------------------------

    override fun observeFloors(homeId: String): Flow<List<Floor>> = floorState

    override fun observeDevices(homeId: String): Flow<List<Device>> = deviceState

    override fun observeAlerts(homeId: String): Flow<List<Alert>> = alertState

    override fun observeUsage(homeId: String): Flow<List<UsageEvent>> = usageState

    // ---- floors -------------------------------------------------------------

    override suspend fun saveFloor(homeId: String, floor: Floor): String {
        val id = floor.id.ifBlank { nextId("floor") }
        floorState.update { current ->
            val stored = floor.copy(id = id)
            if (current.any { it.id == id }) {
                current.map { if (it.id == id) stored else it }
            } else {
                current + stored
            }
        }
        return id
    }

    override suspend fun deleteFloor(homeId: String, floorId: String) {
        floorState.update { list -> list.filterNot { it.id == floorId } }
    }

    // ---- devices ------------------------------------------------------------

    override suspend fun saveDevice(homeId: String, device: Device): String {
        val id = device.id.ifBlank { nextId("device") }
        deviceState.update { current ->
            val existing = current.firstOrNull { it.id == id }
            val stored = if (existing == null) {
                // A brand new device has never reported in, so it is honestly
                // DISCONNECTED until the simulated hardware picks it up.
                device.copy(
                    id = id,
                    link = LinkState.DISCONNECTED,
                    slots = device.slots.map { it.copy(status = SlotStatus.DISCONNECTED) },
                )
            } else {
                existing.copy(
                    floorId = device.floorId,
                    name = device.name,
                    gridX = device.gridX,
                    gridY = device.gridY,
                )
            }
            if (existing == null) current + stored else current.map { if (it.id == id) stored else it }
        }
        return id
    }

    override suspend fun deleteDevice(homeId: String, deviceId: String) {
        deviceState.update { list -> list.filterNot { it.id == deviceId } }
    }

    override suspend fun moveDevice(homeId: String, deviceId: String, gridX: Int, gridY: Int) {
        mutateDevice(deviceId) { it.copy(gridX = gridX, gridY = gridY) }
    }

    // ---- control ------------------------------------------------------------

    override suspend fun setDesiredState(
        homeId: String,
        deviceId: String,
        slotId: String,
        desired: Boolean,
    ) {
        mutateSlot(deviceId, slotId) { it.copy(desiredState = desired) }

        // The "hardware" obeys after a delay — or, if it has been faulted or
        // unplugged, does not obey at all, which is what produces a real
        // ERROR / DISCONNECTED instead of a decorative one.
        scope.launch {
            delay(actuationDelayMs)
            if (deviceId in unplugged) return@launch
            if (deviceId in faulted) return@launch
            actuate(deviceId, slotId, desired)
        }
    }

    /** The relay physically moves, and the worker's derivation follows. */
    private fun actuate(deviceId: String, slotId: String, on: Boolean) {
        mutateSlot(deviceId, slotId) { slot ->
            slot.copy(
                reportedState = on,
                status = if (on) SlotStatus.ON else SlotStatus.OFF,
                onSince = if (on) (slot.onSince ?: now()) else null,
            )
        }
    }

    // ---- slot configuration -------------------------------------------------

    override suspend fun updateSlotLabel(
        homeId: String,
        deviceId: String,
        slotId: String,
        label: String,
    ) = mutateSlot(deviceId, slotId) { it.copy(label = label) }

    override suspend fun updateAppliance(
        homeId: String,
        deviceId: String,
        slotId: String,
        appliance: Appliance,
    ) = mutateSlot(deviceId, slotId) { it.copy(appliance = appliance) }

    override suspend fun updateSafety(
        homeId: String,
        deviceId: String,
        slotId: String,
        safety: Safety,
    ) = mutateSlot(deviceId, slotId) { it.copy(safety = safety) }

    override suspend fun updateSchedule(
        homeId: String,
        deviceId: String,
        slotId: String,
        schedule: Schedule,
    ) = mutateSlot(deviceId, slotId) { it.copy(schedule = schedule) }

    // ---- append-only --------------------------------------------------------

    override suspend fun appendUsageEvent(homeId: String, event: UsageEvent) {
        usageState.update { it + event.copy(id = nextId("usage")) }
    }

    override suspend fun acknowledgeAlert(homeId: String, alertId: String) {
        alertState.update { list ->
            list.map { if (it.id == alertId) it.copy(acknowledged = true) else it }
        }
    }

    override suspend fun ensureSignedIn(): String = "demo-user"

    // ---- chaos controls, mirroring the simulator's three buttons ------------

    /** The relay stops obeying: desired and reported diverge, so status → ERROR. */
    fun injectFault(deviceId: String) {
        faulted += deviceId
        mutateDevice(deviceId) { device ->
            device.copy(
                slots = device.slots.map { slot ->
                    if (slot.isMismatched || slot.desiredState) {
                        slot.copy(reportedState = !slot.desiredState, status = SlotStatus.ERROR)
                    } else {
                        slot.copy(status = SlotStatus.ERROR)
                    }
                },
            )
        }
    }

    fun clearFault(deviceId: String) {
        faulted -= deviceId
        mutateDevice(deviceId) { device ->
            device.copy(
                slots = device.slots.map { slot ->
                    slot.copy(
                        reportedState = slot.desiredState,
                        status = if (slot.desiredState) SlotStatus.ON else SlotStatus.OFF,
                    )
                },
            )
        }
    }

    /** The heartbeat stops. Everything on the node becomes DISCONNECTED. */
    fun unplug(deviceId: String) {
        unplugged += deviceId
        mutateDevice(deviceId) { device ->
            device.copy(
                link = LinkState.DISCONNECTED,
                lastSeen = now() - STALE_AFTER_MS,
                slots = device.slots.map { it.copy(status = SlotStatus.DISCONNECTED) },
            )
        }
    }

    fun replug(deviceId: String) {
        unplugged -= deviceId
        mutateDevice(deviceId) { device ->
            device.copy(
                link = LinkState.ONLINE,
                lastSeen = now(),
                slots = device.slots.map { slot ->
                    slot.copy(
                        reportedState = slot.desiredState,
                        status = if (slot.desiredState) SlotStatus.ON else SlotStatus.OFF,
                    )
                },
            )
        }
    }

    /** A change that originates at the hardware, with no app involvement. */
    fun physicalToggle(deviceId: String, slotId: String) {
        val slot = deviceState.value.firstOrNull { it.id == deviceId }?.slot(slotId) ?: return
        val next = !slot.reportedState
        mutateSlot(deviceId, slotId) {
            it.copy(
                desiredState = next,
                reportedState = next,
                status = if (next) SlotStatus.ON else SlotStatus.OFF,
                onSince = if (next) now() else null,
            )
        }
        scope.launch {
            appendUsageEvent(
                DEMO_HOME_ID,
                UsageEvent(
                    deviceId = deviceId,
                    slotId = slotId,
                    at = now(),
                    event = if (next) UsageEventType.ON else UsageEventType.OFF,
                    source = EventSource.SIMULATOR,
                ),
            )
        }
    }

    // ---- the safety worker, in miniature ------------------------------------

    /**
     * Ticks once a second and enforces `max_on_duration`, exactly as the real
     * worker does — including logging a CUTOFF usage event and raising a
     * CRITICAL alert. The demo build can therefore show a genuine automatic
     * cutoff on camera without a Node process running.
     */
    private suspend fun runSafetyLoop() {
        while (scope.isActive) {
            delay(TICK_MS)
            val nowMillis = now()
            deviceState.value.forEach { device ->
                if (device.id in unplugged) return@forEach
                device.slots.forEach { slot ->
                    if (!slot.safety.isArmed) return@forEach
                    if (slot.status != SlotStatus.ON) return@forEach
                    val elapsed = slot.onDurationSeconds(nowMillis)
                    if (elapsed >= slot.safety.maxOnDuration) {
                        cutOff(device, slot.id, elapsed)
                    }
                }
            }
        }
    }

    private suspend fun cutOff(device: Device, slotId: String, elapsedSec: Long) {
        val slot = device.slot(slotId) ?: return
        mutateSlot(device.id, slotId) {
            it.copy(
                desiredState = false,
                reportedState = false,
                status = SlotStatus.OFF,
                onSince = null,
            )
        }
        appendUsageEvent(
            DEMO_HOME_ID,
            UsageEvent(
                deviceId = device.id,
                slotId = slotId,
                at = now(),
                event = UsageEventType.CUTOFF,
                durationSec = elapsedSec,
                source = EventSource.WORKER,
            ),
        )
        alertState.update { current ->
            current + Alert(
                id = nextId("alert"),
                at = now(),
                deviceId = device.id,
                slotId = slotId,
                severity = AlertSeverity.CRITICAL,
                message = "${slot.label.ifBlank { device.name }} was switched off automatically " +
                    "after ${slot.safety.maxOnDuration}s (max_on_duration reached).",
                acknowledged = false,
            )
        }
    }

    // ---- helpers ------------------------------------------------------------

    private fun mutateDevice(deviceId: String, transform: (Device) -> Device) {
        deviceState.update { list ->
            list.map { if (it.id == deviceId) transform(it) else it }
        }
    }

    private fun mutateSlot(deviceId: String, slotId: String, transform: (Slot) -> Slot) {
        mutateDevice(deviceId) { device ->
            device.copy(slots = device.slots.map { if (it.id == slotId) transform(it) else it })
        }
    }

    private fun nextId(prefix: String) = "$prefix-${ids.incrementAndGet()}"

    private fun now() = System.currentTimeMillis()

    // ---- seed ---------------------------------------------------------------

    private fun seedDemoHome() {
        floorState.value = listOf(
            Floor(
                id = "floor-ground",
                name = "Ground floor",
                level = 0,
                planAsset = "plans/ground_floor.svg",
                gridCols = 8,
                gridRows = 6,
            ),
            Floor(
                id = "floor-upper",
                name = "First floor",
                level = 1,
                planAsset = "plans/first_floor.svg",
                gridCols = 8,
                gridRows = 6,
            ),
        )

        deviceState.value = listOf(
            Device(
                id = "dev-iron",
                floorId = "floor-ground",
                name = "Utility outlet",
                gridX = 1,
                gridY = 1,
                kind = DeviceKind.OUTLET,
                lastSeen = now(),
                link = LinkState.ONLINE,
                slots = listOf(
                    Slot(
                        id = "s1",
                        label = "Iron",
                        appliance = Appliance("Steam iron", hazardous = true, watts = 1200),
                        status = SlotStatus.OFF,
                        safety = Safety(maxOnDuration = 30L, autoCutoffEnabled = true),
                    ),
                ),
            ),
            Device(
                id = "dev-hall-gang",
                floorId = "floor-ground",
                name = "Hall gang box",
                gridX = 4,
                gridY = 2,
                kind = DeviceKind.MULTI_SWITCH,
                lastSeen = now(),
                link = LinkState.ONLINE,
                slots = listOf(
                    Slot(
                        id = "s1",
                        label = "Ceiling light",
                        appliance = Appliance("LED panel", watts = 24),
                        status = SlotStatus.OFF,
                        schedule = Schedule(
                            enabled = true,
                            onAtMinuteOfDay = 18 * 60,
                            offAtMinuteOfDay = 23 * 60,
                        ),
                    ),
                    Slot(
                        id = "s2",
                        label = "Porch light",
                        appliance = Appliance("Bulb", watts = 12),
                        status = SlotStatus.OFF,
                        schedule = Schedule(
                            enabled = true,
                            onAtMinuteOfDay = 18 * 60 + 30,
                            offAtMinuteOfDay = 6 * 60,
                        ),
                    ),
                    Slot(
                        id = "s3",
                        label = "Ceiling fan",
                        appliance = Appliance("Fan", watts = 60),
                        status = SlotStatus.OFF,
                    ),
                ),
            ),
            Device(
                id = "dev-kitchen",
                floorId = "floor-ground",
                name = "Kitchen outlet",
                gridX = 6,
                gridY = 4,
                kind = DeviceKind.OUTLET,
                lastSeen = now(),
                link = LinkState.ONLINE,
                slots = listOf(
                    Slot(
                        id = "s1",
                        label = "Kettle",
                        appliance = Appliance("Electric kettle", hazardous = true, watts = 1800),
                        status = SlotStatus.OFF,
                        safety = Safety(maxOnDuration = 120L, autoCutoffEnabled = true),
                    ),
                ),
            ),
            Device(
                id = "dev-front-cam",
                floorId = "floor-ground",
                name = "Front door camera",
                gridX = 0,
                gridY = 4,
                kind = DeviceKind.CAMERA,
                lastSeen = now(),
                link = LinkState.ONLINE,
                camera = CameraInfo(
                    snapshotUrl = "asset://cameras/front_door.svg",
                    streamUrl = "mock://stream/front_door",
                    lastFrameAt = now(),
                ),
            ),
            Device(
                id = "dev-bed-gang",
                floorId = "floor-upper",
                name = "Bedroom gang box",
                gridX = 2,
                gridY = 2,
                kind = DeviceKind.MULTI_SWITCH,
                lastSeen = now(),
                link = LinkState.ONLINE,
                slots = listOf(
                    Slot(id = "s1", label = "Bed light", appliance = Appliance("Bulb", watts = 9), status = SlotStatus.OFF),
                    Slot(id = "s2", label = "Reading lamp", appliance = Appliance("Lamp", watts = 7), status = SlotStatus.OFF),
                ),
            ),
            Device(
                id = "dev-landing-cam",
                floorId = "floor-upper",
                name = "Landing camera",
                gridX = 6,
                gridY = 1,
                kind = DeviceKind.CAMERA,
                lastSeen = now(),
                link = LinkState.ONLINE,
                camera = CameraInfo(
                    snapshotUrl = "asset://cameras/landing.svg",
                    streamUrl = "mock://stream/landing",
                    lastFrameAt = now(),
                ),
            ),
        )
    }

    companion object {
        const val DEMO_HOME_ID = "demo-home"
        private const val TICK_MS = 1_000L
        private const val STALE_AFTER_MS = 20_000L
    }
}
