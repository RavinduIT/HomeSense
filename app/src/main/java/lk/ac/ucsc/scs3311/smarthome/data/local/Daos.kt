package lk.ac.ucsc.scs3311.smarthome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorDao {

    @Query("SELECT * FROM floors ORDER BY level ASC, name ASC")
    fun observeAll(): Flow<List<FloorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(floors: List<FloorEntity>)

    @Query("DELETE FROM floors WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)

    @Query("DELETE FROM floors")
    suspend fun clear()

    /** Mirrors the cloud exactly: upsert what exists, drop what no longer does. */
    @Transaction
    suspend fun replaceAll(floors: List<FloorEntity>) {
        if (floors.isEmpty()) {
            clear()
        } else {
            upsertAll(floors)
            deleteMissing(floors.map { it.id })
        }
    }
}

@Dao
interface DeviceLayoutDao {

    @Query("SELECT * FROM device_layout WHERE floorId = :floorId")
    fun observeForFloor(floorId: String): Flow<List<DeviceLayoutEntity>>

    @Query("SELECT * FROM device_layout")
    fun observeAll(): Flow<List<DeviceLayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceLayoutEntity>)

    @Query("DELETE FROM device_layout WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)

    @Query("DELETE FROM device_layout")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(devices: List<DeviceLayoutEntity>) {
        if (devices.isEmpty()) {
            clear()
        } else {
            upsertAll(devices)
            deleteMissing(devices.map { it.id })
        }
    }
}

@Dao
interface UsageDao {

    /**
     * Upsert, keyed on the cloud push id, so replaying the log after a
     * reconnect cannot double-count.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<UsageEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: UsageEventEntity)

    @Query("SELECT * FROM usage_events ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<UsageEventEntity>>

    @Query("SELECT * FROM usage_events WHERE at >= :from AND at < :to ORDER BY at ASC")
    suspend fun between(from: Long, to: Long): List<UsageEventEntity>

    /**
     * On-time per slot within a window.
     *
     * Only OFF and CUTOFF rows carry `durationSec` — they are the events that
     * *close* an on-period — so summing them gives total on-time without any
     * state reconstruction. `sessions` counts the ON events that opened those
     * periods, and `cutoffs` counts how often the safety worker had to step in.
     */
    @Query(
        """
        SELECT
            deviceId                                                        AS deviceId,
            slotId                                                          AS slotId,
            MAX(slotLabel)                                                  AS slotLabel,
            COALESCE(SUM(CASE WHEN event IN ('OFF','CUTOFF')
                              THEN durationSec ELSE 0 END), 0)              AS totalOnSeconds,
            SUM(CASE WHEN event = 'ON'     THEN 1 ELSE 0 END)               AS sessions,
            SUM(CASE WHEN event = 'CUTOFF' THEN 1 ELSE 0 END)               AS cutoffs,
            MAX(watts)                                                      AS watts
        FROM usage_events
        WHERE at >= :from AND at < :to
        GROUP BY deviceId, slotId
        ORDER BY totalOnSeconds DESC
        """,
    )
    fun observeTotals(from: Long, to: Long): Flow<List<SlotUsageTotal>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN event IN ('OFF','CUTOFF')
                                 THEN durationSec ELSE 0 END), 0)
        FROM usage_events
        WHERE at >= :from AND at < :to
        """,
    )
    fun observeTotalOnSeconds(from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM usage_events WHERE event = 'CUTOFF' AND at >= :from AND at < :to")
    fun observeCutoffCount(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM usage_events")
    suspend fun count(): Int

    @Query("DELETE FROM usage_events")
    suspend fun clear()
}

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY at DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
    fun observeUnacknowledgedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(alerts: List<AlertEntity>)

    @Query("DELETE FROM alerts")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(alerts: List<AlertEntity>) {
        clear()
        if (alerts.isNotEmpty()) upsertAll(alerts)
    }
}
