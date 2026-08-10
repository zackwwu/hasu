package com.hasu.tilelayout.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.ui.canvas.drawIsometricRoom
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.launch

@Composable
fun PreviewTab(vm: RoomEditorViewModel) {
    val surfaces by vm.surfaces.collectAsState()
    val selectedId by vm.selectedSurfaceId.collectAsState()
    val viewAngle by vm.viewAngle.collectAsState()
    val scope = rememberCoroutineScope()
    var topDown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 3D Canvas
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val tap = event.changes.firstOrNull() ?: continue
                            if (tap.pressed && !topDown) {
                                val id = vm.hitTest(
                                    tapX = tap.position.x.toDouble(),
                                    tapY = tap.position.y.toDouble(),
                                    canvasWidth = size.width.toDouble(),
                                    canvasHeight = size.height.toDouble()
                                )
                                if (id != null) {
                                    scope.launch { vm.selectSurface(id) }
                                }
                                tap.consume()
                            }
                        }
                    }
                }
        ) {
            drawIsometricRoom(
                surfaces = surfaces,
                viewAngle = if (topDown) 90 else viewAngle,
                selectedSurfaceId = selectedId
            )
        }

        // Surface info chip
        selectedId?.let { sid ->
            surfaces.find { it.id == sid }?.let { surface ->
                val typeName = if (surface.type == SurfaceType.WALL) "Wall" else "Floor"
                Text(
                    "$typeName ${surface.width.toInt()}×${surface.height.toInt()}mm — Angle: ${surface.position.rotation.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Rotation controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { vm.rotateView(-90) }) { Text("◀", fontSize = 18.sp) }
            Text("${viewAngle}°", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { vm.rotateView(90) }) { Text("▶", fontSize = 18.sp) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Top-Down", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                Switch(checked = topDown, onCheckedChange = { topDown = it })
            }
        }
    }
}
