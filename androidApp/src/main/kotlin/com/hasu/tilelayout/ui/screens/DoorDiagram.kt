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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.engine.DoorPickerGeometry
import kotlin.math.max
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
    doorWidth: Double = 900.0,
    doorOffset: Double? = null,
    centerCaption: String? = null,
    doorCaption: String? = null,
    onAnyTap: (() -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("door-diagram")
                .pointerInput(roomWidth, roomDepth, onAnyTap != null) {
                    detectTapGestures { tap ->
                        if (onAnyTap != null) {
                            onAnyTap()
                        } else {
                            val isSideDoor = selectedWall == 90.0 || selectedWall == 270.0
                            val sideInset = if (isSideDoor) 130f else 60f
                            val topInset = 22f
                            val bottomInset = 46f
                            val availW = max(size.width - sideInset * 2f, 40f)
                            val availH = max(size.height - topInset - bottomInset, 40f)
                            DoorPickerGeometry.wallAtTap(
                                x = tap.x.toDouble(),
                                y = (tap.y - topInset).toDouble(),
                                diagramW = size.width.toDouble(),
                                diagramH = availH.toDouble(),
                                roomWidth = roomWidth,
                                roomDepth = roomDepth,
                            )?.let(onWallSelected)
                        }
                    }
                },
        ) {
            // Reserve space OUTSIDE the room box for the wall labels — and the
            // door caption stacked under the door wall's label — so they never
            // clip at the canvas edges or sit inside the room.
            val isSideDoor = selectedWall == 90.0 || selectedWall == 270.0
            val sideInset = if (isSideDoor) 130f else 60f
            val topInset = 22f
            val bottomInset = 46f
            val availW = max(size.width - sideInset * 2f, 40f)
            val availH = max(size.height - topInset - bottomInset, 40f)
            val scale = min(availW / roomWidth.toFloat(), availH / roomDepth.toFloat())
            val rectW = (roomWidth * scale).toFloat()
            val rectH = (roomDepth * scale).toFloat()
            val left = (size.width - rectW) / 2f
            val top = topInset + (availH - rectH) / 2f

            drawRect(
                DiagramEdgeColor,
                topLeft = Offset(left, top),
                size = Size(rectW, rectH),
                style = Stroke(width = 2f),
            )

            // Centered annotation inside the room (e.g. dimensions).
            centerCaption?.let { caption ->
                val style = TextStyle(color = DiagramEdgeColor, fontSize = 10.sp)
                val measured = textMeasurer.measure(caption, style)
                drawText(
                    textMeasurer = textMeasurer,
                    text = caption,
                    style = style,
                    topLeft = Offset(
                        left + (rectW - measured.size.width) / 2f,
                        top + (rectH - measured.size.height) / 2f,
                    ),
                )
            }


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

            // Wall names are door-relative, so they only exist once a door wall has
            // been selected — and they follow the door (mirrors Surface.displayName()).
            // Labels are anchored to the ROOM RECTANGLE and drawn OUTSIDE the box;
            // the door wall's label stacks its caption underneath it.
            if (selectedWall != null) {
                val labelStyle = TextStyle(color = DiagramEdgeColor, fontSize = 10.sp)
                val captionStyle = TextStyle(color = DiagramAccent, fontSize = 10.sp)

                // Top edge (180): name above the box; door caption above the name.
                val topName = doorRelativeName(180.0, selectedWall)
                val topNameM = textMeasurer.measure(topName, labelStyle)
                drawText(textMeasurer = textMeasurer, text = topName, style = labelStyle, topLeft = Offset(
                    left + (rectW - topNameM.size.width) / 2f,
                    top - topNameM.size.height - 4f,
                ))
                if (selectedWall == 180.0 && !doorCaption.isNullOrBlank()) {
                    val cm = textMeasurer.measure(doorCaption, captionStyle)
                    drawText(textMeasurer = textMeasurer, text = doorCaption, style = captionStyle, topLeft = Offset(
                        left + (rectW - cm.size.width) / 2f,
                        top - topNameM.size.height - 6f - cm.size.height,
                    ))
                }

                // Bottom edge (0): name below the box; door caption under the name.
                val bottomName = doorRelativeName(0.0, selectedWall)
                val bottomNameM = textMeasurer.measure(bottomName, labelStyle)
                drawText(textMeasurer = textMeasurer, text = bottomName, style = labelStyle, topLeft = Offset(
                    left + (rectW - bottomNameM.size.width) / 2f,
                    top + rectH + 4f,
                ))
                if (selectedWall == 0.0 && !doorCaption.isNullOrBlank()) {
                    val cm = textMeasurer.measure(doorCaption, captionStyle)
                    drawText(textMeasurer = textMeasurer, text = doorCaption, style = captionStyle, topLeft = Offset(
                        left + (rectW - cm.size.width) / 2f,
                        top + rectH + 6f + bottomNameM.size.height,
                    ))
                }

                // Left edge (90): name left of the box; door caption under the name.
                val leftName = doorRelativeName(90.0, selectedWall)
                val leftNameM = textMeasurer.measure(leftName, labelStyle)
                drawText(textMeasurer = textMeasurer, text = leftName, style = labelStyle, topLeft = Offset(
                    left - leftNameM.size.width - 4f,
                    top + (rectH - leftNameM.size.height) / 2f,
                ))
                if (selectedWall == 90.0 && !doorCaption.isNullOrBlank()) {
                    val cm = textMeasurer.measure(doorCaption, captionStyle)
                    drawText(textMeasurer = textMeasurer, text = doorCaption, style = captionStyle, topLeft = Offset(
                        left - cm.size.width - 4f,
                        top + (rectH - leftNameM.size.height) / 2f + leftNameM.size.height + 2f,
                    ))
                }

                // Right edge (270): name right of the box; door caption under the name.
                val rightName = doorRelativeName(270.0, selectedWall)
                val rightNameM = textMeasurer.measure(rightName, labelStyle)
                drawText(textMeasurer = textMeasurer, text = rightName, style = labelStyle, topLeft = Offset(
                    left + rectW + 4f,
                    top + (rectH - rightNameM.size.height) / 2f,
                ))
                if (selectedWall == 270.0 && !doorCaption.isNullOrBlank()) {
                    val cm = textMeasurer.measure(doorCaption, captionStyle)
                    drawText(textMeasurer = textMeasurer, text = doorCaption, style = captionStyle, topLeft = Offset(
                        left + rectW + 4f,
                        top + (rectH - rightNameM.size.height) / 2f + rightNameM.size.height + 2f,
                    ))
                }
            }
        }

    }
}

/** Door-relative name for a diagram edge: 0 → Door Wall, 90 → Left Wall, 180 → Front Wall, 270 → Right Wall. */
private fun doorRelativeName(edge: Double, door: Double?): String {
    if (door == null) return ""
    val delta = ((edge.toInt() - door.toInt()) % 360 + 360) % 360
    return when (delta) {
        0 -> "Door Wall"
        90 -> "Left Wall"
        180 -> "Front Wall"
        270 -> "Right Wall"
        else -> ""
    }
}
