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

        // World-space corners (x, y up, z).
        // Walls: rotation 0/180 span X, 90/270 span Z (position is the anchor corner).
        val world: List<Triple<Double, Double, Double>> = if (surface.type == SurfaceType.FLOOR) {
            listOf(
                Triple(px, py, pz),
                Triple(px + w, py, pz),
                Triple(px + w, py, pz + h),
                Triple(px, py, pz + h),
            )
        } else {
            wallWorldCorners(surface).map { (x, y, z) -> Triple(x * scale, y * scale, z * scale) }
        }
        return world.map { (x, y, z) -> project(x, y, z, viewAngle, originX, originY) }
    }

    internal fun normalizeRotation(rotation: Double): Int =
        ((rotation.toInt() % 360) + 360) % 360

    /**
     * Shading factor in [0.1, 1.0] for a surface face so both platforms
     * render faces identically. The light follows the view azimuth:
     * faces toward the camera are brightest, away are darkest, sides mid.
     * Floors always get full light.
     */
    fun faceLightFactor(surface: Surface, viewAngle: Int): Double {
        if (surface.type == SurfaceType.FLOOR) return 1.0
        val rad = viewAngle * PI / 180
        val sinA = sin(rad); val cosA = cos(rad)

        // Outward face normal in the X-Z plane for each wall rotation
        val (nx, nz) = when (normalizeRotation(surface.position.rotation)) {
            90 -> -1.0 to 0.0    // left wall faces -X
            180 -> 0.0 to -1.0   // back wall faces -Z
            270 -> 1.0 to 0.0    // right wall faces +X
            else -> 0.0 to 1.0   // front wall faces +Z
        }

        // Light direction matches the camera azimuth
        val lightX = sinA
        val lightZ = cosA
        val len = sqrt(lightX * lightX + lightZ * lightZ)
        if (len < 1e-6) return 0.55
        val dot = (nx * lightX + nz * lightZ) / len
        return 0.55 + 0.45 * dot
    }

    fun orderSurfaces(surfaces: List<Surface>, viewAngle: Int): List<Surface> {
        val rad = viewAngle * PI / 180
        val sinA = sin(rad); val cosA = cos(rad)
        val floors = surfaces.filter { it.type == SurfaceType.FLOOR }
        val walls = surfaces.filter { it.type == SurfaceType.WALL }
            .sortedBy { surface ->
                // Painter's key: the wall's NEAREST corner along the view axis.
                // Sorting by the anchor position misorders rotated walls
                // (e.g. the left wall's anchor is its far corner, so it
                // would draw on top of everything).
                wallWorldCorners(surface).minOf { (x, _, z) -> x * sinA + z * cosA }
            }
        return floors + walls
    }

    /** World-space corners of a wall (x, y up, z) respecting its rotation. */
    private fun wallWorldCorners(surface: Surface): List<Triple<Double, Double, Double>> {
        val p = surface.position
        val w = surface.width; val h = surface.height
        return when (normalizeRotation(p.rotation)) {
            90 -> listOf(
                Triple(p.x, p.y, p.z),
                Triple(p.x, p.y, p.z - w),
                Triple(p.x, p.y + h, p.z - w),
                Triple(p.x, p.y + h, p.z),
            )
            180 -> listOf(
                Triple(p.x, p.y, p.z),
                Triple(p.x - w, p.y, p.z),
                Triple(p.x - w, p.y + h, p.z),
                Triple(p.x, p.y + h, p.z),
            )
            270 -> listOf(
                Triple(p.x, p.y, p.z),
                Triple(p.x, p.y, p.z + w),
                Triple(p.x, p.y + h, p.z + w),
                Triple(p.x, p.y + h, p.z),
            )
            else -> listOf(
                Triple(p.x, p.y, p.z),
                Triple(p.x + w, p.y, p.z),
                Triple(p.x + w, p.y + h, p.z),
                Triple(p.x, p.y + h, p.z),
            )
        }
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
