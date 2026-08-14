package lk.ac.ucsc.scs3311.smarthome.ui.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grid arithmetic is the one place in this app where a floating-point slip
 * shows up as a device drawn in the wrong room during the demo. It is pure, so
 * it is tested exhaustively rather than eyeballed.
 */
class GridMapperTest {

    private val tolerance = 0.001f

    /** 8x6 grid on a 4:3 plan inside a square canvas — letterboxed vertically. */
    private fun squareCanvas() = GridMapper(
        cols = 8,
        rows = 6,
        canvasSize = Size(600f, 600f),
        planAspect = 4f / 3f,
    )

    /** The canvas matches the plan exactly: no letterboxing at all. */
    private fun exactCanvas() = GridMapper(
        cols = 4,
        rows = 2,
        canvasSize = Size(400f, 200f),
        planAspect = 2f,
    )

    @Test
    fun `an exactly-matching canvas has no letterbox bars`() {
        val mapper = exactCanvas()
        assertEquals(0f, mapper.content.left, tolerance)
        assertEquals(0f, mapper.content.top, tolerance)
        assertEquals(400f, mapper.content.width, tolerance)
        assertEquals(200f, mapper.content.height, tolerance)
        assertEquals(100f, mapper.cellWidth, tolerance)
        assertEquals(100f, mapper.cellHeight, tolerance)
    }

    @Test
    fun `a taller canvas letterboxes top and bottom and stays centred`() {
        val mapper = squareCanvas()
        // 4:3 plan in a 600x600 canvas -> 600 wide, 450 tall, 75px bars.
        assertEquals(0f, mapper.content.left, tolerance)
        assertEquals(75f, mapper.content.top, tolerance)
        assertEquals(600f, mapper.content.width, tolerance)
        assertEquals(450f, mapper.content.height, tolerance)
        // Equal bars above and below is what "centred" means.
        assertEquals(mapper.content.top, 600f - mapper.content.bottom, tolerance)
    }

    @Test
    fun `a wider canvas letterboxes left and right and stays centred`() {
        val mapper = GridMapper(8, 6, Size(1000f, 400f), planAspect = 1f)
        // Square plan in a 1000x400 canvas -> 400x400, 300px bars each side.
        assertEquals(300f, mapper.content.left, tolerance)
        assertEquals(700f, mapper.content.right, tolerance)
        assertEquals(0f, mapper.content.top, tolerance)
        assertEquals(400f, mapper.content.height, tolerance)
        assertEquals(mapper.content.left, 1000f - mapper.content.right, tolerance)
    }

    @Test
    fun `cell centres are inside their own cell rectangles`() {
        val mapper = squareCanvas()
        for (y in 0 until mapper.rows) {
            for (x in 0 until mapper.cols) {
                val centre = mapper.cellCenter(x, y)
                val rect = mapper.cellRect(x, y)
                assertTrue("cell $x,$y centre $centre outside $rect", rect.contains(centre))
            }
        }
    }

    @Test
    fun `tapping a cell centre returns that same cell — round trip`() {
        val mapper = squareCanvas()
        for (y in 0 until mapper.rows) {
            for (x in 0 until mapper.cols) {
                assertEquals(IntCell(x, y), mapper.cellAt(mapper.cellCenter(x, y)))
            }
        }
    }

    @Test
    fun `taps on the letterbox bars hit nothing`() {
        val mapper = squareCanvas() // bars are y in 0..75 and 525..600
        assertNull(mapper.cellAt(Offset(300f, 10f)))
        assertNull(mapper.cellAt(Offset(300f, 590f)))
        // Just inside the plan does hit.
        assertEquals(IntCell(4, 0), mapper.cellAt(Offset(300f, 76f)))
    }

    @Test
    fun `the far edges belong to the last cell rather than falling through`() {
        val mapper = exactCanvas() // 4x2 cells of 100x100 at origin
        // A tap exactly on the right or bottom border must still place a device.
        assertEquals(IntCell(3, 1), mapper.cellAt(Offset(400f, 200f)))
        assertEquals(IntCell(3, 0), mapper.cellAt(Offset(400f, 50f)))
        assertEquals(IntCell(0, 1), mapper.cellAt(Offset(0f, 200f)))
    }

    @Test
    fun `boundaries between cells belong to the cell on the right and below`() {
        val mapper = exactCanvas()
        // x = 100 is the boundary between column 0 and column 1.
        assertEquals(IntCell(1, 0), mapper.cellAt(Offset(100f, 50f)))
        assertEquals(IntCell(0, 0), mapper.cellAt(Offset(99.9f, 50f)))
    }

    @Test
    fun `nearestCell clamps a finger that has left the plan`() {
        val mapper = squareCanvas()
        // Dragging above the plan should pin to the top row, not vanish.
        assertEquals(IntCell(4, 0), mapper.nearestCell(Offset(300f, -500f)))
        assertEquals(IntCell(4, 5), mapper.nearestCell(Offset(300f, 5_000f)))
        assertEquals(IntCell(0, 3), mapper.nearestCell(Offset(-100f, 300f)))
        assertEquals(IntCell(7, 3), mapper.nearestCell(Offset(9_999f, 300f)))
    }

    @Test
    fun `a zero-sized canvas degrades quietly instead of dividing by zero`() {
        val mapper = GridMapper(8, 6, Size.Zero, planAspect = 1.5f)
        assertEquals(0f, mapper.content.width, tolerance)
        assertEquals(0f, mapper.cellWidth, tolerance)
        assertNull(mapper.cellAt(Offset(10f, 10f)))
        assertNull(mapper.nearestCell(Offset(10f, 10f)))
    }

    @Test
    fun `an invalid plan aspect degrades quietly`() {
        val mapper = GridMapper(4, 4, Size(100f, 100f), planAspect = 0f)
        assertEquals(0f, mapper.content.width, tolerance)
        assertNull(mapper.cellAt(Offset(50f, 50f)))
    }

    @Test
    fun `isInsideGrid rejects coordinates off the grid`() {
        val mapper = squareCanvas()
        assertTrue(mapper.isInsideGrid(0, 0))
        assertTrue(mapper.isInsideGrid(7, 5))
        assertFalse(mapper.isInsideGrid(8, 5))
        assertFalse(mapper.isInsideGrid(7, 6))
        assertFalse(mapper.isInsideGrid(-1, 0))
    }

    @Test
    fun `freeCells excludes occupied cells and keeps reading order`() {
        val mapper = GridMapper(3, 2, Size(300f, 200f), planAspect = 1.5f)
        val free = mapper.freeCells(setOf(IntCell(0, 0), IntCell(2, 1)))

        assertEquals(4, free.size)
        assertEquals(
            listOf(IntCell(1, 0), IntCell(2, 0), IntCell(0, 1), IntCell(1, 1)),
            free,
        )
    }

    @Test
    fun `placement survives a change of screen size`() {
        // The same device on the same cell, drawn on a phone and on a tablet.
        val phone = GridMapper(8, 6, Size(1080f, 1920f), planAspect = 4f / 3f)
        val tablet = GridMapper(8, 6, Size(2560f, 1600f), planAspect = 4f / 3f)

        val cell = IntCell(5, 3)
        // Different pixels...
        val onPhone = phone.cellCenter(cell.x, cell.y)
        val onTablet = tablet.cellCenter(cell.x, cell.y)
        assertTrue(onPhone != onTablet)
        // ...but the same cell comes back from both.
        assertEquals(cell, phone.cellAt(onPhone))
        assertEquals(cell, tablet.cellAt(onTablet))
    }

    @Test
    fun `a one by one grid is valid`() {
        val mapper = GridMapper(1, 1, Size(100f, 100f), planAspect = 1f)
        assertEquals(IntCell(0, 0), mapper.cellAt(Offset(50f, 50f)))
        assertEquals(100f, mapper.cellWidth, tolerance)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a grid with no cells is rejected loudly`() {
        GridMapper(0, 6, Size(100f, 100f), planAspect = 1f)
    }
}
