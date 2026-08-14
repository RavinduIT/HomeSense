package lk.ac.ucsc.scs3311.smarthome.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.Appliance
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.Safety
import lk.ac.ucsc.scs3311.smarthome.domain.model.Schedule
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEvent

/**
 * Realtime Database implementation of [RemoteHomeSource].
 *
 * ### Why child listeners rather than one value listener
 * `addChildEventListener` delivers one callback per changed child. Toggling a
 * single slot therefore costs one small `onChildChanged` carrying that device,
 * not a re-send of the whole `/devices` subtree. That is the property that
 * makes the toggle grid feel instant, and it is the main reason this project
 * uses Realtime Database rather than Firestore — see docs/adr/0001.
 *
 * ### Why callbackFlow
 * Firebase's API is callback-based and must be unregistered to avoid leaking.
 * `callbackFlow` + `awaitClose` binds the listener's lifetime to the collector's
 * scope, so a listener is removed the moment the ViewModel that collects it is
 * cleared. No manual teardown, no leaked listeners on rotation.
 */
class RealtimeDatabaseSource(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
) : RemoteHomeSource {

    private fun home(homeId: String): DatabaseReference =
        database.reference.child(NODE_HOMES).child(homeId)

    // ---- observation --------------------------------------------------------

    /**
     * Wraps a Firebase child listener as a cold [Flow] of the whole child list.
     *
     * The running collection is held in a [LinkedHashMap] keyed by child id, so
     * an update replaces exactly one entry and insertion order is stable — the
     * device list does not reshuffle itself under the user's finger when an
     * unrelated device reports in.
     */
    private fun <T> DatabaseReference.childListFlow(
        tag: String,
        parse: (id: String, raw: Map<*, *>) -> T?,
    ): Flow<List<T>> = callbackFlow {
        val items = LinkedHashMap<String, T>()

        fun emit() {
            trySend(items.values.toList())
        }

        fun put(snapshot: DataSnapshot) {
            val id = snapshot.key ?: return
            val raw = snapshot.value as? Map<*, *>
            if (raw == null) {
                items.remove(id)
            } else {
                runCatching { parse(id, raw) }
                    .onSuccess { parsed -> if (parsed != null) items[id] = parsed }
                    .onFailure { Log.w(TAG, "Dropping malformed $tag/$id", it) }
            }
            emit()
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousName: String?) = put(snapshot)
            override fun onChildChanged(snapshot: DataSnapshot, previousName: String?) = put(snapshot)
            override fun onChildMoved(snapshot: DataSnapshot, previousName: String?) = Unit

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { items.remove(it) }
                emit()
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        addChildEventListener(listener)
        // Firebase replays every existing child through onChildAdded, so an
        // empty node would otherwise never emit and the UI would hang on a
        // spinner forever. Emit the empty list up front instead.
        emit()

        awaitClose { removeEventListener(listener) }
    }.conflate()

    override fun observeFloors(homeId: String): Flow<List<Floor>> =
        home(homeId).child(NODE_FLOORS).childListFlow("floors", Wire::floorFrom)

    override fun observeDevices(homeId: String): Flow<List<Device>> =
        home(homeId).child(NODE_DEVICES).childListFlow("devices", Wire::deviceFrom)

    override fun observeAlerts(homeId: String): Flow<List<Alert>> =
        home(homeId).child(NODE_ALERTS).childListFlow("alerts", Wire::alertFrom)

    /**
     * Usage is nested `usage/{deviceId}/{slotId}/{eventId}`, so one child
     * listener at the top yields a per-device subtree that is flattened here.
     */
    override fun observeUsage(homeId: String): Flow<List<UsageEvent>> =
        home(homeId).child(NODE_USAGE)
            .childListFlow("usage") { deviceId, raw -> usageEventsOf(deviceId, raw) }
            // One child per device, each carrying that device's whole log, so
            // the per-device lists are flattened into a single stream.
            .map { perDevice -> perDevice.flatten() }

    private fun usageEventsOf(deviceId: String, raw: Map<*, *>): List<UsageEvent> =
        raw.entries.flatMap { (slotKey, slotValue) ->
            val slotId = slotKey as? String ?: return@flatMap emptyList()
            val events = slotValue as? Map<*, *> ?: return@flatMap emptyList()
            events.entries.mapNotNull { (eventKey, eventValue) ->
                val eventId = eventKey as? String ?: return@mapNotNull null
                val event = eventValue as? Map<*, *> ?: return@mapNotNull null
                Wire.usageEventFrom(eventId, deviceId, slotId, event)
            }
        }

    // ---- floors -------------------------------------------------------------

    override suspend fun saveFloor(homeId: String, floor: Floor): String {
        val floors = home(homeId).child(NODE_FLOORS)
        val ref = if (floor.id.isBlank()) floors.push() else floors.child(floor.id)
        ref.updateChildren(Wire.floorToMap(floor)).await()
        return ref.key.orEmpty()
    }

    override suspend fun deleteFloor(homeId: String, floorId: String) {
        home(homeId).child(NODE_FLOORS).child(floorId).removeValue().await()
    }

    // ---- devices ------------------------------------------------------------

    override suspend fun saveDevice(homeId: String, device: Device): String {
        val devices = home(homeId).child(NODE_DEVICES)
        return if (device.id.isBlank()) {
            val ref = devices.push()
            ref.setValue(Wire.deviceToCreateMap(device)).await()
            ref.key.orEmpty()
        } else {
            // An update must not clobber slots/status/link written by the other
            // two actors, so only the metadata fields are sent.
            devices.child(device.id).updateChildren(
                mapOf(
                    "floorId" to device.floorId,
                    "name" to device.name,
                    "gridX" to device.gridX,
                    "gridY" to device.gridY,
                ),
            ).await()
            device.id
        }
    }

    override suspend fun deleteDevice(homeId: String, deviceId: String) {
        home(homeId).child(NODE_DEVICES).child(deviceId).removeValue().await()
    }

    override suspend fun moveDevice(homeId: String, deviceId: String, gridX: Int, gridY: Int) {
        home(homeId).child(NODE_DEVICES).child(deviceId)
            .updateChildren(mapOf("gridX" to gridX, "gridY" to gridY))
            .await()
    }

    // ---- control ------------------------------------------------------------

    override suspend fun setDesiredState(
        homeId: String,
        deviceId: String,
        slotId: String,
        desired: Boolean,
    ) {
        slot(homeId, deviceId, slotId).child(FIELD_DESIRED_STATE).setValue(desired).await()
    }

    // ---- slot configuration -------------------------------------------------

    override suspend fun updateSlotLabel(
        homeId: String,
        deviceId: String,
        slotId: String,
        label: String,
    ) {
        slot(homeId, deviceId, slotId).child("label").setValue(label).await()
    }

    override suspend fun updateAppliance(
        homeId: String,
        deviceId: String,
        slotId: String,
        appliance: Appliance,
    ) {
        slot(homeId, deviceId, slotId).child("appliance")
            .updateChildren(Wire.applianceToMap(appliance)).await()
    }

    override suspend fun updateSafety(
        homeId: String,
        deviceId: String,
        slotId: String,
        safety: Safety,
    ) {
        slot(homeId, deviceId, slotId).child("safety")
            .updateChildren(Wire.safetyToMap(safety)).await()
    }

    override suspend fun updateSchedule(
        homeId: String,
        deviceId: String,
        slotId: String,
        schedule: Schedule,
    ) {
        slot(homeId, deviceId, slotId).child("schedule")
            .setValue(Wire.scheduleToMap(schedule)).await()
    }

    // ---- append-only --------------------------------------------------------

    override suspend fun appendUsageEvent(homeId: String, event: UsageEvent) {
        val ref = home(homeId)
            .child(NODE_USAGE)
            .child(event.deviceId)
            .child(event.slotId)
            .push()
        // `at` is stamped by the server so that phones with skewed clocks cannot
        // corrupt the ordering of the usage log.
        val payload = Wire.usageEventToMap(event).toMutableMap()
        payload["at"] = ServerValue.TIMESTAMP
        ref.setValue(payload).await()
    }

    override suspend fun acknowledgeAlert(homeId: String, alertId: String) {
        home(homeId).child(NODE_ALERTS).child(alertId)
            .child("acknowledged").setValue(true).await()
    }

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return result.user?.uid.orEmpty()
    }

    private fun slot(homeId: String, deviceId: String, slotId: String): DatabaseReference =
        home(homeId).child(NODE_DEVICES).child(deviceId).child(NODE_SLOTS).child(slotId)

    private companion object {
        const val TAG = "RtdbSource"
        const val NODE_HOMES = "homes"
        const val NODE_FLOORS = "floors"
        const val NODE_DEVICES = "devices"
        const val NODE_SLOTS = "slots"
        const val NODE_USAGE = "usage"
        const val NODE_ALERTS = "alerts"
        const val FIELD_DESIRED_STATE = "desiredState"
    }
}
