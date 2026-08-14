package lk.ac.ucsc.scs3311.smarthome.data.remote

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

/**
 * Conversion between the Realtime Database wire format and the domain models.
 *
 * These are **pure functions over plain maps**, deliberately not Firebase
 * annotation-driven POJOs. Three reasons:
 *
 *  1. They can be unit-tested on the JVM with no emulator and no Firebase.
 *  2. Nothing depends on reflection, so R8 cannot silently break release builds
 *     by renaming a field.
 *  3. `max_on_duration` keeps the exact name the specification uses, and the
 *     rename to an idiomatic Kotlin property happens in one visible place.
 *
 * Every getter tolerates a missing or wrong-typed value, because the simulator
 * and the worker write into the same tree and a half-written node must never
 * crash the phone.
 */
object Wire {

    // ---- primitive coercion -------------------------------------------------
    // RTDB hands back Long for whole numbers and Double for anything with a
    // decimal point, so numbers are read defensively rather than cast.

    private fun Map<*, *>.str(key: String, default: String = ""): String =
        (this[key] as? String) ?: default

    private fun Map<*, *>.bool(key: String, default: Boolean = false): Boolean =
        (this[key] as? Boolean) ?: default

    private fun Map<*, *>.long(key: String, default: Long = 0L): Long = when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        is String -> v.toLongOrNull() ?: default
        else -> default
    }

    private fun Map<*, *>.longOrNull(key: String): Long? =
        if (this[key] == null) null else long(key)

    private fun Map<*, *>.int(key: String, default: Int = 0): Int = long(key, default.toLong()).toInt()

    private fun Map<*, *>.intOrNull(key: String): Int? =
        if (this[key] == null) null else int(key)

    private fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>

    private fun Map<*, *>.intList(key: String): List<Int> = when (val v = this[key]) {
        is List<*> -> v.mapNotNull { (it as? Number)?.toInt() }
        // RTDB collapses sparse arrays into maps keyed by index.
        is Map<*, *> -> v.values.mapNotNull { (it as? Number)?.toInt() }
        else -> emptyList()
    }

    private inline fun <reified E : Enum<E>> Map<*, *>.enum(key: String, default: E): E =
        runCatching { enumValueOf<E>(str(key).uppercase()) }.getOrDefault(default)

    // ---- floors -------------------------------------------------------------

    fun floorFrom(id: String, raw: Map<*, *>): Floor = Floor(
        id = id,
        name = raw.str("name"),
        level = raw.int("level"),
        planAsset = raw.str("planAsset"),
        gridCols = raw.int("gridCols", Floor.DEFAULT_COLS).coerceIn(Floor.COL_RANGE),
        gridRows = raw.int("gridRows", Floor.DEFAULT_ROWS).coerceIn(Floor.ROW_RANGE),
    )

    fun floorToMap(floor: Floor): Map<String, Any?> = mapOf(
        "name" to floor.name,
        "level" to floor.level,
        "planAsset" to floor.planAsset,
        "gridCols" to floor.gridCols,
        "gridRows" to floor.gridRows,
    )

    // ---- devices ------------------------------------------------------------

    fun deviceFrom(id: String, raw: Map<*, *>): Device {
        val kind = raw.enum("kind", DeviceKind.OUTLET)
        val slotsRaw = raw.map("slots")
        val slots = slotsRaw?.entries
            ?.mapNotNull { (slotId, slotValue) ->
                val slotMap = slotValue as? Map<*, *> ?: return@mapNotNull null
                slotFrom(slotId as? String ?: return@mapNotNull null, slotMap)
            }
            // Slot ids are generated as s1, s2, ... so lexical order is display
            // order for up to 9 slots, which is above the 6-slot maximum.
            ?.sortedBy { it.id }
            .orEmpty()

        return Device(
            id = id,
            floorId = raw.str("floorId"),
            name = raw.str("name"),
            gridX = raw.int("gridX"),
            gridY = raw.int("gridY"),
            kind = kind,
            lastSeen = raw.long("lastSeen"),
            link = raw.enum("link", LinkState.DISCONNECTED),
            camera = raw.map("camera")?.let { cameraFrom(it) },
            slots = slots,
        )
    }

    private fun cameraFrom(raw: Map<*, *>) = CameraInfo(
        snapshotUrl = raw.str("snapshotUrl"),
        streamUrl = raw.str("streamUrl"),
        lastFrameAt = raw.long("lastFrameAt"),
    )

    fun slotFrom(id: String, raw: Map<*, *>): Slot {
        val safetyRaw = raw.map("safety")
        val scheduleRaw = raw.map("schedule")
        val applianceRaw = raw.map("appliance")

        return Slot(
            id = id,
            label = raw.str("label"),
            appliance = Appliance(
                name = applianceRaw?.str("name").orEmpty(),
                hazardous = applianceRaw?.bool("hazardous") ?: false,
                watts = applianceRaw?.intOrNull("watts"),
            ),
            desiredState = raw.bool("desiredState"),
            reportedState = raw.bool("reportedState"),
            status = raw.enum("status", SlotStatus.OFF),
            onSince = raw.longOrNull("onSince"),
            safety = Safety(
                // The spec's exact spelling, preserved on the wire.
                maxOnDuration = safetyRaw?.long(KEY_MAX_ON_DURATION) ?: 0L,
                autoCutoffEnabled = safetyRaw?.bool("autoCutoffEnabled") ?: false,
            ),
            schedule = Schedule(
                enabled = scheduleRaw?.bool("enabled") ?: false,
                onAtMinuteOfDay = scheduleRaw?.int("onAtMinuteOfDay") ?: 0,
                offAtMinuteOfDay = scheduleRaw?.int("offAtMinuteOfDay") ?: 0,
                days = scheduleRaw?.intList("days").orEmpty(),
            ),
        )
    }

    /**
     * Full device payload, used only when *creating* a device.
     *
     * Note what is absent: `status`, `link` and `onSince` are never written by
     * the app. A new device starts DISCONNECTED and stays that way until the
     * simulator sends its first heartbeat and the worker promotes it — which is
     * the honest answer, since nothing has reported in yet.
     */
    fun deviceToCreateMap(device: Device): Map<String, Any?> = buildMap {
        put("floorId", device.floorId)
        put("name", device.name)
        put("gridX", device.gridX)
        put("gridY", device.gridY)
        put("kind", device.kind.name)
        put("lastSeen", 0L)
        put("link", LinkState.DISCONNECTED.name)
        device.camera?.let {
            put(
                "camera",
                mapOf(
                    "snapshotUrl" to it.snapshotUrl,
                    "streamUrl" to it.streamUrl,
                    "lastFrameAt" to it.lastFrameAt,
                ),
            )
        }
        if (device.slots.isNotEmpty()) {
            put("slots", device.slots.associate { it.id to slotToCreateMap(it) })
        }
    }

    private fun slotToCreateMap(slot: Slot): Map<String, Any?> = mapOf(
        "label" to slot.label,
        "appliance" to applianceToMap(slot.appliance),
        "desiredState" to slot.desiredState,
        "reportedState" to false,
        "status" to SlotStatus.DISCONNECTED.name,
        "onSince" to null,
        "safety" to safetyToMap(slot.safety),
        "schedule" to scheduleToMap(slot.schedule),
    )

    fun applianceToMap(appliance: Appliance): Map<String, Any?> = mapOf(
        "name" to appliance.name,
        "hazardous" to appliance.hazardous,
        "watts" to appliance.watts,
    )

    fun safetyToMap(safety: Safety): Map<String, Any?> = mapOf(
        KEY_MAX_ON_DURATION to safety.maxOnDuration,
        "autoCutoffEnabled" to safety.autoCutoffEnabled,
    )

    fun scheduleToMap(schedule: Schedule): Map<String, Any?> = mapOf(
        "enabled" to schedule.enabled,
        "onAtMinuteOfDay" to schedule.onAtMinuteOfDay,
        "offAtMinuteOfDay" to schedule.offAtMinuteOfDay,
        "days" to schedule.days,
    )

    // ---- usage --------------------------------------------------------------

    fun usageEventFrom(id: String, deviceId: String, slotId: String, raw: Map<*, *>) = UsageEvent(
        id = id,
        deviceId = deviceId,
        slotId = slotId,
        at = raw.long("at"),
        event = raw.enum("event", UsageEventType.ON),
        durationSec = raw.longOrNull("durationSec"),
        source = raw.enum("source", EventSource.APP),
    )

    fun usageEventToMap(event: UsageEvent): Map<String, Any?> = mapOf(
        "at" to event.at,
        "event" to event.event.name,
        "durationSec" to event.durationSec,
        "source" to event.source.name,
    )

    // ---- alerts -------------------------------------------------------------

    fun alertFrom(id: String, raw: Map<*, *>) = Alert(
        id = id,
        at = raw.long("at"),
        deviceId = raw.str("deviceId"),
        slotId = raw.str("slotId"),
        severity = raw.enum("severity", AlertSeverity.INFO),
        message = raw.str("message"),
        acknowledged = raw.bool("acknowledged"),
    )

    const val KEY_MAX_ON_DURATION = "max_on_duration"
}
