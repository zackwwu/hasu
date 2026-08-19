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
            Text(
                text = "Room ${it.width.toInt()} × ${it.depth.toInt()} × ${it.height.toInt()} mm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DoorCard(
                room = it,
                onSave = { doorWall, doorWidth, doorHeight, doorOffset ->
                    scope.launch {
                        roomRepo.updateDoor(roomId, doorWall, doorWidth, doorHeight, doorOffset)
                        room = roomRepo.getById(roomId)
                        vm.loadSurfaces(roomId)
                    }
                },
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

// ── Door configuration ──

private const val MIN_DOOR_WIDTH = 400.0
private const val MIN_DOOR_HEIGHT = 1500.0

/** Wall span in mm for a given door wall rotation. */
private fun wallSpanFor(room: Room, wall: Double?): Double = when (wall) {
    90.0, 270.0 -> room.depth
    else -> room.width
}

/**
 * Door configuration card: diagram wall picker, width/height/offset fields,
 * inline validation, and a Save button gated on dirty & valid state.
 */
@Composable
private fun DoorCard(
    room: Room,
    onSave: (doorWall: Double?, doorWidth: Double, doorHeight: Double, doorOffset: Double?) -> Unit,
) {
    var selectedWall by remember(room) { mutableStateOf(room.doorWall) }
    var widthText by remember(room) { mutableStateOf(room.doorWidth.toInt().toString()) }
    var heightText by remember(room) { mutableStateOf(room.doorHeight.toInt().toString()) }
    var offsetText by remember(room) { mutableStateOf(room.doorOffset?.toInt()?.toString() ?: "") }
    var offsetTouched by remember(room) { mutableStateOf(room.doorOffset != null) }

    val wallSpan = wallSpanFor(room, selectedWall)
    val width = widthText.toDoubleOrNull() ?: 0.0
    val height = heightText.toDoubleOrNull() ?: 0.0
    val offset = offsetText.toDoubleOrNull()

    val widthError = width < MIN_DOOR_WIDTH || width > wallSpan
    val heightError = height < MIN_DOOR_HEIGHT || height > room.height
    val offsetError =
        offsetTouched && !offsetText.isBlank() && offset != null && (offset < 0.0 || offset > wallSpan - width)
    val valid = !widthError && !heightError && !offsetError

    val centeredOffset = ((wallSpan - width) / 2.0).coerceAtLeast(0.0)

    val dirty = selectedWall != room.doorWall ||
        abs(width - room.doorWidth) > 0.01 ||
        abs(height - room.doorHeight) > 0.01 ||
        offset != room.doorOffset

    // No wall selected (clearing the door) needs no field validity.
    val canSave = if (selectedWall == null) dirty else dirty && valid

    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚪", fontSize = 18.sp)
                Text(" Door", style = MaterialTheme.typography.titleMedium)
            }

            DoorDiagram(
                roomWidth = room.width,
                roomDepth = room.depth,
                selectedWall = selectedWall,
                onWallSelected = { wall ->
                    selectedWall = wall
                    // Auto-fill the centered offset for the new wall
                    offsetText = (((wallSpanFor(room, wall) - width) / 2.0).coerceAtLeast(0.0)).toInt().toString()
                    offsetTouched = false
                },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )

            TextButton(
                onClick = { selectedWall = null },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("None")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text("Width (mm)") },
                    singleLine = true,
                    enabled = selectedWall != null,
                    isError = selectedWall != null && widthError,
                    supportingText = {
                        if (selectedWall != null && widthError) {
                            Text("Door width must be ${MIN_DOOR_WIDTH.toInt()}–${wallSpan.toInt()} mm")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (mm)") },
                    singleLine = true,
                    enabled = selectedWall != null,
                    isError = selectedWall != null && heightError,
                    supportingText = {
                        if (selectedWall != null && heightError) {
                            Text("Door height must be ${MIN_DOOR_HEIGHT.toInt()}–${room.height.toInt()} mm")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = offsetText,
                onValueChange = {
                    offsetText = it
                    offsetTouched = true
                },
                label = { Text("Offset from wall corner (mm)") },
                singleLine = true,
                enabled = selectedWall != null,
                isError = offsetError,
                supportingText = {
                    when {
                        offsetError ->
                            Text("Offset must be 0–${(wallSpan - width).toInt()} mm")
                        selectedWall != null && !offsetTouched ->
                            Text("Auto-centered on wall selection (${centeredOffset.toInt()} mm)")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(selectedWall, width, height, offset)
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Save Door" },
            ) {
                Text("Save Door")
            }
        }
    }
}
