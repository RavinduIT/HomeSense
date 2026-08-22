package lk.ac.ucsc.scs3311.smarthome.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.ucsc.scs3311.smarthome.domain.model.Alert
import lk.ac.ucsc.scs3311.smarthome.domain.model.AlertSeverity
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The alert centre: everything the safety worker has raised.
 *
 * These alerts are written **by the server**, and the database rules let a
 * client change only `acknowledged`. A phone cannot invent an alert, edit its
 * message, or delete one — which is what makes this list a record rather than
 * a notification tray.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    modifier: Modifier = Modifier,
    viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alerts")
                        if (state.unacknowledgedCount > 0) {
                            Text(
                                "${state.unacknowledgedCount} unacknowledged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.alerts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Nothing to report", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Safety cut-offs and device faults appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { text ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusColors.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                    }
                }
            }

            items(state.alerts, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    deviceName = state.deviceNames[alert.deviceId] ?: alert.deviceId,
                    onAcknowledge = { viewModel.acknowledge(alert.id) },
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    deviceName: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, accent) = alert.severity.visuals()

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (alert.acknowledged) 0.dp else 2.dp,
        ),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = alert.severity.name,
                tint = if (alert.acknowledged) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (alert.acknowledged) FontWeight.Normal else FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.at.asTimestamp(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!alert.acknowledged) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onAcknowledge) { Text("Acknowledge") }
                    } else {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "acknowledged",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun AlertSeverity.visuals(): Pair<ImageVector, Color> = when (this) {
    AlertSeverity.CRITICAL -> Icons.Default.LocalFireDepartment to StatusColors.error
    AlertSeverity.WARNING -> Icons.Default.Warning to StatusColors.warning
    AlertSeverity.INFO -> Icons.Default.Info to StatusColors.off
}

private fun Long.asTimestamp(): String =
    SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(Date(this))

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun AlertCardPreview() {
    HomeSenseTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AlertCard(
                alert = Alert(
                    id = "a1",
                    at = 1_786_000_000_000,
                    deviceId = "dev-iron",
                    severity = AlertSeverity.CRITICAL,
                    message = "Iron was switched off automatically after 30s " +
                        "(max_on_duration is 30s).",
                ),
                deviceName = "Utility outlet",
                onAcknowledge = {},
            )
            AlertCard(
                alert = Alert(
                    id = "a2",
                    at = 1_786_000_000_000,
                    deviceId = "dev-gang",
                    severity = AlertSeverity.WARNING,
                    message = "Ceiling fan is not responding: the hub asked for ON " +
                        "but it reports OFF.",
                ),
                deviceName = "Hall gang box",
                onAcknowledge = {},
            )
            AlertCard(
                alert = Alert(
                    id = "a3",
                    at = 1_786_000_000_000,
                    deviceId = "dev-cam",
                    severity = AlertSeverity.INFO,
                    message = "Front door camera reconnected.",
                    acknowledged = true,
                ),
                deviceName = "Front door camera",
                onAcknowledge = {},
            )
        }
    }
}

/** Kept beside the severity mapping so the offline icon has one home. */
internal val OfflineIcon = Icons.Default.CloudOff
