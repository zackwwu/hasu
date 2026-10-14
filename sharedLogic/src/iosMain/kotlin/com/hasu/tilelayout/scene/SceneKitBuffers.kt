package com.hasu.tilelayout.scene

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Packed vertex/index data for building an `SCNGeometry` from a [MeshPart]
 * without copying element-by-element across the Kotlin/Swift boundary.
 */
object SceneKitBuffers {
    /** float32 xyz per vertex. */
    fun positions(part: MeshPart): NSData = part.positions.toNSData()

    /** float32 xyz per vertex. */
    fun normals(part: MeshPart): NSData = part.normals.toNSData()

    /** float32 uv per vertex, v = 0 at the top of the image (SceneKit's convention). */
    fun uvs(part: MeshPart): NSData = part.uvs.toNSData()

    /** float32 rgb per vertex: the baked [SceneLighting] shade. */
    fun shadeColors(part: MeshPart): NSData {
        val shades = part.vertexShades
        val rgb = FloatArray(shades.size * 3)
        for (i in shades.indices) {
            rgb[i * 3] = shades[i]
            rgb[i * 3 + 1] = shades[i]
            rgb[i * 3 + 2] = shades[i]
        }
        return rgb.toNSData()
    }

    /** uint32 triangle list. */
    fun indices(part: MeshPart): NSData = part.indices.toNSData()
}

@OptIn(ExperimentalForeignApi::class)
private fun FloatArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = (size * 4).toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun IntArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = (size * 4).toULong()) }
