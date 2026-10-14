package com.hasu.tilelayout.scene

import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.scene.RoomSceneBuilder.GROUT_DEPTH_MM
import com.hasu.tilelayout.scene.RoomSceneBuilder.TILE_THICKNESS_MM
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_D
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_H
import com.hasu.tilelayout.scene.SceneTestFixtures.ROOM_W
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoomSceneBuilderTest {

    private val group = TileGroup(
        id = "tg1", projectId = "p1", name = "Subway",
        tileWidth = 300.0, tileHeight = 200.0, texturePath = "/textures/tg1.png",
    )

    private fun gridTiles(width: Double, height: Double, groutWidth: Double = 3.0): List<PlacedTile> {
        val tiles = mutableListOf<PlacedTile>()
        var y = 0.0
        while (y < height) {
            var x = 0.0
            while (x < width) {
                val w = minOf(group.tileWidth, width - x)
                val h = minOf(group.tileHeight, height - y)
                tiles += PlacedTile(x, y, w, h, tileGroupId = group.id)
                x += group.tileWidth + groutWidth
            }
            y += group.tileHeight + groutWidth
        }
        return tiles
    }

    private fun input(surface: Surface, tiles: List<PlacedTile> = emptyList(), door: RegionRect? = null) =
        SurfaceSceneInput(surface, tiles, mapOf(group.id to group), door)

    private fun triangles(part: MeshPart) = (0 until part.indexCount / 3).map { t ->
        IntArray(3) { part.indices[t * 3 + it] }
    }

    private fun vertex(part: MeshPart, i: Int) =
        Vec3(part.positions[i * 3].toDouble(), part.positions[i * 3 + 1].toDouble(), part.positions[i * 3 + 2].toDouble())

    private fun normal(part: MeshPart, i: Int) =
        Vec3(part.normals[i * 3].toDouble(), part.normals[i * 3 + 1].toDouble(), part.normals[i * 3 + 2].toDouble())

    @Test
    fun everyTriangleIsCounterClockwiseAroundItsNormal() {
        val surfaces = SceneTestFixtures.room()
        val front = SceneTestFixtures.wall(surfaces, 0.0)
        val scene = RoomSceneBuilder.build(
            surfaces.map { s ->
                val door = if (s.id == front.id) RegionRect(1050.0, 300.0, 900.0, 2100.0) else null
                input(s, gridTiles(s.width, s.height), door)
            }
        )
        var checked = 0
        for (mesh in scene.surfaces) for (part in mesh.parts) for (tri in triangles(part)) {
            val a = vertex(part, tri[0]); val b = vertex(part, tri[1]); val c = vertex(part, tri[2])
            val faceNormal = (b - a) cross (c - a)
            assertTrue(faceNormal dot normal(part, tri[0]) > 0, "Triangle in ${mesh.surfaceId} faces away from its normal")
            checked++
        }
        assertTrue(checked > 1000)
    }

    @Test
    fun wallWithoutTilesIsASingleSolidBody() {
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0)
        val mesh = RoomSceneBuilder.buildSurface(input(wall))
        assertTrue(mesh.isWall)
        assertEquals(1, mesh.parts.size)
        assertEquals(SceneMaterial.Solid(SceneColors.WALL), mesh.parts.single().material)
    }

    @Test
    fun tilesProduceGroutAndOneTilePartPerGroup() {
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0).copy(groutColor = GroutColor.BLACK)
        val mesh = RoomSceneBuilder.buildSurface(input(wall, gridTiles(wall.width, wall.height)))
        val materials = mesh.parts.map { it.material }
        assertTrue(SceneMaterial.Solid(SceneColors.grout(GroutColor.BLACK)) in materials)
        val tileMaterial = materials.filterIsInstance<SceneMaterial.Tile>().single()
        assertEquals("tg1", tileMaterial.tileGroupId)
        assertEquals("/textures/tg1.png", tileMaterial.texturePath)
    }

    @Test
    fun tileFacesSitProudOfTheGroutBed() {
        // Front wall interior faces +Z, so depth maps straight to z
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0)
        val tiles = listOf(PlacedTile(100.0, 100.0, 300.0, 200.0, tileGroupId = group.id))
        val mesh = RoomSceneBuilder.buildSurface(input(wall, tiles))
        val tilePart = mesh.parts.first { it.material is SceneMaterial.Tile }
        val groutPart = mesh.parts.first { it.material == SceneMaterial.Solid(SceneColors.grout(wall.groutColor)) }
        val zs = (0 until tilePart.vertexCount).map { tilePart.positions[it * 3 + 2].toDouble() }
        assertEquals(TILE_THICKNESS_MM / 1000, zs.max(), 1e-6)
        assertEquals(GROUT_DEPTH_MM / 1000, zs.min(), 1e-6)
        assertTrue((0 until groutPart.vertexCount).all { abs(groutPart.positions[it * 3 + 2] - GROUT_DEPTH_MM / 1000) < 1e-6 })
    }

    @Test
    fun tileSideWallsAreDroppedForVeryDenseLayouts() {
        val floor = SceneTestFixtures.floor(SceneTestFixtures.room())
        val many = (0 until RoomSceneBuilder.MAX_TILES_WITH_EDGES + 1).map {
            PlacedTile((it % 100) * 20.0, (it / 100) * 20.0, 15.0, 15.0, tileGroupId = group.id)
        }
        val mesh = RoomSceneBuilder.buildSurface(input(floor, many))
        val tilePart = mesh.parts.first { it.material is SceneMaterial.Tile }
        assertEquals(many.size * 4, tilePart.vertexCount)
    }

    @Test
    fun doorIsCutThroughTheWall() {
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0)
        val door = RegionRect(1050.0, 300.0, 900.0, 2100.0)
        val mesh = RoomSceneBuilder.buildSurface(input(wall, gridTiles(wall.width, wall.height), door))
        // A ray straight through the door centre, along −Z, must not cross any triangle.
        // Layout y runs down from the top of the wall, so the door centre is 1050 mm above the floor.
        val px = (door.x + door.width / 2) / 1000
        val py = (ROOM_H - door.y - door.height / 2) / 1000
        assertEquals(1.05, py, 1e-9)
        for (part in mesh.parts) for (tri in triangles(part)) {
            val a = vertex(part, tri[0]); val b = vertex(part, tri[1]); val c = vertex(part, tri[2])
            assertFalse(containsXY(a, b, c, px, py), "Door opening is covered by ${part.material}")
        }
    }

    @Test
    fun zeroGroutTilesStillGetSideFaces() {
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0).copy(groutWidth = 0.0)
        val tiles = listOf(PlacedTile(100.0, 100.0, 300.0, 200.0, tileGroupId = group.id))
        val mesh = RoomSceneBuilder.buildSurface(input(wall, tiles))
        val tilePart = mesh.parts.first { it.material is SceneMaterial.Tile }
        assertEquals(4 * 5, tilePart.vertexCount, "face + four sides")
    }

    @Test
    fun wallTilesAreOrientedLikeTheLayoutTab() {
        // A tile at the top of the 2D layout (y = 0) sits under the ceiling line in 3D,
        // and its image is upright: v = 0 (image top) is the higher edge.
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0)
        val tiles = listOf(PlacedTile(100.0, 0.0, 300.0, 200.0, tileGroupId = group.id))
        val tilePart = RoomSceneBuilder.buildSurface(input(wall, tiles)).parts.first { it.material is SceneMaterial.Tile }
        val front = (0 until tilePart.vertexCount).filter {
            abs(tilePart.positions[it * 3 + 2] - TILE_THICKNESS_MM / 1000) < 1e-6
        }
        val ys = front.map { tilePart.positions[it * 3 + 1].toDouble() }
        assertEquals(ROOM_H / 1000, ys.max(), 1e-6)
        assertEquals((ROOM_H - 200.0) / 1000, ys.min(), 1e-6)
        for (i in front) {
            val y = tilePart.positions[i * 3 + 1].toDouble()
            val v = tilePart.uvs[i * 2 + 1].toDouble()
            assertEquals(if (y > (ROOM_H - 100.0) / 1000) 0.0 else 1.0, v, 1e-6)
        }
    }

    @Test
    fun boundsCoverTheRoomAndWallThickness() {
        val scene = RoomSceneBuilder.build(SceneTestFixtures.room().map { input(it) })
        val t = RoomSceneBuilder.WALL_THICKNESS_MM / 1000
        val b = scene.bounds
        assertEquals(-t, b.min.x, 1e-6)
        assertEquals(-t, b.min.z, 1e-6)
        assertEquals(ROOM_W / 1000 + t, b.max.x, 1e-6)
        assertEquals(ROOM_D / 1000 + t, b.max.z, 1e-6)
        assertEquals(ROOM_H / 1000, b.max.y, 1e-6)
        assertEquals(-RoomSceneBuilder.FLOOR_THICKNESS_MM / 1000, b.min.y, 1e-6)
    }

    @Test
    fun emptySceneHasUnitBounds() {
        assertEquals(Bounds.UNIT, RoomScene.EMPTY.bounds)
    }

    // ── Texture windows ──

    private fun tile(w: Double, h: Double, vararg edges: CutEdge) =
        PlacedTile(0.0, 0.0, w, h, isCut = edges.isNotEmpty(), cutEdges = edges.toList(), tileGroupId = group.id)

    @Test
    fun fullTileUsesWholeTextureUpright() {
        val uv = RoomSceneBuilder.tileUv(tile(300.0, 200.0), group)
        assertEquals(UvRect(0.0, 1.0, vLow = 0.0, vHigh = 1.0), uv)
    }

    @Test
    fun leftCutKeepsTheRightPartOfTheTexture() {
        val uv = RoomSceneBuilder.tileUv(tile(150.0, 200.0, CutEdge.LEFT), group)
        assertEquals(0.5, uv.u0, 1e-9)
        assertEquals(1.0, uv.u1, 1e-9)
    }

    @Test
    fun rightCutKeepsTheLeftPartOfTheTexture() {
        val uv = RoomSceneBuilder.tileUv(tile(75.0, 200.0, CutEdge.RIGHT), group)
        assertEquals(0.0, uv.u0, 1e-9)
        assertEquals(0.25, uv.u1, 1e-9)
    }

    @Test
    fun topCutKeepsTheBottomOfTheImage() {
        // TOP flags the low-y edge (the top in the Layout tab): the tile was trimmed there, so the
        // remaining piece is the lower part of the image, still upright (image top at low y)
        val uv = RoomSceneBuilder.tileUv(tile(300.0, 100.0, CutEdge.TOP), group)
        assertEquals(0.5, uv.vLow, 1e-9)
        assertEquals(1.0, uv.vHigh, 1e-9)
    }

    @Test
    fun bottomCutKeepsTheTopOfTheImage() {
        val uv = RoomSceneBuilder.tileUv(tile(300.0, 50.0, CutEdge.BOTTOM), group)
        assertEquals(0.0, uv.vLow, 1e-9)
        assertEquals(0.25, uv.vHigh, 1e-9)
    }

    @Test
    fun missingTileGroupFallsBackToFullTexture() {
        val uv = RoomSceneBuilder.tileUv(tile(120.0, 80.0, CutEdge.RIGHT), null)
        assertEquals(UvRect.FULL, uv)
        val wall = SceneTestFixtures.wall(SceneTestFixtures.room(), 0.0)
        val mesh = RoomSceneBuilder.buildSurface(
            SurfaceSceneInput(wall, listOf(tile(120.0, 80.0)), emptyMap())
        )
        val material = assertNotNull(mesh.parts.firstNotNullOfOrNull { it.material as? SceneMaterial.Tile })
        assertEquals(null, material.texturePath)
    }

    private fun containsXY(a: Vec3, b: Vec3, c: Vec3, x: Double, y: Double): Boolean {
        fun sign(p: Vec3, q: Vec3) = (x - q.x) * (p.y - q.y) - (p.x - q.x) * (y - q.y)
        val d1 = sign(a, b); val d2 = sign(b, c); val d3 = sign(c, a)
        val area = abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y))
        if (area < 1e-12) return false
        val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
        val hasPos = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNeg && hasPos)
    }
}
