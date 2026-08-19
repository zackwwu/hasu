package com.hasu.tilelayout.engine

import kotlin.math.min

/**
 * Edge-hit logic for the mini top-down room diagram used in the door wall
 * picker. Pure geometry shared by the Compose and SwiftUI pickers.
 */
object DoorPickerGeometry {

    /**
     * Which wall edge a tap hits in a top-down diagram, returned as the
     * wall's rotation: Front (z=0, bottom edge) = 0, Back (top edge) = 180,
     * Left (x=0) = 90, Right (x=width) = 270. Null for taps outside the room
     * rectangle or inside it but farther than the margin from every edge.
     *
     * The room rectangle is drawn centered in the diagram, aspect-preserved
     * (width maps roomWidth, height maps roomDepth). The edge hit margin is
     * [edgeMarginFraction] of the rectangle's smaller side.
     */
    fun wallAtTap(
        x: Double,
        y: Double,
        diagramW: Double,
        diagramH: Double,
        roomWidth: Double,
        roomDepth: Double,
        edgeMarginFraction: Double = 0.15,
    ): Double? {
        if (diagramW <= 0.0 || diagramH <= 0.0 || roomWidth <= 0.0 || roomDepth <= 0.0) return null

        val scale = min(diagramW / roomWidth, diagramH / roomDepth)
        val rectW = roomWidth * scale
        val rectH = roomDepth * scale
        val left = (diagramW - rectW) / 2.0
        val top = (diagramH - rectH) / 2.0

        // Taps outside the room rectangle (diagram padding) never hit a wall.
        if (x < left || x > left + rectW || y < top || y > top + rectH) return null

        val margin = min(rectW, rectH) * edgeMarginFraction

        // Distance from each edge; the nearest edge within the margin wins.
        val candidates = listOf(
            ((top + rectH) - y) to 0.0,     // Front edge (z=0, bottom)
            (y - top) to 180.0,             // Back edge (z=depth, top)
            (x - left) to 90.0,             // Left edge (x=0)
            ((left + rectW) - x) to 270.0,  // Right edge (x=width)
        )

        return candidates
            .filter { it.first <= margin }
            .minByOrNull { it.first }
            ?.second
    }
}
