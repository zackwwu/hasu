package com.hasu.tilelayout.scene

import kotlin.math.max

/**
 * Fixed world lighting shared by both renderers: an ambient term plus a key
 * light from above-front-right and a weak fill from the opposite side.
 * Both renderers bake [shade] into vertex colours (via
 * [MeshPart.vertexShades]) and draw unlit, so faces shade identically on
 * Android and iOS and no per-pixel lighting runs on the GPU.
 * Intensities sum to < 1 so a fully lit face never clips its albedo.
 */
object SceneLighting {
    const val AMBIENT = 0.58
    const val KEY_INTENSITY = 0.38
    const val FILL_INTENSITY = 0.14

    /** Unit vectors pointing from the surface towards each light. */
    val KEY_DIRECTION = Vec3(0.45, 0.8, 0.4).normalized()
    val FILL_DIRECTION = Vec3(-0.6, 0.35, -0.7).normalized()

    fun shade(nx: Double, ny: Double, nz: Double): Double {
        val key = max(0.0, nx * KEY_DIRECTION.x + ny * KEY_DIRECTION.y + nz * KEY_DIRECTION.z)
        val fill = max(0.0, nx * FILL_DIRECTION.x + ny * FILL_DIRECTION.y + nz * FILL_DIRECTION.z)
        return AMBIENT + KEY_INTENSITY * key + FILL_INTENSITY * fill
    }
}
