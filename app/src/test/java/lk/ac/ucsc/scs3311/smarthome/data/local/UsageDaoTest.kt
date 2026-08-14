package lk.ac.ucsc.scs3311.smarthome.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room DAO tests in the style taught in the course — `inMemoryDatabaseBuilder`
 * and `AndroidJUnit4` — but run on the JVM under Robolectric so that CI and a
 * laptop with no emulator can both execute them.
 *
 * The aggregate query is the part worth testing: the reporting screen's numbers
 * are only as trustworthy as this SQL.
 */
@RunWith(AndroidJUnit4::class)
class UsageDaoTest {

    private lateinit var db: HomeSenseDatabase
    private lateinit var dao: UsageDao

    /** Monday 2026-08-10 09:00 UTC, as a stable base for the windows below. */
    private val base = 1_786_000_000_000L
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HomeSenseDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.usageDao()
    }

    @After
    fun tearDown() = db.close()

    private fun event(
        id: String,
        slotId: String,
        at: Long,
        type: String,
        durationSec: Long? = null,
        label: String = "Iron",
        watts: Int? = 1200,
        deviceId: String = "dev-iron",
    ) = UsageEventEntity(
        id = id,
        deviceId = deviceId,
        slotId = slotId,
        at = at,
        event = type,
        durationSec = durationSec,
        source = "APP",
        slotLabel = label,
        watts = watts,
    )

    @Test
    fun `totals sum only the events that close an on-period`() = runTest {
        dao.upsertAll(
            listOf(
                event("1", "s1", base, "ON"),
                event("2", "s1", base + 600_000, "OFF", durationSec = 600),
                event("3", "s1", base + 2 * hour, "ON"),
                event("4", "s1", base + 2 * hour + 300_000, "CUTOFF", durationSec = 300),
            ),
        )

        dao.observeTotals(base - hour, base + 24 * hour).test {
            val row = awaitItem().single()
            assertEquals("dev-iron", row.deviceId)
            assertEquals("s1", row.slotId)
            assertEquals("Iron", row.slotLabel)
            // 600 + 300; the two ON rows carry no duration and must not count.
            assertEquals(900L, row.totalOnSeconds)
            assertEquals(2, row.sessions)
            assertEquals(1, row.cutoffs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals are grouped per slot so a gang box does not merge into one row`() = runTest {
        dao.upsertAll(
            listOf(
                event("1", "s1", base, "OFF", durationSec = 100, label = "Ceiling light", watts = 24, deviceId = "dev-gang"),
                event("2", "s2", base, "OFF", durationSec = 250, label = "Porch light", watts = 12, deviceId = "dev-gang"),
                event("3", "s3", base, "OFF", durationSec = 50, label = "Fan", watts = 60, deviceId = "dev-gang"),
            ),
        )

        dao.observeTotals(base - hour, base + hour).test {
            val rows = awaitItem()
            assertEquals(3, rows.size)
            // Ordered by on-time descending — this is the runtime leaderboard.
            assertEquals(listOf("Porch light", "Ceiling light", "Fan"), rows.map { it.slotLabel })
            assertEquals(listOf(250L, 100L, 50L), rows.map { it.totalOnSeconds })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the time window is respected at both ends`() = runTest {
        dao.upsertAll(
            listOf(
                event("before", "s1", base - hour, "OFF", durationSec = 999),
                event("inside", "s1", base + hour, "OFF", durationSec = 60),
                event("after", "s1", base + 10 * hour, "OFF", durationSec = 999),
            ),
        )

        dao.observeTotalOnSeconds(base, base + 5 * hour).test {
            assertEquals(60L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-syncing the same cloud event does not double count`() = runTest {
        val row = event("push-key-abc", "s1", base, "OFF", durationSec = 600)
        dao.upsert(row)
        dao.upsert(row)
        dao.upsert(row.copy(durationSec = 600))

        assertEquals(1, dao.count())
        dao.observeTotalOnSeconds(base - hour, base + hour).test {
            assertEquals(600L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cutoff incidents are counted separately`() = runTest {
        dao.upsertAll(
            listOf(
                event("1", "s1", base, "CUTOFF", durationSec = 30),
                event("2", "s1", base + hour, "CUTOFF", durationSec = 30),
                event("3", "s1", base + 2 * hour, "OFF", durationSec = 30),
            ),
        )

        dao.observeCutoffCount(base - hour, base + 5 * hour).test {
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `kWh estimate follows watts and on-time`() = runTest {
        // 1200 W for one hour = 1.2 kWh.
        dao.upsert(event("1", "s1", base, "OFF", durationSec = 3600, watts = 1200))

        dao.observeTotals(base - hour, base + hour).test {
            assertEquals(1.2, awaitItem().single().estimatedKwh, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `between returns rows in chronological order for the CSV export`() = runTest {
        dao.upsertAll(
            listOf(
                event("3", "s1", base + 2 * hour, "OFF", durationSec = 1),
                event("1", "s1", base, "ON"),
                event("2", "s1", base + hour, "OFF", durationSec = 1),
            ),
        )

        val rows = dao.between(base - hour, base + 5 * hour)
        assertEquals(listOf("1", "2", "3"), rows.map { it.id })
    }
}

@RunWith(AndroidJUnit4::class)
class FloorDaoTest {

    private lateinit var db: HomeSenseDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HomeSenseDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `replaceAll mirrors the cloud including deletions`() = runTest {
        val dao = db.floorDao()
        dao.replaceAll(
            listOf(
                FloorEntity("f1", "Ground floor", 0, "plans/a.svg", 8, 6),
                FloorEntity("f2", "First floor", 1, "plans/b.svg", 8, 6),
            ),
        )
        dao.observeAll().test {
            assertEquals(listOf("Ground floor", "First floor"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }

        // The first floor is deleted in the cloud; the cache must follow.
        dao.replaceAll(listOf(FloorEntity("f1", "Ground floor", 0, "plans/a.svg", 8, 6)))
        dao.observeAll().test {
            assertEquals(listOf("Ground floor"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `floors are ordered by storey`() = runTest {
        val dao = db.floorDao()
        dao.replaceAll(
            listOf(
                FloorEntity("f2", "First floor", 1, "", 8, 6),
                FloorEntity("f0", "Basement", -1, "", 8, 6),
                FloorEntity("f1", "Ground floor", 0, "", 8, 6),
            ),
        )
        dao.observeAll().test {
            assertEquals(listOf("Basement", "Ground floor", "First floor"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
