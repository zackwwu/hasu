package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType

/** World-space point (x, y up, z). Bridges cleanly to Swift — Kotlin's Triple does not. */
data class WorldPoint(val x: Double, val y: Double, val z: Double)

/**
 * Door geometry helpers shared by layout exclusion, the 3D preview and the
 * door configuration UI.
 */
object DoorGeometry {

    /**
     * Door rect in surface-local coords (x along the wall's span from its
     * anchor corner, y from the floor up). Null when the room has no door
     * or this surface is not the door wall.
     */
    fun surfaceLocalRect(room: Room, surface: Surface): RegionRect? {
        val wall = room.doorWall ?: return null
        if (surface.type != SurfaceType.WALL) return null
        if (IsometricProjection.normalizeRotation(wall) !=
            IsometricProjection.normalizeRotation(surface.position.rotation)
        ) {
            return null
        }
        val offset = room.doorOffset ?: (surface.width - room.doorWidth) / 2.0
        return RegionRect(offset, 0.0, room.doorWidth, room.doorHeight)
    }

    /**
     * Door rectangle as world-space corners (x, y up, z), using the same
     * wall rotation rules as the isometric wall projection: the door offset
     * runs along the wall's span direction from the anchor corner.
     * Null when [surfaceLocalRect] is null.
     */
    fun worldCorners(room: Room, surface: Surface): List<WorldPoint>? {
        val rect = surfaceLocalRect(room, surface) ?: return null
        val p = surface.position
        val x = rect.x
        val w = rect.width
        val y0 = p.y + rect.y
        val y1 = p.y + rect.y + rect.height

        return when (IsometricProjection.normalizeRotation(p.rotation)) {
            // Left wall: span −Z from anchor
            90 -> listOf(
                WorldPoint(p.x, y0, p.z - x),
                WorldPoint(p.x, y0, p.z - x - w),
                WorldPoint(p.x, y1, p.z - x - w),
                WorldPoint(p.x, y1, p.z - x),
            )
            // Back wall: span −X from anchor
            180 -> listOf(
                WorldPoint(p.x - x, y0, p.z),
                WorldPoint(p.x - x - w, y0, p.z),
                WorldPoint(p.x - x - w, y1, p.z),
                WorldPoint(p.x - x, y1, p.z),
            )
            // Right wall: span +Z from anchor
            270 -> listOf(
                WorldPoint(p.x, y0, p.z + x),
                WorldPoint(p.x, y0, p.z + x + w),
                WorldPoint(p.x, y1, p.z + x + w),
                WorldPoint(p.x, y1, p.z + x),
            )
            // Front wall (0): span +X from anchor
            else -> listOf(
                WorldPoint(p.x + x, y0, p.z),
                WorldPoint(p.x + x + w, y0, p.z),
                WorldPoint(p.x + x + w, y1, p.z),
                WorldPoint(p.x + x, y1, p.z),
            )
        }
    }
}
