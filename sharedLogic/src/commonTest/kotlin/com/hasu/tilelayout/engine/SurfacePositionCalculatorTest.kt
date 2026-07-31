package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.test.*

class SurfacePositionCalculatorTest {

    @Test
    fun allSurfacesGeneratedWhenAllEnabled() {
        // have: all 5 surfaces enabled (front, back, left, right, floor)
        // want: exactly 5 surfaces returned
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = true, includeBack = true, includeLeft = true,
            includeRight = true, includeFloor = true,
        )
        assertEquals(5, surfaces.size)
    }

    @Test
    fun noSurfacesWhenAllDisabled() {
        // have: all surface flags false
        // want: empty list
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = false, includeBack = false, includeLeft = false,
            includeRight = false, includeFloor = false,
        )
        assertTrue(surfaces.isEmpty())
    }

    @Test
    fun frontWallDimensions() {
        // have: only front wall enabled, room 3000w × 2000d × 2400h
        // want: type=WALL, width=roomWidth(3000), height=roomHeight(2400)
        //       position at origin (0,0,0), rotation=0°
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = true, includeBack = false, includeLeft = false,
            includeRight = false, includeFloor = false,
        )
        assertEquals(1, surfaces.size)
        val front = surfaces[0]
        assertEquals(SurfaceType.WALL, front.type)
        assertEquals(3000.0, front.width)
        assertEquals(2400.0, front.height)
        assertEquals(0.0, front.position.x)
        assertEquals(0.0, front.position.y)
        assertEquals(0.0, front.position.z)
        assertEquals(0.0, front.position.rotation)
    }

    @Test
    fun backWallPosition() {
        // have: only back wall
        // want: type=WALL, width=roomWidth(3000), height=roomHeight(2400)
        //       position=(3000, 0, 2000), rotation=180°
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = false, includeBack = true, includeLeft = false,
            includeRight = false, includeFloor = false,
        )
        val back = surfaces[0]
        assertEquals(SurfaceType.WALL, back.type)
        assertEquals(3000.0, back.width)
        assertEquals(2400.0, back.height)
        assertEquals(3000.0, back.position.x)
        assertEquals(0.0, back.position.y)
        assertEquals(2000.0, back.position.z)
        assertEquals(180.0, back.position.rotation)
    }

    @Test
    fun leftWallUsesDepthAsWidth() {
        // have: only left wall, room 3000w × 2000d × 2400h
        // want: width=roomDepth(2000), height=roomHeight(2400)
        //       position=(0, 0, 2000), rotation=90°
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = false, includeBack = false, includeLeft = true,
            includeRight = false, includeFloor = false,
        )
        val left = surfaces[0]
        assertEquals(SurfaceType.WALL, left.type)
        assertEquals(2000.0, left.width)
        assertEquals(2400.0, left.height)
        assertEquals(0.0, left.position.x)
        assertEquals(2000.0, left.position.z)
        assertEquals(90.0, left.position.rotation)
    }

    @Test
    fun rightWallPosition() {
        // have: only right wall
        // want: width=roomDepth(2000), height=roomHeight(2400)
        //       position=(3000, 0, 0), rotation=270°
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = false, includeBack = false, includeLeft = false,
            includeRight = true, includeFloor = false,
        )
        val right = surfaces[0]
        assertEquals(SurfaceType.WALL, right.type)
        assertEquals(2000.0, right.width)
        assertEquals(2400.0, right.height)
        assertEquals(3000.0, right.position.x)
        assertEquals(0.0, right.position.z)
        assertEquals(270.0, right.position.rotation)
    }

    @Test
    fun floorDimensions() {
        // have: only floor enabled
        // want: type=FLOOR, width=roomWidth(3000), height=roomDepth(2000)
        //       position at origin, rotation=0°
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "room1", roomWidth = 3000.0, roomDepth = 2000.0, roomHeight = 2400.0,
            includeFront = false, includeBack = false, includeLeft = false,
            includeRight = false, includeFloor = true,
        )
        val floor = surfaces[0]
        assertEquals(SurfaceType.FLOOR, floor.type)
        assertEquals(3000.0, floor.width)
        assertEquals(2000.0, floor.height)
        assertEquals(0.0, floor.position.x)
        assertEquals(0.0, floor.position.y)
        assertEquals(0.0, floor.position.z)
    }

    @Test
    fun allSurfacesHaveCorrectRoomId() {
        // have: roomId="my-room"
        // want: all generated surfaces reference that roomId
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "my-room", roomWidth = 1000.0, roomDepth = 1000.0, roomHeight = 1000.0,
            includeFront = true, includeBack = true, includeLeft = true,
            includeRight = true, includeFloor = true,
        )
        assertTrue(surfaces.all { it.roomId == "my-room" })
    }

    @Test
    fun wallsAreWallTypeFloorIsFloorType() {
        // have: all surfaces enabled
        // want: 4 walls with type=WALL, 1 floor with type=FLOOR
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "r1", roomWidth = 2000.0, roomDepth = 1500.0, roomHeight = 2400.0,
            includeFront = true, includeBack = true, includeLeft = true,
            includeRight = true, includeFloor = true,
        )
        assertEquals(4, surfaces.count { it.type == SurfaceType.WALL })
        assertEquals(1, surfaces.count { it.type == SurfaceType.FLOOR })
    }

    @Test
    fun singleWallOnly() {
        // have: only front wall, everything else disabled
        // want: exactly 1 surface
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "r1", roomWidth = 5000.0, roomDepth = 4000.0, roomHeight = 3000.0,
            includeFront = true, includeBack = false, includeLeft = false,
            includeRight = false, includeFloor = false,
        )
        assertEquals(1, surfaces.size)
        assertEquals(SurfaceType.WALL, surfaces[0].type)
    }

    @Test
    fun surfacesHaveDefaultGroutValues() {
        // have: generated surfaces
        // want: all have default groutColor=GREY and groutWidth=3.0
        val surfaces = SurfacePositionCalculator.generate(
            roomId = "r1", roomWidth = 2000.0, roomDepth = 1500.0, roomHeight = 2400.0,
            includeFront = true, includeBack = true, includeLeft = true,
            includeRight = true, includeFloor = true,
        )
        for (surface in surfaces) {
            assertEquals(GroutColor.GREY, surface.groutColor,
                "${surface.type} should have default grout color GREY")
            assertEquals(3.0, surface.groutWidth, 0.001,
                "${surface.type} should have default grout width 3.0")
        }
    }
}
