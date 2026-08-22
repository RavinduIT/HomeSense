package lk.ac.ucsc.scs3311.smarthome.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lk.ac.ucsc.scs3311.smarthome.data.local.HomeSenseDatabase
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.EventSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import lk.ac.ucsc.scs3311.smarthome.domain.model.UsageEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The synchronisation proof.
 *
 * The requirement is that "externally-driven changes reach the mobile viewport
 * quickly with no manual refresh". These tests demonstrate exactly that: a
 * change is pushed onto the source's stream — imitating a Firebase child
 * listener firing because the simulator or the worker wrote something — and the
 * repository's flow, which is what the UI collects, is asserted to carry it.
 *
 * Nothing in these tests ever calls a refresh, reload or fetch method, because
 * no such method exists on [HomeRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DefaultHomeRepositoryTest {

    private lateinit var db: HomeSenseDatabase

    private val iron = Device(
        id = "dev-iron",
        floorId = "floor-ground",
        name = "Utility outlet",
        gridX = 1,
        gridY = 1,
        kind = DeviceKind.OUTLET,
        slots = listOf(TestRemoteSource.slot("s1", "Iron")),
    )

    private val gangBox = Device(
        id = "dev-gang",
        floorId = "floor-ground",
        name = "Hall gang box",
        gridX = 4,
        gridY = 2,
        kind = DeviceKind.MULTI_SWITCH,
        slots = listOf(
            TestRemoteSource.slot("s1", "Ceiling light"),
            TestRemoteSource.slot("s2", "Porch light"),
            TestRemoteSource.slot("s3", "Fan"),
        ),
    )

    private val upstairsLamp = Device(
        id = "dev-lamp",
        floorId = "floor-upper",
        name = "Bedroom outlet",
        kind = DeviceKind.OUTLET,
        slots = listOf(TestRemoteSource.slot("s1", "Lamp")),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HomeSenseDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    /**
     * The repository is scoped to whichever household is active, so the tests
     * supply that as a flow. [activeHome] lets a test emit null to represent a
     * signed-out session and assert that the streams empty out.
     */
    private val activeHome = MutableStateFlow<String?>(HOME_ID)

    private fun repositoryOn(source: TestRemoteSource, scope: CoroutineScope) =
        DefaultHomeRepository(source, db, scope, activeHome)

    /**
     * The repository's flows are `stateIn(..., emptyList())`, so the first
     * value a collector sees may be the empty placeholder that a cold start
     * shows before any snapshot has arrived. Skipping forward to the first
     * value that satisfies a condition keeps these tests from depending on
     * whether that placeholder was conflated away.
     */
    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        repeat(MAX_EMISSIONS) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
        error("no emission satisfied the predicate within $MAX_EMISSIONS values")
    }

    /** Blocks until the repository has its first snapshot, as the UI would. */
    private suspend fun DefaultHomeRepository.awaitLoaded(): List<Device> =
        devices.first { it.isNotEmpty() }

    @Test
    fun `a change made outside the app reaches the repository stream`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron))
        val repository = repositoryOn(source, backgroundScope)

        repository.devices.test {
            val loaded = awaitUntil { it.isNotEmpty() }
            assertEquals(SlotStatus.OFF, loaded.single().slots.single().status)

            // The simulator flips the relay and the worker publishes ON.
            // Nobody tells the app; it simply arrives.
            source.simulateExternalChange("dev-iron", "s1") {
                it.copy(reportedState = true, status = SlotStatus.ON, onSince = 1_000L)
            }

            val updated = awaitUntil { it.single().slots.single().status == SlotStatus.ON }
                .single().slots.single()
            assertTrue(updated.reportedState)
            assertEquals(1_000L, updated.onSince)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an external fault surfaces as ERROR without the app writing anything`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron))
        val repository = repositoryOn(source, backgroundScope)

        repository.devices.test {
            awaitUntil { it.isNotEmpty() }

            source.simulateExternalChange("dev-iron", "s1") {
                it.copy(desiredState = true, reportedState = false, status = SlotStatus.ERROR)
            }

            val slot = awaitUntil { it.single().slots.single().status == SlotStatus.ERROR }
                .single().slots.single()
            assertTrue(slot.isMismatched)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue("the app must not have written anything", source.writes.isEmpty())
    }

    @Test
    fun `toggling writes desiredState only and never status`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron))
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.devices.test {
            awaitUntil { it.isNotEmpty() }
            repository.setSlotDesiredState("dev-iron", "s1", desired = true)

            val slot = awaitUntil { it.single().slots.single().desiredState }
                .single().slots.single()

            // The crucial assertion: wanting it on does not make it on. Only
            // the worker may move `status`, and it has not spoken yet.
            assertEquals(SlotStatus.OFF, slot.status)
            assertFalse(slot.reportedState)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("setDesiredState(dev-iron/s1=true)"), source.writes)
    }

    @Test
    fun `toggling appends exactly one usage event attributed to the app`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron))
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.setSlotDesiredState("dev-iron", "s1", desired = true)

        assertEquals(1, source.appendedEvents.size)
        assertEquals(UsageEventType.ON, source.appendedEvents.single().event)
        assertEquals(EventSource.APP, source.appendedEvents.single().source)
    }

    @Test
    fun `switching off records the elapsed on-time from onSince`() = runTest {
        val onSince = System.currentTimeMillis() - 90_000
        val source = TestRemoteSource(
            initialDevices = listOf(
                iron.copy(
                    slots = listOf(
                        TestRemoteSource.slot("s1", "Iron", SlotStatus.ON)
                            .copy(desiredState = true, reportedState = true, onSince = onSince),
                    ),
                ),
            ),
        )
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.setSlotDesiredState("dev-iron", "s1", desired = false)

        val event = source.appendedEvents.single()
        assertEquals(UsageEventType.OFF, event.event)
        // ~90s; allow a little slack for test execution time.
        assertTrue("was ${event.durationSec}", event.durationSec in 89L..95L)
    }

    @Test
    fun `the master toggle addresses every slot of the gang box individually`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(gangBox))
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.setAllSlots("dev-gang", desired = true)

        assertEquals(
            listOf(
                "setDesiredState(dev-gang/s1=true)",
                "setDesiredState(dev-gang/s2=true)",
                "setDesiredState(dev-gang/s3=true)",
            ),
            source.writes,
        )
        // Three slots addressed, but still exactly one device in the system.
        assertEquals(1, repository.devices.value.size)
        assertEquals(3, repository.devices.value.single().slots.size)
    }

    @Test
    fun `slots of one gang box toggle independently`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(gangBox))
        val repository = repositoryOn(source, backgroundScope)

        repository.devices.test {
            awaitUntil { it.isNotEmpty() }

            source.simulateExternalChange("dev-gang", "s2") {
                it.copy(reportedState = true, status = SlotStatus.ON)
            }

            val device = awaitUntil { list ->
                list.single().slots.any { it.status == SlotStatus.ON }
            }.single()

            assertEquals(SlotStatus.OFF, device.slots[0].status)
            assertEquals(SlotStatus.ON, device.slots[1].status)
            assertEquals(SlotStatus.OFF, device.slots[2].status)
            assertTrue(device.anySlotOn)
            assertFalse(device.allSlotsOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `devicesOnFloor filters to a single storey`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron, gangBox, upstairsLamp))
        val repository = repositoryOn(source, backgroundScope)

        repository.devicesOnFloor("floor-ground").test {
            assertEquals(listOf("dev-iron", "dev-gang"), awaitUntil { it.isNotEmpty() }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        repository.devicesOnFloor("floor-upper").test {
            assertEquals(listOf("dev-lamp"), awaitUntil { it.isNotEmpty() }.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a floor in the cloud appears without any refresh call`() = runTest {
        val source = TestRemoteSource()
        val repository = repositoryOn(source, backgroundScope)

        repository.floors.test {
            assertEquals(emptyList<Floor>(), awaitItem())

            source.emitFloors(listOf(Floor(id = "f1", name = "Ground floor", level = 0)))

            assertEquals(listOf("Ground floor"), awaitUntil { it.isNotEmpty() }.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a floor removes the devices standing on it`() = runTest {
        val source = TestRemoteSource(
            initialFloors = listOf(Floor(id = "floor-ground", name = "Ground floor")),
            initialDevices = listOf(iron, gangBox, upstairsLamp),
        )
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.deleteFloor("floor-ground")

        // The upstairs lamp is untouched; the two ground-floor devices are not
        // left orphaned in the tree, invisible but still reporting.
        assertEquals(
            listOf("deleteDevice(dev-iron)", "deleteDevice(dev-gang)", "deleteFloor(floor-ground)"),
            source.writes,
        )
    }

    @Test
    fun `unacknowledged alert count tracks the cloud`() = runTest {
        val source = TestRemoteSource()
        val repository = repositoryOn(source, backgroundScope)

        repository.unacknowledgedAlertCount.test {
            assertEquals(0, awaitItem())

            source.emitAlerts(
                listOf(
                    Alert(id = "a1", at = 2, message = "cutoff"),
                    Alert(id = "a2", at = 1, acknowledged = true),
                ),
            )

            assertEquals(1, awaitUntil { it == 1 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signing out empties every stream`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron, gangBox))
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.devices.test {
            awaitUntil { it.isNotEmpty() }

            // A null active household is what a signed-out session produces.
            activeHome.value = null

            assertEquals(emptyList<Device>(), awaitUntil { it.isEmpty() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a write with no active household is dropped rather than misdirected`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron))
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        activeHome.value = null
        repository.devices.first { it.isEmpty() }

        repository.setSlotDesiredState("dev-iron", "s1", desired = true)

        // The important property is that it did not fall back to some other
        // household: no write reached the source at all.
        assertTrue("writes were: ${source.writes}", source.writes.isEmpty())
        assertTrue(source.appendedEvents.isEmpty())
    }

    @Test
    fun `switching household stops serving the previous one`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron), servesHomeId = HOME_ID)
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.devices.test {
            awaitUntil { it.isNotEmpty() }

            // A household this source holds no data for. If the repository had
            // kept its previous subscription, the old devices would persist.
            activeHome.value = "another-home"

            assertEquals(emptyList<Device>(), awaitUntil { it.isEmpty() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A refused read must not be fatal, and must not be permanent.
     *
     * The rules revoke a household's reads the moment the account stops being a
     * member, so an open listener is cancelled rather than merely returning
     * nothing. Recovering from that outside the `flatMapLatest` ends the chain
     * itself: the failure is reported once and the stream never carries another
     * value, so every screen keeps rendering what it last saw for the rest of
     * the process. This asserts the recovery is placed where a later household
     * still starts a new subscription.
     */
    @Test
    fun `a refused read does not stop the stream serving the next household`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron), servesHomeId = HOME_ID)
        source.refuseReadsFor("revoked-home")
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        repository.devices.test {
            awaitUntil { it.isNotEmpty() }

            // Removed from this household while the screen was open.
            activeHome.value = "revoked-home"
            assertEquals(emptyList<Device>(), awaitUntil { it.isEmpty() })

            // Re-admitted, or simply switched back. The chain must still be live.
            activeHome.value = HOME_ID
            assertEquals(
                listOf(iron.id),
                awaitUntil { it.isNotEmpty() }.map { device -> device.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The refusal is reported, not swallowed. */
    @Test
    fun `a refused read is surfaced through the error channel`() = runTest {
        val source = TestRemoteSource(initialDevices = listOf(iron), servesHomeId = HOME_ID)
        source.refuseReadsFor("revoked-home")
        val repository = repositoryOn(source, backgroundScope)
        repository.awaitLoaded()

        assertNull(repository.syncError.value)

        activeHome.value = "revoked-home"
        repository.devices.test {
            awaitUntil { it.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.syncError.value != null)
    }

    private companion object {
        const val HOME_ID = "test-home"
        const val MAX_EMISSIONS = 20
    }
}
