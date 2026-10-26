package com.hasu.tilelayout.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneLightingTest {

    @Test
    fun litFacesNeverClip() {
        val brightest = SceneLighting.shade(
            SceneLighting.KEY_DIRECTION.x, SceneLighting.KEY_DIRECTION.y, SceneLighting.KEY_DIRECTION.z,
        )
        assertTrue(brightest <= 1.0, "shade $brightest would clip the albedo")
        assertTrue(SceneLighting.shade(0.0, -1.0, 0.0) >= SceneLighting.AMBIENT)
    }

    @Test
    fun differentlyFacingWallsShadeDifferently() {
        val front = SceneLighting.shade(0.0, 0.0, 1.0)
        val side = SceneLighting.shade(1.0, 0.0, 0.0)
        val back = SceneLighting.shade(0.0, 0.0, -1.0)
        val floor = SceneLighting.shade(0.0, 1.0, 0.0)
        assertEquals(4, setOf(front, side, back, floor).size)
        assertTrue(floor > front)
    }

    @Test
    fun vertexShadesFollowNormals() {
        val part = MeshPart(
            material = SceneMaterial.Solid(SceneColors.FLOOR),
            positions = FloatArray(6),
            normals = floatArrayOf(0f, 1f, 0f, 0f, 0f, -1f),
            uvs = FloatArray(4),
            indices = IntArray(0),
        )
        val shades = part.vertexShades
        assertEquals(2, shades.size)
        assertEquals(SceneLighting.shade(0.0, 1.0, 0.0).toFloat(), shades[0], 1e-6f)
        assertEquals(SceneLighting.shade(0.0, 0.0, -1.0).toFloat(), shades[1], 1e-6f)
    }
}
