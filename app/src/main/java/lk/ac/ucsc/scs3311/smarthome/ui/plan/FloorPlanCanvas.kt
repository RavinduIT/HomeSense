package lk.ac.ucsc.scs3311.smarthome.ui.plan

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * Colours and sizes for the plan, kept in one place so the drawing code below
 * reads as geometry rather than as a pile of literals.
 */
data class PlanStyle(
    val roomFill: Color,
    val roomStroke: Color,
    val wallStroke: Color,
    val gridLine: Color,
    val gridLineStrong: Color,
    val label: Color,
    val selection: Color,
    val onSurface: Color,
)

/**
 * Draws a floor plan, the abstract grid over it, and the devices placed on
 * cells — plus the tap handling that turns a touch into a grid coordinate.
 *
 * All of the pixel arithmetic is delegated to [GridMapper]; nothing here does
 * its own division. That is deliberate, because the mapper is the part that is
 * unit-tested.
 */
@Composable
fun FloorPlanCanvas(
    plan: FloorPlanSpec,
    cols: Int,
    rows: Int,
    devices: List<Device>,
    style: PlanStyle,
    modifier: Modifier = Modifier,
    selectedCell: IntCell? = null,
    onCellTap: (IntCell) -> Unit = {},
    onDeviceTap: (Device) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    var mapper by remember { mutableStateOf<GridMapper?>(null) }

    val devicesByCell = remember(devices) {
        devices.associateBy { IntCell(it.gridX, it.gridY) }
    }

    ComposeCanvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(devicesByCell, cols, rows) {
                detectTapGestures { position ->
                    val cell = mapper?.cellAt(position) ?: return@detectTapGestures
                    // A tap on an occupied cell opens that device; a tap on an
                    // empty cell is a request to place something there.
                    val device = devicesByCell[cell]
                    if (device != null) onDeviceTap(device) else onCellTap(cell)
                }
            },
    ) {
        val current = GridMapper(
            cols = cols,
            rows = rows,
            canvasSize = Size(size.width, size.height),
            planAspect = plan.aspect,
        )
        mapper = current
        if (current.content.width <= 0f) return@ComposeCanvas

        drawPlan(plan, current.content, style, textMeasurer)
        drawGrid(current, style)
        selectedCell?.let { drawSelection(current, it, style) }
        devicesByCell.forEach { (cell, device) ->
            drawDeviceMarker(current, cell, device, style, textMeasurer)
        }
    }
}

// ---- plan ------------------------------------------------------------------

/** Maps a point in plan units into canvas pixels inside [content]. */
private fun FloorPlanSpec.toPixels(content: Rect, x: Float, y: Float) = Offset(
    content.left + (x / widthUnits) * content.width,
    content.top + (y / heightUnits) * content.height,
)

private fun DrawScope.drawPlan(
    plan: FloorPlanSpec,
    content: Rect,
    style: PlanStyle,
    textMeasurer: TextMeasurer,
) {
    val wallWidth = (content.width / plan.widthUnits) * 0.14f

    plan.rooms.forEach { room ->
        val topLeft = plan.toPixels(content, room.x, room.y)
        val bottomRight = plan.toPixels(content, room.x + room.width, room.y + room.height)
        val size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)

        drawRect(color = style.roomFill, topLeft = topLeft, size = size)
        drawRect(
            color = style.roomStroke,
            topLeft = topLeft,
            size = size,
            style = Stroke(width = wallWidth),
        )

        // Room names are what make an abstract plan readable at a glance from
        // the back of a lecture theatre.
        val layout = textMeasurer.measure(
            text = room.name,
            style = TextStyle(fontSize = 10.sp, color = style.label),
        )
        if (layout.size.width < size.width * 0.92f) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    topLeft.x + (size.width - layout.size.width) / 2f,
                    topLeft.y + wallWidth * 1.2f,
                ),
            )
        }
    }

    // Openings are painted over the walls, so a door reads as a gap.
    plan.openings.forEach { opening ->
        val start = plan.toPixels(content, opening.x, opening.y)
        val end = if (opening.horizontal) {
            plan.toPixels(content, opening.x + opening.length, opening.y)
        } else {
            plan.toPixels(content, opening.x, opening.y + opening.length)
        }
        drawLine(
            color = if (opening.kind == OpeningKind.DOOR) style.roomFill else style.wallStroke,
            start = start,
            end = end,
            strokeWidth = wallWidth * if (opening.kind == OpeningKind.DOOR) 1.6f else 0.8f,
        )
    }
}

// ---- grid ------------------------------------------------------------------

private fun DrawScope.drawGrid(mapper: GridMapper, style: PlanStyle) {
    val content = mapper.content
    val thin = 1f
    val thick = 2f

    for (col in 0..mapper.cols) {
        val x = content.left + col * mapper.cellWidth
        val isEdge = col == 0 || col == mapper.cols
        drawLine(
            color = if (isEdge) style.gridLineStrong else style.gridLine,
            start = Offset(x, content.top),
            end = Offset(x, content.bottom),
            strokeWidth = if (isEdge) thick else thin,
        )
    }
    for (row in 0..mapper.rows) {
        val y = content.top + row * mapper.cellHeight
        val isEdge = row == 0 || row == mapper.rows
        drawLine(
            color = if (isEdge) style.gridLineStrong else style.gridLine,
            start = Offset(content.left, y),
            end = Offset(content.right, y),
            strokeWidth = if (isEdge) thick else thin,
        )
    }
}

private fun DrawScope.drawSelection(mapper: GridMapper, cell: IntCell, style: PlanStyle) {
    val rect = mapper.cellRect(cell.x, cell.y)
    drawRect(
        color = style.selection.copy(alpha = 0.20f),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
    )
    drawRect(
        color = style.selection,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style = Stroke(width = 3f),
    )
}

// ---- devices ---------------------------------------------------------------

/**
 * The aggregate status of a device as shown on the plan.
 *
 * A gang box has several slots and therefore several statuses. The marker shows
 * the most *alarming* one: a fault on one channel must not be hidden by two
 * healthy ones.
 */
fun Device.planStatus(): SlotStatus = when {
    kind == DeviceKind.CAMERA ->
        if (link == lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState.ONLINE) {
            SlotStatus.ON
        } else {
            SlotStatus.DISCONNECTED
        }
    slots.any { it.status == SlotStatus.ERROR } -> SlotStatus.ERROR
    slots.any { it.status == SlotStatus.DISCONNECTED } -> SlotStatus.DISCONNECTED
    slots.any { it.status == SlotStatus.ON } -> SlotStatus.ON
    else -> SlotStatus.OFF
}

private fun DrawScope.drawDeviceMarker(
    mapper: GridMapper,
    cell: IntCell,
    device: Device,
    style: PlanStyle,
    textMeasurer: TextMeasurer,
) {
    val centre = mapper.cellCenter(cell.x, cell.y)
    val radius = mapper.cellMinSide * 0.30f
    val status = device.planStatus()
    val colour = statusColor(status)

    // Shape carries the device kind, decoration carries the status. Neither is
    // conveyed by colour alone — it has to survive a projector and colour
    // blindness.
    when (device.kind) {
        DeviceKind.OUTLET -> drawCircle(colour, radius, centre, style = markerStyle(status))
        DeviceKind.MULTI_SWITCH -> {
            val side = radius * 1.7f
            drawRect(
                color = colour,
                topLeft = Offset(centre.x - side / 2f, centre.y - side / 2f),
                size = Size(side, side),
                style = markerStyle(status),
            )
            // One pip per addressable slot: a 3-gang box looks like a 3-gang box.
            val pipRadius = side * 0.09f
            device.slots.forEachIndexed { index, slot ->
                val spread = side * 0.62f
                val step = if (device.slots.size == 1) 0f else spread / (device.slots.size - 1)
                val x = centre.x - spread / 2f + step * index
                drawCircle(
                    color = if (slot.status == SlotStatus.ON) colour else style.onSurface.copy(alpha = 0.35f),
                    radius = pipRadius,
                    center = Offset(x, centre.y + side * 0.32f),
                )
            }
        }
        DeviceKind.CAMERA -> {
            val path = Path().apply {
                moveTo(centre.x - radius, centre.y - radius * 0.7f)
                lineTo(centre.x + radius * 0.35f, centre.y - radius * 0.7f)
                lineTo(centre.x + radius * 0.35f, centre.y + radius * 0.7f)
                lineTo(centre.x - radius, centre.y + radius * 0.7f)
                close()
            }
            drawPath(path, colour, style = markerStyle(status))
            // Lens cone.
            val lens = Path().apply {
                moveTo(centre.x + radius * 0.35f, centre.y - radius * 0.35f)
                lineTo(centre.x + radius, centre.y - radius * 0.7f)
                lineTo(centre.x + radius, centre.y + radius * 0.7f)
                lineTo(centre.x + radius * 0.35f, centre.y + radius * 0.35f)
                close()
            }
            drawPath(lens, colour, style = markerStyle(status))
        }
    }

    if (status == SlotStatus.ERROR) {
        // An unmistakable cross, readable with no colour at all.
        val r = radius * 0.55f
        drawLine(colour, Offset(centre.x - r, centre.y - r), Offset(centre.x + r, centre.y + r), strokeWidth = 3f)
        drawLine(colour, Offset(centre.x - r, centre.y + r), Offset(centre.x + r, centre.y - r), strokeWidth = 3f)
    }

    val layout = textMeasurer.measure(
        text = device.name,
        style = TextStyle(fontSize = 9.sp, color = style.onSurface),
    )
    val labelY = centre.y + radius + 4f
    if (labelY + layout.size.height < mapper.content.bottom) {
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(centre.x - layout.size.width / 2f, labelY),
        )
    }
}

/**
 * ON is solid, OFF is a plain outline, DISCONNECTED is a dashed outline. The
 * pattern alone tells you the state with the screen in greyscale.
 */
private fun markerStyle(status: SlotStatus): DrawStyleOrFill = when (status) {
    SlotStatus.ON -> Fill
    SlotStatus.OFF -> Stroke(width = 3f)
    SlotStatus.ERROR -> Stroke(width = 3f)
    SlotStatus.DISCONNECTED -> Stroke(
        width = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
    )
}

private typealias DrawStyleOrFill = androidx.compose.ui.graphics.drawscope.DrawStyle
private val Fill = androidx.compose.ui.graphics.drawscope.Fill

private fun statusColor(status: SlotStatus): Color = when (status) {
    SlotStatus.ON -> Color(0xFF1B873F)
    SlotStatus.OFF -> Color(0xFF6B7280)
    SlotStatus.ERROR -> Color(0xFFBA1A1A)
    SlotStatus.DISCONNECTED -> Color(0xFF8A6100)
}
