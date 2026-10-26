package com.hasu.tilelayout.scene

import com.hasu.tilelayout.engine.DoorGeometry
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfacePosition
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_D
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_H
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_W
import com.hasu.tilelayout.scene.SceneTestFixtures.assertVecEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceFrameTest {

    private val roomCenter = Vec3(ROOM_W / 2, ROOM_H / 2, ROOM_D / 2)

    @Test
    fun wallNormalsPointIntoTheRoom() {
        for (wall in SceneTestFixtures.room().filter { it.type == SurfaceType.WALL }) {
            val frame = SurfaceFrame.of(wall)
            val mid = frame.point(wall.width / 2, wall.height / 2)
            assertTrue(
                (roomCenter - mid) dot frame.normal > 0,
                "${wall.displayName()} normal ${frame.normal} should face the room centre",
            )
        }
    }

    @Test
    fun wallsSpanTheRoomFootprint() {
        val surfaces = SceneTestFixtures.room()
        val expectedEnds = mapOf(
            0.0 to Vec3(ROOM_W, 0.0, 0.0),
            90.0 to Vec3(0.0, 0.0, 0.0),
            180.0 to Vec3(0.0, 0.0, ROOM_D),
            270.0 to Vec3(ROOM_W, 0.0, ROOM_D),
        )
        for ((rotation, end) in expectedEnds) {
            val wall = SceneTestFixtures.wall(surfaces, rotation)
            assertVecEquals(end, SurfaceFrame.of(wall).point(wall.width, wall.height), message = "rotation $rotation")
        }
    }

    @Test
    fun wallLayoutYRunsDownFromTheTopEdge() {
        // Same orientation as the 2D Layout tab: y = 0 is the top of the wall, y = height the floor line
        for (wall in SceneTestFixtures.room().filter { it.type == SurfaceType.WALL }) {
            val frame = SurfaceFrame.of(wall)
            assertEquals(ROOM_H, frame.point(0.0, 0.0).y, 1e-9)
            assertEquals(0.0, frame.point(0.0, wall.height).y, 1e-9)
        }
    }

    @Test
    fun layoutCoordinatesAreNotMirrored() {
        // Facing a surface from inside the room (from above for the floor), +x must be to the
        // viewer's right and +y down, i.e. u × v points away from the viewer, along −normal.
        for (surface in SceneTestFixtures.room()) {
            val frame = SurfaceFrame.of(surface)
            assertVecEquals(-frame.normal, frame.u cross frame.v, message = surface.displayName())
        }
    }

    @Test
    fun floorPlanTopEdgeMeetsTheRotationZeroWall() {
        val surfaces = SceneTestFixtures.room()
        val floor = SurfaceFrame.of(SceneTestFixtures.floor(surfaces))
        val front = SceneTestFixtures.wall(surfaces, 0.0)
        val wall = SurfaceFrame.of(front)
        for (x in listOf(0.0, 1234.0, ROOM_W)) {
            assertVecEquals(wall.point(x, front.height), floor.point(x, 0.0), message = "x = $x")
        }
    }

    @Test
    fun floorSpansXAndZFacingUp() {
        val floor = SceneTestFixtures.floor(SceneTestFixtures.room())
        val frame = SurfaceFrame.of(floor)
        assertVecEquals(Vec3.UP, frame.normal)
        assertVecEquals(Vec3(ROOM_W, 0.0, ROOM_D), frame.point(floor.width, floor.height))
    }

    @Test
    fun toLocalInvertsPoint() {
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 90.0)
        val frame = SurfaceFrame.of(wall)
        assertVecEquals(Vec3(123.0, 456.0, 7.0), frame.toLocal(frame.point(123.0, 456.0, 7.0)))
    }

    // ── Door placement: surface-local door rect → world, per wall rotation ──

    private fun doorRoom(doorWall: Double) = Room(
        projectId = "p1", name = "Test",
        width = ROOM_W, depth = ROOM_D, height = ROOM_H,
        doorWall = doorWall,
    )

    private fun doorCorners(doorWall: Double, wall: Surface): List<Vec3> {
        val rect = DoorGeometry.surfaceLocalRect(doorRoom(doorWall), wall)!!
        val frame = SurfaceFrame.of(wall)
        return listOf(
            frame.point(rect.x, rect.y),
            frame.point(rect.x + rect.width, rect.y),
            frame.point(rect.x + rect.width, rect.y + rect.height),
            frame.point(rect.x, rect.y + rect.height),
        )
    }

    private fun wallAt(rotation: Double, width: Double, x: Double, z: Double) = Surface(
        roomId = "r1", type = SurfaceType.WALL, width = width, height = ROOM_H,
        position = SurfacePosition(x, 0.0, z, rotation),
    )

    private fun assertCorners(expected: List<Vec3>, actual: List<Vec3>) =
        expected.zip(actual).forEach { (e, a) -> assertVecEquals(e, a) }

    @Test
    fun doorOnFrontWallSpansPositiveX() = assertCorners(
        listOf(Vec3(1050.0, 2100.0, 0.0), Vec3(1950.0, 2100.0, 0.0), Vec3(1950.0, 0.0, 0.0), Vec3(1050.0, 0.0, 0.0)),
        doorCorners(0.0, wallAt(0.0, ROOM_W, 0.0, 0.0)),
    )

    @Test
    fun doorOnBackWallSpansNegativeX() = assertCorners(
        listOf(Vec3(1950.0, 2100.0, ROOM_D), Vec3(1050.0, 2100.0, ROOM_D), Vec3(1050.0, 0.0, ROOM_D), Vec3(1950.0, 0.0, ROOM_D)),
        doorCorners(180.0, wallAt(180.0, ROOM_W, ROOM_W, ROOM_D)),
    )

    @Test
    fun doorOnLeftWallSpansNegativeZ() = assertCorners(
        // offset 1550 = (4000 − 900) / 2 along −Z from anchor z = 4000
        listOf(Vec3(0.0, 2100.0, 2450.0), Vec3(0.0, 2100.0, 1550.0), Vec3(0.0, 0.0, 1550.0), Vec3(0.0, 0.0, 2450.0)),
        doorCorners(90.0, wallAt(90.0, ROOM_D, 0.0, ROOM_D)),
    )

    @Test
    fun doorOnRightWallSpansPositiveZ() = assertCorners(
        listOf(Vec3(ROOM_W, 2100.0, 1550.0), Vec3(ROOM_W, 2100.0, 2450.0), Vec3(ROOM_W, 0.0, 2450.0), Vec3(ROOM_W, 0.0, 1550.0)),
        doorCorners(270.0, wallAt(270.0, ROOM_D, ROOM_W, 0.0)),
    )
}
