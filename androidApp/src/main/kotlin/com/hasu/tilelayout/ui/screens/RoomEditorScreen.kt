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
import androidx.compose.runtime.DisposableEffect
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
import com.hasu.tilelayout.db.SqlDelightLayoutResultRepository
import com.hasu.tilelayout.db.SqlDelightRoomRepository
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.engine.SurfacePositionCalculator
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.ui.tabs.CutListTab
import com.hasu.tilelayout.ui.tabs.LayoutTab
import com.hasu.tilelayout.ui.tabs.PreviewTab
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Room editor with tab scaffold: Surfaces | Layout | Preview | Cut List.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomEditorScreen(
    projectId: String,
    roomId: String,
    onBack: () -> Unit,
    onSurfaceClick: (String) -> Unit,
) {
    val roomRepo = remember {
        SqlDelightRoomRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val scope = remember {
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
    val vm = remember {
        RoomEditorViewModel(
            roomRepo = roomRepo,
            surfaceRepo = SqlDelightSurfaceRepository(AppDatabase.instance.tileLayoutDbQueries),
            tileGroupRepo = SqlDelightTileGroupRepository(AppDatabase.instance.tileLayoutDbQueries),
            layoutRepo = SqlDelightLayoutResultRepository(AppDatabase.instance.tileLayoutDbQueries),
            scope = scope,
        )
    }

    var roomName by remember { mutableStateOf("Room") }
    var tab by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    var showEditRoom by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) {
        roomName = roomRepo.getById(roomId)?.name ?: "Room"
        vm.loadSurfaces(roomId)
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.cancelPendingLayouts()
            scope.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roomName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditRoom = true }) {
                        Text("✎")
                    }
                    IconButton(onClick = { showHelp = true }) {
                        Text("?")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Surfaces") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Layout") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Preview") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Cut List") })
            }
            when (tab) {
                0 -> SurfacesListView(
                    vm = vm,
                    roomId = roomId,
                    onSurfaceClick = onSurfaceClick,
                    onEditRoom = { showEditRoom = true },
                )
                1 -> LayoutTab(vm = vm, onOpenSurfaces = { tab = 0 })
                2 -> PreviewTab(vm = vm)
                3 -> CutListTab(vm = vm)
            }
        }
    }

    if (showHelp) {
        HelpDiagramDialog(onDismiss = { showHelp = false })
    }

    if (showEditRoom) {
        EditRoomDialog(
            roomId = roomId,
            onDismiss = { showEditRoom = false },
            onSaved = { newName ->
                roomName = newName
                scope.launch { vm.loadSurfaces(roomId) }
                showEditRoom = false
            },
        )
    }
}

/**
 * Edit a room after creation: name, dimensions, and door. Confirming
 * regenerates the wall/floor surfaces automatically whenever the dimensions
 * changed (or the room has no surfaces yet), clearing old tile assignments.
 */
@Composable
private fun EditRoomDialog(
    roomId: String,
    onDismiss: () -> Unit,
    onSaved: (newName: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val roomRepo = remember {
        SqlDelightRoomRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val surfaceRepo = remember {
        SqlDelightSurfaceRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val layoutRepo = remember {
        SqlDelightLayoutResultRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }

    var original by remember { mutableStateOf<Room?>(null) }
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("3000") }
    var depth by remember { mutableStateOf("4000") }
    var height by remember { mutableStateOf("2400") }
    var doorWall by remember { mutableStateOf<Double?>(null) }
    var doorWidth by remember { mutableStateOf("900") }
    var doorHeight by remember { mutableStateOf("2100") }
    var doorOffset by remember { mutableStateOf("") }
    var doorCentered by remember { mutableStateOf(true) }

    LaunchedEffect(roomId) {
        val room = roomRepo.getById(roomId) ?: return@LaunchedEffect
        original = room
        name = room.name
        width = room.width.toInt().toString()
        depth = room.depth.toInt().toString()
        height = room.height.toInt().toString()
        doorWall = room.doorWall
        doorWidth = room.doorWidth.toInt().toString()
        doorHeight = room.doorHeight.toInt().toString()
        doorOffset = room.doorOffset?.toInt()?.toString() ?: ""
        doorCentered = room.doorOffset == null
    }

    val widthValue = width.toDoubleOrNull() ?: 0.0
    val depthValue = depth.toDoubleOrNull() ?: 0.0
    val heightValue = height.toDoubleOrNull() ?: 0.0
    val doorWidthValue = doorWidth.toDoubleOrNull() ?: 0.0
    val doorHeightValue = doorHeight.toDoubleOrNull() ?: 0.0
    val doorOffsetValue = doorOffset.toDoubleOrNull()

    val dimsChanged = original != null &&
        (widthValue != original!!.width || depthValue != original!!.depth || heightValue != original!!.height)

    val wallSpan = if (doorWall == 90.0 || doorWall == 270.0) depthValue else widthValue
    val centeredOffsetString = (((wallSpan - doorWidthValue) / 2).coerceAtLeast(0.0)).toInt().toString()
    // Centered = null offset (saved as null → "(centered)" everywhere).
    val effectiveOffset = if (doorCentered) null else doorOffsetValue
    val valid = name.isNotBlank() && widthValue > 0 && depthValue > 0 && heightValue > 0 &&
        doorWall != null &&
        doorWidthValue >= 400 && doorWidthValue <= wallSpan &&
        doorHeightValue >= 1500 && doorHeightValue <= heightValue &&
        (effectiveOffset == null || (effectiveOffset >= 0 && effectiveOffset <= wallSpan - doorWidthValue))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Room") },
        text = {
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
                    modifier = Modifier.testTag("edit-room-name"),
                )
                DimensionField("Width (mm)", width, { width = it })
                DimensionField("Depth (mm)", depth, { depth = it })
                DimensionField("Height (mm)", height, { height = it })

                Text(
                    "Door",
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

                if (dimsChanged) {
                    Text(
                        "Saving with new dimensions regenerates the wall and floor surfaces and clears their tile assignments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val room = original ?: return@TextButton
                    val updated = room.copy(
                        name = name.trim(),
                        width = widthValue,
                        depth = depthValue,
                        height = heightValue,
                        doorWall = doorWall,
                        doorWidth = doorWidthValue,
                        doorHeight = doorHeightValue,
                        doorOffset = effectiveOffset,
                    )
                    scope.launch {
                        roomRepo.insert(updated)
                        val existing = surfaceRepo.getByRoom(roomId)
                        if (dimsChanged || existing.isEmpty()) {
                            existing.forEach { surface ->
                                surfaceRepo.getSTGsBySurface(surface.id).forEach {
                                    surfaceRepo.deleteSTG(it.id)
                                }
                                layoutRepo.getBySurface(surface.id)?.let {
                                    layoutRepo.delete(it.id)
                                }
                                surfaceRepo.delete(surface.id)
                            }
                            SurfacePositionCalculator.generate(
                                roomId = roomId,
                                roomWidth = widthValue,
                                roomDepth = depthValue,
                                roomHeight = heightValue,
                                includeFront = true,
                                includeBack = true,
                                includeLeft = true,
                                includeRight = true,
                                includeFloor = true,
                            ).forEach { surfaceRepo.insert(it) }
                        }
                        onSaved(updated.name)
                    }
                },
                enabled = valid,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
