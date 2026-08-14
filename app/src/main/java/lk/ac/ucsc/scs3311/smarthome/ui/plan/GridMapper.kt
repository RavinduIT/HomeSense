package lk.ac.ucsc.scs3311.smarthome.ui.plan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.floor
import kotlin.math.min

/**
 * The single place where grid cells and screen pixels meet.
 *
 * Device positions are stored as integer cell coordinates, never pixels, so a
 * plan renders identically on a 5-inch phone and a tablet, in either
 * orientation. Everything needed to go between the two representations lives
 * here as pure arithmetic with no Android dependency, which is what lets it be
 * unit-tested — and floating-point placement bugs are exactly the sort that
 * only show up in front of an examiner.
 *
 * The plan image is fitted inside the canvas preserving its aspect ratio, so
 * there is usually letterboxing on two sides. [content] is the fitted
 * rectangle; all cell arithmetic happens inside it, never against the raw
 * canvas bounds.
 */
data class GridMapper(
    val cols: Int,
    val rows: Int,
    val canvasSize: Size,
    /** Plan aspect ratio, width / height. */
    val planAspect: Float,
) {
    init {
        require(cols > 0 && rows > 0) { "grid must have at least one cell, was ${cols}x$rows" }
    }

    /**
     * The letterboxed rectangle the plan actually occupies, centred in the
     * canvas. A zero-sized canvas (the first frame, before measurement) yields
     * an empty rect rather than a division by zero.
     */
    val content: Rect = computeContentRect()

    val cellWidth: Float get() = if (content.width <= 0f) 0f else content.width / cols
    val cellHeight: Float get() = if (content.height <= 0f) 0f else content.height / rows

    /** The largest square that fits a cell — used for device markers. */
    val cellMinSide: Float get() = min(cellWidth, cellHeight)

    private fun computeContentRect(): Rect {
        val cw = canvasSize.width
        val ch = canvasSize.height
        if (cw <= 0f || ch <= 0f || planAspect <= 0f || !planAspect.isFinite()) return Rect.Zero

        val canvasAspect = cw / ch
        return if (canvasAspect > planAspect) {
            // Canvas is wider than the plan: bars on the left and right.
            val width = ch * planAspect
            val left = (cw - width) / 2f
            Rect(left, 0f, left + width, ch)
        } else {
            // Canvas is taller: bars above and below.
            val height = cw / planAspect
            val top = (ch - height) / 2f
            Rect(0f, top, cw, top + height)
        }
    }

    fun isInsideGrid(cellX: Int, cellY: Int): Boolean =
        cellX in 0 until cols && cellY in 0 until rows

    /** Top-left corner of a cell, in canvas pixels. */
    fun cellTopLeft(cellX: Int, cellY: Int): Offset =
        Offset(content.left + cellX * cellWidth, content.top + cellY * cellHeight)

    /** Centre of a cell, in canvas pixels. Where a device marker is drawn. */
    fun cellCenter(cellX: Int, cellY: Int): Offset =
        Offset(
            content.left + (cellX + 0.5f) * cellWidth,
            content.top + (cellY + 0.5f) * cellHeight,
        )

    fun cellRect(cellX: Int, cellY: Int): Rect {
        val topLeft = cellTopLeft(cellX, cellY)
        return Rect(topLeft.x, topLeft.y, topLeft.x + cellWidth, topLeft.y + cellHeight)
    }

    /**
     * The cell under a touch, or null when the touch landed on the letterbox
     * bars outside the plan.
     *
     * The right and bottom edges belong to the last cell rather than to a
     * non-existent cell `cols`/`rows`: a tap exactly on the border of the plan
     * should place a device, not be silently swallowed.
     */
    fun cellAt(position: Offset): IntCell? {
        if (content.width <= 0f || content.height <= 0f) return null
        if (position.x < content.left || position.x > content.right) return null
        if (position.y < content.top || position.y > content.bottom) return null

        val cellX = floor((position.x - content.left) / cellWidth).toInt().coerceIn(0, cols - 1)
        val cellY = floor((position.y - content.top) / cellHeight).toInt().coerceIn(0, rows - 1)
        return IntCell(cellX, cellY)
    }

    /**
     * The cell nearest a touch, clamped into the grid. Used while dragging a
     * device, where a finger leaving the plan should pin the device to the edge
     * rather than drop it.
     */
    fun nearestCell(position: Offset): IntCell? {
        if (content.width <= 0f || content.height <= 0f) return null
        val clamped = Offset(
            position.x.coerceIn(content.left, content.right),
            position.y.coerceIn(content.top, content.bottom),
        )
        return cellAt(clamped)
    }

    /** Free cells, given the ones already occupied. Drives "where can I place this?". */
    fun freeCells(occupied: Set<IntCell>): List<IntCell> =
        (0 until rows).flatMap { y ->
            (0 until cols).map { x -> IntCell(x, y) }
        }.filterNot { it in occupied }
}

/** An integer grid coordinate. Deliberately not a pixel offset. */
data class IntCell(val x: Int, val y: Int)
