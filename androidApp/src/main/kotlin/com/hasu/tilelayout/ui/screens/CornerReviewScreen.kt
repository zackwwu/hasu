package com.hasu.tilelayout.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hasu.tilelayout.engine.ScanGuideGeometry
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manual corner adjustment before perspective correction.
 *
 * Coordinate model: corners live in BITMAP pixel coordinates (that's what
 * [PerspectiveCorrector] consumes). The bitmap is drawn letterboxed into the
 * available box: view = bitmap * scale + offset, with scale chosen to fit and
 * offset centering it. Handles are positioned via that mapping and drag deltas
 * are divided by [scale] to land back in bitmap pixels, so what you see is
 * exactly what gets warped.
 */
@Composable
fun CornerReviewScreen(
    bitmap: Bitmap?,
    initialCorners: QuadCorners?,
    tileWidth: Double,
    tileHeight: Double,
    onAccept: (Bitmap) -> Unit,
    onRetry: () -> Unit,
) {
    val bmp = bitmap ?: return

    // No detection — seed from the guide frame over the bitmap's own extent, so the
    // user has something to drag instead of a dead end.
    var corners by remember(bmp, initialCorners) {
        mutableStateOf(
            initialCorners ?: ScanGuideGeometry
                .frame(
                    viewportWidth = bmp.width.toDouble(),
                    viewportHeight = bmp.height.toDouble(),
                    tileWidth = tileWidth,
                    tileHeight = tileHeight,
                    insetFraction = 0.85,
                )
                .let { g ->
                    QuadCorners(
                        tl = PointF(g.x.toFloat(), g.y.toFloat()),
                        tr = PointF((g.x + g.width).toFloat(), g.y.toFloat()),
                        br = PointF((g.x + g.width).toFloat(), (g.y + g.height).toFloat()),
                        bl = PointF(g.x.toFloat(), (g.y + g.height).toFloat()),
                    )
                }
        )
    }

    val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

    // Letterbox mapping between the review area and bitmap pixels.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val mapping = remember(bmp, viewSize) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            val scale = min(
                viewSize.width / bmp.width.toFloat(),
                viewSize.height / bmp.height.toFloat(),
            )
            BitmapViewMapping(
                scale = scale,
                offset = Offset(
                    (viewSize.width - bmp.width * scale) / 2f,
                    (viewSize.height - bmp.height * scale) / 2f,
                ),
            )
        } else {
            BitmapViewMapping(scale = 1f, offset = Offset.Zero)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Adjust Corners",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            "Drag corners to align with tile edges",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewSize = it },
        ) {
            // Draw bitmap + quad outline (view coordinates via the letterbox mapping).
            Canvas(modifier = Modifier.fillMaxSize()) {
                val m = mapping
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(m.offset.x.roundToInt(), m.offset.y.roundToInt()),
                    dstSize = IntSize(
                        (bmp.width * m.scale).roundToInt(),
                        (bmp.height * m.scale).roundToInt(),
                    ),
                )
                fun view(p: PointF) = Offset(m.offset.x + p.x * m.scale, m.offset.y + p.y * m.scale)
                val points = listOf(corners.tl, corners.tr, corners.br, corners.bl).map { view(it) }
                val accent = Color(0xFF4CAF50)
                for (i in 0..3) {
                    drawLine(accent, points[i], points[(i + 1) % 4], strokeWidth = 2.dp.toPx())
                }
                points.forEach {
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = it)
                    drawCircle(accent, radius = 3.dp.toPx(), center = it)
                }
            }
            // 4 draggable handles (transparent touch targets on top of the drawn dots).
            CornerHandle(
                pos = mapping.offset + Offset(corners.tl.x * mapping.scale, corners.tl.y * mapping.scale),
                onDrag = { dx, dy -> corners = corners.copy(tl = clampCorner(corners.tl, dx, dy, mapping, bmp)) },
            )
            CornerHandle(
                pos = mapping.offset + Offset(corners.tr.x * mapping.scale, corners.tr.y * mapping.scale),
                onDrag = { dx, dy -> corners = corners.copy(tr = clampCorner(corners.tr, dx, dy, mapping, bmp)) },
            )
            CornerHandle(
                pos = mapping.offset + Offset(corners.br.x * mapping.scale, corners.br.y * mapping.scale),
                onDrag = { dx, dy -> corners = corners.copy(br = clampCorner(corners.br, dx, dy, mapping, bmp)) },
            )
            CornerHandle(
                pos = mapping.offset + Offset(corners.bl.x * mapping.scale, corners.bl.y * mapping.scale),
                onDrag = { dx, dy -> corners = corners.copy(bl = clampCorner(corners.bl, dx, dy, mapping, bmp)) },
            )
        }

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onRetry) { Text("Re-scan") }
            Button(onClick = {
                val outputSize = PerspectiveCorrector.outputSize(tileWidth, tileHeight, maxDimension = 512)
                val corrected = PerspectiveCorrector.correct(
                    source = bmp,
                    corners = corners,
                    tileWidth = tileWidth,
                    tileHeight = tileHeight,
                    outputSize = outputSize,
                )
                onAccept(corrected)
            }) { Text("Accept") }
        }
    }
}

/** View mapping for the letterboxed bitmap: view = bitmap * scale + offset. */
private data class BitmapViewMapping(val scale: Float, val offset: Offset)

/** Transparent 48dp touch target centered on [pos]; drags move the corner in bitmap pixels. */
@Composable
private fun CornerHandle(pos: Offset, onDrag: (dx: Float, dy: Float) -> Unit) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pos.x - 24.dp.toPx()).roundToInt(),
                    (pos.y - 24.dp.toPx()).roundToInt(),
                )
            }
            .size(48.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    )
}

/** Convert a view-space drag delta back to bitmap pixels and clamp inside the bitmap. */
private fun clampCorner(
    p: PointF,
    dx: Float,
    dy: Float,
    mapping: BitmapViewMapping,
    bmp: Bitmap,
): PointF = PointF(
    (p.x + dx / mapping.scale).coerceIn(0f, bmp.width.toFloat()),
    (p.y + dy / mapping.scale).coerceIn(0f, bmp.height.toFloat()),
)
