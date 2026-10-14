package com.hasu.tilelayout.ui.preview3d

import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.hasu.tilelayout.scene.OrbitCamera
import com.hasu.tilelayout.scene.RoomScene

/** Lets UI outside the 3D view (e.g. export) reach the live renderer. */
class RoomRendererHandle {
    var renderer: FilamentRoomRenderer? = null
        internal set
}

/** Filament-backed 3D view of [scene]; input is handled separately by [roomViewGestures]. */
@Composable
fun RoomView3D(
    scene: RoomScene,
    camera: OrbitCamera,
    hiddenSurfaceIds: Set<String>,
    selectedSurfaceId: String?,
    handle: RoomRendererHandle,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).also { view ->
                val renderer = FilamentRoomRenderer(view)
                view.tag = renderer
                handle.renderer = renderer
            }
        },
        update = { view ->
            (view.tag as FilamentRoomRenderer).apply {
                setScene(scene)
                setCamera(camera)
                setHiddenSurfaces(hiddenSurfaceIds)
                setSelectedSurface(selectedSurfaceId)
            }
        },
        onRelease = { view ->
            (view.tag as FilamentRoomRenderer).destroy()
            handle.renderer = null
        },
        modifier = modifier,
    )
}

/**
 * Touch and mouse input for the 3D view: one-finger drag orbits, pinch
 * zooms (two-finger drag also orbits), mouse wheel / trackpad scroll zooms,
 * and a tap reports its position for picking.
 */
fun Modifier.roomViewGestures(
    onOrbit: (deltaYawDegrees: Double, deltaPitchDegrees: Double) -> Unit,
    onZoom: (factor: Double) -> Unit,
    onTap: (position: Offset, viewportSize: IntSize) -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures { position -> onTap(position, size) }
    }
    .pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            if (pan != Offset.Zero) {
                val k = OrbitCamera.DEGREES_PER_POINT / density
                onOrbit(-pan.x * k, pan.y * k)
            }
            if (zoom != 1f) onZoom(zoom.toDouble())
        }
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                // Compose reports one unit per wheel notch, positive when rolled towards the user
                val scroll = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                if (scroll != 0.0) {
                    onZoom(OrbitCamera.zoomFactorForScroll(scroll))
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
