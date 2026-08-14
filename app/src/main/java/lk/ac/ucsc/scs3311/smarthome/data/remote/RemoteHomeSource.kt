package lk.ac.ucsc.scs3311.smarthome.data.remote

import kotlinx.coroutines.flow.Flow
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEvent

/**
 * Everything the app is allowed to do to the cloud, as one interface.
 *
 * The interface exists so the repository can be unit-tested against an
 * in-memory fake — which is also what proves that an *externally* originated
 * change (the simulator, the worker) reaches the UI without a manual refresh.
 *
 * Note what is **not** here: nothing writes `status`, `link`, `reportedState`
 * or `lastSeen`. Those belong to the worker and the simulator. The absence is
 * the design, and the database rules enforce it independently.
 */
interface RemoteHomeSource {

    fun observeFloors(homeId: String): Flow<List<Floor>>

    fun observeDevices(homeId: String): Flow<List<Device>>

    fun observeAlerts(homeId: String): Flow<List<Alert>>

    fun observeUsage(homeId: String): Flow<List<UsageEvent>>

    // ---- floor management ---------------------------------------------------

    /** Creates the floor when [Floor.id] is blank, otherwise updates it. Returns the id. */
    suspend fun saveFloor(homeId: String, floor: Floor): String

    suspend fun deleteFloor(homeId: String, floorId: String)

    // ---- device management --------------------------------------------------

    /** Creates the device when [Device.id] is blank, otherwise updates its metadata. */
    suspend fun saveDevice(homeId: String, device: Device): String

    suspend fun deleteDevice(homeId: String, deviceId: String)

    suspend fun moveDevice(homeId: String, deviceId: String, gridX: Int, gridY: Int)

    // ---- the one control write ----------------------------------------------

    /**
     * The only state write the app ever makes: "I want this slot on/off".
     * What actually happens to the relay is the simulator's business, and what
     * the UI shows is the worker's.
     */
    suspend fun setDesiredState(
        homeId: String,
        deviceId: String,
        slotId: String,
        desired: Boolean,
    )

    // ---- slot configuration -------------------------------------------------

    suspend fun updateSlotLabel(homeId: String, deviceId: String, slotId: String, label: String)

    suspend fun updateAppliance(
        homeId: String,
        deviceId: String,
        slotId: String,
        appliance: Appliance,
    )

    suspend fun updateSafety(homeId: String, deviceId: String, slotId: String, safety: Safety)

    suspend fun updateSchedule(homeId: String, deviceId: String, slotId: String, schedule: Schedule)

    // ---- append-only log ----------------------------------------------------

    suspend fun appendUsageEvent(homeId: String, event: UsageEvent)

    suspend fun acknowledgeAlert(homeId: String, alertId: String)

    /** Signs in anonymously if needed; returns the uid. */
    suspend fun ensureSignedIn(): String
}
