package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightLayoutResultRepository
import com.hasu.tilelayout.db.SqlDelightRoomRepository
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceTileGroup
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.models.TilePattern
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Surface detail: grout settings, assigned tile groups, layout recalculation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurfaceDetailScreen(
    projectId: String,
    roomId: String,
    surfaceId: String,
    onBack: () -> Unit,
    onAddRegion: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val surfaceRepo = remember {
        SqlDelightSurfaceRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val tileGroupRepo = remember {
        SqlDelightTileGroupRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val roomRepo = remember {
        SqlDelightRoomRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val vm = remember {
        RoomEditorViewModel(
            roomRepo = roomRepo,
            surfaceRepo = surfaceRepo,
            tileGroupRepo = tileGroupRepo,
            layoutRepo = SqlDelightLayoutResultRepository(AppDatabase.instance.tileLayoutDbQueries),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }

    var surface by remember { mutableStateOf<Surface?>(null) }
    var tileGroups by remember { mutableStateOf<List<TileGroup>>(emptyList()) }
    var stgs by remember { mutableStateOf<List<SurfaceTileGroup>>(emptyList()) }
    var groutColor by remember { mutableStateOf(GroutColor.GREY) }
    var groutWidth by remember { mutableStateOf(3f) }
    var recomputing by remember { mutableStateOf(false) }

    LaunchedEffect(surfaceId) {
        val s = surfaceRepo.getById(surfaceId) ?: return@LaunchedEffect
        surface = s
        groutColor = s.groutColor
        groutWidth = s.groutWidth.toFloat()
        val room = roomRepo.getById(s.roomId)
        tileGroups = room?.let { tileGroupRepo.getByProject(it.projectId) } ?: emptyList()
        stgs = surfaceRepo.getSTGsBySurface(surfaceId)
    }

    val currentSurface = surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSurface?.let { surfaceTitle(it) } ?: "Surface") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
            )
        },
    ) { padding ->
        val s = currentSurface
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SurfaceInfoCard(s)
                }
                item {
                    GroutCard(
                        selectedColor = groutColor,
                        width = groutWidth,
                        onColorChange = { color ->
                            groutColor = color
                            scope.launch {
                                surfaceRepo.updateGrout(surfaceId, color, groutWidth.toDouble())
                            }
                        },
                        onWidthChange = { groutWidth = it },
                        onWidthCommit = {
                            scope.launch {
                                surfaceRepo.updateGrout(surfaceId, groutColor, groutWidth.toDouble())
                            }
                        },
                    )
                }
                item {
                    Text(
                        "Tile Groups (${stgs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (stgs.isEmpty()) {
                    item {
                        Text(
                            "No tile groups assigned. Tap Add Tile Group to cover this surface.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(stgs, key = { it.id }) { stg ->
                        STGCard(
                            stg = stg,
                            tileGroups = tileGroups,
                            onDelete = {
                                scope.launch {
                                    surfaceRepo.deleteSTG(stg.id)
                                    stgs = surfaceRepo.getSTGsBySurface(surfaceId)
                                }
                            },
                        )
                    }
                }
                item {
                    Button(
                        onClick = onAddRegion,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add Tile Group")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                recomputing = true
                                vm.computeLayout(surfaceId)
                                recomputing = false
                            }
                        },
                        enabled = !recomputing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (recomputing) "Recalculating…" else "Recalculate Layout")
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceInfoCard(surface: Surface) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow("Type", if (surface.type == SurfaceType.WALL) "Wall" else "Floor")
            InfoRow("Dimensions", "${surface.width.toInt()} × ${surface.height.toInt()} mm")
            InfoRow("Position", "(x ${surface.position.x.toInt()}, y ${surface.position.y.toInt()}, z ${surface.position.z.toInt()})")
            InfoRow("Rotation", "${surface.position.rotation.toInt()}°")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GroutCard(
    selectedColor: GroutColor,
    width: Float,
    onColorChange: (GroutColor) -> Unit,
    onWidthChange: (Float) -> Unit,
    onWidthCommit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Grout", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GroutColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(groutComposeColor(color), CircleShape)
                            .border(
                                width = if (selectedColor == color) 3.dp else 1.dp,
                                color = if (selectedColor == color) {
                                    Color(0xFF1E88E5)
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            )
                            .clickable { onColorChange(color) },
                    )
                }
            }
            Column {
                Text(
                    "Width: ${width.toInt()} mm",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = width,
                    onValueChange = onWidthChange,
                    onValueChangeFinished = onWidthCommit,
                    valueRange = 1f..10f,
                )
            }
        }
    }
}

private fun groutComposeColor(color: GroutColor): Color = when (color) {
    GroutColor.BLACK -> Color(0xFF000000)
    GroutColor.GREY -> Color(0xFF9E9E9E)
    GroutColor.WHITE -> Color(0xFFF5F5F5)
}

@Composable
private fun STGCard(
    stg: SurfaceTileGroup,
    tileGroups: List<TileGroup>,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tileGroups.firstOrNull { it.id == stg.tileGroupId }?.name ?: stg.tileGroupId,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                PatternChip(stg.pattern)
            }
            Text(
                "Region: (${stg.region.x.toInt()}, ${stg.region.y.toInt()}) ${stg.region.width.toInt()}×${stg.region.height.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Offset: (${stg.offsetX.toInt()}, ${stg.offsetY.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Text("✕", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PatternChip(pattern: TilePattern) {
    Text(
        text = patternLabel(pattern),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF00695C),
        modifier = Modifier
            .background(Color(0xFFB2DFDB), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun surfaceTitle(surface: Surface): String {
    val typeName = if (surface.type == SurfaceType.WALL) "Wall" else "Floor"
    return "$typeName ${surface.width.toInt()}×${surface.height.toInt()}"
}
