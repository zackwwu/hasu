package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.db.LayoutResultRepository
import com.hasu.tilelayout.db.RoomRepository
import com.hasu.tilelayout.db.SurfaceRepository
import com.hasu.tilelayout.db.TileGroupRepository
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
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RoomEditorViewModelTest {

    // ── Fake implementations ──

    private class FakeRoomRepository : RoomRepository {
        private val rooms = mutableListOf<Room>()
        override suspend fun getByProject(projectId: String) = rooms.filter { it.projectId == projectId }
        override suspend fun getById(id: String) = rooms.find { it.id == id }
        override suspend fun insert(room: Room) { rooms.add(room) }
        override suspend fun delete(id: String) { rooms.removeAll { it.id == id } }
    }

    private class FakeSurfaceRepository : SurfaceRepository {
        val surfaces = mutableListOf<Surface>()
        val stgs = mutableListOf<SurfaceTileGroup>()

        override suspend fun getByRoom(roomId: String) = surfaces.filter { it.roomId == roomId }
        override suspend fun getById(id: String) = surfaces.find { it.id == id }
        override suspend fun insert(surface: Surface) { surfaces.add(surface) }
        override suspend fun delete(id: String) { surfaces.removeAll { it.id == id } }

        override suspend fun getSTGsBySurface(surfaceId: String) = stgs.filter { it.surfaceId == surfaceId }
        override suspend fun getSTGById(id: String) = stgs.find { it.id == id }
        override suspend fun insertSTG(stg: SurfaceTileGroup) { stgs.add(stg) }
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
        override suspend fun insert(tileGroup: TileGroup) { tileGroups.add(tileGroup) }
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

    private lateinit var surfaceRepo: FakeSurfaceRepository
    private lateinit var tileGroupRepo: FakeTileGroupRepository
    private lateinit var layoutRepo: FakeLayoutResultRepository
    private lateinit var vm: RoomEditorViewModel

    private fun setUp() {
        surfaceRepo = FakeSurfaceRepository()
        tileGroupRepo = FakeTileGroupRepository()
        layoutRepo = FakeLayoutResultRepository()
        vm = RoomEditorViewModel(
            roomRepo = FakeRoomRepository(),
            surfaceRepo = surfaceRepo,
            tileGroupRepo = tileGroupRepo,
            layoutRepo = layoutRepo,
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
    ): SurfaceTileGroup = SurfaceTileGroup(
        id = id, surfaceId = surfaceId, tileGroupId = tileGroupId,
        region = RegionRect(0.0, 0.0, 2000.0, 1200.0),
        offsetX = offsetX, offsetY = offsetY,
    )

    // ── Tests ──

    @Test
    fun selectSurfaceUpdatesSelectedId() {
        // given: fresh VM
        setUp()
        assertNull(vm.selectedSurfaceId.value)

        // when: select a surface
        vm.selectSurface("s1")

        // want: selectedSurfaceId updated
        assertEquals("s1", vm.selectedSurfaceId.value)
    }

    @Test
    fun rotateViewUpdatesViewAngle() {
        // given: initial angle 0
        setUp()
        assertEquals(0, vm.viewAngle.value)

        // when: rotate ±90
        vm.rotateView(90)
        assertEquals(90, vm.viewAngle.value)

        vm.rotateView(-90)
        assertEquals(0, vm.viewAngle.value)

        // want: wraps at 360
        vm.rotateView(360)
        assertEquals(0, vm.viewAngle.value)
    }

    @Test
    fun toggleLockAddsAndRemovesSurfaceIds() {
        // given: empty lock set
        setUp()
        assertTrue(vm.lockedSurfaceIds.value.isEmpty())

        // when: toggle lock on s1
        vm.toggleLock("s1")
        assertTrue("s1" in vm.lockedSurfaceIds.value)

        // when: toggle lock on s2
        vm.toggleLock("s2")
        assertEquals(2, vm.lockedSurfaceIds.value.size)

        // when: toggle lock off on s1
        vm.toggleLock("s1")
        assertFalse("s1" in vm.lockedSurfaceIds.value)
        assertTrue("s2" in vm.lockedSurfaceIds.value)
    }

    @Test
    fun dragStartSnapshotsOffsets() = runBlocking {
        // given: 2 surfaces, s1 selected, s2 locked, each with STGs at known offsets
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

        // when: drag starts
        vm.onDragStart()

        // want: undoBuffer has offsets for both surfaces' STGs
        val buffer = vm.undoBuffer.value
        assertNotNull(buffer)
        assertEquals(2, buffer.size)
        assertEquals(Pair(10.0, 20.0), buffer["stg1"])
        assertEquals(Pair(30.0, 40.0), buffer["stg2"])
    }

    @Test
    fun dragStartWithoutSelectionReturnsEarly() = runBlocking {
        // given: no surface selected
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")

        // when: drag starts with no selection
        vm.onDragStart()

        // want: undoBuffer stays null (early return)
        assertNull(vm.undoBuffer.value)
    }

    @Test
    fun dragEndAppliesOffsetToSelectedSurface() = runBlocking {
        // given: s1 with STG at offset (10, 20)
        setUp()
        val s1 = createWallSurface(id = "s1", width = 2000.0, height = 1200.0)
        val tg = createTileGroup("tg1", 300.0, 200.0)
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", offsetX = 10.0, offsetY = 20.0))
        tileGroupRepo.tileGroups.add(tg)
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // when: drag ends with (5, -3)
        vm.onDragEnd(5.0, -3.0)

        // want: STG offset updated
        val updated = surfaceRepo.getSTGById("stg1")
        assertNotNull(updated)
        assertEquals(15.0, updated.offsetX, 0.01)
        assertEquals(17.0, updated.offsetY, 0.01)
    }

    @Test
    fun lockPropagationParallelWallsPassBothAxes() = runBlocking {
        // given: s1 (rotation=0°) and s2 (rotation=180°) — parallel walls
        //        s1 selected, s2 locked
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

        // when: drag (10, 5)
        vm.onDragEnd(10.0, 5.0)

        // want: s2 also gets (10, 5) because walls are parallel
        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(10.0, stg2.offsetX, 0.01, "Parallel walls: dx should propagate")
        assertEquals(5.0, stg2.offsetY, 0.01, "Parallel walls: dy should propagate")
    }

    @Test
    fun lockPropagationPerpendicularWallsPassVerticalOnly() = runBlocking {
        // given: s1 (rotation=0°) and s2 (rotation=90°) — perpendicular walls
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

        // when: drag (10, 5)
        vm.onDragEnd(10.0, 5.0)

        // want: s2 gets dy=5 only (vertical), dx=0 (horizontal blocked for perpendicular walls)
        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(0.0, stg2.offsetX, 0.01, "Perpendicular walls: dx should NOT propagate")
        assertEquals(5.0, stg2.offsetY, 0.01, "Perpendicular walls: dy should propagate")
    }

    @Test
    fun lockPropagationFloorToFloorBothAxes() = runBlocking {
        // given: 2 floors, s1 selected, s2 locked
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

        // when: drag (8, 4)
        vm.onDragEnd(8.0, 4.0)

        // want: both axes propagate floor→floor
        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(8.0, stg2.offsetX, 0.01, "Floor→floor: dx should propagate")
        assertEquals(4.0, stg2.offsetY, 0.01, "Floor→floor: dy should propagate")
    }

    @Test
    fun lockPropagationWallToFloorNoPropagation() = runBlocking {
        // given: s1 = wall (selected), s2 = floor (locked)
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

        // when: drag (10, 5)
        vm.onDragEnd(10.0, 5.0)

        // want: floor STG unchanged (wall→floor = no propagation)
        val stg2 = surfaceRepo.getSTGById("stg2")
        assertNotNull(stg2)
        assertEquals(0.0, stg2.offsetX, 0.01, "Wall→floor: dx should NOT propagate")
        assertEquals(0.0, stg2.offsetY, 0.01, "Wall→floor: dy should NOT propagate")
    }

    @Test
    fun undoConsumesBuffer() = runBlocking {
        // given: drag started with selected surface
        setUp()
        val s1 = createWallSurface("s1")
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", offsetX = 5.0, offsetY = 10.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1"))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")
        vm.onDragStart()
        assertNotNull(vm.undoBuffer.value)

        // when: undo is called
        vm.undo()

        // want: undoBuffer cleared
        assertNull(vm.undoBuffer.value)
    }

    @Test
    fun undoRestoreRevertsOffsets() = runBlocking {
        // given: s1 with STG at current offset (50, 30), priors from undo buffer
        setUp()
        val s1 = createWallSurface("s1")
        surfaceRepo.surfaces.add(s1)
        surfaceRepo.stgs.add(createSTG("stg1", "s1", offsetX = 50.0, offsetY = 30.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1"))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        val priors = mapOf("stg1" to Pair(10.0, 20.0))

        // when: undoRestore with prior offsets
        vm.undoRestore(priors)

        // want: STG offset reverted to prior values
        val restored = surfaceRepo.getSTGById("stg1")
        assertNotNull(restored)
        assertEquals(10.0, restored.offsetX, 0.01)
        assertEquals(20.0, restored.offsetY, 0.01)
    }

    @Test
    fun computeLayoutGeneratesTiles() = runBlocking {
        // given: surface with STG covering full region, 300x200 tile, 3mm grout
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

        // when: compute layout
        vm.computeLayout("s1")

        // want: layout result saved with tiles
        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Layout result should be saved")
        assertTrue(result.tiles.isNotEmpty(), "Should have placed tiles")
        assertFalse(result.stale, "Should be marked fresh")
    }

    @Test
    fun computeLayoutWithSelectedSurfaceUpdatesCurrentTiles() = runBlocking {
        // given: surface selected, then layout computed
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

        // when: compute layout
        vm.computeLayout("s1")

        // want: currentTiles populated
        assertTrue(vm.currentTiles.value.isNotEmpty(), "currentTiles should be updated for selected surface")
    }

    @Test
    fun hitTestReturnsSurfaceIdWhenTapInsideProjectedPolygon() = runBlocking {
        // given: 2 wall surfaces visible in isometric view
        setUp()
        val s1 = Surface(
            id = "s1", roomId = "r1", type = SurfaceType.WALL,
            width = 2000.0, height = 1200.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val s2 = Surface(
            id = "s2", roomId = "r1", type = SurfaceType.WALL,
            width = 2000.0, height = 1200.0,
            position = SurfacePosition(0.0, 0.0, 1000.0, 90.0),
        )
        surfaceRepo.surfaces.addAll(listOf(s1, s2))
        vm.loadSurfaces("r1")

        // want: tap near center of canvas (where front wall s1 projects) hits s1
        //   At viewAngle=0, origin(400,300): s1 projects near center
        val canvasW = 800.0
        val canvasH = 600.0
        val centerX = canvasW / 2.0 // 400
        val centerY = canvasH * 0.6 // 360 — near the isometric origin

        val hitId = vm.hitTest(centerX, centerY, canvasW, canvasH)

        // hitTest should return a surface id (not null) for tap near origin
        assertNotNull(hitId, "Tap near isometric origin should hit a surface")
        // trailing Unit: assertNotNull returns the value (String), JUnit requires void test methods
        Unit
    }

    @Test
    fun hitTestReturnsNullWhenNoSurfaceHit() = runBlocking {
        // given: 1 surface
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1"))
        vm.loadSurfaces("r1")

        // want: tap far outside any projected surface returns null
        val hitId = vm.hitTest(-9999.0, -9999.0, 800.0, 600.0)

        assertNull(hitId, "Tap far outside should hit nothing")
    }

    @Test
    fun loadSurfacesPopulatesSurfacesStateFlow() = runBlocking {
        // given: room with 2 surfaces
        setUp()
        surfaceRepo.surfaces.addAll(listOf(
            createWallSurface("s1"),
            createWallSurface("s2"),
        ))

        // when: load surfaces for room
        vm.loadSurfaces("r1")

        // want: StateFlow has both surfaces
        assertEquals(2, vm.surfaces.value.size)
    }

    @Test
    fun rotateViewHandlesNegativeAngles() {
        setUp()

        // when: rotate by -90 from 0
        vm.rotateView(-90)

        // want: properly wrapped to 270 (0..359 range via floor-mod)
        assertEquals(270, vm.viewAngle.value)
    }
}
