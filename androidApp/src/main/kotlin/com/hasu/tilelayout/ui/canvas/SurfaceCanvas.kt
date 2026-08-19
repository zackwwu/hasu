package com.hasu.tilelayout.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.RegionRect

// Grout color mapping
fun GroutColor.toComposeColor(): Color = when (this) {
    GroutColor.BLACK -> Color.Black
    GroutColor.GREY -> Color.Gray
    GroutColor.WHITE -> Color.White
}

// Draw all tiles on a surface
fun DrawScope.drawTiles(
    tiles: List<PlacedTile>,
    groutColor: GroutColor = GroutColor.GREY,
    groutWidth: Double = 3.0,
    scale: Float = 1f,
    doorRect: RegionRect? = null,
) {
    val grout = groutColor.toComposeColor()
    for (tile in tiles) {
        val x = (tile.x * scale).toFloat()
        val y = (tile.y * scale).toFloat()
        val w = (tile.width * scale).toFloat()
        val h = (tile.height * scale).toFloat()

        // Draw grout (slightly larger rect)
        val g = groutWidth.toFloat() * scale
        drawRect(color = grout, topLeft = Offset(x - g, y - g), size = Size(w + 2 * g, h + 2 * g))

        // Draw tile body
        val tileColor = if (tile.isCut) Color(0xFFE8D5B7) else Color(0xFFD4A574) // lighter for cuts
        drawRect(color = tileColor, topLeft = Offset(x, y), size = Size(w, h))

        // Draw cut edge indicators
        for (edge in tile.cutEdges) {
            val lineColor = Color.Red
            when (edge) {
                CutEdge.LEFT -> drawLine(lineColor, Offset(x, y), Offset(x, y + h), strokeWidth = 1.5f)
                CutEdge.RIGHT -> drawLine(lineColor, Offset(x + w, y), Offset(x + w, y + h), strokeWidth = 1.5f)
                CutEdge.TOP -> drawLine(lineColor, Offset(x, y), Offset(x + w, y), strokeWidth = 1.5f)
                CutEdge.BOTTOM -> drawLine(lineColor, Offset(x, y + h), Offset(x + w, y + h), strokeWidth = 1.5f)
            }
        }
    }

    // Dashed door outline drawn after the tiles so the door area reads as explicit
    if (doorRect != null) {
        drawRect(
            color = Color.Gray,
            topLeft = Offset(
                (doorRect.x * scale).toFloat(),
                (doorRect.y * scale).toFloat(),
            ),
            size = Size(
                (doorRect.width * scale).toFloat(),
                (doorRect.height * scale).toFloat(),
            ),
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            ),
        )
    }
}
