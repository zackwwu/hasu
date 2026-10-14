package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfacePosition
import com.hasu.tilelayout.models.SurfaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DoorGeometryTest {

    private fun room(
        doorWall: Double? = 0.0,
        doorWidth: Double = 900.0,
        doorHeight: Double = 2100.0,
        doorOffset: Double? = null,
    ) = Room(
        projectId = "p1",
        name = "Test",
        width = 3000.0,
        depth = 4000.0,
        height = 2400.0,
        doorWall = doorWall,
        doorWidth = doorWidth,
        doorHeight = doorHeight,
        doorOffset = doorOffset,
    )

    private fun wall(rotation: Double, width: Double = 3000.0) = Surface(
        roomId = "r1",
        type = SurfaceType.WALL,
        width = width,
        height = 2400.0,
        position = SurfacePosition(0.0, 0.0, 0.0, rotation),
    )

    // ── surfaceLocalRect ──

    @Test
    fun localRectCentersWhenOffsetNull() {
        val rect = DoorGeometry.surfaceLocalRect(room(), wall(0.0))
        assertEquals(RegionRect(1050.0, 0.0, 900.0, 2100.0), rect)
    }

    @Test
    fun localRectUsesExplicitOffset() {
        val rect = DoorGeometry.surfaceLocalRect(room(doorOffset = 200.0), wall(0.0))
        assertEquals(RegionRect(200.0, 0.0, 900.0, 2100.0), rect)
    }

    @Test
    fun localRectMatchesDoorWallAcrossRotations() {
        // Door on the back wall (180): rect lands on the surface rotated 180
        val rect = DoorGeometry.surfaceLocalRect(room(doorWall = 180.0), wall(180.0))
        assertEquals(RegionRect(1050.0, 0.0, 900.0, 2100.0), rect)
        // Door on the left wall (90): rect on the surface rotated 90
        val leftRect = DoorGeometry.surfaceLocalRect(room(doorWall = 90.0), wall(90.0))
        assertEquals(RegionRect(1050.0, 0.0, 900.0, 2100.0), leftRect)
    }

    @Test
    fun localRectNullWhenNotDoorWall() {
        assertNull(DoorGeometry.surfaceLocalRect(room(doorWall = 0.0), wall(90.0)))
        assertNull(DoorGeometry.surfaceLocalRect(room(doorWall = 180.0), wall(0.0)))
    }

    @Test
    fun localRectNullWhenNoDoor() {
        assertNull(DoorGeometry.surfaceLocalRect(room(doorWall = null), wall(0.0)))
    }

    @Test
    fun localRectNullForFloorSurface() {
        val floor = Surface(
            roomId = "r1",
            type = SurfaceType.FLOOR,
            width = 3000.0,
            height = 4000.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        assertNull(DoorGeometry.surfaceLocalRect(room(), floor))
    }
}
