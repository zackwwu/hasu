package com.hasu.tilelayout.scene

import com.hasu.tilelayout.scene.SceneTestFixtures.assertVecEquals
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrbitCameraTest {

    private val bounds = Bounds(Vec3(-0.1, -0.08, -0.1), Vec3(3.1, 2.4, 4.1))

    @Test
    fun orbitWrapsYawAndClampsPitch() {
        val cam = OrbitCamera(yawDegrees = 350.0, pitchDegrees = 80.0).orbitBy(20.0, 30.0)
        assertEquals(10.0, cam.yawDegrees, 1e-9)
        assertEquals(OrbitCamera.MAX_PITCH, cam.pitchDegrees)

        val down = OrbitCamera(yawDegrees = 5.0).orbitBy(-10.0, -500.0)
        assertEquals(355.0, down.yawDegrees, 1e-9)
        assertEquals(OrbitCamera.MIN_PITCH, down.pitchDegrees)
    }

    @Test
    fun zoomIsClamped() {
        assertEquals(OrbitCamera.MAX_ZOOM, OrbitCamera().zoomBy(100.0).zoom)
        assertEquals(OrbitCamera.MIN_ZOOM, OrbitCamera().zoomBy(0.001).zoom)
        assertEquals(2.0, OrbitCamera().zoomBy(2.0).zoom, 1e-9)
    }

    @Test
    fun zoomMovesCameraTowardsTarget() {
        val far = OrbitCamera().pose(bounds, 1.0)
        val near = OrbitCamera().zoomBy(2.0).pose(bounds, 1.0)
        assertEquals((far.eye - far.target).length / 2, (near.eye - near.target).length, 1e-9)
    }

    @Test
    fun poseLooksAtRoomCentreFromYawAndPitch() {
        val pose = OrbitCamera(yawDegrees = 0.0, pitchDegrees = 0.0).pose(bounds, 1.0)
        assertVecEquals(bounds.center, pose.target)
        val dir = (pose.eye - pose.target).normalized()
        assertVecEquals(Vec3(0.0, 0.0, 1.0), dir, message = "yaw 0 sits on +Z")

        val side = OrbitCamera(yawDegrees = 90.0, pitchDegrees = 0.0).pose(bounds, 1.0)
        assertVecEquals(Vec3(1.0, 0.0, 0.0), (side.eye - side.target).normalized(), message = "yaw 90 sits on +X")

        val above = OrbitCamera().pose(bounds, 1.0)
        assertTrue(above.eye.y > above.target.y, "Default view looks down into the room")
    }

    @Test
    fun fittedPoseKeepsEveryBoundsCornerInView() {
        for (aspect in listOf(0.5, 1.0, 2.0)) {
            for (yaw in listOf(0.0, 35.0, 120.0, 260.0)) for (pitch in listOf(-60.0, 40.0, OrbitCamera.MAX_PITCH)) {
                val pose = OrbitCamera(yawDegrees = yaw, pitchDegrees = pitch).pose(bounds, aspect)
                val forward = (pose.target - pose.eye).normalized()
                val right = (forward cross pose.up).normalized()
                val up = right cross forward
                val tanY = tan(pose.fovYDegrees * PI / 360)
                val tanX = tanY * aspect
                for (corner in corners(bounds)) {
                    val rel = corner - pose.eye
                    val depth = rel dot forward
                    assertTrue(depth > pose.near && depth < pose.far, "Corner outside clip range")
                    assertTrue(kotlin.math.abs((rel dot right) / depth) <= tanX + 1e-9, "Corner off-screen horizontally (aspect $aspect, yaw $yaw)")
                    assertTrue(kotlin.math.abs((rel dot up) / depth) <= tanY + 1e-9, "Corner off-screen vertically (aspect $aspect, yaw $yaw)")
                }
            }
        }
    }

    @Test
    fun rayThroughViewportCentreIsTheViewDirection() {
        val pose = OrbitCamera().pose(bounds, 0.75)
        val ray = pose.rayThrough(300.0, 400.0, 600.0, 800.0)
        assertVecEquals(pose.eye, ray.origin)
        assertVecEquals((pose.target - pose.eye).normalized(), ray.direction)
    }

    @Test
    fun rayThroughViewportEdgeMatchesFieldOfView() {
        val pose = OrbitCamera(pitchDegrees = 0.0).pose(bounds, 1.0)
        val top = pose.rayThrough(400.0, 0.0, 800.0, 800.0)
        val forward = (pose.target - pose.eye).normalized()
        val angle = atan(kotlin.math.sqrt(1 - (top.direction dot forward).let { it * it }) / (top.direction dot forward))
        assertEquals(OrbitCamera.FOV_Y_DEGREES / 2, angle * 180 / PI, 1e-6)
        assertTrue(top.direction.y > forward.y, "Top of the viewport looks upwards")
    }

    @Test
    fun topLooksStraightDownFromAnyStartingPitch() {
        for (start in listOf(OrbitCamera(), OrbitCamera(pitchDegrees = -40.0), OrbitCamera(pitchDegrees = 85.0), OrbitCamera(yawDegrees = 200.0, pitchDegrees = -85.0, zoom = 2.0))) {
            val top = start.top()
            assertEquals(OrbitCamera.MAX_PITCH, top.pitchDegrees)
            assertEquals(90.0, top.pitchDegrees)
            assertEquals(start.zoom, top.zoom)
            val pose = top.pose(bounds, 1.0)
            assertVecEquals(Vec3(0.0, -1.0, 0.0), (pose.target - pose.eye).normalized(), message = "from $start")
        }
    }

    @Test
    fun topViewMatchesTheLayoutTabFloorPlan() {
        // +X to the right and the rotation-0 wall (z = 0) at the top of the screen, like the 2D floor canvas
        val pose = OrbitCamera().top().pose(bounds, 1.0)
        val forward = (pose.target - pose.eye).normalized()
        assertEquals(0.0, pose.up dot forward, 1e-12)
        assertVecEquals(Vec3(0.0, 0.0, -1.0), pose.up)
        assertVecEquals(Vec3(1.0, 0.0, 0.0), (forward cross pose.up).normalized())
        val topOfScreen = pose.rayThrough(500.0, 0.0, 1000.0, 1000.0)
        assertTrue(topOfScreen.direction.z < 0)
    }

    @Test
    fun upVectorIsOrthogonalToTheViewAtEveryPitch() {
        for (pitch in listOf(-85.0, -30.0, 0.0, 40.0, 85.0, 90.0)) for (yaw in listOf(0.0, 35.0, 200.0)) {
            val pose = OrbitCamera(yawDegrees = yaw, pitchDegrees = pitch).pose(bounds, 1.0)
            val forward = (pose.target - pose.eye).normalized()
            assertEquals(0.0, pose.up dot forward, 1e-9)
            assertEquals(1.0, pose.up.length, 1e-9)
            assertTrue(pose.up.y >= -1e-12, "camera must not roll upside down (pitch $pitch, yaw $yaw)")
        }
    }

    @Test
    fun scrollingAwayFromTheUserZoomsIn() {
        assertTrue(OrbitCamera.zoomFactorForScroll(-1.0) > 1.0)
        assertTrue(OrbitCamera.zoomFactorForScroll(1.0) < 1.0)
        assertEquals(1.0, OrbitCamera.zoomFactorForScroll(2.0) * OrbitCamera.zoomFactorForScroll(-2.0), 1e-12)
    }

    private fun corners(b: Bounds) = listOf(
        Vec3(b.min.x, b.min.y, b.min.z), Vec3(b.max.x, b.min.y, b.min.z),
        Vec3(b.min.x, b.max.y, b.min.z), Vec3(b.max.x, b.max.y, b.min.z),
        Vec3(b.min.x, b.min.y, b.max.z), Vec3(b.max.x, b.min.y, b.max.z),
        Vec3(b.min.x, b.max.y, b.max.z), Vec3(b.max.x, b.max.y, b.max.z),
    )
}
