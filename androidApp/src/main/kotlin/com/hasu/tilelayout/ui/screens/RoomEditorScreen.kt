package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightLayoutResultRepository
import com.hasu.tilelayout.db.SqlDelightRoomRepository
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.ui.tabs.CutListTab
import com.hasu.tilelayout.ui.tabs.LayoutTab
import com.hasu.tilelayout.ui.tabs.PreviewTab
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
                )
                1 -> LayoutTab(vm = vm)
                2 -> PreviewTab(vm = vm)
                3 -> CutListTab(vm = vm)
            }
        }
    }

    if (showHelp) {
        HelpDiagramDialog(onDismiss = { showHelp = false })
    }
}
