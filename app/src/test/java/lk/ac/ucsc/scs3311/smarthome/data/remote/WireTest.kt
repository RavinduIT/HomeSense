package lk.ac.ucsc.scs3311.smarthome.data.remote

import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format is the contract shared with the worker and the simulator,
 * both of which are written in another language. These tests pin it down.
 */
class WireTest {

    @Test
    fun `parses a multi-switch as one device with N slots`() {
        val raw = mapOf(
            "floorId" to "floor-1",
            "name" to "Hall gang box",
            "gridX" to 4L,
            "gridY" to 2L,
            "kind" to "MULTI_SWITCH",
            "lastSeen" to 1_700_000_000_000L,
            "link" to "ONLINE",
            "slots" to mapOf(
                "s1" to mapOf("label" to "Ceiling light", "status" to "ON", "desiredState" to true, "reportedState" to true),
                "s2" to mapOf("label" to "Porch light", "status" to "OFF"),
                "s3" to mapOf("label" to "Fan", "status" to "ERROR", "desiredState" to true, "reportedState" to false),
            ),
        )

        val device = Wire.deviceFrom("dev-1", raw)

        assertEquals(DeviceKind.MULTI_SWITCH, device.kind)
        assertEquals(3, device.slots.size)
        assertEquals(LinkState.ONLINE, device.link)
        // One entity: one id, one cell.
        assertEquals("dev-1", device.id)
        assertEquals(4, device.gridX)
        assertEquals(SlotStatus.ON, device.slots[0].status)
        assertEquals(SlotStatus.ERROR, device.slots[2].status)
        assertTrue(device.slots[2].isMismatched)
        assertTrue(device.anySlotOn)
        assertFalse(device.allSlotsOn)
    }

    @Test
    fun `reads max_on_duration under the spec's exact key`() {
        val raw = mapOf(
            "label" to "Iron",
            "appliance" to mapOf("name" to "Steam iron", "hazardous" to true, "watts" to 1200L),
            "safety" to mapOf("max_on_duration" to 30L, "autoCutoffEnabled" to true),
        )

        val slot = Wire.slotFrom("s1", raw)

        assertEquals(30L, slot.safety.maxOnDuration)
        assertTrue(slot.safety.isArmed)
        assertTrue(slot.appliance.hazardous)
        assertEquals(1200, slot.appliance.watts)
    }

    @Test
    fun `writes max_on_duration back under the same key`() {
        val slot = Wire.slotFrom(
            "s1",
            mapOf("safety" to mapOf("max_on_duration" to 45L, "autoCutoffEnabled" to true)),
        )
        val written = Wire.safetyToMap(slot.safety)

        assertEquals(45L, written["max_on_duration"])
        assertNull("the camelCase spelling must not appear", written["maxOnDuration"])
    }

    @Test
    fun `a created device never carries app-written status`() {
        val device = Wire.deviceFrom(
            "d",
            mapOf("kind" to "OUTLET", "slots" to mapOf("s1" to mapOf("label" to "Iron"))),
        )

        val payload = Wire.deviceToCreateMap(device)

        // The app may seed a starting value, but it must be the honest one:
        // nothing has reported in yet.
        assertEquals(LinkState.DISCONNECTED.name, payload["link"])
        @Suppress("UNCHECKED_CAST")
        val slots = payload["slots"] as Map<String, Map<String, Any?>>
        assertEquals(SlotStatus.DISCONNECTED.name, slots.getValue("s1")["status"])
        assertEquals(false, slots.getValue("s1")["reportedState"])
    }

    @Test
    fun `tolerates numbers arriving as Double or String`() {
        // RTDB returns Double for anything that was written with a decimal
        // point, and JSON imported by hand can produce strings.
        val slot = Wire.slotFrom(
            "s1",
            mapOf(
                "safety" to mapOf("max_on_duration" to 30.0),
                "schedule" to mapOf("enabled" to true, "onAtMinuteOfDay" to "1080", "offAtMinuteOfDay" to 1380.0),
            ),
        )

        assertEquals(30L, slot.safety.maxOnDuration)
        assertEquals(1080, slot.schedule.onAtMinuteOfDay)
        assertEquals(1380, slot.schedule.offAtMinuteOfDay)
    }

    @Test
    fun `tolerates missing nodes without throwing`() {
        val device = Wire.deviceFrom("d", mapOf("name" to "Bare"))

        assertEquals(DeviceKind.OUTLET, device.kind)
        assertEquals(LinkState.DISCONNECTED, device.link)
        assertTrue(device.slots.isEmpty())
        assertNull(device.camera)
    }

    @Test
    fun `unknown enum values fall back instead of crashing the phone`() {
        // A future worker version could introduce a status this build predates.
        val slot = Wire.slotFrom("s1", mapOf("status" to "SOMETHING_NEW"))
        assertEquals(SlotStatus.OFF, slot.status)
    }

    @Test
    fun `schedule days survive RTDB collapsing the array into a map`() {
        val asList = Wire.slotFrom("s", mapOf("schedule" to mapOf("days" to listOf(1L, 2L, 3L))))
        val asMap = Wire.slotFrom("s", mapOf("schedule" to mapOf("days" to mapOf("0" to 1L, "1" to 2L, "2" to 3L))))

        assertEquals(listOf(1, 2, 3), asList.schedule.days)
        assertEquals(listOf(1, 2, 3), asMap.schedule.days)
    }

    @Test
    fun `parses camera nodes`() {
        val device = Wire.deviceFrom(
            "cam",
            mapOf(
                "kind" to "CAMERA",
                "camera" to mapOf(
                    "snapshotUrl" to "asset://cameras/front.svg",
                    "streamUrl" to "mock://stream/front",
                    "lastFrameAt" to 42L,
                ),
            ),
        )

        assertEquals(DeviceKind.CAMERA, device.kind)
        assertEquals("mock://stream/front", device.camera?.streamUrl)
        assertFalse(device.kind.supportsSlots)
    }
}
