package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.engine.DoorPickerGeometry
import kotlin.math.min

private val DiagramEdgeColor = Color(0xFF9E9386)
private val DiagramAccent = Color(0xFF0091EA)
private val DoorCutout = Color(0xFF3A3A3A)

/**
 * Mini top-down room diagram for the door wall picker. The room rectangle is
 * drawn centered and aspect-preserved; taps hit-test through the shared
 * [DoorPickerGeometry.wallAtTap] (inside the rectangle, near an edge).
 */
@Composable
fun DoorDiagram(
    roomWidth: Double,
    roomDepth: Double,
    selectedWall: Double?,
    onWallSelected: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("door-diagram")
                .pointerInput(roomWidth, roomDepth) {
                    detectTapGestures { tap ->
                        DoorPickerGeometry.wallAtTap(
                            x = tap.x.toDouble(),
                            y = tap.y.toDouble(),
                            diagramW = size.width.toDouble(),
                            diagramH = size.height.toDouble(),
                            roomWidth = roomWidth,
                            roomDepth = roomDepth,
                        )?.let(onWallSelected)
                    }
                },
        ) {
            val scale = min(size.width / roomWidth, size.height / roomDepth)
            val rectW = (roomWidth * scale).toFloat()
            val rectH = (roomDepth * scale).toFloat()
            val left = (size.width - rectW) / 2f
            val top = (size.height - rectH) / 2f

            drawRect(
                DiagramEdgeColor,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                style = Stroke(width = 2f),
            )

            val notchW = rectW * 0.2f
            val notchH = rectH * 0.2f
            when (selectedWall) {
                // Front wall (z=0) = bottom edge
                0.0 -> {
                    drawLine(
                        DiagramAccent,
                        Offset(left, top + rectH),
                        Offset(left + rectW, top + rectH),
                        strokeWidth = 3f,
                    )
                    drawRect(
                        DoorCutout,
                        topLeft = Offset(left + (rectW - notchW) / 2f, top + rectH - 8f),
                        size = Size(notchW, 8f),
                    )
                }
                // Left wall (x=0) = left edge
                90.0 -> {
                    drawLine(
                        DiagramAccent,
                        Offset(left, top),
                        Offset(left, top + rectH),
                        strokeWidth = 3f,
                    )
                    drawRect(
                        DoorCutout,
                        topLeft = Offset(left, top + (rectH - notchH) / 2f),
                        size = Size(8f, notchH),
                    )
                }
                // Back wall (z=depth) = top edge
                180.0 -> {
                    drawLine(
                        DiagramAccent,
                        Offset(left, top),
                        Offset(left + rectW, top),
                        strokeWidth = 3f,
                    )
                    drawRect(
                        DoorCutout,
                        topLeft = Offset(left + (rectW - notchW) / 2f, top),
                        size = Size(notchW, 8f),
                    )
                }
                // Right wall (x=width) = right edge
                270.0 -> {
                    drawLine(
                        DiagramAccent,
                        Offset(left + rectW, top),
                        Offset(left + rectW, top + rectH),
                        strokeWidth = 3f,
                    )
                    drawRect(
                        DoorCutout,
                        topLeft = Offset(left + rectW - 8f, top + (rectH - notchH) / 2f),
                        size = Size(8f, notchH),
                    )
                }
            }
        }

        Text(
            "Back",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
            fontSize = 10.sp,
            color = DiagramEdgeColor,
        )
        Text(
            "Front",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            fontSize = 10.sp,
            color = DiagramEdgeColor,
        )
        Text(
            "Left",
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
            fontSize = 10.sp,
            color = DiagramEdgeColor,
        )
        Text(
            "Right",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            fontSize = 10.sp,
            color = DiagramEdgeColor,
        )
    }
}
