package lk.ac.ucsc.scs3311.smarthome.ui.floors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.ucsc.scs3311.smarthome.domain.model.Floor
import lk.ac.ucsc.scs3311.smarthome.ui.plan.PlanLibrary
import lk.ac.ucsc.scs3311.smarthome.ui.theme.HomeSenseTheme
import lk.ac.ucsc.scs3311.smarthome.ui.theme.StatusColors
import androidx.compose.foundation.text.KeyboardOptions as TextKeyboardOptions

/**
 * The multi-floor dashboard: every storey of the house, with a live summary,
 * and the entry point for adding another one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorsScreen(
    onOpenFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FloorsViewModel = viewModel(factory = FloorsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Floor?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Floors") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add a floor")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.floors.isEmpty() -> EmptyFloors(Modifier.fillMaxSize().padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.floors, key = { it.floor.id }) { summary ->
                    FloorCard(
                        summary = summary,
                        onClick = { onOpenFloor(summary.floor.id) },
                        onDelete = { pendingDelete = summary.floor },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddFloorDialog(
            existingLevels = state.floors.map { it.floor.level }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name, level, planId, cols, rows ->
                viewModel.addFloor(name, level, planId, cols, rows)
                showAddDialog = false
            },
        )
    }

    pendingDelete?.let { floor ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${floor.name}?") },
            text = {
                Text(
                    "Every device placed on this floor is removed with it. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFloor(floor.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyFloors(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Layers,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("No floors yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add a floor to place devices on its plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FloorCard(
    summary: FloorSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(summary.floor.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${summary.planName} · ${summary.floor.gridCols}×${summary.floor.gridRows} grid",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountChip("${summary.deviceCount} devices", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (summary.onCount > 0) {
                        CountChip("${summary.onCount} on", StatusColors.on)
                    }
                    if (summary.problemCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusColors.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            CountChip("${summary.problemCount} need attention", StatusColors.error)
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${summary.floor.name}")
            }
        }
    }
}

@Composable
private fun CountChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFloorDialog(
    existingLevels: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, level: Int, planId: String, cols: Int, rows: Int) -> Unit,
) {
    // A sensible default level: the next storey up from whatever exists.
    val defaultLevel = remember(existingLevels) { (existingLevels.maxOrNull() ?: -1) + 1 }

    var name by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(defaultLevel.toString()) }
    var planId by rememberSaveable { mutableStateOf(PlanLibrary.groundFloor.id) }
    var cols by rememberSaveable { mutableIntStateOf(Floor.DEFAULT_COLS) }
    var rows by rememberSaveable { mutableIntStateOf(Floor.DEFAULT_ROWS) }
    var planMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a floor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Ground floor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("Storey (0 = ground)") },
                    singleLine = true,
                    keyboardOptions = TextKeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = planMenuOpen,
                    onExpandedChange = { planMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = PlanLibrary.byId(planId).displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Plan") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(planMenuOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            ),
                    )
                    ExposedDropdownMenu(
                        expanded = planMenuOpen,
                        onDismissRequest = { planMenuOpen = false },
                    ) {
                        PlanLibrary.all.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text(plan.displayName) },
                                onClick = {
                                    planId = plan.id
                                    planMenuOpen = false
                                },
                            )
                        }
                    }
                }

                Text(
                    "Grid: $cols columns × $rows rows",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "The grid is the abstract mapping devices are placed on. " +
                        "Coarser grids are easier to hit on a phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = cols.toFloat(),
                    onValueChange = { cols = it.toInt() },
                    valueRange = Floor.COL_RANGE.first.toFloat()..Floor.COL_RANGE.last.toFloat(),
                    steps = Floor.COL_RANGE.count() - 2,
                )
                Slider(
                    value = rows.toFloat(),
                    onValueChange = { rows = it.toInt() },
                    valueRange = Floor.ROW_RANGE.first.toFloat()..Floor.ROW_RANGE.last.toFloat(),
                    steps = Floor.ROW_RANGE.count() - 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, level.toIntOrNull() ?: defaultLevel, planId, cols, rows)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun FloorCardPreview() {
    HomeSenseTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FloorCard(
                summary = FloorSummary(
                    floor = Floor("f1", "Ground floor", 0, "ground_floor", 8, 6),
                    deviceCount = 4,
                    onCount = 2,
                    problemCount = 0,
                ),
                onClick = {},
                onDelete = {},
            )
            FloorCard(
                summary = FloorSummary(
                    floor = Floor("f2", "First floor", 1, "first_floor", 8, 6),
                    deviceCount = 3,
                    onCount = 0,
                    problemCount = 2,
                ),
                onClick = {},
                onDelete = {},
            )
        }
    }
}
