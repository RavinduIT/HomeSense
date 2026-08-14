package lk.ac.ucsc.scs3311.smarthome.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import lk.ac.ucsc.scs3311.smarthome.data.local.AlertEntity
import lk.ac.ucsc.scs3311.smarthome.data.local.DeviceLayoutEntity
import lk.ac.ucsc.scs3311.smarthome.data.local.FloorEntity
import lk.ac.ucsc.scs3311.smarthome.data.local.HomeSenseDatabase
import lk.ac.ucsc.scs3311.smarthome.data.local.SlotUsageTotal
import lk.ac.ucsc.scs3311.smarthome.data.local.UsageEventEntity
import lk.ac.ucsc.scs3311.smarthome.data.remote.RemoteHomeSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.EventSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEvent
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEventType

/**
 * Cloud-backed repository.
 *
 * ### How synchronisation actually works here
 * The device list is a `StateFlow` fed directly by the Realtime Database child
 * listeners. There is no polling, no refresh call and no pull-to-refresh
 * gesture anywhere in the app — a change made by the simulator, by the safety
 * worker, or by another phone arrives as a listener callback and re-renders the
 * screen. "No manual refresh" is not a feature that was added; it is the only
 * behaviour this design can produce.
 *
 * ### Room's role
 * The cloud stream is *mirrored* into Room as a side effect. Room is the source
 * for reporting aggregates and for what the app can draw before the first
 * snapshot arrives — never the source of live device status.
 */
class DefaultHomeRepository(
    private val remote: RemoteHomeSource,
    private val db: HomeSenseDatabase,
    private val scope: CoroutineScope,
    private val homeId: String,
) : HomeRepository {

    private var started = false

    private val errors = MutableStateFlow<Throwable?>(null)

    override val floors: StateFlow<List<Floor>> =
        remote.observeFloors(homeId)
            .onEach { mirrorFloors(it) }
            .catch { errors.value = it }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val devices: StateFlow<List<Device>> =
        remote.observeDevices(homeId)
            .onEach { mirrorDevices(it) }
            .catch { errors.value = it }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val alerts: StateFlow<List<Alert>> =
        remote.observeAlerts(homeId)
            .map { list -> list.sortedByDescending { it.at } }
            .onEach { mirrorAlerts(it) }
            .catch { errors.value = it }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val unacknowledgedAlertCount: Flow<Int> =
        alerts.map { list -> list.count { !it.acknowledged } }.distinctUntilChanged()

    override fun devicesOnFloor(floorId: String): Flow<List<Device>> =
        devices.map { list -> list.filter { it.floorId == floorId } }.distinctUntilChanged()

    override fun device(deviceId: String): Flow<Device?> =
        devices.map { list -> list.firstOrNull { it.id == deviceId } }.distinctUntilChanged()

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            runCatching { remote.ensureSignedIn() }
                .onFailure { errors.value = it }
        }
        // Usage is mirrored independently of the screens that show it, so the
        // reporting numbers are already warm when the user opens that tab.
        scope.launch {
            remote.observeUsage(homeId)
                .catch { errors.value = it }
                .collect { mirrorUsage(it) }
        }
    }

    // ---- floors -------------------------------------------------------------

    override suspend fun saveFloor(floor: Floor): String = remote.saveFloor(homeId, floor)

    override suspend fun deleteFloor(floorId: String) {
        // Devices on a deleted floor would otherwise be orphaned: invisible in
        // the UI but still consuming a grid cell and still reporting.
        devices.value.filter { it.floorId == floorId }
            .forEach { remote.deleteDevice(homeId, it.id) }
        remote.deleteFloor(homeId, floorId)
    }

    // ---- devices ------------------------------------------------------------

    override suspend fun saveDevice(device: Device): String = remote.saveDevice(homeId, device)

    override suspend fun deleteDevice(deviceId: String) = remote.deleteDevice(homeId, deviceId)

    override suspend fun moveDevice(deviceId: String, gridX: Int, gridY: Int) =
        remote.moveDevice(homeId, deviceId, gridX, gridY)

    // ---- control ------------------------------------------------------------

    override suspend fun setSlotDesiredState(deviceId: String, slotId: String, desired: Boolean) {
        remote.setDesiredState(homeId, deviceId, slotId, desired)
        logTransition(deviceId, slotId, desired)
    }

    override suspend fun setAllSlots(deviceId: String, desired: Boolean) {
        val device = devices.value.firstOrNull { it.id == deviceId } ?: return
        device.slots.forEach { slot ->
            remote.setDesiredState(homeId, deviceId, slot.id, desired)
            logTransition(deviceId, slot.id, desired)
        }
    }

    /**
     * Appends the usage event for a user-initiated change.
     *
     * The event is logged by the actor that caused the transition — here, the
     * app. The worker logs its own CUTOFF events and the simulator logs
     * hardware-originated ones, so the log stays complete even with the phone
     * switched off. `durationSec` on an OFF is computed from `onSince`, which
     * only the worker maintains, so it is authoritative.
     */
    private suspend fun logTransition(deviceId: String, slotId: String, desired: Boolean) {
        val slot = devices.value.firstOrNull { it.id == deviceId }?.slot(slotId)
        val duration = if (!desired && slot?.onSince != null) {
            ((System.currentTimeMillis() - slot.onSince) / 1000L).coerceAtLeast(0L)
        } else {
            null
        }
        runCatching {
            remote.appendUsageEvent(
                homeId,
                UsageEvent(
                    deviceId = deviceId,
                    slotId = slotId,
                    at = System.currentTimeMillis(),
                    event = if (desired) UsageEventType.ON else UsageEventType.OFF,
                    durationSec = duration,
                    source = EventSource.APP,
                ),
            )
        }.onFailure { errors.value = it }
    }

    // ---- slot configuration -------------------------------------------------

    override suspend fun updateSlotLabel(deviceId: String, slotId: String, label: String) =
        remote.updateSlotLabel(homeId, deviceId, slotId, label)

    override suspend fun updateAppliance(deviceId: String, slotId: String, appliance: Appliance) =
        remote.updateAppliance(homeId, deviceId, slotId, appliance)

    override suspend fun updateSafety(deviceId: String, slotId: String, safety: Safety) =
        remote.updateSafety(homeId, deviceId, slotId, safety)

    override suspend fun updateSchedule(deviceId: String, slotId: String, schedule: Schedule) =
        remote.updateSchedule(homeId, deviceId, slotId, schedule)

    // ---- alerts and reporting -----------------------------------------------

    override suspend fun acknowledgeAlert(alertId: String) = remote.acknowledgeAlert(homeId, alertId)

    override fun usageTotals(fromMillis: Long, toMillis: Long): Flow<List<SlotUsageTotal>> =
        db.usageDao().observeTotals(fromMillis, toMillis)

    override fun totalOnSeconds(fromMillis: Long, toMillis: Long): Flow<Long> =
        db.usageDao().observeTotalOnSeconds(fromMillis, toMillis)

    override fun cutoffCount(fromMillis: Long, toMillis: Long): Flow<Int> =
        db.usageDao().observeCutoffCount(fromMillis, toMillis)

    override suspend fun usageRows(fromMillis: Long, toMillis: Long): List<UsageRow> {
        val deviceNames = devices.value.associate { it.id to it.name }
        return db.usageDao().between(fromMillis, toMillis).map { row ->
            UsageRow(
                at = row.at,
                deviceName = deviceNames[row.deviceId] ?: row.deviceId,
                slotLabel = row.slotLabel.ifBlank { row.slotId },
                event = row.event,
                durationSec = row.durationSec,
                source = row.source,
            )
        }
    }

    // ---- mirroring ----------------------------------------------------------

    private suspend fun mirrorFloors(list: List<Floor>) {
        runCatching { db.floorDao().replaceAll(list.map(FloorEntity::from)) }
    }

    private suspend fun mirrorDevices(list: List<Device>) {
        runCatching {
            db.deviceLayoutDao().replaceAll(
                list.map {
                    DeviceLayoutEntity(
                        id = it.id,
                        floorId = it.floorId,
                        name = it.name,
                        gridX = it.gridX,
                        gridY = it.gridY,
                        kind = it.kind.name,
                        slotCount = it.slots.size,
                    )
                },
            )
        }
    }

    private suspend fun mirrorAlerts(list: List<Alert>) {
        runCatching { db.alertDao().replaceAll(list.map(AlertEntity::from)) }
    }

    /**
     * Usage rows are upserted, never replaced wholesale: the log is append-only
     * and a row that has scrolled out of the cloud window must not vanish from
     * the local history. The slot label and wattage are denormalised in at the
     * same time so the report can render without a join.
     */
    private suspend fun mirrorUsage(list: List<UsageEvent>) {
        if (list.isEmpty()) return
        val slotIndex = devices.value.flatMap { device ->
            device.slots.map { slot -> (device.id to slot.id) to slot }
        }.toMap()

        runCatching {
            db.usageDao().upsertAll(
                list.map { event ->
                    val slot = slotIndex[event.deviceId to event.slotId]
                    UsageEventEntity.from(
                        event = event,
                        slotLabel = slot?.label.orEmpty(),
                        watts = slot?.appliance?.watts,
                    )
                },
            )
        }
    }

    /*
     * ### Why `SharingStarted.Eagerly` and not `WhileSubscribed`
     *
     * `WhileSubscribed` is the usual advice, and it is wrong here for two
     * concrete reasons:
     *
     *  1. **Writes need current state.** Computing `durationSec` for an OFF
     *     event, iterating a gang box's slots for the master toggle, and
     *     cascading a floor deletion all read `devices.value`. With
     *     `WhileSubscribed` that value is empty whenever no screen happens to
     *     be collecting, so those operations would silently do nothing.
     *  2. **This is a monitoring app.** Alerts and the usage mirror must keep
     *     flowing while the user is on another screen. Tearing the listeners
     *     down on every navigation is the opposite of what the product does.
     *
     * The cost is one always-open Realtime Database connection for the life of
     * the process, which is exactly the connection the app needs anyway.
     */
}
