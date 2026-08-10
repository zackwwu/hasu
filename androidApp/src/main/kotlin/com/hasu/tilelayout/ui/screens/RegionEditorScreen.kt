package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.engine.RegionValidator
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceTileGroup
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.models.TilePattern
import kotlinx.coroutines.launch

/**
 * Adds a tile region (SurfaceTileGroup) to a surface. The region must lie
 * within the surface bounds and must not overlap existing regions
 * (RegionValidator also enforces full coverage).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionEditorScreen(
    projectId: String,
    roomId: String,
    surfaceId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val surfaceRepo = remember {
        SqlDelightSurfaceRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val tileGroupRepo = remember {
        SqlDelightTileGroupRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }

    var surface by remember { mutableStateOf<Surface?>(null) }
    var tileGroups by remember { mutableStateOf<List<TileGroup>>(emptyList()) }
    var selectedTileGroupId by remember { mutableStateOf<String?>(null) }
    var showTileGroupPicker by remember { mutableStateOf(false) }

    var x by remember { mutableStateOf("0") }
    var y by remember { mutableStateOf("0") }
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var offsetX by remember { mutableStateOf("0") }
    var offsetY by remember { mutableStateOf("0") }
    var pattern by remember { mutableStateOf(TilePattern.GRID) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(surfaceId) {
        val s = surfaceRepo.getById(surfaceId) ?: return@LaunchedEffect
        surface = s
        width = s.width.toInt().toString()
        height = s.height.toInt().toString()
        val groups = tileGroupRepo.getByProject(projectId)
        tileGroups = groups
        selectedTileGroupId = groups.firstOrNull()?.id
    }

    val s = surface
    val selectedTileGroup = tileGroups.firstOrNull { it.id == selectedTileGroupId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Tile Region") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
            )
        },
    ) { padding ->
        if (s == null) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Surface ${s.width.toInt()} × ${s.height.toInt()} mm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier.fillMaxWidth().clickable { showTileGroupPicker = true },
            ) {
                OutlinedTextField(
                    value = selectedTileGroup?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tile group") },
                    placeholder = { Text("Select a tile group") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RegionField("X (mm)", x, { x = it }, Modifier.weight(1f))
                RegionField("Y (mm)", y, { y = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RegionField("Width (mm)", width, { width = it }, Modifier.weight(1f))
                RegionField("Height (mm)", height, { height = it }, Modifier.weight(1f))
            }

            Text("Pattern", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TilePattern.entries.forEach { p ->
                    FilterChip(
                        selected = pattern == p,
                        onClick = { pattern = p },
                        label = { Text(patternLabel(p)) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RegionField("Offset X", offsetX, { offsetX = it }, Modifier.weight(1f))
                RegionField("Offset Y", offsetY, { offsetY = it }, Modifier.weight(1f))
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    val region = RegionRect(
                        x = x.toDoubleOrNull() ?: 0.0,
                        y = y.toDoubleOrNull() ?: 0.0,
                        width = width.toDoubleOrNull() ?: 0.0,
                        height = height.toDoubleOrNull() ?: 0.0,
                    )
                    val tgId = selectedTileGroupId
                    when {
                        tgId == null -> error = "Select a tile group first."
                        region.width <= 0 || region.height <= 0 ->
                            error = "Region width and height must be positive."
                        region.x < 0 || region.y < 0 ->
                            error = "Region must lie within the surface bounds."
                        region.x + region.width > s.width + 0.01 || region.y + region.height > s.height + 0.01 ->
                            error = "Region must lie within the surface bounds."
                        else -> {
                            scope.launch {
                                saving = true
                                val existing = surfaceRepo.getSTGsBySurface(surfaceId)
                                val result = RegionValidator.validate(
                                    newRegion = region,
                                    existingRegions = existing,
                                    surfaceWidth = s.width,
                                    surfaceHeight = s.height,
                                )
                                if (result.isValid) {
                                    surfaceRepo.insertSTG(
                                        SurfaceTileGroup(
                                            surfaceId = surfaceId,
                                            tileGroupId = tgId,
                                            region = region,
                                            pattern = pattern,
                                            offsetX = offsetX.toDoubleOrNull() ?: 0.0,
                                            offsetY = offsetY.toDoubleOrNull() ?: 0.0,
                                        )
                                    )
                                    onBack()
                                } else {
                                    error = result.errors.joinToString("\n") { it.message }
                                    saving = false
                                }
                            }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Validating…" else "Save Region")
            }
        }
    }

    if (showTileGroupPicker) {
        TileGroupPickerDialog(
            tileGroups = tileGroups,
            onDismiss = { showTileGroupPicker = false },
            onSelect = { group ->
                selectedTileGroupId = group.id
                showTileGroupPicker = false
            },
        )
    }
}

@Composable
private fun RegionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun TileGroupPickerDialog(
    tileGroups: List<TileGroup>,
    onDismiss: () -> Unit,
    onSelect: (TileGroup) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tile Group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tileGroups.forEach { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        supportingContent = {
                            Text("${group.tileWidth.toInt()} × ${group.tileHeight.toInt()} mm")
                        },
                        modifier = Modifier.clickable { onSelect(group) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun patternLabel(pattern: TilePattern): String = when (pattern) {
    TilePattern.GRID -> "Grid"
    TilePattern.BRICK -> "Brick"
    TilePattern.STACKED -> "Stacked"
    TilePattern.HERRINGBONE -> "Herringbone"
}
