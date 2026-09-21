package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightProjectRepository
import com.hasu.tilelayout.db.SqlDelightRoomRepository
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.engine.SurfacePositionCalculator
import com.hasu.tilelayout.models.Room
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onRoomClick: (String) -> Unit,
    onTileGroupClick: (String) -> Unit,
) {
    val projectRepo = remember {
        SqlDelightProjectRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val roomRepo = remember {
        SqlDelightRoomRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val surfaceRepo = remember {
        SqlDelightSurfaceRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var projectName by remember { mutableStateOf("Project") }
    var showAddRoom by remember { mutableStateOf(false) }
    var refreshRooms by remember { mutableStateOf(0) }

    LaunchedEffect(projectId) {
        projectName = projectRepo.getById(projectId)?.name ?: "Project"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = { showAddRoom = true },
                    modifier = Modifier.testTag("add-room"),
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Rooms") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tile Library") })
            }
            when (tab) {
                0 -> RoomListView(
                    projectId = projectId,
                    refreshSignal = refreshRooms,
                    onRoomClick = onRoomClick,
                )
                else -> TileLibraryScreen(
                    projectId = projectId,
                    onTileGroupClick = onTileGroupClick,
                    onCaptureTexture = onTileGroupClick,
                )
            }
        }
    }

    if (showAddRoom) {
        AddRoomDialog(
            onDismiss = { showAddRoom = false },
            onConfirm = { name, width, depth, height, doorWall, doorWidth, doorHeight, doorOffset ->
                scope.launch {
                    val room = Room(
                        projectId = projectId,
                        name = name,
                        width = width,
                        depth = depth,
                        height = height,
                        doorWall = doorWall,
                        doorWidth = doorWidth,
                        doorHeight = doorHeight,
                        doorOffset = doorOffset,
                    )
                    roomRepo.insert(room)
                    // Surfaces generate automatically from the room dimensions —
                    // no separate "Generate Surfaces" step for new rooms.
                    SurfacePositionCalculator.generate(
                        roomId = room.id,
                        roomWidth = width,
                        roomDepth = depth,
                        roomHeight = height,
                        includeFront = true,
                        includeBack = true,
                        includeLeft = true,
                        includeRight = true,
                        includeFloor = true,
                    ).forEach { surfaceRepo.insert(it) }
                    refreshRooms++
                    showAddRoom = false
                    // Adding a room goes straight into the room editor.
                    onRoomClick(room.id)
                }
            },
        )
    }
}

@Composable
private fun AddRoomDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, width: Double, depth: Double, height: Double,
               doorWall: Double?, doorWidth: Double, doorHeight: Double, doorOffset: Double?) -> Unit,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("3000") }
    var depth by remember { mutableStateOf("4000") }
    var height by remember { mutableStateOf("2400") }
    // No door selected until the user taps a wall — Add stays disabled.
    var doorWall by remember { mutableStateOf<Double?>(null) }
    var doorWidth by remember { mutableStateOf("900") }
    var doorHeight by remember { mutableStateOf("2100") }
    var doorOffset by remember { mutableStateOf("") }
    var doorCentered by remember { mutableStateOf(true) }

    val widthValue = width.toDoubleOrNull() ?: 0.0
    val depthValue = depth.toDoubleOrNull() ?: 0.0
    val heightValue = height.toDoubleOrNull() ?: 0.0
    val doorWidthValue = doorWidth.toDoubleOrNull() ?: 0.0
    val doorHeightValue = doorHeight.toDoubleOrNull() ?: 0.0
    val doorOffsetValue = doorOffset.toDoubleOrNull()
    val dimsValid = widthValue > 0 && depthValue > 0 && heightValue > 0

    // Door validation mirrors DoorSectionView: width/height fit the room,
    // offset stays inside the wall span.
    val wallSpan = if (doorWall == 90.0 || doorWall == 270.0) depthValue else widthValue
    val centeredOffsetString = (((wallSpan - doorWidthValue) / 2).coerceAtLeast(0.0)).toInt().toString()
    // Centered = null offset (saved as null → "(centered)" everywhere).
    val effectiveOffset = if (doorCentered) null else doorOffsetValue
    val doorValid = doorWall != null &&
        doorWidthValue >= 400 && doorWidthValue <= wallSpan &&
        doorHeightValue >= 1500 && doorHeightValue <= heightValue &&
        (effectiveOffset == null || (effectiveOffset >= 0 && effectiveOffset <= wallSpan - doorWidthValue))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) "New Room" else "Door") },
        text = {
            if (step == 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { keyboard?.hide() }
                    },
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Room name") },
                        singleLine = true,
                        modifier = Modifier.testTag("room-name-field"),
                    )
                    DimensionField("Width (mm)", width, { width = it })
                    DimensionField("Depth (mm)", depth, { depth = it })
                    DimensionField("Height (mm)", height, { height = it })
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { keyboard?.hide() }
                    },
                ) {
                    Text(
                        "Tap the wall with the door",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DoorDiagram(
                        roomWidth = widthValue,
                        roomDepth = depthValue,
                        selectedWall = doorWall,
                        onWallSelected = { wall ->
                            doorWall = wall
                            doorCentered = true
                        },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        doorWidth = doorWidthValue,
                        doorOffset = doorOffsetValue,
                    )
                    DimensionField("Door width (mm)", doorWidth, { doorWidth = it })
                    DimensionField("Door height (mm)", doorHeight, { doorHeight = it })
                    OutlinedTextField(
                        value = if (doorCentered) centeredOffsetString else doorOffset,
                        onValueChange = {
                            doorCentered = false
                            doorOffset = it
                        },
                        label = { Text("Offset from wall corner (mm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text(if (doorCentered) "(centered)" else "") },
                    )
                    TextButton(
                        onClick = { doorCentered = true },
                        enabled = doorWall != null,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("↔ Center Door")
                    }
                }
            }
        },
        confirmButton = {
            if (step == 0) {
                TextButton(
                    onClick = { step = 1 },
                    enabled = dimsValid,
                    modifier = Modifier.testTag("add-room-next"),
                ) {
                    Text("Next")
                }
            } else {
                TextButton(
                    onClick = {
                        onConfirm(
                            name.trim(), widthValue, depthValue, heightValue,
                            doorWall, doorWidthValue, doorHeightValue, effectiveOffset,
                        )
                    },
                    enabled = doorValid,
                    modifier = Modifier.testTag("add-room-confirm"),
                ) {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 1) step = 0 else onDismiss() }) {
                Text(if (step == 1) "Back" else "Cancel")
            }
        },
    )
}

@Composable
private fun DimensionField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
