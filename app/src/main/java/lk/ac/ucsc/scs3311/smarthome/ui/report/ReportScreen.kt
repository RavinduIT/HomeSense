package lk.ac.ucsc.scs3311.smarthome.ui.report

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lk.ac.ucsc.scs3311.smarthome.data.local.SlotUsageTotal
import lk.ac.ucsc.scs3311.smarthome.ui.device.asClock
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors
import java.io.File

/**
 * Usage reporting over the devices that matter.
 *
 * Every number here comes from logged transitions in the append-only usage
 * log, aggregated in SQL. Nothing is inferred by diffing state, so events that
 * happened while the phone was closed — including safety cut-offs — are
 * counted exactly like the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = viewModel(factory = ReportViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Usage") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val rows = viewModel.exportRows()
                                withContext(Dispatchers.IO) {
                                    shareCsv(context, UsageCsv.build(rows))
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export as CSV")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportRange.entries.forEach { option ->
                        FilterChip(
                            selected = state.range == option,
                            onClick = { viewModel.selectRange(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Default.Timer,
                        value = state.totalOnSeconds.asDuration(),
                        label = "total on-time",
                        accent = StatusColors.on,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Default.Bolt,
                        value = "%.2f".format(state.estimatedKwh),
                        label = "kWh (estimate)",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        icon = Icons.Default.Warning,
                        value = state.cutoffCount.toString(),
                        label = if (state.cutoffCount == 1) "cut-off" else "cut-offs",
                        accent = if (state.cutoffCount > 0) StatusColors.error else StatusColors.off,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!state.hasData) {
                item { EmptyReport() }
                return@LazyColumn
            }

            item {
                Text("Runtime leaderboard", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Longest-running appliances in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val peak = state.leaderboard.maxOfOrNull { it.totalOnSeconds } ?: 1L
            items(state.leaderboard, key = { it.deviceId + it.slotId }) { row ->
                UsageBar(row = row, peakSeconds = peak.coerceAtLeast(1L))
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "About these numbers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "On-time is summed from logged OFF and CUTOFF events, each of " +
                                "which records how long its slot had been running. Energy is " +
                                "an estimate: it assumes the appliance draws its rated wattage " +
                                "for the whole period.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One bar of the leaderboard.
 *
 * Drawn with a Box rather than a chart library: one dependency fewer, and
 * three undergraduates can explain every line of it in a viva.
 */
@Composable
private fun UsageBar(row: SlotUsageTotal, peakSeconds: Long, modifier: Modifier = Modifier) {
    val fraction = (row.totalOnSeconds.toFloat() / peakSeconds.toFloat()).coerceIn(0f, 1f)

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.slotLabel.ifBlank { row.slotId },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.totalOnSeconds.asDuration(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (row.cutoffs > 0) StatusColors.warning else StatusColors.on),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildString {
                append("${row.sessions} session${if (row.sessions == 1) "" else "s"}")
                if (row.cutoffs > 0) {
                    append(" · ${row.cutoffs} safety cut-off${if (row.cutoffs == 1) "" else "s"}")
                }
                row.watts?.let { append(" · ${"%.2f".format(row.estimatedKwh)} kWh est.") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyReport() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Insights,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Nothing recorded yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Switch a device on and off and it will appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun Long.asDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Hands the CSV to whatever the user picks — Drive, Gmail, a file manager.
 *
 * A `FileProvider` URI with a read grant, not a raw `file://` path: the latter
 * throws `FileUriExposedException` from Android 7 onwards. This is the Intents
 * material from the course used for something the report genuinely needs.
 */
private fun shareCsv(context: Context, csv: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "homesense-usage.csv")
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "HomeSense usage export")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(intent, "Export usage").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun UsageBarPreview() {
    HomeSenseTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Default.Timer, "3h 42m", "total on-time", StatusColors.on, Modifier.weight(1f))
                StatTile(Icons.Default.Bolt, "2.14", "kWh (estimate)", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile(Icons.Default.Warning, "2", "cut-offs", StatusColors.error, Modifier.weight(1f))
            }
            UsageBar(
                row = SlotUsageTotal("d1", "s1", "Iron", 5_400, 6, 2, 1200),
                peakSeconds = 5_400,
            )
            UsageBar(
                row = SlotUsageTotal("d2", "s1", "Ceiling light", 3_600, 2, 0, 24),
                peakSeconds = 5_400,
            )
            UsageBar(
                row = SlotUsageTotal("d2", "s3", "Ceiling fan", 900, 1, 0, 60),
                peakSeconds = 5_400,
            )
        }
    }
}
