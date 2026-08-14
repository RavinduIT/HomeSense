package lk.ac.ucsc.scs3311.smarthome.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.AlertSeverity
import lk.ac.ucsc.scs3311.smarthome.domain.model.EventSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEvent
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEventType

/**
 * Room is not required by the specification. It is here for two deliberate
 * reasons, both defensible:
 *
 *  1. **Offline resilience** — the floor plan and the last known device states
 *     render immediately on a cold start, before the first cloud snapshot
 *     arrives, and survive a flight-mode launch.
 *  2. **Usage aggregation** — the reporting screen runs SQL sums and group-bys
 *     over thousands of events. Doing that in Kotlin over a Firebase snapshot
 *     would mean holding the whole log in memory and recomputing on every emit.
 *
 * Cached device *state* is deliberately **not** stored: a stale ON badge from
 * last week is worse than an honest DISCONNECTED. Only usage, alerts and the
 * floor/device layout are persisted.
 */
@Entity(tableName = "floors")
data class FloorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val level: Int,
    val planAsset: String,
    val gridCols: Int,
    val gridRows: Int,
) {
    fun toDomain() = Floor(
        id = id,
        name = name,
        level = level,
        planAsset = planAsset,
        gridCols = gridCols,
        gridRows = gridRows,
    )

    companion object {
        fun from(floor: Floor) = FloorEntity(
            id = floor.id,
            name = floor.name,
            level = floor.level,
            planAsset = floor.planAsset,
            gridCols = floor.gridCols,
            gridRows = floor.gridRows,
        )
    }
}

/** Layout only — enough to draw the plan offline. State comes from the cloud. */
@Entity(tableName = "device_layout", indices = [Index("floorId")])
data class DeviceLayoutEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val name: String,
    val gridX: Int,
    val gridY: Int,
    val kind: String,
    val slotCount: Int,
)

/**
 * One append-only usage event.
 *
 * [id] is the cloud-assigned push key, so re-syncing the same event is an
 * idempotent `INSERT OR REPLACE` rather than a duplicate row. Without that, a
 * reconnect would double every number on the reporting screen.
 */
@Entity(
    tableName = "usage_events",
    indices = [Index("deviceId", "slotId"), Index("at")],
)
data class UsageEventEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val slotId: String,
    val at: Long,
    val event: String,
    val durationSec: Long?,
    val source: String,
    /** Denormalised so the report can label rows without joining the cloud. */
    val slotLabel: String = "",
    val watts: Int? = null,
) {
    fun toDomain() = UsageEvent(
        id = id,
        deviceId = deviceId,
        slotId = slotId,
        at = at,
        event = runCatching { UsageEventType.valueOf(event) }.getOrDefault(UsageEventType.ON),
        durationSec = durationSec,
        source = runCatching { EventSource.valueOf(source) }.getOrDefault(EventSource.APP),
    )

    companion object {
        fun from(event: UsageEvent, slotLabel: String = "", watts: Int? = null) = UsageEventEntity(
            id = event.id,
            deviceId = event.deviceId,
            slotId = event.slotId,
            at = event.at,
            event = event.event.name,
            durationSec = event.durationSec,
            source = event.source.name,
            slotLabel = slotLabel,
            watts = watts,
        )
    }
}

@Entity(tableName = "alerts", indices = [Index("at")])
data class AlertEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val deviceId: String,
    val slotId: String,
    val severity: String,
    val message: String,
    val acknowledged: Boolean,
) {
    fun toDomain() = Alert(
        id = id,
        at = at,
        deviceId = deviceId,
        slotId = slotId,
        severity = runCatching { AlertSeverity.valueOf(severity) }.getOrDefault(AlertSeverity.INFO),
        message = message,
        acknowledged = acknowledged,
    )

    companion object {
        fun from(alert: Alert) = AlertEntity(
            id = alert.id,
            at = alert.at,
            deviceId = alert.deviceId,
            slotId = alert.slotId,
            severity = alert.severity.name,
            message = alert.message,
            acknowledged = alert.acknowledged,
        )
    }
}

/** Projection returned by the reporting aggregate queries. */
data class SlotUsageTotal(
    val deviceId: String,
    val slotId: String,
    val slotLabel: String,
    val totalOnSeconds: Long,
    val sessions: Int,
    val cutoffs: Int,
    val watts: Int?,
) {
    /**
     * Energy estimate. Labelled an estimate everywhere it is shown, because it
     * assumes the appliance draws its rated wattage for the whole on-period.
     */
    val estimatedKwh: Double
        get() = watts?.let { it * (totalOnSeconds / 3600.0) / 1000.0 } ?: 0.0
}
