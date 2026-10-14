package com.hasu.tilelayout.scene

import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_D
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_W
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScenePickerTest {

    private val surfaces = SceneTestFixtures.room()
    private val front = SceneTestFixtures.wall(surfaces, 0.0)
    private val back = SceneTestFixtures.wall(surfaces, 180.0)
    private val floor = SceneTestFixtures.floor(surfaces)
    // Layout coordinates: y down from the top of the 2400 wall, so the opening is 0–2100 mm above the floor
    private val door = RegionRect(1050.0, 300.0, 900.0, 2100.0)

    private fun targets(hidden: Set<String> = emptySet(), withDoor: Boolean = false) = surfaces
        .filter { it.id !in hidden }
        .map { PickTarget(it, if (withDoor && it.id == front.id) door else null) }

    /** Horizontal ray at 1.5 m height from in front of the front wall, looking into the room (+Z). */
    private fun rayIntoRoomAt(xMm: Double, heightMm: Double = 1500.0) =
        Ray(Vec3(xMm / 1000, heightMm / 1000, -2.0), Vec3(0.0, 0.0, 1.0))

    @Test
    fun nearestSolidWins() {
        assertEquals(front.id, ScenePicker.pick(rayIntoRoomAt(ROOM_W / 2), targets()))
    }

    @Test
    fun hiddenSurfacesAreSkipped() {
        assertEquals(back.id, ScenePicker.pick(rayIntoRoomAt(ROOM_W / 2), targets(hidden = setOf(front.id))))
    }

    @Test
    fun rayThroughTheDoorwayHitsWhatIsBehindIt() {
        assertEquals(back.id, ScenePicker.pick(rayIntoRoomAt(ROOM_W / 2, 1000.0), targets(withDoor = true)))
        assertEquals(front.id, ScenePicker.pick(rayIntoRoomAt(ROOM_W / 2, 2300.0), targets(withDoor = true)))
    }

    @Test
    fun rayEnteringTheDoorwayCanStillHitTheJamb() {
        // Enters the wall's outer face inside the opening at x = 1.9 m, then crosses the jamb at x = 1.95 m
        val dir = Vec3(1.0, 0.0, 1.0).normalized()
        val ray = Ray(Vec3(0.9, 1.0, -1.1), dir)
        assertEquals(front.id, ScenePicker.pick(ray, targets(withDoor = true)))
    }

    @Test
    fun lookingDownFromAboveHitsTheFloor() {
        val ray = Ray(Vec3(ROOM_W / 2000, 5.0, ROOM_D / 2000), Vec3(0.0, -1.0, 0.0))
        assertEquals(floor.id, ScenePicker.pick(ray, targets()))
    }

    @Test
    fun missesReturnNull() {
        val ray = Ray(Vec3(10.0, 1.0, -2.0), Vec3(0.0, 0.0, 1.0))
        assertNull(ScenePicker.pick(ray, targets()))
        val away = Ray(Vec3(1.5, 1.0, -2.0), Vec3(0.0, 0.0, -1.0))
        assertNull(ScenePicker.pick(away, targets()))
    }

    @Test
    fun cameraPoseRayPicksTheCentreOfTheView() {
        val scene = RoomSceneBuilder.build(surfaces.map { SurfaceSceneInput(it) })
        // Straight down onto the room: the centre of the view is the floor
        val pose = OrbitCamera(pitchDegrees = OrbitCamera.MAX_PITCH).pose(scene.bounds, 1.0)
        val ray = pose.rayThrough(500.0, 500.0, 1000.0, 1000.0)
        assertEquals(floor.id, ScenePicker.pick(ray, targets()))
    }
}
