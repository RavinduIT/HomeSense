package lk.ac.ucsc.scs3311.smarthome.domain.model

/**
 * A device is one *physical node*: one grid cell, one network connection, one
 * heartbeat. What it can control lives in [slots].
 *
 * The distinction matters for the multi-switch requirement: a 5-gang switch
 * plate is a single [Device] with five [Slot]s, not five devices. It occupies
 * one cell on the floor plan and appears as one entity everywhere in the system.
 */
data class Device(
    val id: String = "",
    val floorId: String = "",
    val name: String = "",
    val gridX: Int = 0,
    val gridY: Int = 0,
    val kind: DeviceKind = DeviceKind.OUTLET,
    /** Server timestamp of the last simulator heartbeat. Written only by the simulator. */
    val lastSeen: Long = 0L,
    /** Derived from [lastSeen] by the worker. Never written by the app. */
    val link: LinkState = LinkState.DISCONNECTED,
    val camera: CameraInfo? = null,
    val slots: List<Slot> = emptyList(),
) {
    /** A device is "on" if any of its slots is on — used for the master toggle. */
    val anySlotOn: Boolean get() = slots.any { it.status == SlotStatus.ON }

    /** True when every slot is on; drives the tri-state master switch. */
    val allSlotsOn: Boolean get() = slots.isNotEmpty() && slots.all { it.status == SlotStatus.ON }

    fun slot(slotId: String): Slot? = slots.firstOrNull { it.id == slotId }
}

enum class DeviceKind {
    /** Exactly one slot. A single wall socket. */
    OUTLET,

    /** One gang box, 2..6 individually addressable slots, one entity. */
    MULTI_SWITCH,

    /** No slots — nothing to toggle. Provides snapshot/stream URIs instead. */
    CAMERA,
    ;

    val supportsSlots: Boolean get() = this != CAMERA
}

/**
 * Transport-level reachability of the node, as observed by the worker from the
 * heartbeat. Separate from [SlotStatus] because a reachable device can still
 * have a faulty slot, and an unreachable device says nothing about what its
 * relay was last doing.
 */
enum class LinkState { ONLINE, DISCONNECTED }

data class CameraInfo(
    val snapshotUrl: String = "",
    val streamUrl: String = "",
    val lastFrameAt: Long = 0L,
)

/** Allowed slot counts when creating a multi-switch unit. */
val MULTI_SWITCH_SLOT_RANGE = 2..6
