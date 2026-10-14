package com.hasu.tilelayout.scene

import com.hasu.tilelayout.engine.SurfacePositionCalculator
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import kotlin.math.abs
import kotlin.test.assertTrue

internal object SceneTestFixtures {
    const val ROOM_W = 3000.0
    const val ROOM_D = 4000.0
    const val ROOM_H = 2400.0

    /** Front/back/left/right walls plus floor, laid out by the app's own calculator. */
    fun room(): List<Surface> = SurfacePositionCalculator.generate(
        roomId = "r1",
        roomWidth = ROOM_W, roomDepth = ROOM_D, roomHeight = ROOM_H,
        includeFront = true, includeBack = true, includeLeft = true, includeRight = true,
        includeFloor = true,
    )

    fun wall(surfaces: List<Surface>, rotation: Double): Surface =
        surfaces.first { it.type == SurfaceType.WALL && it.position.rotation == rotation }

    fun floor(surfaces: List<Surface>): Surface = surfaces.first { it.type == SurfaceType.FLOOR }

    fun assertVecEquals(expected: Vec3, actual: Vec3, eps: Double = 1e-6, message: String = "") {
        assertTrue(
            abs(expected.x - actual.x) < eps && abs(expected.y - actual.y) < eps && abs(expected.z - actual.z) < eps,
            "$message expected $expected but was $actual",
        )
    }
}
