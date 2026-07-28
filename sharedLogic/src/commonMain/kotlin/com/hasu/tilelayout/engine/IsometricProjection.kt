package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.math.*

object IsometricProjection {
    private const val COS30 = 0.8660254
    private const val SIN30 = 0.5

    data class ScreenPoint(val x: Double, val y: Double)

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

    fun projectSurfaceCorners(surface: Surface, viewAngle: Int, originX: Double, originY: Double): List<ScreenPoint> {
        val p = surface.position
        val w = surface.width; val h = surface.height
        return listOf(
            project(p.x, p.y, p.z, viewAngle, originX, originY),
            project(p.x + w, p.y, p.z, viewAngle, originX, originY),
            project(p.x + w, p.y + h, p.z, viewAngle, originX, originY),
            project(p.x, p.y + h, p.z, viewAngle, originX, originY),
        )
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
