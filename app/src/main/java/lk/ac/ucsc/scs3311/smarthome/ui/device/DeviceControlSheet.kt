package lk.ac.ucsc.scs3311.smarthome.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.ucsc.scs3311.smarthome.ui.demo.DemoChaosPanel

/**
 * The controls for one device, as a bottom sheet over the floor plan.
 *
 * The sheet is a thin shell: it resolves the device from the ViewModel and
 * hands it to the card that matches its kind. All of the per-profile behaviour
 * lives in those cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlSheet(
    deviceId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // Keyed by device. Without a key, Compose derives one from the ViewModel
    // class alone, so opening a second device within the same navigation entry
    // would return the first device's ViewModel, complete with its identifier.
    viewModel: DeviceViewModel = viewModel(
        key = deviceId,
        factory = DeviceViewModel.factory(deviceId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        val device = state.device
        if (device == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    "Loading device…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ModalBottomSheet
        }

        DeviceCard(
            device = device,
            nowMillis = state.nowMillis,
            onToggleSlot = viewModel::toggleSlot,
            onToggleAll = viewModel::toggleAll,
            onEditSchedule = viewModel::updateSchedule,
            onEditSafety = viewModel::updateSafety,
            onEditSlot = viewModel::editSlot,
            onDelete = {
                viewModel.deleteDevice()
                onDismiss()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            pendingSlotIds = state.pendingSlotIds,
        )

        // Shown when an optimistic toggle was rolled back because the hardware
        // never reported the change.
        state.message?.let { text ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
            }
        }

        // Present only in the demo flavour; renders nothing in live builds.
        DemoChaosPanel(
            device = device,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}
