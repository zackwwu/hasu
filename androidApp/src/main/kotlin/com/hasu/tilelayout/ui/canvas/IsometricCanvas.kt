package com.hasu.tilelayout.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hasu.tilelayout.engine.IsometricProjection
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType

fun DrawScope.drawIsometricRoom(
    surfaces: List<Surface>,
    viewAngle: Int,
    selectedSurfaceId: String?,
) {
    val originX = size.width / 2
    val originY = size.height * 0.6f
    val ordered = IsometricProjection.orderSurfaces(surfaces, viewAngle)

    for (surface in ordered) {
        val corners = IsometricProjection.projectSurfaceCorners(
            surface, viewAngle, originX.toDouble(), originY.toDouble()
        )
        val path = Path().apply {
            moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
            for (i in 1 until corners.size) {
                lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
            }
            close()
        }

        val isSelected = surface.id == selectedSurfaceId
        val isWall = surface.type == SurfaceType.WALL

        val fillColor = when {
            isSelected -> Color(0x4400BCD4)  // cyan highlight
            isWall -> Color(0xFFE8E0D8)       // warm beige
            else -> Color(0xFFD4C8B8)         // darker beige for floor
        }
        drawPath(path, fillColor, style = Fill)
        drawPath(path, Color.Gray, style = Stroke(width = 1.5f))
    }
}
