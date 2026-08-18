package com.hasu.tilelayout.models

import kotlin.test.Test
import kotlin.test.assertEquals

class SurfaceDisplayNameTest {

    private fun wall(rotation: Double) = Surface(
        roomId = "r1",
        type = SurfaceType.WALL,
        width = 3000.0,
        height = 2400.0,
        position = SurfacePosition(0.0, 0.0, 0.0, rotation),
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
