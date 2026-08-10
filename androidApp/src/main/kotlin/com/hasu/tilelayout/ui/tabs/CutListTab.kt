package com.hasu.tilelayout.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.cutlist.CutListGenerator
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightLayoutResultRepository
import com.hasu.tilelayout.db.SqlDelightSurfaceRepository
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.models.CutEntry
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.SurfaceType

/**
 * Cut list tab: loads all surfaces for the room, gathers their layout results,
 * and groups cut tiles via [CutListGenerator].
 */
@Composable
fun CutListTab(projectId: String, roomId: String) {
    val db = AppDatabase.instance
    val surfaceRepo = remember { SqlDelightSurfaceRepository(db.tileLayoutDbQueries) }
    val layoutRepo = remember { SqlDelightLayoutResultRepository(db.tileLayoutDbQueries) }
    val tileGroupRepo = remember { SqlDelightTileGroupRepository(db.tileLayoutDbQueries) }

    var cutEntries by remember { mutableStateOf<List<CutEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(roomId) {
        val surfaces = surfaceRepo.getByRoom(roomId)
        val resultsBySurface = mutableMapOf<String, LayoutResult>()
        val surfaceNames = mutableMapOf<String, String>()
        val tileGroupNames = mutableMapOf<String, String>()

        for (surface in surfaces) {
            surfaceNames[surface.id] =
                "${if (surface.type == SurfaceType.WALL) "Wall" else "Floor"} ${surface.width.toInt()}×${surface.height.toInt()}"
            val result = layoutRepo.getBySurface(surface.id)
            if (result != null) {
                resultsBySurface[surface.id] = result
                for (tile in result.tiles) {
                    if (!tileGroupNames.containsKey(tile.tileGroupId)) {
                        tileGroupNames[tile.tileGroupId] =
                            tileGroupRepo.getById(tile.tileGroupId)?.name ?: tile.tileGroupId
                    }
                }
            }
        }

        cutEntries = CutListGenerator.generate(resultsBySurface, surfaceNames, tileGroupNames)
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (cutEntries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cuts needed — all tiles fit perfectly.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            items(cutEntries) { entry ->
                CutEntryCard(entry)
            }
        }
    }
}

@Composable
fun CutEntryCard(entry: CutEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(entry.tileGroupName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${entry.width.toInt()}×${entry.height.toInt()}mm — ${entry.cutTypeDescription}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Total: ${entry.totalCount} tiles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                entry.locations.forEach { loc ->
                    Text(
                        "${loc.surfaceName}: ${loc.count} tiles",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Less" else "More")
            }
        }
    }
}
