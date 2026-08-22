package lk.ac.ucsc.scs3311.smarthome.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
 * Cloud-backed repository, scoped to whichever household is currently active.
 *
 * ### Why the household is a flow rather than a constructor argument
 * Which home the user may see is not known at application start. It depends on
 * the restored authentication session, on the memberships that session grants,
 * and on which of those the user last selected. Taking a fixed identifier at
 * construction time would mean the data layer had to be rebuilt on every sign-in,
 * or worse, would point at a household the signed-in account has no right to
 * read.
 *
 * Every stream is therefore derived with `flatMapLatest` over [activeHomeId].
 * Signing out emits null, which tears down every listener; signing in as someone
 * else re-subscribes against their household. No listener outlives the session
 * that was entitled to it.
 *
 * ### How synchronisation works
 * The device list is a `StateFlow` fed by Realtime Database child listeners.
 * There is no polling, no refresh call and no pull-to-refresh gesture anywhere
 * in the application: a change made by the simulator, by the safety worker, or
 * by another member arrives as a listener callback and re-renders the screen.
 *
 * ### Room's role
 * The cloud stream is mirrored into Room as a side effect. Room supplies the
 * reporting aggregates and what can be drawn before the first snapshot arrives,
 * never the live device status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeRepository(
    private val remote: RemoteHomeSource,
    private val db: HomeSenseDatabase,
    private val scope: CoroutineScope,
    private val activeHomeId: Flow<String?>,
) : HomeRepository {

    private var started = false

    private val errors = MutableStateFlow<Throwable?>(null)
    override val syncError: StateFlow<Throwable?> = errors.asStateFlow()

    override fun clearSyncError() {
        errors.value = null
    }

    /**
     * The household the write methods act on.
     *
     * Writes are user-initiated and synchronous with respect to the flows above,
     * so the identifier is cached here rather than suspended for on every call.
     * A write with no active household is dropped rather than defaulting to
     * some other home.
     */
    @Volatile
    private var writeTargetHomeId: String? = null

    private val homeId: Flow<String?> = activeHomeId
        .distinctUntilChanged()
        .onEach { writeTargetHomeId = it }

    /** Runs [block] against the active household, or does nothing if there is none. */
    private suspend inline fun <T> withHome(block: (String) -> T): T? {
        val id = writeTargetHomeId ?: return null
        return block(id)
    }

    /**
     * Recovers from a read the server refused.
     *
     * A listener is cancelled when the rules stop permitting its read: the
     * account signed out, or its membership was removed while a screen was
     * open. Both are ordinary events, and neither should be fatal.
     *
     * Placement matters more than the handling. Inside [flatMapLatest] this
     * ends only the stream for the household that was refused, and the chain
     * remains able to start a new one when another is selected. Outside it —
     * where this was — the failure ends the chain itself, and every screen
     * built from it keeps rendering the last values it saw for the rest of the
     * process, with nothing to indicate that it has stopped listening.
     */
    private fun <T> Flow<T>.recoverFromRefusal(fallback: T): Flow<T> =
        catch { error ->
            errors.value = error
            emit(fallback)
        }

    override val floors: StateFlow<List<Floor>> =
        homeId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else remote.observeFloors(id).recoverFromRefusal(emptyList())
        }
            .onEach { mirrorFloors(it) }
            .catch { errors.value = it }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val devices: StateFlow<List<Device>> =
        homeId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else remote.observeDevices(id).recoverFromRefusal(emptyList())
        }
            .onEach { mirrorDevices(it) }
            .catch { errors.value = it }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val alerts: StateFlow<List<Alert>> =
        homeId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else remote.observeAlerts(id).recoverFromRefusal(emptyList())
        }
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
        // Usage is mirrored independently of the screens that display it, so the
        // reporting figures are already available when that tab is opened.
        scope.launch {
            homeId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else remote.observeUsage(id).recoverFromRefusal(emptyList())
            }
                .catch { errors.value = it }
                .collect { mirrorUsage(it) }
        }
    }

    // ---- floors -------------------------------------------------------------

    override suspend fun saveFloor(floor: Floor): String =
        withHome { id -> remote.saveFloor(id, floor) }.orEmpty()

    override suspend fun deleteFloor(floorId: String) {
        withHome { id ->
            // Devices on a deleted floor would otherwise be orphaned: invisible
            // in the interface but still occupying a cell and still reporting.
            devices.value.filter { it.floorId == floorId }
                .forEach { remote.deleteDevice(id, it.id) }
            remote.deleteFloor(id, floorId)
        }
    }

    // ---- devices ------------------------------------------------------------

    override suspend fun saveDevice(device: Device): String =
        withHome { id -> remote.saveDevice(id, device) }.orEmpty()

    override suspend fun deleteDevice(deviceId: String) {
        withHome { id -> remote.deleteDevice(id, deviceId) }
    }

    override suspend fun moveDevice(deviceId: String, gridX: Int, gridY: Int) {
        withHome { id -> remote.moveDevice(id, deviceId, gridX, gridY) }
    }

    // ---- control ------------------------------------------------------------

    override suspend fun setSlotDesiredState(deviceId: String, slotId: String, desired: Boolean) {
        withHome { id ->
            remote.setDesiredState(id, deviceId, slotId, desired)
            logTransition(id, deviceId, slotId, desired)
        }
    }

    override suspend fun setAllSlots(deviceId: String, desired: Boolean) {
        withHome { id ->
            val device = devices.value.firstOrNull { it.id == deviceId } ?: return@withHome
            device.slots.forEach { slot ->
                remote.setDesiredState(id, deviceId, slot.id, desired)
                logTransition(id, deviceId, slot.id, desired)
            }
        }
    }

    /**
     * Appends the usage event for a user-initiated change.
     *
     * The event is logged by the component that caused the transition, here the
     * application. The worker logs its own cut-off events and the simulator logs
     * hardware-originated ones, so the log remains complete with the phone
     * switched off. The duration on an off event is computed from `onSince`,
     * which only the worker maintains, so it is authoritative.
     */
    private suspend fun logTransition(
        home: String,
        deviceId: String,
        slotId: String,
        desired: Boolean,
    ) {
        val slot = devices.value.firstOrNull { it.id == deviceId }?.slot(slotId)
        val duration = if (!desired && slot?.onSince != null) {
            ((System.currentTimeMillis() - slot.onSince) / 1000L).coerceAtLeast(0L)
        } else {
            null
        }
        runCatching {
            remote.appendUsageEvent(
                home,
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

    override suspend fun updateSlotLabel(deviceId: String, slotId: String, label: String) {
        withHome { id -> remote.updateSlotLabel(id, deviceId, slotId, label) }
    }

    override suspend fun updateAppliance(deviceId: String, slotId: String, appliance: Appliance) {
        withHome { id -> remote.updateAppliance(id, deviceId, slotId, appliance) }
    }

    override suspend fun updateSafety(deviceId: String, slotId: String, safety: Safety) {
        withHome { id -> remote.updateSafety(id, deviceId, slotId, safety) }
    }

    override suspend fun updateSchedule(deviceId: String, slotId: String, schedule: Schedule) {
        withHome { id -> remote.updateSchedule(id, deviceId, slotId, schedule) }
    }

    // ---- alerts and reporting -----------------------------------------------

    override suspend fun acknowledgeAlert(alertId: String) {
        withHome { id -> remote.acknowledgeAlert(id, alertId) }
    }

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
     * Usage rows are upserted rather than replaced wholesale: the log is
     * append-only and a row that has scrolled out of the cloud window must not
     * disappear from local history. The slot label and wattage are denormalised
     * at the same time so the report renders without a join.
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
     * `WhileSubscribed` is the usual advice and is wrong here for two reasons.
     *
     *  1. Writes need current state. Computing the duration for an off event,
     *     iterating a gang box's slots for the master control, and cascading a
     *     floor deletion all read `devices.value`. With `WhileSubscribed` that
     *     value is empty whenever no screen happens to be collecting, so those
     *     operations would silently do nothing.
     *  2. This is a monitoring application. Alerts and the usage mirror must
     *     continue while the user is on another screen.
     *
     * The cost is one open database connection for the life of the session,
     * which is the connection the application needs in any case. Signing out
     * closes it, because the upstream flow switches to an empty source.
     */
}
