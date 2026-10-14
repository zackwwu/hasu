package com.hasu.tilelayout.scene

import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup

/** Inputs for one surface of the 3D scene. Tile coordinates are surface-local millimetres. */
data class SurfaceSceneInput(
    val surface: Surface,
    val tiles: List<PlacedTile> = emptyList(),
    val tileGroups: Map<String, TileGroup> = emptyMap(),
    /** Surface-local door opening (see [com.hasu.tilelayout.engine.DoorGeometry.surfaceLocalRect]). */
    val doorRect: RegionRect? = null,
)

/**
 * Turns surfaces and their computed tile layouts into GPU-ready meshes.
 *
 * Each wall is a solid slab behind its interior face (extended by its own
 * thickness past its span end so neighbouring walls close the corners), with
 * the door cut through it. The floor is a slab below y = 0. On the interior
 * side every tile gets a grout bed (the tile rect grown by the grout width,
 * like the 2D canvas) and a raised tile body on top of it.
 */
object RoomSceneBuilder {
    const val WALL_THICKNESS_MM = 100.0
    const val FLOOR_THICKNESS_MM = 80.0
    const val GROUT_DEPTH_MM = 2.0
    const val TILE_THICKNESS_MM = 8.0

    /** Above this many tiles on a surface only tile faces are emitted (no side walls) to bound vertex count. */
    const val MAX_TILES_WITH_EDGES = 4000

    fun build(inputs: List<SurfaceSceneInput>): RoomScene = RoomScene(inputs.map(::buildSurface))

    fun buildSurface(input: SurfaceSceneInput): SurfaceMesh {
        val surface = input.surface
        val frame = SurfaceFrame.of(surface)
        val isWall = surface.type == SurfaceType.WALL
        val extent = Rect2(0.0, 0.0, surface.width, surface.height)
        val door = if (isWall) input.doorRect?.toRect2()?.intersect(extent) else null

        val parts = mutableListOf<MeshPart>()
        val body = MeshAccumulator(frame)
        if (isWall) wallBody(body, surface.width, surface.height, door)
        else floorBody(body, surface.width, surface.height)
        parts += body.toMeshPart(SceneMaterial.Solid(if (isWall) SceneColors.WALL else SceneColors.FLOOR))
        var bounds = body.bounds ?: Bounds.UNIT

        if (input.tiles.isNotEmpty()) {
            val grout = MeshAccumulator(frame)
            val tilesByGroup = linkedMapOf<String, MeshAccumulator>()
            val g = surface.groutWidth.coerceAtLeast(0.0)
            val withEdges = g > 0.0 && input.tiles.size <= MAX_TILES_WITH_EDGES

            for (tile in input.tiles) {
                val rect = Rect2(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height)
                    .intersect(extent) ?: continue
                // Layouts saved before the door was configured can still have tiles in the opening
                if (door != null && rect.intersect(door) != null) continue
                Rect2(rect.x0 - g, rect.y0 - g, rect.x1 + g, rect.y1 + g)
                    .intersect(extent)
                    ?.minus(door)
                    ?.forEach { grout.face(it, GROUT_DEPTH_MM, front = true) }

                val acc = tilesByGroup.getOrPut(tile.tileGroupId) { MeshAccumulator(frame) }
                val uv = tileUv(tile, input.tileGroups[tile.tileGroupId])
                acc.face(rect, TILE_THICKNESS_MM, front = true, uv = uv)
                if (withEdges) {
                    val d0 = GROUT_DEPTH_MM
                    val d1 = TILE_THICKNESS_MM
                    acc.sideX(rect.x0, rect.y0, rect.y1, d0, d1, positive = false, u = uv.u0, vLow = uv.vLow, vHigh = uv.vHigh)
                    acc.sideX(rect.x1, rect.y0, rect.y1, d0, d1, positive = true, u = uv.u1, vLow = uv.vLow, vHigh = uv.vHigh)
                    acc.sideY(rect.y0, rect.x0, rect.x1, d0, d1, positive = false, u0 = uv.u0, u1 = uv.u1, v = uv.vLow)
                    acc.sideY(rect.y1, rect.x0, rect.x1, d0, d1, positive = true, u0 = uv.u0, u1 = uv.u1, v = uv.vHigh)
                }
            }

            if (!grout.isEmpty) {
                parts += grout.toMeshPart(SceneMaterial.Solid(SceneColors.grout(surface.groutColor)))
            }
            for ((groupId, acc) in tilesByGroup) {
                parts += acc.toMeshPart(
                    SceneMaterial.Tile(
                        tileGroupId = groupId,
                        texturePath = input.tileGroups[groupId]?.texturePath,
                        color = SceneColors.TILE,
                    )
                )
                acc.bounds?.let { bounds = bounds.union(it) }
            }
        }

        return SurfaceMesh(surface.id, isWall, parts, bounds)
    }

    private fun wallBody(acc: MeshAccumulator, width: Double, height: Double, door: Rect2?) {
        val t = WALL_THICKNESS_MM
        val base = -FLOOR_THICKNESS_MM
        val length = width + t

        Rect2(0.0, 0.0, length, height).minus(door).forEach { acc.face(it, 0.0, front = true) }
        Rect2(0.0, base, length, height).minus(door).forEach { acc.face(it, -t, front = false) }
        acc.sideY(height, 0.0, length, -t, 0.0, positive = true)
        acc.sideY(base, 0.0, length, -t, 0.0, positive = false)
        acc.sideX(0.0, base, height, -t, 0.0, positive = false)
        acc.sideX(length, base, height, -t, 0.0, positive = true)

        if (door != null) {
            acc.sideX(door.x0, door.y0, door.y1, -t, 0.0, positive = true)
            acc.sideX(door.x1, door.y0, door.y1, -t, 0.0, positive = false)
            acc.sideY(door.y0, door.x0, door.x1, -t, 0.0, positive = true)
            if (door.y1 < height) acc.sideY(door.y1, door.x0, door.x1, -t, 0.0, positive = false)
        }
    }

    private fun floorBody(acc: MeshAccumulator, width: Double, depth: Double) {
        val f = FLOOR_THICKNESS_MM
        val top = Rect2(0.0, 0.0, width, depth)
        acc.face(top, 0.0, front = true)
        acc.face(top, -f, front = false)
        acc.sideX(0.0, 0.0, depth, -f, 0.0, positive = false)
        acc.sideX(width, 0.0, depth, -f, 0.0, positive = true)
        acc.sideY(0.0, 0.0, width, -f, 0.0, positive = false)
        acc.sideY(depth, 0.0, width, -f, 0.0, positive = true)
    }

    /**
     * Texture window for a (possibly cut) tile. A cut tile shows the part of
     * the full tile that remains after trimming the flagged edge, so pattern
     * texture lines up with the uncut neighbours. The image is upright on
     * walls: its top row sits at the tile's high-y edge.
     */
    internal fun tileUv(tile: PlacedTile, group: TileGroup?): UvRect {
        val fullW = group?.tileWidth?.takeIf { it > 0 } ?: tile.width
        val fullH = group?.tileHeight?.takeIf { it > 0 } ?: tile.height
        val (u0, u1) = window(
            fraction = tile.width / fullW,
            trimmedLow = CutEdge.LEFT in tile.cutEdges,
            trimmedHigh = CutEdge.RIGHT in tile.cutEdges,
        )
        val (f0, f1) = window(
            fraction = tile.height / fullH,
            trimmedLow = CutEdge.TOP in tile.cutEdges,
            trimmedHigh = CutEdge.BOTTOM in tile.cutEdges,
        )
        return UvRect(u0, u1, vLow = 1.0 - f0, vHigh = 1.0 - f1)
    }

    private fun window(fraction: Double, trimmedLow: Boolean, trimmedHigh: Boolean): Pair<Double, Double> {
        val f = fraction.coerceIn(0.0, 1.0)
        return when {
            trimmedLow && !trimmedHigh -> (1.0 - f) to 1.0
            trimmedLow && trimmedHigh -> (0.5 - f / 2) to (0.5 + f / 2)
            else -> 0.0 to f
        }
    }

    private fun RegionRect.toRect2() = Rect2(x, y, x + width, y + height)
}
