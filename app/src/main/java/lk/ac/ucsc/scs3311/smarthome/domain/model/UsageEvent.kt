package lk.ac.ucsc.scs3311.smarthome.domain.model

/**
 * An append-only record of one state transition.
 *
 * Usage is *logged at the moment of the transition* by whichever actor caused
 * it — never recomputed later by diffing snapshots. Diffing would silently lose
 * every transition that happened while the app was closed, which is exactly the
 * window the safety worker exists to cover.
 */
data class UsageEvent(
    val id: String = "",
    val deviceId: String = "",
    val slotId: String = "",
    val at: Long = 0L,
    val event: UsageEventType = UsageEventType.ON,
    /** Populated on OFF/CUTOFF events: how long the slot had been on. */
    val durationSec: Long? = null,
    val source: EventSource = EventSource.APP,
)

enum class UsageEventType {
    ON,
    OFF,

    /** The worker forced the slot off because max_on_duration was breached. */
    CUTOFF,
    ERROR,
    RECONNECT,
}

enum class EventSource {
    /** A user tapped a control in the mobile app. */
    APP,

    /** The change originated at the hardware itself. */
    SIMULATOR,

    /** The safety worker acted — a cutoff or a fault classification. */
    WORKER,

    /** A configured on/off window fired. */
    SCHEDULE,
}
