package com.hasu.tilelayout.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Explains the tile layout concepts: surface region, tile grid,
 * offset, and patterns.
 */
@Composable
fun HelpDiagramDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tile Layout Concepts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Diagram: outer surface, inner region, tile grid, offset arrow.
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) {
                    val surfaceRect = androidx.compose.ui.geometry.Rect(
                        left = 24f,
                        top = 16f,
                        right = size.width - 64f,
                        bottom = size.height - 16f,
                    )
                    val regionRect = androidx.compose.ui.geometry.Rect(
                        left = surfaceRect.left + 40f,
                        top = surfaceRect.top + 36f,
                        right = surfaceRect.right - 40f,
                        bottom = surfaceRect.bottom - 36f,
                    )

                    // Surface boundary.
                    drawRect(
                        color = Color(0xFF1E88E5),
                        topLeft = surfaceRect.topLeft,
                        size = surfaceRect.size,
                        style = Stroke(width = 3f),
                    )

                    // Region boundary.
                    drawRect(
                        color = Color(0xFF00897B),
                        topLeft = regionRect.topLeft,
                        size = regionRect.size,
                        style = Stroke(width = 2f),
                    )

                    // Tile grid inside the region (2 rows x 3 cols).
                    val cols = 3
                    val rows = 2
                    val cellW = regionRect.width / cols
                    val cellH = regionRect.height / rows
                    for (c in 1 until cols) {
                        val x = regionRect.left + cellW * c
                        drawLine(
                            color = Color(0xFF90A4AE),
                            start = Offset(x, regionRect.top),
                            end = Offset(x, regionRect.bottom),
                            strokeWidth = 1f,
                        )
                    }
                    for (r in 1 until rows) {
                        val y = regionRect.top + cellH * r
                        drawLine(
                            color = Color(0xFF90A4AE),
                            start = Offset(regionRect.left, y),
                            end = Offset(regionRect.right, y),
                            strokeWidth = 1f,
                        )
                    }

                    // Offset arrow: shifts the grid start inside the region.
                    val arrowStart = Offset(regionRect.left, regionRect.bottom + 18f)
                    val arrowEnd = Offset(regionRect.left + 46f, regionRect.bottom + 18f)
                    drawLine(
                        color = Color(0xFFE53935),
                        start = arrowStart,
                        end = arrowEnd,
                        strokeWidth = 3f,
                    )
                    drawLine(
                        color = Color(0xFFE53935),
                        start = arrowEnd,
                        end = Offset(arrowEnd.x - 8f, arrowEnd.y - 6f),
                        strokeWidth = 3f,
                    )
                    drawLine(
                        color = Color(0xFFE53935),
                        start = arrowEnd,
                        end = Offset(arrowEnd.x - 8f, arrowEnd.y + 6f),
                        strokeWidth = 3f,
                    )
                }
                Text(
                    "• Surface: the wall or floor you are tiling.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• Region: the area of a surface covered by one tile group.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• Grid: tiles repeat in a pattern (grid, brick, stacked, herringbone).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• Offset: shifts the grid's start position inside the region (red arrow).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
