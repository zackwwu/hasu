package com.hasu.tilelayout.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.ui.export.ExportDialog
import com.hasu.tilelayout.ui.preview3d.PreviewIcons
import com.hasu.tilelayout.ui.preview3d.RoomRendererHandle
import com.hasu.tilelayout.ui.preview3d.RoomView3D
import com.hasu.tilelayout.ui.preview3d.roomViewGestures
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun PreviewTab(vm: RoomEditorViewModel) {
    val surfaces by vm.surfaces.collectAsState()
    val selectedId by vm.selectedSurfaceId.collectAsState()
    val scene by vm.previewScene.collectAsState()
    val camera by vm.previewCamera.collectAsState()
    val hiddenIds by vm.hiddenSurfaceIds.collectAsState()
    val scope = rememberCoroutineScope()
    val rendererHandle = remember { RoomRendererHandle() }
    var showHint by remember { mutableStateOf(true) }
    var showExport by remember { mutableStateOf(false) }

    val walls = surfaces.filter { it.type == SurfaceType.WALL }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RoomView3D(
                scene = scene,
                camera = camera,
                hiddenSurfaceIds = hiddenIds,
                selectedSurfaceId = selectedId,
                handle = rendererHandle,
                modifier = Modifier.fillMaxSize(),
            )
            // Gestures live on a Compose overlay because the SurfaceView doesn't
            // participate in Compose pointer input.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("preview-3d")
                    .roomViewGestures(
                        onOrbit = { dYaw, dPitch ->
                            showHint = false
                            vm.orbitPreview(dYaw, dPitch)
                        },
                        onZoom = { factor ->
                            showHint = false
                            vm.zoomPreviewBy(factor)
                        },
                        onTap = { position, viewport ->
                            val id = vm.pickSurface(
                                x = position.x.toDouble(),
                                y = position.y.toDouble(),
                                viewportWidth = viewport.width.toDouble(),
                                viewportHeight = viewport.height.toDouble(),
                            )
                            if (id != null) scope.launch { vm.selectSurface(id) }
                        },
                    )
            )

            if (showHint && surfaces.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) {
                    Text(
                        "Drag to rotate · Pinch to zoom · Tap a wall to select",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { vm.orbitPreview(0.0, 90.0) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("Top", fontSize = 12.sp) }
                FilledTonalButton(
                    onClick = { vm.resetPreviewCamera() },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("Reset view", fontSize = 12.sp) }
            }
        }

        val selected = surfaces.find { it.id == selectedId }
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${selected.displayName()} · ${selected.width.toInt()}×${selected.height.toInt()} mm",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                if (selected.type == SurfaceType.WALL) {
                    val hidden = selected.id in hiddenIds
                    TextButton(onClick = { vm.toggleSurfaceHidden(selected.id) }) {
                        Icon(
                            if (hidden) PreviewIcons.Visible else PreviewIcons.Hidden,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (hidden) "Show wall" else "Hide wall")
                    }
                }
            }
        }

        if (walls.isNotEmpty()) {
            Text(
                "Walls",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(walls, key = { it.id }) { wall ->
                    val visible = wall.id !in hiddenIds
                    FilterChip(
                        selected = visible,
                        onClick = { vm.toggleSurfaceHidden(wall.id) },
                        label = { Text(wall.displayName()) },
                        leadingIcon = {
                            Icon(
                                if (visible) PreviewIcons.Visible else PreviewIcons.Hidden,
                                contentDescription = if (visible) "Visible" else "Hidden",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.testTag("wall-chip-${wall.id}"),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hiddenIds.isNotEmpty()) {
                TextButton(onClick = { vm.showAllSurfaces() }) { Text("Show all walls") }
            }
            OutlinedButton(onClick = { showExport = true }) { Text("Export", fontSize = 12.sp) }
        }

        if (showExport) {
            ExportDialog(
                title = "3D Preview",
                onDismiss = { showExport = false },
                onRender = { dpi ->
                    val scale = dpi / 72f
                    var w = (1200 * scale).toInt()
                    var h = (900 * scale).toInt()
                    val longest = max(w, h)
                    if (longest > MAX_EXPORT_PX) {
                        w = w * MAX_EXPORT_PX / longest
                        h = h * MAX_EXPORT_PX / longest
                    }
                    rendererHandle.renderer?.capture(w, h)
                },
            )
        }
    }
}

private const val MAX_EXPORT_PX = 4096
