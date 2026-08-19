package com.hasu.tilelayout.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.hasu.tilelayout.engine.IsometricProjection
import com.hasu.tilelayout.engine.WorldPoint
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType

// Base face colors (shaded per face orientation for a 3D look)
private const val WALL_BASE_R = 0xE8
private const val WALL_BASE_G = 0xE0
private const val WALL_BASE_B = 0xD8
private const val FLOOR_BASE_R = 0xD4
private const val FLOOR_BASE_G = 0xC8
private const val FLOOR_BASE_B = 0xB8

private fun shaded(baseR: Int, baseG: Int, baseB: Int, factor: Float): Color = Color(
    red = baseR / 255f * factor,
    green = baseG / 255f * factor,
    blue = baseB / 255f * factor,
    alpha = 1f,
)

fun DrawScope.drawIsometricRoom(
    surfaces: List<Surface>,
    viewAngle: Int,
    selectedSurfaceId: String?,
    zoom: Double = 1.0,
    textMeasurer: TextMeasurer? = null,
    doorWorldRects: Map<String, List<WorldPoint>> = emptyMap(),
) {
    val fit = IsometricProjection.fitViewport(
        surfaces, viewAngle, size.width.toDouble(), size.height.toDouble(),
    )
    val scale = fit.scale * zoom
    val ordered = IsometricProjection.orderSurfaces(surfaces, viewAngle)

    // Drop shadow: the floor silhouette offset down-right, drawn first
    val floor = surfaces.firstOrNull { it.type == SurfaceType.FLOOR }
    if (floor != null) {
        val floorCorners = IsometricProjection.projectSurfaceCorners(
            floor, viewAngle, fit.originX, fit.originY, scale,
        )
        val shadowOffset = (10f * zoom).toFloat().coerceAtLeast(4f)
        val shadowPath = Path().apply {
            moveTo(floorCorners[0].x.toFloat() + shadowOffset, floorCorners[0].y.toFloat() + shadowOffset * 1.4f)
            for (i in 1 until floorCorners.size) {
                lineTo(floorCorners[i].x.toFloat() + shadowOffset, floorCorners[i].y.toFloat() + shadowOffset * 1.4f)
            }
            close()
        }
        drawPath(shadowPath, Color.Black.copy(alpha = 0.15f), style = Fill)
    }

    for (surface in ordered) {
        val corners = IsometricProjection.projectSurfaceCorners(
            surface, viewAngle, fit.originX, fit.originY, scale
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
            isSelected -> Color(0x5500BCD4)  // cyan highlight overlay
            isWall -> {
                val light = IsometricProjection.faceLightFactor(surface, viewAngle).toFloat()
                shaded(WALL_BASE_R, WALL_BASE_G, WALL_BASE_B, light)
            }
            else -> {
                val light = IsometricProjection.faceLightFactor(surface, viewAngle).toFloat()
                shaded(FLOOR_BASE_R, FLOOR_BASE_G, FLOOR_BASE_B, 0.9f + 0.1f * light)
            }
        }
        drawPath(path, fillColor, style = Fill)
        drawPath(
            path,
            if (isSelected) Color(0xFF0091EA) else Color(0xFF9E9386),
            style = Stroke(width = if (isSelected) 2.5f else 1.2f),
        )

        // Door opening: dark cutout drawn after the wall fill, before the label
        doorWorldRects[surface.id]?.let { doorCorners ->
            val doorPath = Path().apply {
                doorCorners.forEachIndexed { i, corner ->
                    val sp = IsometricProjection.project(
                        corner.x * scale, corner.y * scale, corner.z * scale,
                        viewAngle, fit.originX, fit.originY,
                    )
                    if (i == 0) moveTo(sp.x.toFloat(), sp.y.toFloat())
                    else lineTo(sp.x.toFloat(), sp.y.toFloat())
                }
                close()
            }
            drawPath(doorPath, Color(0xFF3A3A3A), style = Fill)
            drawPath(doorPath, Color(0xFF6E6658), style = Stroke(width = 1.2f))
        }

        // Surface name label at the polygon centroid
        textMeasurer?.let { measurer ->
            val cx = corners.sumOf { it.x } / corners.size
            val cy = corners.sumOf { it.y } / corners.size
            val label = surface.displayName()
            val layout = measurer.measure(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = Color(0xFF4A4238),
                ),
            )
            drawText(
                textMeasurer = measurer,
                text = label,
                topLeft = Offset(
                    (cx - layout.size.width / 2f).toFloat(),
                    (cy - layout.size.height / 2f).toFloat(),
                ),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = Color(0xFF4A4238),
                ),
            )
        }
    }
}
