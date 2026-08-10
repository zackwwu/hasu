package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightProjectRepository
import com.hasu.tilelayout.db.SqlDelightRoomRepository
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
                FloatingActionButton(onClick = { showAddRoom = true }) {
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
                )
            }
        }
    }

    if (showAddRoom) {
        AddRoomDialog(
            onDismiss = { showAddRoom = false },
            onConfirm = { name, width, depth, height ->
                scope.launch {
                    roomRepo.insert(
                        Room(
                            projectId = projectId,
                            name = name,
                            width = width,
                            depth = depth,
                            height = height,
                        )
                    )
                    refreshRooms++
                    showAddRoom = false
                }
            },
        )
    }
}

@Composable
private fun AddRoomDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, width: Double, depth: Double, height: Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("3000") }
    var depth by remember { mutableStateOf("4000") }
    var height by remember { mutableStateOf("2400") }

    val widthValue = width.toDoubleOrNull() ?: 0.0
    val depthValue = depth.toDoubleOrNull() ?: 0.0
    val heightValue = height.toDoubleOrNull() ?: 0.0
    val valid = name.isNotBlank() && widthValue > 0 && depthValue > 0 && heightValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Room name") },
                    singleLine = true,
                )
                DimensionField("Width (mm)", width, { width = it })
                DimensionField("Depth (mm)", depth, { depth = it })
                DimensionField("Height (mm)", height, { height = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), widthValue, depthValue, heightValue) },
                enabled = valid,
            ) {
                Text("Add")
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
