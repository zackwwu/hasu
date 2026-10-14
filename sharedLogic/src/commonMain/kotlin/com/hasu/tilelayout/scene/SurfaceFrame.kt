package com.hasu.tilelayout.scene

import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orthonormal frame of a surface in world space (millimetres).
 *
 * Surface-local coordinates map as: `x` along [u] from the anchor corner,
 * `y` along [v] (walls: up from the floor; floor: towards +Z), and `depth`
 * along [normal], which points into the room. Walls of rotation 0/90/180/270
 * span +X/−Z/−X/+Z from their anchor, matching [com.hasu.tilelayout.engine.DoorGeometry].
 */
data class SurfaceFrame(
    val origin: Vec3,
    val u: Vec3,
    val v: Vec3,
    val normal: Vec3,
) {
    fun point(x: Double, y: Double, depth: Double = 0.0): Vec3 =
        origin + u * x + v * y + normal * depth

    /** World point → (x, y, depth) in this frame. */
    fun toLocal(p: Vec3): Vec3 {
        val d = p - origin
        return Vec3(d dot u, d dot v, d dot normal)
    }

    /** World direction → (x, y, depth) components in this frame. */
    fun directionToLocal(dir: Vec3): Vec3 = Vec3(dir dot u, dir dot v, dir dot normal)

    companion object {
        fun of(surface: Surface): SurfaceFrame {
            val p = surface.position
            val origin = Vec3(p.x, p.y, p.z)
            return when (surface.type) {
                SurfaceType.FLOOR -> SurfaceFrame(
                    origin = origin,
                    u = Vec3(1.0, 0.0, 0.0),
                    v = Vec3(0.0, 0.0, 1.0),
                    normal = Vec3.UP,
                )
                SurfaceType.WALL -> {
                    val rad = p.rotation * PI / 180.0
                    val c = snap(cos(rad))
                    val s = snap(sin(rad))
                    SurfaceFrame(
                        origin = origin,
                        u = Vec3(c, 0.0, -s),
                        v = Vec3.UP,
                        normal = Vec3(s, 0.0, c),
                    )
                }
            }
        }

        /** Remove floating-point noise so axis-aligned walls stay exactly axis-aligned. */
        private fun snap(value: Double): Double = when {
            abs(value) < 1e-12 -> 0.0
            abs(value - 1.0) < 1e-12 -> 1.0
            abs(value + 1.0) < 1e-12 -> -1.0
            else -> value
        }
    }
}
