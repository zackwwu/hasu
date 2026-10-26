package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.db.LayoutResultRepository
import com.hasu.tilelayout.db.RoomRepository
import com.hasu.tilelayout.db.SurfaceRepository
import com.hasu.tilelayout.db.TileGroupRepository
import com.hasu.tilelayout.engine.DoorGeometry
import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfacePosition
import com.hasu.tilelayout.models.SurfaceTileGroup
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.models.TilePattern
import com.hasu.tilelayout.scene.OrbitCamera
import com.hasu.tilelayout.scene.SceneMaterial
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RoomEditorViewModelTest {

    // ── Fake implementations ──

    private class FakeRoomRepository : RoomRepository {
        private val rooms = mutableListOf<Room>()
        override suspend fun getByProject(projectId: String) = rooms.filter { it.projectId == projectId }
        override suspend fun getById(id: String) = rooms.find { it.id == id }
        override suspend fun insert(room: Room) {
            require(rooms.none { it.id == room.id }) { "Duplicate room id: ${room.id}" }
            rooms.add(room)
        }
        override suspend fun updateDoor(roomId: String, doorWall: Double?, doorWidth: Double, doorHeight: Double, doorOffset: Double?) {
            val idx = rooms.indexOfFirst { it.id == roomId }
            if (idx >= 0) {
                rooms[idx] = rooms[idx].copy(doorWall = doorWall, doorWidth = doorWidth, doorHeight = doorHeight, doorOffset = doorOffset)
            }
        }
        override suspend fun delete(id: String) { rooms.removeAll { it.id == id } }
    }

    private class FakeSurfaceRepository : SurfaceRepository {
        val surfaces = mutableListOf<Surface>()
        val stgs = mutableListOf<SurfaceTileGroup>()

        override suspend fun getByRoom(roomId: String) = surfaces.filter { it.roomId == roomId }
        override suspend fun getById(id: String) = surfaces.find { it.id == id }
        override suspend fun insert(surface: Surface) {
            require(surfaces.none { it.id == surface.id }) { "Duplicate surface id: ${surface.id}" }
            surfaces.add(surface)
        }
        override suspend fun updateGrout(id: String, groutColor: GroutColor, groutWidth: Double) {
            val idx = surfaces.indexOfFirst { it.id == id }
            if (idx >= 0) {
                surfaces[idx] = surfaces[idx].copy(groutColor = groutColor, groutWidth = groutWidth)
            }
        }
        override suspend fun delete(id: String) { surfaces.removeAll { it.id == id } }

        override suspend fun getSTGsBySurface(surfaceId: String) = stgs.filter { it.surfaceId == surfaceId }
        override suspend fun getSTGById(id: String) = stgs.find { it.id == id }
        override suspend fun insertSTG(stg: SurfaceTileGroup) {
            require(stgs.none { it.id == stg.id }) { "Duplicate STG id: ${stg.id}" }
            stgs.add(stg)
        }
        override suspend fun updateSTG(stg: SurfaceTileGroup) {
            val idx = stgs.indexOfFirst { it.id == stg.id }
            if (idx >= 0) stgs[idx] = stg
        }
        override suspend fun deleteSTG(id: String) { stgs.removeAll { it.id == id } }
    }

    private class FakeTileGroupRepository : TileGroupRepository {
        val tileGroups = mutableListOf<TileGroup>()
        override suspend fun getByProject(projectId: String) = tileGroups.filter { it.projectId == projectId }
        override suspend fun getById(id: String) = tileGroups.find { it.id == id }
        override suspend fun insert(tileGroup: TileGroup) {
            require(tileGroups.none { it.id == tileGroup.id }) { "Duplicate tileGroup id: ${tileGroup.id}" }
            tileGroups.add(tileGroup)
        }
        override suspend fun delete(id: String) { tileGroups.removeAll { it.id == id } }
    }

    private class FakeLayoutResultRepository : LayoutResultRepository {
        val results = mutableListOf<LayoutResult>()
        override suspend fun getBySurface(surfaceId: String) = results.find { it.surfaceId == surfaceId }
        override suspend fun save(layoutResult: LayoutResult) {
            results.removeAll { it.surfaceId == layoutResult.surfaceId }
            results.add(layoutResult)
        }
        override suspend fun delete(id: String) { results.removeAll { it.id == id } }
    }

    // ── Helpers ──

    private lateinit var roomRepo: FakeRoomRepository
    private lateinit var surfaceRepo: FakeSurfaceRepository
    private lateinit var tileGroupRepo: FakeTileGroupRepository
    private lateinit var layoutRepo: FakeLayoutResultRepository
    private lateinit var vm: RoomEditorViewModel

    private fun TestScope.setUp(debounceMs: Long = 0L) {
        roomRepo = FakeRoomRepository()
        surfaceRepo = FakeSurfaceRepository()
        tileGroupRepo = FakeTileGroupRepository()
        layoutRepo = FakeLayoutResultRepository()
        vm = RoomEditorViewModel(
            roomRepo = roomRepo,
            surfaceRepo = surfaceRepo,
            tileGroupRepo = tileGroupRepo,
            layoutRepo = layoutRepo,
            scope = this,
            debounceMs = debounceMs,
        )
    }

    private fun createWallSurface(
        id: String = "s1",
        roomId: String = "r1",
        width: Double = 2000.0,
        height: Double = 1200.0,
        rotation: Double = 0.0,
    ): Surface = Surface(
        id = id, roomId = roomId, type = SurfaceType.WALL,
        width = width, height = height,
        position = SurfacePosition(0.0, 0.0, 0.0, rotation),
    )

    private fun createFloorSurface(
        id: String = "s1",
        roomId: String = "r1",
        width: Double = 2000.0,
        depth: Double = 1500.0,
    ): Surface = Surface(
        id = id, roomId = roomId, type = SurfaceType.FLOOR,
        width = width, height = depth,
        position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
    )

    private fun createTileGroup(id: String = "tg1", width: Double = 300.0, height: Double = 200.0): TileGroup =
        TileGroup(id = id, projectId = "p1", name = "Test Tile", tileWidth = width, tileHeight = height)

    private fun createSTG(
        id: String = "stg1",
        surfaceId: String = "s1",
        tileGroupId: String = "tg1",
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
        regionW: Double = 2000.0,
        regionH: Double = 1200.0,
    ): SurfaceTileGroup = SurfaceTileGroup(
        id = id, surfaceId = surfaceId, tileGroupId = tileGroupId,
        region = RegionRect(0.0, 0.0, regionW, regionH),
        offsetX = offsetX, offsetY = offsetY,
    )

    // ── Selection Tests ──

    @Test
    fun selectSurfaceUpdatesSelectedIdAndLoadsTiles() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1")
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.computeLayout("s1")

        vm.selectSurface("s1")

        assertEquals("s1", vm.selectedSurfaceId.value)
        assertTrue(vm.currentTiles.value.isNotEmpty(), "selectSurface should load tiles")
    }

    @Test
    fun selectSurfaceNullClearsTiles() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1", width = 903.0, height = 603.0))
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1"))
        vm.loadSurfaces("r1")
        vm.computeLayout("s1")
        vm.selectSurface("s1")
        assertTrue(vm.currentTiles.value.isNotEmpty())

        vm.selectSurface(null)

        assertNull(vm.selectedSurfaceId.value)
        assertTrue(vm.currentTiles.value.isEmpty(), "Deselecting should clear tiles")
    }

    // ── 3D Preview Camera Tests ──

    @Test
    fun orbitPreviewRotatesAndClampsCamera() = runTest {
        setUp()
        val start = vm.previewCamera.value
        assertEquals(OrbitCamera(), start)

        vm.orbitPreview(30.0, 10.0)
        assertEquals(start.yawDegrees + 30.0, vm.previewCamera.value.yawDegrees, 1e-9)
        assertEquals(start.pitchDegrees + 10.0, vm.previewCamera.value.pitchDegrees, 1e-9)

        vm.orbitPreview(0.0, 1000.0)
        assertEquals(OrbitCamera.MAX_PITCH, vm.previewCamera.value.pitchDegrees)
    }

    @Test
    fun zoomPreviewByClampsAndResetRestoresDefaults() = runTest {
        setUp()
        vm.zoomPreviewBy(2.0)
        assertEquals(2.0, vm.previewCamera.value.zoom, 1e-9)
        vm.zoomPreviewBy(1000.0)
        assertEquals(OrbitCamera.MAX_ZOOM, vm.previewCamera.value.zoom)
        vm.orbitPreview(45.0, 0.0)

        vm.resetPreviewCamera()
        assertEquals(OrbitCamera(), vm.previewCamera.value)
    }

    @Test
    fun topPreviewLooksStraightDownWhateverThePitch() = runTest {
        setUp()
        vm.orbitPreview(0.0, -1000.0)
        vm.zoomPreviewBy(2.0)

        vm.topPreviewCamera()
        assertEquals(90.0, vm.previewCamera.value.pitchDegrees)
        assertEquals(2.0, vm.previewCamera.value.zoom, 1e-9)

        vm.topPreviewCamera()
        assertEquals(90.0, vm.previewCamera.value.pitchDegrees, "Top is idempotent")
    }

    // ── Door Tests ──

    @Test
    fun doorRectSitsOnTheFloorLineInLayoutCoordinates() = runTest {
        setUp()
        roomRepo.insert(Room(id = "r1", projectId = "p1", name = "Bath", width = 2000.0, depth = 1500.0, height = 1200.0,
            doorWall = 0.0, doorWidth = 900.0, doorHeight = 1000.0))
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")

        assertEquals(RegionRect(550.0, 200.0, 900.0, 1000.0), vm.doorLocalRects.value["s1"])
    }

    private suspend fun seedDoorRoom() {
        roomRepo.insert(Room(id = "r1", projectId = "p1", name = "Bath", width = 2000.0, depth = 1500.0, height = 1200.0,
            doorWall = 0.0, doorWidth = 900.0, doorHeight = 1000.0))
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1"))
        tileGroupRepo.tileGroups.add(createTileGroup())
    }

    private fun legacy(vararg tiles: PlacedTile) = LayoutResult(
        surfaceId = "s1", tiles = tiles.toList(), doorConvention = LayoutResult.DOOR_CONVENTION_CEILING,
    )

    @Test
    fun legacyLayoutCoveringTheNewDoorIsRecomputedOnLoad() = runTest {
        setUp()
        seedDoorRoom()
        // Saved while the door was excluded at the top of the layout: tiles fill the floor-side opening
        layoutRepo.save(legacy(PlacedTile(600.0, 1000.0, 300.0, 200.0, tileGroupId = "tg1")))

        vm.loadSurfaces("r1")

        val door = vm.doorLocalRects.value.getValue("s1")
        val saved = layoutRepo.getBySurface("s1")!!
        assertEquals(LayoutResult.DOOR_CONVENTION_FLOOR, saved.doorConvention)
        assertTrue(saved.tiles.isNotEmpty())
        assertTrue(saved.tiles.none { door.overlaps(RegionRect(it.x, it.y, it.width, it.height)) })
        assertTrue(saved.tiles.any { it.y < door.y }, "Tiles above the door are kept")
    }

    @Test
    fun legacyLayoutClearOfTheNewDoorIsStillRecomputedOnLoad() = runTest {
        setUp()
        seedDoorRoom()
        // Only a tile beside the old ceiling gap survives; nothing overlaps the new floor-level door
        val stray = PlacedTile(1700.0, 0.0, 300.0, 200.0, isCut = true, cutEdges = listOf(CutEdge.LEFT), tileGroupId = "tg1")
        layoutRepo.save(legacy(stray))
        val door = DoorGeometry.surfaceLocalRect(roomRepo.getById("r1")!!, surfaceRepo.surfaces.single())!!
        assertFalse(door.overlaps(RegionRect(stray.x, stray.y, stray.width, stray.height)))

        vm.loadSurfaces("r1")

        val saved = layoutRepo.getBySurface("s1")!!
        assertEquals(LayoutResult.DOOR_CONVENTION_FLOOR, saved.doorConvention)
        assertTrue(stray !in saved.tiles)
        assertTrue(saved.tiles.size > 1, "Recomputed against the floor-level door")
        // The old ceiling gap (x 550–1450, y 0–1000) is tiled again
        assertTrue(saved.tiles.any { RegionRect(550.0, 0.0, 900.0, 200.0).overlaps(RegionRect(it.x, it.y, it.width, it.height)) })
    }

    @Test
    fun legacyLayoutOnAWallWithoutADoorIsRecomputedOnce() = runTest {
        setUp()
        seedDoorRoom()
        surfaceRepo.surfaces.add(createWallSurface("s2", rotation = 90.0))
        surfaceRepo.stgs.add(createSTG("stg2", "s2", "tg1"))
        layoutRepo.save(legacy().copy(surfaceId = "s2"))

        vm.loadSurfaces("r1")
        val first = layoutRepo.getBySurface("s2")!!
        assertEquals(LayoutResult.DOOR_CONVENTION_FLOOR, first.doorConvention)
        assertTrue(first.tiles.isNotEmpty())

        vm.loadSurfaces("r1")
        assertEquals(first.id, layoutRepo.getBySurface("s2")!!.id, "Not recomputed again")
    }

    @Test
    fun currentConventionLayoutIsLeftUntouchedOnLoad() = runTest {
        setUp()
        seedDoorRoom()
        val saved = LayoutResult(surfaceId = "s1", tiles = listOf(PlacedTile(0.0, 0.0, 123.0, 45.0, tileGroupId = "tg1")))
        layoutRepo.save(saved)

        vm.loadSurfaces("r1")

        assertEquals(saved, layoutRepo.getBySurface("s1"))
    }

    // ── Wall Visibility Tests ──

    @Test
    fun toggleSurfaceHiddenHidesAndShowsWalls() = runTest {
        setUp()
        surfaceRepo.surfaces.addAll(listOf(createWallSurface("w1"), createWallSurface("w2", rotation = 90.0)))
        vm.loadSurfaces("r1")

        vm.toggleSurfaceHidden("w1")
        vm.toggleSurfaceHidden("w2")
        assertEquals(setOf("w1", "w2"), vm.hiddenSurfaceIds.value)

        vm.toggleSurfaceHidden("w1")
        assertEquals(setOf("w2"), vm.hiddenSurfaceIds.value)

        vm.showAllSurfaces()
        assertTrue(vm.hiddenSurfaceIds.value.isEmpty())
    }

    @Test
    fun floorsCannotBeHidden() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createFloorSurface("f1"))
        vm.loadSurfaces("r1")

        vm.toggleSurfaceHidden("f1")
        vm.toggleSurfaceHidden("unknown")

        assertTrue(vm.hiddenSurfaceIds.value.isEmpty())
    }

    @Test
    fun reloadingSurfacesDropsHiddenIdsThatNoLongerExist() = runTest {
        setUp()
        surfaceRepo.surfaces.addAll(listOf(createWallSurface("w1"), createWallSurface("w2")))
        vm.loadSurfaces("r1")
        vm.toggleSurfaceHidden("w1")
        vm.toggleSurfaceHidden("w2")

        surfaceRepo.delete("w1")
        vm.loadSurfaces("r1")

        assertEquals(setOf("w2"), vm.hiddenSurfaceIds.value)
    }

    // ── 3D Preview Scene Tests ──

    @Test
    fun loadSurfacesBuildsOneMeshPerSurface() = runTest {
        setUp()
        surfaceRepo.surfaces.addAll(listOf(createWallSurface("w1"), createFloorSurface("f1")))
        vm.loadSurfaces("r1")

        val scene = vm.previewScene.value
        assertEquals(listOf("w1", "f1"), scene.surfaces.map { it.surfaceId })
        assertTrue(scene.surfaces.all { it.parts.isNotEmpty() })
    }

    @Test
    fun computeLayoutAddsTilesToThatSurfaceOnly() = runTest {
        setUp()
        surfaceRepo.surfaces.addAll(listOf(
            createWallSurface("w1", width = 903.0, height = 603.0),
            createWallSurface("w2", width = 903.0, height = 603.0, rotation = 90.0),
        ))
        surfaceRepo.stgs.add(createSTG("stg1", "w1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1"))
        vm.loadSurfaces("r1")
        val untouched = vm.previewScene.value.surface("w2")

        vm.computeLayout("w1")

        val scene = vm.previewScene.value
        val tileParts = scene.surface("w1")!!.parts.filter { it.material is SceneMaterial.Tile }
        assertEquals(1, tileParts.size)
        assertEquals("tg1", (tileParts.single().material as SceneMaterial.Tile).tileGroupId)
        assertSame(untouched, scene.surface("w2"), "Unchanged surfaces keep their mesh instance")
    }

    @Test
    fun loadSurfacesUsesStoredLayoutsForEverySurface() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("w1", width = 903.0, height = 603.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1"))
        layoutRepo.save(LayoutResult(surfaceId = "w1", tiles = listOf(PlacedTile(0.0, 0.0, 300.0, 200.0, tileGroupId = "tg1"))))

        vm.loadSurfaces("r1")

        assertTrue(vm.previewScene.value.surface("w1")!!.parts.any { it.material is SceneMaterial.Tile })
    }

    // ── Lock Tests ──

    @Test
    fun toggleLockAddsAndRemovesSurfaceIds() = runTest {
        setUp()
        assertTrue(vm.lockedSurfaceIds.value.isEmpty())

        vm.toggleLock("s1")
        assertTrue("s1" in vm.lockedSurfaceIds.value)

        vm.toggleLock("s2")
        assertEquals(2, vm.lockedSurfaceIds.value.size)

        vm.toggleLock("s1")
        assertFalse("s1" in vm.lockedSurfaceIds.value)
        assertTrue("s2" in vm.lockedSurfaceIds.value)
    }

    // ── Drag Tests ──

    @Test
    fun dragStartSnapshotsOffsets() = runTest {
        setUp()
        val s1 = createWallSurface(id = "s1", rotation = 0.0)
        val s2 = createWallSurface(id = "s2", rotation = 0.0)
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", offsetX = 10.0, offsetY = 20.0),
            createSTG("stg2", "s2", offsetX = 30.0, offsetY = 40.0),
        ))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragStart()

        val buffer = vm.undoBuffer.value
        assertNotNull(buffer)
        assertEquals(2, buffer.size)
        assertEquals(Pair(10.0, 20.0), buffer["stg1"])
        assertEquals(Pair(30.0, 40.0), buffer["stg2"])
    }

    @Test
    fun dragStartWithoutSelectionReturnsEarly() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")

        vm.onDragStart()

        assertNull(vm.undoBuffer.value)
    }

    @Test
    fun dragEndAppliesOffsetToSelectedSurface() = runTest {
        setUp()
        val s1 = createWallSurface(id = "s1", width = 2000.0, height = 1200.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 10.0, offsetY = 20.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.onDragEnd(5.0, -3.0)
        advanceTimeBy(1) // flush debounce (0ms in tests)

        val updated = surfaceRepo.getSTGById("stg1")
        assertNotNull(updated)
        assertEquals(15.0, updated.offsetX, 0.01)
        assertEquals(17.0, updated.offsetY, 0.01)
    }

    // ── Lock Propagation Tests ──

    @Test
    fun lockPropagationParallelWallsPassBothAxes() = runTest {
        setUp()
        val s1 = createWallSurface(id = "s1", rotation = 0.0)
        val s2 = createWallSurface(id = "s2", rotation = 180.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1", offsetX = 0.0, offsetY = 0.0),
            createSTG("stg2", "s2", "tg1", offsetX = 0.0, offsetY = 0.0),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragStart()
        vm.onDragEnd(10.0, 5.0)
        advanceTimeBy(1)

        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(10.0, stg2.offsetX, 0.01, "Parallel walls: dx should propagate")
        assertEquals(5.0, stg2.offsetY, 0.01, "Parallel walls: dy should propagate")
    }

    @Test
    fun lockPropagationParallelWallsWithNegativeRotation() = runTest {
        setUp()
        val s1 = createWallSurface(id = "s1", rotation = -45.0)
        val s2 = createWallSurface(id = "s2", rotation = 135.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1", offsetX = 0.0, offsetY = 0.0),
            createSTG("stg2", "s2", "tg1", offsetX = 0.0, offsetY = 0.0),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragStart()
        vm.onDragEnd(7.0, 3.0)
        advanceTimeBy(1)

        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(7.0, stg2.offsetX, 0.01, "Negative rotation parallel walls: dx should propagate")
        assertEquals(3.0, stg2.offsetY, 0.01, "Negative rotation parallel walls: dy should propagate")
    }

    @Test
    fun lockPropagationPerpendicularWallsPassVerticalOnly() = runTest {
        setUp()
        val s1 = createWallSurface(id = "s1", rotation = 0.0)
        val s2 = createWallSurface(id = "s2", rotation = 90.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1", offsetX = 0.0, offsetY = 0.0),
            createSTG("stg2", "s2", "tg1", offsetX = 0.0, offsetY = 0.0),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragStart()
        vm.onDragEnd(10.0, 5.0)
        advanceTimeBy(1)

        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(0.0, stg2.offsetX, 0.01, "Perpendicular walls: dx should NOT propagate")
        assertEquals(5.0, stg2.offsetY, 0.01, "Perpendicular walls: dy should propagate")
    }

    @Test
    fun lockPropagationFloorToFloorBothAxes() = runTest {
        setUp()
        val f1 = createFloorSurface(id = "s1", width = 3000.0, depth = 2000.0)
        val f2 = createFloorSurface(id = "s2", width = 3000.0, depth = 2000.0)
        val tg = createTileGroup("tg1", 300.0, 300.0)
        surfaceRepo.surfaces.addAll(listOf(f1, f2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1"),
            createSTG("stg2", "s2", "tg1"),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragStart()
        vm.onDragEnd(8.0, 4.0)
        advanceTimeBy(1)

        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(8.0, stg2.offsetX, 0.01, "Floor→floor: dx should propagate")
        assertEquals(4.0, stg2.offsetY, 0.01, "Floor→floor: dy should propagate")
    }

    @Test
    fun lockPropagationWallToFloorNoPropagation() = runTest {
        setUp()
        val wall = createWallSurface(id = "s1", rotation = 0.0)
        val floor = createFloorSurface(id = "s2")
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.addAll(listOf(wall, floor))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1"),
            createSTG("stg2", "s2", "tg1"),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragEnd(10.0, 5.0)
        advanceTimeBy(1)

        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(0.0, stg2.offsetX, 0.01, "Wall→floor: dx should NOT propagate")
        assertEquals(0.0, stg2.offsetY, 0.01, "Wall→floor: dy should NOT propagate")
    }

    // ── Debounce Tests ──

    @Test
    fun onDragEndComputesLayoutImmediatelyBypassingDebounce() = runTest {
        setUp(debounceMs = 100L)
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // onDragEnd is the final drag position — compute immediately, no debounce wait
        vm.onDragEnd(5.0, 0.0)
        assertNotNull(layoutRepo.getBySurface("s1"), "Layout should compute immediately on drag end")

        // Subsequent drags also compute immediately
        advanceTimeBy(50)
        vm.onDragEnd(5.0, 0.0)
        assertNotNull(layoutRepo.getBySurface("s1"), "Each drag end should compute immediately")
        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(10.0, stg.offsetX, 0.01, "Both offsets should accumulate")
    }

    @Test
    fun undoBypassesDebounceAndComputesImmediately() = runTest {
        setUp(debounceMs = 100L)
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 5.0, offsetY = 10.0, regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.onDragStart()
        vm.onDragEnd(20.0, 15.0)

        // Undo before debounce fires — should compute immediately
        vm.undo()

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Undo should trigger immediate layout compute, bypassing debounce")
        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(5.0, stg.offsetX, 0.01)
        assertEquals(10.0, stg.offsetY, 0.01)
    }

    @Test
    fun resetToAutoBypassesDebounce() = runTest {
        setUp(debounceMs = 100L)
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 50.0, offsetY = 30.0, regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.resetToAuto("s1")

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "resetToAuto should compute immediately")
        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(0.0, stg.offsetX, 0.01)
    }

    @Test
    fun snapToCenterBypassesDebounce() = runTest {
        setUp(debounceMs = 100L)
        val s1 = Surface(
            id = "s1", roomId = "r1", type = SurfaceType.WALL,
            width = 903.0, height = 603.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
            groutWidth = 3.0,
        )
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 0.0, offsetY = 0.0, regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.snapToCenter("s1")

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "snapToCenter should compute immediately")
    }

    @Test
    fun multipleRapidDragsAccumulateOffsetsAndComputeEachEnd() = runTest {
        setUp(debounceMs = 100L)
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // Simulate rapid incremental drags — each end computes immediately
        vm.onDragEnd(1.0, 0.0)
        advanceTimeBy(30)
        vm.onDragEnd(1.0, 0.0)
        advanceTimeBy(30)
        vm.onDragEnd(1.0, 0.0)
        advanceTimeBy(30)
        vm.onDragEnd(1.0, 0.0)

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Each drag end should compute immediately")

        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(4.0, stg.offsetX, 0.01, "All 4 offsets should accumulate")
    }

    // ── Undo Tests (end-to-end) ──

    @Test
    fun undoFullFlowRestoresOffsetsAndRecomputesLayout() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 5.0, offsetY = 10.0, regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.onDragStart()
        assertNotNull(vm.undoBuffer.value)

        vm.onDragEnd(20.0, 15.0)
        advanceTimeBy(1)
        val afterDrag = surfaceRepo.getSTGById("stg1")!!
        assertEquals(25.0, afterDrag.offsetX, 0.01)
        assertEquals(25.0, afterDrag.offsetY, 0.01)

        vm.undo()
        assertNull(vm.undoBuffer.value)
        val afterUndo = surfaceRepo.getSTGById("stg1")!!
        assertEquals(5.0, afterUndo.offsetX, 0.01)
        assertEquals(10.0, afterUndo.offsetY, 0.01)

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Layout should be recomputed after undo")
        Unit
    }

    @Test
    fun undoWithNoBufferIsNoOp() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        surfaceRepo.stgs.add(createSTG("stg1", "s1", offsetX = 5.0, offsetY = 10.0))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.undo()

        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(5.0, stg.offsetX, 0.01)
        assertEquals(10.0, stg.offsetY, 0.01)
    }

    @Test
    fun undoRecomputesCorrectSurfacesAfterSelectionChange() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val s2 = createWallSurface("s2", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1", offsetX = 0.0, offsetY = 0.0, regionW = 903.0, regionH = 603.0),
            createSTG("stg2", "s2", "tg1", offsetX = 0.0, offsetY = 0.0, regionW = 903.0, regionH = 603.0),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.toggleLock("s2")

        vm.onDragStart()
        vm.onDragEnd(10.0, 10.0)
        advanceTimeBy(1)

        vm.selectSurface("s2")
        vm.undo()

        val stg1 = surfaceRepo.getSTGById("stg1")!!
        val stg2 = surfaceRepo.getSTGById("stg2")!!
        assertEquals(0.0, stg1.offsetX, 0.01)
        assertEquals(0.0, stg2.offsetX, 0.01)

        val result1 = layoutRepo.getBySurface("s1")
        val result2 = layoutRepo.getBySurface("s2")
        assertNotNull(result1, "s1 layout should be recomputed")
        assertNotNull(result2, "s2 layout should be recomputed")
        Unit
    }

    // ── Reset Tests ──

    @Test
    fun resetToAutoZerosOffsetsAndRecomputes() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 50.0, offsetY = 30.0, regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.resetToAuto("s1")

        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(0.0, stg.offsetX, 0.01)
        assertEquals(0.0, stg.offsetY, 0.01)
        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result)
        Unit
    }

    @Test
    fun snapToCenterComputesCenteredOffset() = runTest {
        setUp()
        val s1 = Surface(
            id = "s1", roomId = "r1", type = SurfaceType.WALL,
            width = 903.0, height = 603.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
            groutWidth = 3.0,
        )
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 0.0, offsetY = 0.0, regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.snapToCenter("s1")

        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(148.5, stg.offsetX, 0.01)
        assertEquals(98.5, stg.offsetY, 0.01)
    }

    // ── Layout Computation Tests ──

    @Test
    fun computeLayoutGeneratesTiles() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 903.0, 603.0),
                pattern = TilePattern.GRID,
            )
        )
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")

        vm.computeLayout("s1")

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Layout result should be saved")
        assertTrue(result.tiles.isNotEmpty(), "Should have placed tiles")
        assertFalse(result.stale, "Should be marked fresh")
    }

    @Test
    fun computeLayoutWithBrickPattern() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 903.0, 603.0),
                pattern = TilePattern.BRICK,
            )
        )
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")

        vm.computeLayout("s1")

        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Brick pattern should produce layout")
        assertTrue(result.tiles.isNotEmpty(), "Brick pattern should place tiles")
    }

    @Test
    fun computeLayoutWithSelectedSurfaceUpdatesCurrentTiles() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 903.0, 603.0),
                pattern = TilePattern.GRID,
            )
        )
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.computeLayout("s1")

        assertTrue(vm.currentTiles.value.isNotEmpty(), "currentTiles should be updated for selected surface")
    }

    @Test
    fun computeLayoutForNonSelectedSurfaceDoesNotUpdateCurrentTiles() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val s2 = createWallSurface("s2", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0),
            createSTG("stg2", "s2", "tg1", regionW = 903.0, regionH = 603.0),
        ))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.computeLayout("s1")
        val tilesBeforeS2Compute = vm.currentTiles.value.toList()

        vm.computeLayout("s2")

        assertEquals(tilesBeforeS2Compute, vm.currentTiles.value,
            "Computing layout for non-selected surface should not change currentTiles")
    }

    // ── Cut List Tests ──

    @Test
    fun computeLayoutUpdatesCutEntriesStateFlow() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")

        assertTrue(vm.cutEntries.value.isEmpty(), "No layout yet — no cut entries")

        vm.computeLayout("s1")

        assertTrue(vm.cutEntries.value.isNotEmpty(), "Cut entries should be recomputed after layout")
        assertEquals("Test Tile", vm.cutEntries.value.first().tileGroupName)
        assertTrue(vm.cutEntries.value.all { it.locations.isNotEmpty() })
    }

    @Test
    fun dragEndRefreshesCutEntries() = runTest {
        setUp()
        val s1 = createWallSurface("s1", width = 903.0, height = 603.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        vm.onDragEnd(5.0, 0.0)

        assertTrue(vm.cutEntries.value.isNotEmpty(), "onDragEnd computes layout and refreshes cut entries")
    }

    // ── Pick Tests ──

    @Test
    fun pickSurfaceAtViewCentreHitsTheWall() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")

        assertEquals("s1", vm.pickSurface(400.0, 300.0, 800.0, 600.0))
    }

    @Test
    fun pickSurfaceIgnoresHiddenWalls() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")
        vm.toggleSurfaceHidden("s1")

        assertNull(vm.pickSurface(400.0, 300.0, 800.0, 600.0))
    }

    @Test
    fun pickSurfaceReturnsNullOffTheRoomOrForEmptyViewport() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")

        assertNull(vm.pickSurface(1.0, 1.0, 800.0, 600.0), "Corner of the view is empty space")
        assertNull(vm.pickSurface(0.0, 0.0, 0.0, 0.0))
    }

    // ── Load Tests ──

    @Test
    fun loadSurfacesPopulatesSurfacesStateFlow() = runTest {
        setUp()
        surfaceRepo.surfaces.addAll(listOf(
            createWallSurface("s1"),
            createWallSurface("s2"),
        ))

        vm.loadSurfaces("r1")

        assertEquals(2, vm.surfaces.value.size)
    }

    @Test
    fun loadSurfacesEmptyRoomReturnsEmptyList() = runTest {
        setUp()

        vm.loadSurfaces("nonexistent-room")

        assertTrue(vm.surfaces.value.isEmpty())
    }
}
