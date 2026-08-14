package lk.ac.ucsc.scs3311.smarthome.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the desired/reported/status invariant at the model level, and the
 * countdown maths that the max_on_duration ring renders from.
 */
class SlotTest {

    private val t0 = 1_700_000_000_000L

    private fun ironOn(maxOnDuration: Long = 30L, onSince: Long? = t0) = Slot(
        id = "s1",
        label = "Iron",
        appliance = Appliance(name = "Steam iron", hazardous = true, watts = 1200),
        desiredState = true,
        reportedState = true,
        status = SlotStatus.ON,
        onSince = onSince,
        safety = Safety(maxOnDuration = maxOnDuration, autoCutoffEnabled = true),
    )

    @Test
    fun `mismatch is detected when hardware has not followed the app`() {
        val slot = ironOn().copy(reportedState = false)
        assertTrue(slot.isMismatched)
        assertFalse(ironOn().isMismatched)
    }

    @Test
    fun `on duration counts from onSince`() {
        val slot = ironOn()
        assertEquals(0L, slot.onDurationSeconds(t0))
        assertEquals(10L, slot.onDurationSeconds(t0 + 10_000))
        assertEquals(90L, slot.onDurationSeconds(t0 + 90_000))
    }

    @Test
    fun `on duration is zero when the slot is not on`() {
        val slot = ironOn().copy(status = SlotStatus.OFF)
        assertEquals(0L, slot.onDurationSeconds(t0 + 60_000))
    }

    @Test
    fun `cutoff progress reaches one exactly at max_on_duration`() {
        val slot = ironOn(maxOnDuration = 30L)
        assertEquals(0f, slot.cutoffProgress(t0)!!, 0.001f)
        assertEquals(0.5f, slot.cutoffProgress(t0 + 15_000)!!, 0.001f)
        assertEquals(1f, slot.cutoffProgress(t0 + 30_000)!!, 0.001f)
    }

    @Test
    fun `cutoff progress is clamped once the limit is passed`() {
        val slot = ironOn(maxOnDuration = 30L)
        assertEquals(1f, slot.cutoffProgress(t0 + 300_000)!!, 0.001f)
    }

    @Test
    fun `no cutoff progress when the cutoff is not armed`() {
        val disabled = ironOn().copy(safety = Safety(maxOnDuration = 30L, autoCutoffEnabled = false))
        assertNull(disabled.cutoffProgress(t0 + 10_000))

        val noLimit = ironOn().copy(safety = Safety(maxOnDuration = 0L, autoCutoffEnabled = true))
        assertNull(noLimit.cutoffProgress(t0 + 10_000))
    }

    @Test
    fun `safety is armed only with a positive limit and the flag set`() {
        assertTrue(Safety(maxOnDuration = 30L, autoCutoffEnabled = true).isArmed)
        assertFalse(Safety(maxOnDuration = 0L, autoCutoffEnabled = true).isArmed)
        assertFalse(Safety(maxOnDuration = 30L, autoCutoffEnabled = false).isArmed)
    }

    @Test
    fun `multi switch aggregates its slots without becoming several devices`() {
        val gangBox = Device(
            id = "d1",
            kind = DeviceKind.MULTI_SWITCH,
            slots = listOf(
                Slot(id = "s1", status = SlotStatus.ON),
                Slot(id = "s2", status = SlotStatus.OFF),
                Slot(id = "s3", status = SlotStatus.ON),
            ),
        )
        assertTrue(gangBox.anySlotOn)
        assertFalse(gangBox.allSlotsOn)
        assertEquals(3, gangBox.slots.size)
        assertEquals("s2", gangBox.slot("s2")?.id)
    }
}
