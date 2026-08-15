package lk.ac.ucsc.scs3311.smarthome.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import lk.ac.ucsc.scs3311.smarthome.BuildConfig
import lk.ac.ucsc.scs3311.smarthome.HomeSenseApp
import lk.ac.ucsc.scs3311.smarthome.data.remote.FakeRemoteSource
import lk.ac.ucsc.scs3311.smarthome.domain.model.Device
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors

/**
 * The simulator's three chaos controls, surfaced inside the `demo` flavour.
 *
 * The `demo` build has no Firebase project and therefore no browser simulator
 * to press buttons in — but the fake backend plays the hardware's part
 * faithfully, including refusing to obey a faulted relay and letting the
 * heartbeat go stale. Exposing the same three controls means the offline build
 * can demonstrate ERROR, DISCONNECTED and an externally-originated change
 * exactly as the live system does.
 *
 * Renders nothing at all in the `live` flavour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoChaosPanel(device: Device, modifier: Modifier = Modifier) {
    if (!BuildConfig.USE_FAKE_BACKEND) return

    val context = LocalContext.current
    val fake: FakeRemoteSource = (context.applicationContext as? HomeSenseApp)
        ?.container
        ?.fakeSource
        ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Simulated hardware", style = MaterialTheme.typography.labelLarge)
            Text(
                "Acts on the device itself, not through the app — the same three " +
                    "controls the browser simulator has.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { fake.injectFault(device.id) },
                    label = { Text("Simulate fault") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = StatusColors.error),
                )
                AssistChip(
                    onClick = { fake.clearFault(device.id) },
                    label = { Text("Clear fault") },
                )
                AssistChip(
                    onClick = { fake.unplug(device.id) },
                    label = { Text("Unplug") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = StatusColors.disconnected,
                    ),
                )
                AssistChip(
                    onClick = { fake.replug(device.id) },
                    label = { Text("Plug back in") },
                )
                device.slots.firstOrNull()?.let { slot ->
                    AssistChip(
                        onClick = { fake.physicalToggle(device.id, slot.id) },
                        label = { Text("Physical switch") },
                    )
                }
            }
        }
    }
}
