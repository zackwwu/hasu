package com.hasu.tilelayout.models

import kotlin.test.Test
import kotlin.test.assertEquals

class SurfaceDisplayNameTest {

    private fun wall(rotation: Double, doorRotation: Double? = null) = Surface(
        roomId = "r1",
        type = SurfaceType.WALL,
        width = 3000.0,
        height = 2400.0,
        position = SurfacePosition(0.0, 0.0, 0.0, rotation),
        doorRotation = doorRotation,
    )

    @Test
    fun wallsNamedByOrientation() {
        assertEquals("Front Wall", wall(0.0).displayName())
        assertEquals("Left Wall", wall(90.0).displayName())
        assertEquals("Back Wall", wall(180.0).displayName())
        assertEquals("Right Wall", wall(270.0).displayName())
    }

    @Test
    fun rotationNormalizesAcrossFullTurns() {
        assertEquals("Front Wall", wall(360.0).displayName())
        assertEquals("Left Wall", wall(-270.0).displayName())
        assertEquals("Back Wall", wall(540.0).displayName())
        assertEquals("Right Wall", wall(-90.0).displayName())
    }

    @Test
    fun doorOnZeroWallNamesDoorRelative() {
        assertEquals("Door Wall", wall(0.0, doorRotation = 0.0).displayName())
        assertEquals("Left Wall", wall(90.0, doorRotation = 0.0).displayName())
        assertEquals("Front Wall", wall(180.0, doorRotation = 0.0).displayName())
        assertEquals("Right Wall", wall(270.0, doorRotation = 0.0).displayName())
    }

    @Test
    fun doorOnNinetyWallNamesDoorRelative() {
        assertEquals("Right Wall", wall(0.0, doorRotation = 90.0).displayName())
        assertEquals("Door Wall", wall(90.0, doorRotation = 90.0).displayName())
        assertEquals("Left Wall", wall(180.0, doorRotation = 90.0).displayName())
        assertEquals("Front Wall", wall(270.0, doorRotation = 90.0).displayName())
    }

    @Test
    fun doorOnOneEightyWallNamesDoorRelative() {
        assertEquals("Front Wall", wall(0.0, doorRotation = 180.0).displayName())
        assertEquals("Right Wall", wall(90.0, doorRotation = 180.0).displayName())
        assertEquals("Door Wall", wall(180.0, doorRotation = 180.0).displayName())
        assertEquals("Left Wall", wall(270.0, doorRotation = 180.0).displayName())
    }

    @Test
    fun doorOnTwoSeventyWallNamesDoorRelative() {
        assertEquals("Left Wall", wall(0.0, doorRotation = 270.0).displayName())
        assertEquals("Front Wall", wall(90.0, doorRotation = 270.0).displayName())
        assertEquals("Right Wall", wall(180.0, doorRotation = 270.0).displayName())
        assertEquals("Door Wall", wall(270.0, doorRotation = 270.0).displayName())
    }

    @Test
    fun doorRelativeNamesNormalizeAcrossFullTurns() {
        assertEquals("Door Wall", wall(360.0, doorRotation = 360.0).displayName())
        assertEquals("Left Wall", wall(-270.0, doorRotation = 360.0).displayName())
        assertEquals("Front Wall", wall(180.0, doorRotation = 360.0).displayName())
        assertEquals("Right Wall", wall(0.0, doorRotation = 90.0).displayName())
    }

    @Test
    fun floorNamedFloor() {
        val floor = Surface(
            roomId = "r1",
            type = SurfaceType.FLOOR,
            width = 3000.0,
            height = 4000.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        assertEquals("Floor", floor.displayName())
    }
}
