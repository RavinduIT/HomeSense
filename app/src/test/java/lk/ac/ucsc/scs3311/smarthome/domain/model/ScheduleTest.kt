package lk.ac.ucsc.scs3311.smarthome.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schedule window is the one piece of the model with real branching, and
 * the worker relies on exactly this logic to decide when to flip a slot. The
 * midnight-wrapping case is the one that bites: "on at 18:00, off at 06:00".
 */
class ScheduleTest {

    private fun minutes(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `disabled schedule never fires`() {
        val schedule = Schedule(
            enabled = false,
            onAtMinuteOfDay = minutes(18),
            offAtMinuteOfDay = minutes(22),
        )
        assertFalse(schedule.shouldBeOnAt(minutes(20), dayOfWeek = 1))
    }

    @Test
    fun `simple window is on inside and off outside`() {
        val schedule = Schedule(
            enabled = true,
            onAtMinuteOfDay = minutes(18),
            offAtMinuteOfDay = minutes(22),
        )
        assertFalse(schedule.shouldBeOnAt(minutes(17, 59), dayOfWeek = 1))
        assertTrue(schedule.shouldBeOnAt(minutes(18), dayOfWeek = 1))
        assertTrue(schedule.shouldBeOnAt(minutes(21, 59), dayOfWeek = 1))
        // The off minute itself is exclusive, so the lamp is already off at 22:00.
        assertFalse(schedule.shouldBeOnAt(minutes(22), dayOfWeek = 1))
    }

    @Test
    fun `window wrapping midnight stays on after midnight`() {
        val schedule = Schedule(
            enabled = true,
            onAtMinuteOfDay = minutes(18),
            offAtMinuteOfDay = minutes(6),
        )
        assertTrue(schedule.wrapsMidnight)
        assertTrue(schedule.shouldBeOnAt(minutes(23, 30), dayOfWeek = 1))
        assertTrue(schedule.shouldBeOnAt(minutes(0, 1), dayOfWeek = 1))
        assertTrue(schedule.shouldBeOnAt(minutes(5, 59), dayOfWeek = 1))
        assertFalse(schedule.shouldBeOnAt(minutes(6), dayOfWeek = 1))
        assertFalse(schedule.shouldBeOnAt(minutes(12), dayOfWeek = 1))
    }

    @Test
    fun `empty day list means every day`() {
        val schedule = Schedule(
            enabled = true,
            onAtMinuteOfDay = minutes(8),
            offAtMinuteOfDay = minutes(9),
            days = emptyList(),
        )
        (0..6).forEach { day ->
            assertTrue("day $day", schedule.shouldBeOnAt(minutes(8, 30), dayOfWeek = day))
        }
    }

    @Test
    fun `explicit day list restricts the schedule`() {
        val weekdaysOnly = Schedule(
            enabled = true,
            onAtMinuteOfDay = minutes(8),
            offAtMinuteOfDay = minutes(9),
            days = listOf(1, 2, 3, 4, 5),
        )
        assertTrue(weekdaysOnly.shouldBeOnAt(minutes(8, 30), dayOfWeek = 3))
        assertFalse(weekdaysOnly.shouldBeOnAt(minutes(8, 30), dayOfWeek = 0))
        assertFalse(weekdaysOnly.shouldBeOnAt(minutes(8, 30), dayOfWeek = 6))
    }

    @Test
    fun `zero-length window never fires`() {
        val schedule = Schedule(
            enabled = true,
            onAtMinuteOfDay = minutes(10),
            offAtMinuteOfDay = minutes(10),
        )
        assertFalse(schedule.shouldBeOnAt(minutes(10), dayOfWeek = 1))
    }
}
