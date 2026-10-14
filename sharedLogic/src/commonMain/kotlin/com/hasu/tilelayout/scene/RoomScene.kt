package com.hasu.tilelayout.scene

import com.hasu.tilelayout.models.GroutColor

/** How a [MeshPart] is shaded. Colours are sRGB `0xRRGGBB`. */
sealed interface SceneMaterial {
    data class Solid(val color: Int) : SceneMaterial

    /** Tiles of one tile group; [texturePath] is null when no texture was captured. */
    data class Tile(
        val tileGroupId: String,
        val texturePath: String?,
        val color: Int,
    ) : SceneMaterial
}

/**
 * Triangle mesh sharing one material. Positions are in metres; indices are
 * counter-clockwise when seen from the side the normals point to.
 */
class MeshPart(
    val material: SceneMaterial,
    /** xyz per vertex. */
    val positions: FloatArray,
    /** xyz per vertex, unit length. */
    val normals: FloatArray,
    /** uv per vertex, v = 0 at the top of the texture image. */
    val uvs: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val indexCount: Int get() = indices.size

    /** [SceneLighting.shade] per vertex, for renderers that bake lighting into vertex colours. */
    val vertexShades: FloatArray by lazy {
        FloatArray(vertexCount) { i ->
            SceneLighting.shade(
                normals[i * 3].toDouble(), normals[i * 3 + 1].toDouble(), normals[i * 3 + 2].toDouble(),
            ).toFloat()
        }
    }
}

/** Everything rendered for one surface; hidden/selected as a unit. */
class SurfaceMesh(
    val surfaceId: String,
    val isWall: Boolean,
    val parts: List<MeshPart>,
    val bounds: Bounds,
)

/** Renderable description of a room, independent of any graphics API. */
class RoomScene(val surfaces: List<SurfaceMesh>) {
    val bounds: Bounds =
        surfaces.map { it.bounds }.reduceOrNull { a, b -> a.union(b) } ?: Bounds.UNIT

    fun surface(id: String): SurfaceMesh? = surfaces.firstOrNull { it.surfaceId == id }

    companion object {
        val EMPTY = RoomScene(emptyList())
    }
}

object SceneColors {
    const val BACKGROUND = 0xEFECE6
    const val WALL = 0xE8E0D8
    const val FLOOR = 0xD4C8B8
    const val TILE = 0xD4A574

    /** Multiplied onto every material of the selected surface. */
    const val SELECTION_TINT = 0xA6E4EE

    /** Same palette as the 2D layout canvas. */
    fun grout(color: GroutColor): Int = when (color) {
        GroutColor.BLACK -> 0x000000
        GroutColor.GREY -> 0x888888
        GroutColor.WHITE -> 0xFFFFFF
    }
}
