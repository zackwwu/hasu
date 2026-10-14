package com.hasu.tilelayout.scene

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

data class Ray(val origin: Vec3, val direction: Vec3)

/** Concrete perspective camera, in metres, ready to hand to a 3D engine. */
data class CameraPose(
    val eye: Vec3,
    val target: Vec3,
    val up: Vec3,
    val fovYDegrees: Double,
    val near: Double,
    val far: Double,
) {
    /** World ray through a viewport point given in pixels with the origin at the top-left. */
    fun rayThrough(x: Double, y: Double, viewportWidth: Double, viewportHeight: Double): Ray {
        val forward = (target - eye).normalized()
        val right = (forward cross up).normalized()
        val trueUp = right cross forward
        val tanY = tan(fovYDegrees * PI / 360.0)
        val tanX = tanY * viewportWidth / viewportHeight
        val ndcX = 2.0 * x / viewportWidth - 1.0
        val ndcY = 1.0 - 2.0 * y / viewportHeight
        val dir = forward + right * (ndcX * tanX) + trueUp * (ndcY * tanY)
        return Ray(eye, dir.normalized())
    }
}

/**
 * Orbit camera state for the 3D preview: the camera circles the room centre
 * at [yawDegrees] around the vertical axis (0° = on the +Z side looking
 * towards −Z), [pitchDegrees] above the horizon, and [zoom] × closer than the
 * distance that fits the whole room in view.
 */
data class OrbitCamera(
    val yawDegrees: Double = DEFAULT_YAW,
    val pitchDegrees: Double = DEFAULT_PITCH,
    val zoom: Double = 1.0,
) {
    fun orbitBy(deltaYawDegrees: Double, deltaPitchDegrees: Double) = copy(
        yawDegrees = ((yawDegrees + deltaYawDegrees) % 360.0 + 360.0) % 360.0,
        pitchDegrees = (pitchDegrees + deltaPitchDegrees).coerceIn(MIN_PITCH, MAX_PITCH),
    )

    fun zoomBy(factor: Double) = copy(zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM))

    /**
     * Straight-down plan view, keeping the zoom. Yaw resets to 0 so the plan
     * has the same orientation as the floor in the 2D Layout tab (+X right,
     * the rotation-0 wall at the top).
     */
    fun top() = copy(yawDegrees = 0.0, pitchDegrees = MAX_PITCH)

    fun pose(bounds: Bounds, aspect: Double): CameraPose {
        val target = bounds.center
        val radius = max(bounds.radius, 0.1)
        val halfFovY = FOV_Y_DEGREES * PI / 360.0
        val halfFovX = atan(tan(halfFovY) * aspect.coerceAtLeast(0.01))
        val fitDistance = radius / sin(min(halfFovY, halfFovX))
        val distance = fitDistance / zoom

        val yaw = yawDegrees * PI / 180.0
        val pitch = pitchDegrees * PI / 180.0
        val offset = Vec3(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw))
        // Tangent of the orbit along increasing pitch: exactly orthogonal to the view
        // direction at every pitch, so the camera never rolls or degenerates looking straight down.
        val up = Vec3(-sin(pitch) * sin(yaw), cos(pitch), -sin(pitch) * cos(yaw))
        return CameraPose(
            eye = target + offset * distance,
            target = target,
            up = up,
            fovYDegrees = FOV_Y_DEGREES,
            near = max(MIN_NEAR, distance - radius * 1.5),
            far = distance + radius * 2.0,
        )
    }

    companion object {
        const val FOV_Y_DEGREES = 45.0
        const val DEFAULT_YAW = 35.0
        const val DEFAULT_PITCH = 40.0
        const val MIN_PITCH = -85.0
        const val MAX_PITCH = 90.0
        const val MIN_ZOOM = 0.5
        const val MAX_ZOOM = 8.0
        private const val MIN_NEAR = 0.02

        /** Orbit speed for drag gestures, per density-independent pixel / point. */
        const val DEGREES_PER_POINT = 0.4

        /** Zoom change per mouse-wheel notch (ln of the factor). */
        const val ZOOM_PER_SCROLL_NOTCH = 0.15

        /**
         * Zoom factor for a mouse wheel / trackpad scroll of [notchesTowardsUser]
         * notches (positive = wheel rolled towards the user, i.e. "scroll down").
         * Scrolling away from the user zooms in on both platforms.
         */
        fun zoomFactorForScroll(notchesTowardsUser: Double): Double =
            exp(-notchesTowardsUser * ZOOM_PER_SCROLL_NOTCH)
    }
}
