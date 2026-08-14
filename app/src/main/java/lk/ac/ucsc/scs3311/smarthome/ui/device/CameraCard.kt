package lk.ac.ucsc.scs3311.smarthome.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import lk.ac.ucsc.scs3311.smarthome.domain.model.CameraInfo
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.domain.model.DeviceKind
import lk.ac.ucsc.scs3311.smarthome.domain.model.LinkState
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * The camera profile: no slots, nothing to toggle — a snapshot tile that
 * refreshes, and a tap for the full-screen stream.
 *
 * Frames are **mock**, generated locally from the clock rather than fetched.
 * The assignment asks for mock snapshots and mock URI streams, and a synthetic
 * frame is both honest about that and immune to a dead demo Wi-Fi. The real
 * `snapshotUrl` and `streamUrl` from the database are shown underneath, so the
 * plumbing that a real camera would use is visible.
 */
@Composable
fun CameraBody(
    device: Device,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    var fullScreen by remember { mutableStateOf(false) }
    val camera = device.camera ?: CameraInfo()
    val online = device.link == LinkState.ONLINE

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = online) { fullScreen = true },
        ) {
            MockCameraFrame(
                seed = device.id,
                nowMillis = nowMillis,
                online = online,
                modifier = Modifier.fillMaxSize(),
            )

            if (online) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if ((nowMillis / 1000) % 2 == 0L) Color.Red else Color.Transparent),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "LIVE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = nowMillis.asTimestamp(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )

                IconButton(
                    onClick = { fullScreen = true },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Full screen", tint = Color.White)
                }
            } else {
                Text(
                    "No signal",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                status = if (online) {
                    lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus.ON
                } else {
                    lk.ac.ucsc.scs3311.smarthome.domain.model.SlotStatus.DISCONNECTED
                },
                compact = true,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Mock stream",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = camera.streamUrl.ifBlank { "no stream URI configured" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = camera.snapshotUrl.ifBlank { "no snapshot URI configured" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                MockCameraFrame(
                    seed = device.id,
                    nowMillis = nowMillis,
                    online = online,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center),
                )
                Column(
                    Modifier.align(Alignment.TopStart).padding(16.dp),
                ) {
                    Text(device.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        device.camera?.streamUrl.orEmpty(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(
                    onClick = { fullScreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

/**
 * A synthetic camera frame: a dim scene, a slow pan, sensor noise and a
 * scanline, all derived from [nowMillis] so the picture visibly moves.
 */
@Composable
private fun MockCameraFrame(
    seed: String,
    nowMillis: Long,
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    val hash = remember(seed) { seed.hashCode() }

    ComposeCanvas(modifier) {
        if (!online) {
            drawRect(Color(0xFF121212))
            // Static, so an offline camera is unmistakable.
            val rng = java.util.Random(nowMillis / 200)
            repeat(400) {
                val x = rng.nextFloat() * size.width
                val y = rng.nextFloat() * size.height
                drawCircle(
                    Color.White.copy(alpha = 0.10f + rng.nextFloat() * 0.15f),
                    radius = 1.2f,
                    center = Offset(x, y),
                )
            }
            return@ComposeCanvas
        }

        // Night-vision-ish backdrop.
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF16211B),
                0.55f to Color(0xFF1E2E26),
                1f to Color(0xFF0E1512),
            ),
        )

        val pan = ((nowMillis / 40) % 360).toFloat() / 360f
        val drift = kotlin.math.sin(pan * 2f * Math.PI).toFloat() * size.width * 0.02f

        // Ground line.
        drawRect(
            color = Color(0xFF0B120F),
            topLeft = Offset(0f, size.height * 0.68f),
            size = Size(size.width, size.height * 0.32f),
        )

        // A doorway, offset per camera so two cameras do not look identical.
        val doorX = size.width * (0.32f + (hash % 20) / 100f) + drift
        val doorW = size.width * 0.17f
        val doorH = size.height * 0.42f
        drawRect(
            color = Color(0xFF2A3B33),
            topLeft = Offset(doorX, size.height * 0.68f - doorH),
            size = Size(doorW, doorH),
        )
        drawCircle(
            color = Color(0xFF6FA98F),
            radius = size.height * 0.012f,
            center = Offset(doorX + doorW * 0.85f, size.height * 0.68f - doorH * 0.5f),
        )

        // A plant, for something with a silhouette.
        val plantX = size.width * 0.75f + drift
        drawCircle(
            color = Color(0xFF23372C),
            radius = size.height * 0.11f,
            center = Offset(plantX, size.height * 0.60f),
        )
        drawRect(
            color = Color(0xFF1A241F),
            topLeft = Offset(plantX - size.width * 0.012f, size.height * 0.60f),
            size = Size(size.width * 0.024f, size.height * 0.08f),
        )

        // Sensor noise.
        val rng = java.util.Random(nowMillis / 120 + hash)
        repeat(140) {
            drawCircle(
                Color.White.copy(alpha = 0.03f + rng.nextFloat() * 0.05f),
                radius = 1f,
                center = Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height),
            )
        }

        // Scanline sweeping down the frame.
        val scanY = ((nowMillis % 3000) / 3000f) * size.height
        drawRect(
            color = Color.White.copy(alpha = 0.05f),
            topLeft = Offset(0f, scanY),
            size = Size(size.width, size.height * 0.03f),
        )

        // Vignette.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.width * 0.75f,
            ),
        )
    }
}

private fun Long.asTimestamp(): String {
    val totalSeconds = this / 1000
    val h = (totalSeconds / 3600) % 24
    val m = (totalSeconds / 60) % 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun CameraBodyPreview() {
    HomeSenseTheme {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CameraBody(
                device = Device(
                    id = "cam-1",
                    name = "Front door camera",
                    kind = DeviceKind.CAMERA,
                    link = LinkState.ONLINE,
                    camera = CameraInfo("asset://cameras/front.svg", "mock://stream/front_door"),
                ),
                nowMillis = 1_700_000_000_000L,
            )
            Box(Modifier.height(8.dp))
            CameraBody(
                device = Device(
                    id = "cam-2",
                    name = "Landing camera",
                    kind = DeviceKind.CAMERA,
                    link = LinkState.DISCONNECTED,
                    camera = CameraInfo("asset://cameras/landing.svg", "mock://stream/landing"),
                ),
                nowMillis = 1_700_000_000_000L,
            )
        }
    }
}

/** Used by the offline tile to stay visually distinct from a dark scene. */
internal val OfflineTint = StatusColors.disconnected
