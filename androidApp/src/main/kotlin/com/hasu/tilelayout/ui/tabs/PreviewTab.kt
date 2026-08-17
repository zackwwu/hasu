package com.hasu.tilelayout.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.ui.canvas.drawIsometricRoom
import com.hasu.tilelayout.ui.export.ExportDialog
import com.hasu.tilelayout.ui.export.ExportHelper
import com.hasu.tilelayout.ui.export.drawIsometricRoomToCanvas
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.launch

@Composable
fun PreviewTab(vm: RoomEditorViewModel) {
    val surfaces by vm.surfaces.collectAsState()
    val selectedId by vm.selectedSurfaceId.collectAsState()
    val viewAngle by vm.viewAngle.collectAsState()
    val previewZoom by vm.previewZoom.collectAsState()
    val scope = rememberCoroutineScope()
    var topDown by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var gestureStartAngle by remember { mutableStateOf(0.0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 3D Canvas
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(topDown) {
                    if (!topDown) {
                        detectTapGestures { offset ->
                            val id = vm.hitTest(
                                tapX = offset.x.toDouble(),
                                tapY = offset.y.toDouble(),
                                canvasWidth = size.width.toDouble(),
                                canvasHeight = size.height.toDouble()
                            )
                            if (id != null) {
                                scope.launch { vm.selectSurface(id) }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, gestureZoom, gestureRotation ->
                        // zoom and rotation are cumulative per gesture
                        // (starting at 1.0 / 0.0) — capture the base angle
                        // at gesture start so rotation is absolute
                        if (gestureZoom == 1f && gestureRotation == 0f) {
                            gestureStartAngle = vm.viewAngle.value.toDouble()
                        }
                        vm.zoomPreviewBy(gestureZoom.toDouble())
                        vm.setViewAngle(gestureStartAngle + gestureRotation.toDouble())
                    }
                }
        ) {
            drawIsometricRoom(
                surfaces = surfaces,
                viewAngle = if (topDown) 90 else viewAngle,
                selectedSurfaceId = selectedId,
                zoom = previewZoom,
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

            Text(
                "${(previewZoom * 100).toInt()}%",
                fontSize = 12.sp,
                color = if (previewZoom != 1.0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { vm.resetPreviewZoom() }
                    .padding(4.dp),
            )

            OutlinedButton(onClick = { showExport = true }) {
                Text("Export", fontSize = 12.sp)
            }
        }

        if (showExport) {
            ExportDialog(
                title = "3D Preview",
                onDismiss = { showExport = false },
                onRender = { dpi ->
                    val scale = dpi / 72f
                    val wPx = (1200 * scale).toInt()
                    val hPx = (900 * scale).toInt()
                    ExportHelper.renderToBitmap(wPx, hPx) { canvas ->
                        // Draw isometric room using android.graphics.Canvas
                        drawIsometricRoomToCanvas(
                            canvas = canvas,
                            surfaces = surfaces,
                            viewAngle = if (topDown) 90 else viewAngle,
                            selectedSurfaceId = selectedId,
                            widthPx = wPx.toFloat(),
                            heightPx = hPx.toFloat(),
                        )
                    }
                }
            )
        }
    }
}
