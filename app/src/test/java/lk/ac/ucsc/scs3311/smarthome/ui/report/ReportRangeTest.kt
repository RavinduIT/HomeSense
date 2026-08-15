package lk.ac.ucsc.scs3311.smarthome.ui.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * "Today" has to mean local midnight, not "24 hours ago". A user opening the
 * report at 09:00 expects this morning's numbers, not yesterday's included.
 */
class ReportRangeTest {

    private val colombo = TimeZone.getTimeZone("Asia/Colombo")

    /** 2026-08-14, 09:30 local time in Colombo. */
    private val now = Calendar.getInstance(colombo).apply {
        set(2026, Calendar.AUGUST, 14, 9, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `today starts at local midnight`() {
        val bounds = ReportRange.TODAY.boundsAt(now, colombo)
        val start = Calendar.getInstance(colombo).apply { timeInMillis = bounds.first }

        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, start.get(Calendar.MINUTE))
        assertEquals(14, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(now, bounds.last)
    }

    @Test
    fun `today is not simply the last 24 hours`() {
        val bounds = ReportRange.TODAY.boundsAt(now, colombo)
        // 09:30 in means the window is 9.5 hours, not 24.
        val hours = (bounds.last - bounds.first) / 3_600_000.0
        assertEquals(9.5, hours, 0.01)
    }

    @Test
    fun `this week covers seven local days`() {
        val bounds = ReportRange.WEEK.boundsAt(now, colombo)
        val start = Calendar.getInstance(colombo).apply { timeInMillis = bounds.first }

        assertEquals(8, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `all time starts at the epoch`() {
        val bounds = ReportRange.ALL.boundsAt(now, colombo)
        assertEquals(0L, bounds.first)
        assertEquals(now, bounds.last)
    }

    @Test
    fun `every range ends at now and is non-empty`() {
        ReportRange.entries.forEach { range ->
            val bounds = range.boundsAt(now, colombo)
            assertEquals(range.name, now, bounds.last)
            assertTrue(range.name, bounds.first <= bounds.last)
        }
    }
}
