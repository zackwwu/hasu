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

    /** Distance (mm) along the ray to the surface's solid, or null on a miss. */
    private fun intersect(origin: Vec3, direction: Vec3, target: PickTarget): Double? {
        val surface = target.surface
        val frame = SurfaceFrame.of(surface)
        val o = frame.toLocal(origin)
        val d = frame.directionToLocal(direction)
        val isWall = surface.type == SurfaceType.WALL
        val front = RoomSceneBuilder.TILE_THICKNESS_MM
        val min = if (isWall) {
            Vec3(0.0, -RoomSceneBuilder.FLOOR_THICKNESS_MM, -RoomSceneBuilder.WALL_THICKNESS_MM)
        } else {
            Vec3(0.0, 0.0, -RoomSceneBuilder.FLOOR_THICKNESS_MM)
        }
        val max = if (isWall) {
            Vec3(surface.width + RoomSceneBuilder.WALL_THICKNESS_MM, surface.height, front)
        } else {
            Vec3(surface.width, surface.height, front)
        }

        var tEnter = -Double.MAX_VALUE
        var tExit = Double.MAX_VALUE
        var enterAxis = -1
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
            if (t0 > tEnter) { tEnter = t0; enterAxis = axis }
            if (t1 < tExit) tExit = t1
        }
        if (tExit < maxOf(tEnter, 0.0)) return null
        val t = maxOf(tEnter, 0.0)

        val door = target.doorRect
        if (isWall && door != null && enterAxis == 2) {
            val hitX = o.x + d.x * t
            val hitY = o.y + d.y * t
            val inDoor = hitX > door.x && hitX < door.x + door.width &&
                hitY > door.y && hitY < door.y + door.height
            if (inDoor) return null
        }
        return t
    }
}
