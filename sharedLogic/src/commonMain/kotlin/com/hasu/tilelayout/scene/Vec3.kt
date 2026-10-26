package com.hasu.tilelayout.scene

import kotlin.math.sqrt

/** 3D vector / point in world space (x, y up, z). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    val length: Double get() = sqrt(this dot this)

    fun normalized(): Vec3 {
        val len = length
        return if (len < 1e-12) this else this * (1.0 / len)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UP = Vec3(0.0, 1.0, 0.0)
    }
}

/** Axis-aligned bounding box. */
data class Bounds(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = (min + max) * 0.5

    /** Radius of the bounding sphere that encloses the box. */
    val radius: Double get() = (max - min).length * 0.5

    fun union(other: Bounds) = Bounds(
        Vec3(minOf(min.x, other.min.x), minOf(min.y, other.min.y), minOf(min.z, other.min.z)),
        Vec3(maxOf(max.x, other.max.x), maxOf(max.y, other.max.y), maxOf(max.z, other.max.z)),
    )

    companion object {
        /** A 1 m cube at the origin; used when there is nothing to frame. */
        val UNIT = Bounds(Vec3(-0.5, -0.5, -0.5), Vec3(0.5, 0.5, 0.5))
    }
}
