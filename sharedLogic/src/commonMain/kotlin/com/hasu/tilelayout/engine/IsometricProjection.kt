package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.math.*

object IsometricProjection {
    private const val COS30 = 0.8660254
    private const val SIN30 = 0.5

    data class ScreenPoint(val x: Double, val y: Double)

    /** Scale + centered origin so the room fits inside the viewport. */
    data class ViewportFit(val scale: Double, val originX: Double, val originY: Double)

    fun project(sx: Double, sy: Double, sz: Double, viewAngle: Int, originX: Double, originY: Double): ScreenPoint {
        val rad = viewAngle * PI / 180
        val cosA = cos(rad); val sinA = sin(rad)
        val rx = sx * cosA - sz * sinA
        val rz = sx * sinA + sz * cosA
        return ScreenPoint(
            x = (rx - rz) * COS30 + originX,
            y = (rx + rz) * SIN30 - sy + originY,
        )
    }

    /**
     * Compute a scale and origin that fit all projected surface corners
     * inside the viewport, centered, with a padding margin.
     */
    fun fitViewport(
        surfaces: List<Surface>,
        viewAngle: Int,
        viewportWidth: Double,
        viewportHeight: Double,
        paddingFraction: Double = 0.9,
    ): ViewportFit {
        if (surfaces.isEmpty() || viewportWidth <= 0 || viewportHeight <= 0) {
            return ViewportFit(1.0, viewportWidth / 2, viewportHeight * 0.6)
        }
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (surface in surfaces) {
            for (corner in projectSurfaceCorners(surface, viewAngle, 0.0, 0.0)) {
                minX = min(minX, corner.x); minY = min(minY, corner.y)
                maxX = max(maxX, corner.x); maxY = max(maxY, corner.y)
            }
        }
        val roomW = max(1.0, maxX - minX)
        val roomH = max(1.0, maxY - minY)
        val scale = min(viewportWidth / roomW, viewportHeight / roomH) * paddingFraction
        // Center the scaled room's bounding box in the viewport
        val originX = viewportWidth / 2 - (minX + maxX) / 2 * scale
        val originY = viewportHeight / 2 - (minY + maxY) / 2 * scale
        return ViewportFit(scale, originX, originY)
    }

    fun projectSurfaceCorners(
        surface: Surface,
        viewAngle: Int,
        originX: Double,
        originY: Double,
        scale: Double = 1.0,
    ): List<ScreenPoint> {
        val p = surface.position
        val w = surface.width * scale
        val h = surface.height * scale
        val px = p.x * scale; val py = p.y * scale; val pz = p.z * scale
        return if (surface.type == SurfaceType.FLOOR) {
            listOf(
                project(px, py, pz, viewAngle, originX, originY),
                project(px + w, py, pz, viewAngle, originX, originY),
                project(px + w, py, pz + h, viewAngle, originX, originY),
                project(px, py, pz + h, viewAngle, originX, originY),
            )
        } else {
            listOf(
                project(px, py, pz, viewAngle, originX, originY),
                project(px + w, py, pz, viewAngle, originX, originY),
                project(px + w, py + h, pz, viewAngle, originX, originY),
                project(px, py + h, pz, viewAngle, originX, originY),
            )
        }
    }

    fun orderSurfaces(surfaces: List<Surface>, viewAngle: Int): List<Surface> {
        val rad = viewAngle * PI / 180
        val floors = surfaces.filter { it.type == SurfaceType.FLOOR }
        val walls = surfaces.filter { it.type == SurfaceType.WALL }
            .sortedBy { it.position.x * sin(rad) + it.position.z * cos(rad) }
        return floors + walls
    }

    fun pointInPolygon(px: Double, py: Double, polygon: List<ScreenPoint>): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val yi = polygon[i].y; val yj = polygon[j].y
            val xi = polygon[i].x; val xj = polygon[j].x
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
