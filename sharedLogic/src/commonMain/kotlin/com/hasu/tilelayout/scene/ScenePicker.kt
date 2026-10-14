package com.hasu.tilelayout.scene

import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import kotlin.math.abs

data class PickTarget(val surface: Surface, val doorRect: RegionRect? = null)

/**
 * Ray picking against the same solids [RoomSceneBuilder] emits, so taps
 * select whatever surface is drawn under the finger regardless of engine.
 */
object ScenePicker {

    /** Id of the nearest surface hit by [ray] (world metres), or null. */
    fun pick(ray: Ray, targets: List<PickTarget>): String? {
        val origin = ray.origin * (1.0 / MeshAccumulator.MM_TO_M)
        var bestT = Double.MAX_VALUE
        var bestId: String? = null
        for (target in targets) {
            val t = intersect(origin, ray.direction, target) ?: continue
            if (t < bestT) {
                bestT = t
                bestId = target.surface.id
            }
        }
        return bestId
    }

    /**
     * Distance (mm) along the ray to the surface's solid, or null on a miss.
     * A door wall is the slab minus the opening (cut through its full depth),
     * so a ray that passes through the door can still hit the jamb or header.
     */
    private fun intersect(origin: Vec3, direction: Vec3, target: PickTarget): Double? {
        val surface = target.surface
        val frame = SurfaceFrame.of(surface)
        val o = frame.toLocal(origin)
        val d = frame.directionToLocal(direction)
        val front = RoomSceneBuilder.TILE_THICKNESS_MM
        val footprint: Rect2
        val back: Double
        if (surface.type == SurfaceType.WALL) {
            footprint = Rect2(
                0.0, 0.0,
                surface.width + RoomSceneBuilder.WALL_THICKNESS_MM,
                surface.height + RoomSceneBuilder.FLOOR_THICKNESS_MM,
            )
            back = -RoomSceneBuilder.WALL_THICKNESS_MM
        } else {
            footprint = Rect2(0.0, 0.0, surface.width, surface.height)
            back = -RoomSceneBuilder.FLOOR_THICKNESS_MM
        }
        val door = target.doorRect
            ?.takeIf { surface.type == SurfaceType.WALL }
            ?.let { Rect2(it.x, it.y, it.x + it.width, it.y + it.height) }
        return footprint.minus(door)
            .mapNotNull { slab(o, d, Vec3(it.x0, it.y0, back), Vec3(it.x1, it.y1, front)) }
            .minOrNull()
    }

    /** Ray / axis-aligned box entry distance, clamped to 0 when the origin is inside. */
    private fun slab(o: Vec3, d: Vec3, min: Vec3, max: Vec3): Double? {
        var tEnter = 0.0
        var tExit = Double.MAX_VALUE
        val os = doubleArrayOf(o.x, o.y, o.z)
        val ds = doubleArrayOf(d.x, d.y, d.z)
        val mins = doubleArrayOf(min.x, min.y, min.z)
        val maxs = doubleArrayOf(max.x, max.y, max.z)
        for (axis in 0 until 3) {
            if (abs(ds[axis]) < 1e-12) {
                if (os[axis] < mins[axis] || os[axis] > maxs[axis]) return null
                continue
            }
            var t0 = (mins[axis] - os[axis]) / ds[axis]
            var t1 = (maxs[axis] - os[axis]) / ds[axis]
            if (t0 > t1) { val tmp = t0; t0 = t1; t1 = tmp }
            tEnter = maxOf(tEnter, t0)
            tExit = minOf(tExit, t1)
            if (tExit < tEnter) return null
        }
        return tEnter
    }
}
