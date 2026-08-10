package com.hasu.tilelayout.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.ui.canvas.drawTiles
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.launch

@Composable
fun LayoutTab(vm: RoomEditorViewModel) {
    val surfaces by vm.surfaces.collectAsState()
    val selectedId by vm.selectedSurfaceId.collectAsState()
    val lockedIds by vm.lockedSurfaceIds.collectAsState()
    val currentTiles by vm.currentTiles.collectAsState()
    val undoBuffer by vm.undoBuffer.collectAsState()
    val scope = rememberCoroutineScope()

    // Get selected surface for grout info
    val selectedSurface = surfaces.find { it.id == selectedId }
    val groutColor = selectedSurface?.groutColor ?: com.hasu.tilelayout.models.GroutColor.GREY
    val groutWidth = selectedSurface?.groutWidth ?: 3.0

    var dragAccumulator by remember { mutableStateOf(Offset.Zero) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Surface selector chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(surfaces) { surface ->
                FilterChip(
                    selected = surface.id == selectedId,
                    onClick = { scope.launch { vm.selectSurface(surface.id) } },
                    label = {
                        val typeName = if (surface.type == SurfaceType.WALL) "Wall" else "Floor"
                        Text("$typeName ${surface.width.toInt()}×${surface.height.toInt()}", fontSize = 11.sp)
                    }
                )
            }
        }

        // 2D Canvas
        if (selectedId == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Select a surface to view its tile layout.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val scale = 0.25f // mm → px scale
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                scope.launch { vm.onDragStart() }
                            },
                            onDragEnd = {
                                val dx = (dragAccumulator.x / scale).toDouble()
                                val dy = (dragAccumulator.y / scale).toDouble()
                                scope.launch { vm.onDragEnd(dx, dy) }
                                dragAccumulator = Offset.Zero
                            },
                            onDragCancel = {
                                dragAccumulator = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount
                            }
                        )
                    }
            ) {
                drawTiles(currentTiles, groutColor, groutWidth, scale)
            }
        }

        // Lock propagation chips
        val otherSurfaces = surfaces.filter { it.id != selectedId }
        if (otherSurfaces.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                otherSurfaces.forEach { surface ->
                    FilterChip(
                        selected = surface.id in lockedIds,
                        onClick = { vm.toggleLock(surface.id) },
                        label = {
                            val typeName = if (surface.type == SurfaceType.WALL) "Wall" else "Floor"
                            Text("$typeName ${surface.width.toInt()}×${surface.height.toInt()}", fontSize = 10.sp)
                        }
                    )
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                selectedId?.let { scope.launch { vm.resetToAuto(it) } }
            }) { Text("Reset", fontSize = 12.sp) }

            OutlinedButton(onClick = {
                selectedId?.let { scope.launch { vm.snapToCenter(it) } }
            }) { Text("Snap Center", fontSize = 12.sp) }

            OutlinedButton(onClick = {
                scope.launch { vm.undo() }
            }, enabled = undoBuffer != null) { Text("Undo", fontSize = 12.sp) }
        }
    }
}
