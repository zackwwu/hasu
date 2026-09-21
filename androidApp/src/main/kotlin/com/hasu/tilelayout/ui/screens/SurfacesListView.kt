package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightRoomRepository
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.engine.SurfacePositionCalculator
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.launch

/** Lists surfaces for a room and offers generation of the default 5 surfaces. */
@Composable
fun SurfacesListView(
    vm: RoomEditorViewModel,
    roomId: String,
    onSurfaceClick: (String) -> Unit,
    onEditRoom: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val surfaceRepo = remember {
        SqlDelightSurfaceRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val roomRepo = remember {
        SqlDelightRoomRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val surfaces by vm.surfaces.collectAsState()
    var room by remember { mutableStateOf<Room?>(null) }
    var showGenerateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) {
        room = roomRepo.getById(roomId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        room?.let {
            // Top-down view of the room with dimensions annotated on the
            // drawing and the door size under the door wall. Tap to edit.
            Text(
                "Layout",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DoorDiagram(
                roomWidth = it.width,
                roomDepth = it.depth,
                selectedWall = it.doorWall,
                onWallSelected = {},
                modifier = Modifier.fillMaxWidth().height(240.dp),
                doorWidth = it.doorWidth,
                doorOffset = it.doorOffset,
                centerCaption = "W ${it.width.toInt()} mm\nD ${it.depth.toInt()} mm\nH ${it.height.toInt()} mm",
                doorCaption = {
                    val span = if (it.doorWall == 90.0 || it.doorWall == 270.0) it.depth else it.width
                    val center = (span - it.doorWidth) / 2.0
                    val offset = it.doorOffset
                    val centered = offset == null || kotlin.math.abs(offset - center) <= 0.5
                    "${it.doorWidth.toInt()} × ${it.doorHeight.toInt()} mm" +
                        if (centered) " (centered)"
                        else " · offset ${offset.toInt()} mm"
                }(),
                onAnyTap = { onEditRoom() },
            )
            Text(
                "Tap to edit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider()

        if (surfaces.isEmpty()) {
            EmptyState(
                title = "No Surfaces",
                hint = "Generate the room's wall and floor surfaces from its dimensions.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(surfaces, key = { it.id }) { surface ->
                    SurfaceRow(
                        surface = surface,
                        onClick = { onSurfaceClick(surface.id) },
                    )
                }
            }
        }

        Button(
            onClick = { showGenerateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { contentDescription = "Generate Surfaces" },
        ) {
            Text("Generate Surfaces")
        }
    }

    if (showGenerateDialog) {
        GenerateSurfacesDialog(
            onDismiss = { showGenerateDialog = false },
            onConfirm = { includeFront, includeBack, includeLeft, includeRight, includeFloor ->
                scope.launch {
                    val currentRoom = room ?: return@launch
                    val generated = SurfacePositionCalculator.generate(
                        roomId = roomId,
                        roomWidth = currentRoom.width,
                        roomDepth = currentRoom.depth,
                        roomHeight = currentRoom.height,
                        includeFront = includeFront,
                        includeBack = includeBack,
                        includeLeft = includeLeft,
                        includeRight = includeRight,
                        includeFloor = includeFloor,
                    )
                    for (surface in generated) {
                        surfaceRepo.insert(surface)
                    }
                    vm.loadSurfaces(roomId)
                    showGenerateDialog = false
                }
            },
        )
    }
}

@Composable
private fun SurfaceRow(surface: Surface, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(surfaceName(surface)) },
        supportingContent = { Text("${surface.width.toInt()} × ${surface.height.toInt()} mm") },
        leadingContent = {
            SurfaceTypeBadge(surface.type)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SurfaceTypeBadge(type: SurfaceType) {
    val (label, background) = when (type) {
        SurfaceType.WALL -> "Wall" to Color(0xFF1E88E5)
        SurfaceType.FLOOR -> "Floor" to Color(0xFF8D6E63)
    }
    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun surfaceName(surface: Surface): String =
    "${surface.displayName()} ${surface.width.toInt()}×${surface.height.toInt()}"

@Composable
private fun GenerateSurfacesDialog(
    onDismiss: () -> Unit,
    onConfirm: (front: Boolean, back: Boolean, left: Boolean, right: Boolean, floor: Boolean) -> Unit,
) {
    var front by remember { mutableStateOf(true) }
    var back by remember { mutableStateOf(true) }
    var left by remember { mutableStateOf(true) }
    var right by remember { mutableStateOf(true) }
    var floor by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Surfaces") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GenerateOption("Front wall", front, { front = it })
                GenerateOption("Back wall", back, { back = it })
                GenerateOption("Left wall", left, { left = it })
                GenerateOption("Right wall", right, { right = it })
                GenerateOption("Floor", floor, { floor = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(front, back, left, right, floor) },
                enabled = front || back || left || right || floor,
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun GenerateOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

