package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.normalizedRotation

/**
 * Door geometry helpers shared by layout exclusion, the 3D preview and the
 * door configuration UI.
 */
object DoorGeometry {

    /**
     * Door rect in surface-local layout coords (x along the wall's span from
     * its anchor corner, y down from the top of the wall, like every other
     * layout rect), so the opening sits on the floor line. Null when the room
     * has no door or this surface is not the door wall.
     */
    fun surfaceLocalRect(room: Room, surface: Surface): RegionRect? {
        val wall = room.doorWall ?: return null
        if (surface.type != SurfaceType.WALL) return null
        if (normalizedRotation(wall) != normalizedRotation(surface.position.rotation)) {
            return null
        }
        val offset = room.doorOffset ?: (surface.width - room.doorWidth) / 2.0
        val height = minOf(room.doorHeight, surface.height)
        return RegionRect(offset, surface.height - height, room.doorWidth, height)
    }
}
