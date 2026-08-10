package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightRoomRepository
import com.hasu.tilelayout.models.Room

/** Lists rooms for a project. Reloads whenever [refreshSignal] changes. */
@Composable
fun RoomListView(
    projectId: String,
    refreshSignal: Int,
    onRoomClick: (String) -> Unit,
) {
    val roomRepo = remember {
        SqlDelightRoomRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, refreshSignal) {
        rooms = roomRepo.getByProject(projectId)
        loaded = true
    }

    if (loaded && rooms.isEmpty()) {
        EmptyState(
            title = "No Rooms",
            hint = "Add a room to start designing tile layouts.",
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(rooms, key = { it.id }) { room ->
                ListItem(
                    headlineContent = { Text(room.name) },
                    supportingContent = { Text(formatRoomDimensions(room)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRoomClick(room.id) },
                )
            }
        }
    }
}

private fun formatRoomDimensions(room: Room): String {
    val w = room.width.toInt()
    val d = room.depth.toInt()
    val h = room.height.toInt()
    return "$w x $d x $h mm"
}
