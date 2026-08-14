package lk.ac.ucsc.scs3311.smarthome.data.repository

import kotlinx.coroutines.flow.Flow
import lk.ac.ucsc.scs3311.smarthome.data.local.SlotUsageTotal
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule

/**
 * The single door between the UI layer and everything behind it.
 *
 * ViewModels depend on this interface and nothing else — no Firebase type and
 * no Room type crosses it. That is what lets the whole app run against
 * [FakeHomeRepository] in the `demo` flavour, and what lets the sync behaviour
 * be tested on the JVM with no emulator.
 */
interface HomeRepository {

    val floors: Flow<List<Floor>>

    val devices: Flow<List<Device>>

    val alerts: Flow<List<Alert>>

    val unacknowledgedAlertCount: Flow<Int>

    fun devicesOnFloor(floorId: String): Flow<List<Device>>

    fun device(deviceId: String): Flow<Device?>

    // ---- floors -------------------------------------------------------------

    suspend fun saveFloor(floor: Floor): String

    suspend fun deleteFloor(floorId: String)

    // ---- devices ------------------------------------------------------------

    suspend fun saveDevice(device: Device): String

    suspend fun deleteDevice(deviceId: String)

    suspend fun moveDevice(deviceId: String, gridX: Int, gridY: Int)

    // ---- control ------------------------------------------------------------

    /**
     * Requests a slot state change. Writes `desiredState` and logs a usage
     * event; it does **not** claim the change happened. The UI learns that from
     * `status`, which only the worker writes.
     */
    suspend fun setSlotDesiredState(deviceId: String, slotId: String, desired: Boolean)

    /** Sets every slot of a multi-switch at once, from the master toggle. */
    suspend fun setAllSlots(deviceId: String, desired: Boolean)

    // ---- slot configuration -------------------------------------------------

    suspend fun updateSlotLabel(deviceId: String, slotId: String, label: String)

    suspend fun updateAppliance(deviceId: String, slotId: String, appliance: Appliance)

    suspend fun updateSafety(deviceId: String, slotId: String, safety: Safety)

    suspend fun updateSchedule(deviceId: String, slotId: String, schedule: Schedule)

    // ---- alerts and reporting -----------------------------------------------

    suspend fun acknowledgeAlert(alertId: String)

    fun usageTotals(fromMillis: Long, toMillis: Long): Flow<List<SlotUsageTotal>>

    fun totalOnSeconds(fromMillis: Long, toMillis: Long): Flow<Long>

    fun cutoffCount(fromMillis: Long, toMillis: Long): Flow<Int>

    /** Rows for the CSV export, oldest first. */
    suspend fun usageRows(fromMillis: Long, toMillis: Long): List<UsageRow>

    /** Starts cloud synchronisation. Safe to call more than once. */
    fun start()
}

/** One flattened row of the usage log, ready to be written to CSV. */
data class UsageRow(
    val at: Long,
    val deviceName: String,
    val slotLabel: String,
    val event: String,
    val durationSec: Long?,
    val source: String,
)
