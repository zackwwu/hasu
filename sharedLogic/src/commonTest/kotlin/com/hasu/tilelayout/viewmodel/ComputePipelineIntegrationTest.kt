package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.cutlist.CutListGenerator
import com.hasu.tilelayout.db.LayoutResultRepository
import com.hasu.tilelayout.db.ProjectRepository
import com.hasu.tilelayout.db.RoomRepository
import com.hasu.tilelayout.db.SurfaceRepository
import com.hasu.tilelayout.db.TileGroupRepository
import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.Project
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfacePosition
import com.hasu.tilelayout.models.SurfaceTileGroup
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * End-to-end integration tests for the compute pipeline: repository fakes in,
 * persisted layout results / cut entries out, exercised through the
 * [RoomEditorViewModel] public API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComputePipelineIntegrationTest {

    // ── Fake implementations (in-memory, backed by MutableList) ──

    private class FakeProjectRepository : ProjectRepository {
        private val projects = mutableListOf<Project>()
        override suspend fun getAll() = projects.toList()
        override suspend fun getById(id: String) = projects.find { it.id == id }
        override suspend fun insert(project: Project) {
            require(projects.none { it.id == project.id }) { "Duplicate project id: ${project.id}" }
            projects.add(project)
        }
        override suspend fun delete(id: String) { projects.removeAll { it.id == id } }
    }

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

    private lateinit var projectRepo: FakeProjectRepository
    private lateinit var roomRepo: FakeRoomRepository
    private lateinit var surfaceRepo: FakeSurfaceRepository
    private lateinit var tileGroupRepo: FakeTileGroupRepository
    private lateinit var layoutRepo: FakeLayoutResultRepository
    private lateinit var vm: RoomEditorViewModel

    private fun TestScope.setUp(debounceMs: Long = 0L) {
        projectRepo = FakeProjectRepository()
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

    // ── Test 1: Full Pipeline — Create to Compute ──

    @Test
    fun fullPipelineCreateToComputeProducesTilesWithCutsAndSavesResult() = runTest {
        setUp()

        // GIVEN a project, room, surface, tile group, and surface-tile-group assignment
        val projectListVm = ProjectListViewModel(projectRepo)
        val project = projectListVm.create("Kitchen Reno")
        assertEquals(1, projectListVm.projects.value.size, "Project should be persisted via the repository")
        roomRepo.insert(
            Room(projectId = project.id, name = "Kitchen", width = 4000.0, depth = 3000.0, height = 2400.0)
        )

        surfaceRepo.surfaces.add(createWallSurface("s1", width = 903.0, height = 603.0))
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1", 300.0, 200.0))
        vm.loadSurfaces("r1")

        // WHEN computeLayout is called
        vm.computeLayout("s1")

        // THEN tiles are produced (non-empty list)
        val result = layoutRepo.getBySurface("s1")
        assertNotNull(result, "Layout result should be saved to the repository")
        assertTrue(result.tiles.isNotEmpty(), "computeLayout should produce tiles")

        // AND some tiles may be marked as cuts (at edges)
        assertTrue(result.tiles.any { it.isCut }, "903x603 region with 300x200 tiles should produce edge cuts")
        assertFalse(result.stale, "Freshly computed result should not be stale")

        // AND the layout result is saved to the repository
        assertTrue(
            layoutRepo.getBySurface("s1")!!.tiles.size == result.tiles.size,
            "Saved result should be readable back from the repository",
        )
        assertTrue(vm.cutEntries.value.isNotEmpty(), "Cut list should be regenerated after compute")
    }

    // ── Test 2: Drag Offset Changes Layout ──

    @Test
    fun dragOffsetChangesSTGOffsetsAndProducesDifferentLayout() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1", width = 903.0, height = 603.0))
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1", 300.0, 200.0))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // GIVEN a surface with a computed layout
        vm.computeLayout("s1")
        val tilesBefore = layoutRepo.getBySurface("s1")!!.tiles
        assertTrue(tilesBefore.isNotEmpty())

        // WHEN onDragEnd is called with dx=50, dy=0
        vm.onDragStart()
        vm.onDragEnd(50.0, 0.0)
        advanceUntilIdle()

        // THEN the STG offsets are updated (offsetX changed by 50)
        val stg = surfaceRepo.getSTGById("stg1")!!
        assertEquals(50.0, stg.offsetX, 0.01, "offsetX should increase by 50")
        assertEquals(0.0, stg.offsetY, 0.01, "offsetY should be unchanged")

        // AND computeLayout produces a different tile arrangement
        val tilesAfter = layoutRepo.getBySurface("s1")!!.tiles
        assertNotEquals(
            tilesBefore.map { it.x },
            tilesAfter.map { it.x },
            "A 50px horizontal offset should shift tile positions",
        )
        assertTrue(vm.currentTiles.value.isNotEmpty(), "Selected surface tiles should be refreshed")
    }

    // ── Test 3: Undo Restores Offsets ──

    @Test
    fun undoRestoresOffsetsToPreDragValuesAndRecomputesLayout() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1", width = 903.0, height = 603.0))
        surfaceRepo.stgs.add(
            createSTG("stg1", "s1", "tg1", offsetX = 5.0, offsetY = 10.0, regionW = 903.0, regionH = 603.0)
        )
        tileGroupRepo.tileGroups.add(createTileGroup("tg1", 300.0, 200.0))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // Baseline arrangement at the pre-drag offsets
        vm.computeLayout("s1")
        val baselineXs = layoutRepo.getBySurface("s1")!!.tiles.map { it.x }

        // GIVEN a surface that had onDragStart() then onDragEnd(50, 0)
        vm.onDragStart()
        vm.onDragEnd(50.0, 0.0)
        advanceUntilIdle()
        assertEquals(55.0, surfaceRepo.getSTGById("stg1")!!.offsetX, 0.01, "Drag should have moved the offset")

        // WHEN undo() is called
        vm.undo()
        advanceUntilIdle()

        // THEN the STG offsets are restored to pre-drag values
        val restored = surfaceRepo.getSTGById("stg1")!!
        assertEquals(5.0, restored.offsetX, 0.01, "offsetX should be restored to pre-drag value")
        assertEquals(10.0, restored.offsetY, 0.01, "offsetY should be restored to pre-drag value")
        assertNull(vm.undoBuffer.value, "Undo buffer should be cleared after undo")

        // AND the layout is recomputed to match the pre-drag arrangement
        val recomputed = layoutRepo.getBySurface("s1")
        assertNotNull(recomputed, "Undo should recompute the layout")
        assertEquals(baselineXs, recomputed.tiles.map { it.x }, "Recomputed layout should match pre-drag arrangement")
    }

    // ── Test 4: Cut List Generation After Compute ──

    @Test
    fun cutListGeneratorGroupsCutsByTileGroupAndCutEdges() = runTest {
        setUp()
        surfaceRepo.surfaces.add(createWallSurface("s1", width = 903.0, height = 603.0))
        surfaceRepo.stgs.add(createSTG("stg1", "s1", "tg1", regionW = 903.0, regionH = 603.0))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1", 300.0, 200.0))
        vm.loadSurfaces("r1")

        // GIVEN a surface with a computed layout containing cut tiles
        vm.computeLayout("s1")
        val result = layoutRepo.getBySurface("s1")!!
        val cutTiles = result.tiles.filter { it.isCut }
        assertTrue(cutTiles.isNotEmpty(), "903x603 region with 300x200 tiles must produce cut tiles")

        // WHEN CutListGenerator.generate() is called with the results
        val entries = CutListGenerator.generate(
            resultsBySurface = mapOf("s1" to result),
            surfaceNames = mapOf("s1" to "Wall 903x603"),
            tileGroupNames = mapOf("tg1" to "Test Tile"),
        )

        // THEN cut entries are grouped correctly by tile group + cut edges
        assertTrue(entries.isNotEmpty(), "Generator should produce cut entries")
        assertTrue(
            entries.all { it.tileGroupId == "tg1" && it.tileGroupName == "Test Tile" },
            "All entries should reference the single tile group",
        )
        assertTrue(entries.all { it.locations.isNotEmpty() }, "Every entry should list its surface location")
        assertEquals("s1", entries.first().locations.first().surfaceId)

        // AND totalCount matches the number of cut tiles
        assertEquals(
            cutTiles.size,
            entries.sumOf { it.totalCount },
            "Sum of entry counts must equal the number of cut tiles",
        )

        // Grouping: no two entries share the same (tileGroup, size, cutEdges) combo
        val keys = entries.map { "${it.tileGroupId}|${it.width}|${it.height}|${it.cutEdgesKey}" }
        assertEquals(keys.size, keys.distinct().size, "Each (tile group, size, cut edges) combo appears once")

        // All four edge directions are represented
        assertTrue(keys.any { "LEFT" in it }, "Left-edge cut entries present")
        assertTrue(keys.any { "RIGHT" in it }, "Right-edge cut entries present")
        assertTrue(keys.any { "TOP" in it }, "Top-edge cut entries present")
        assertTrue(keys.any { "BOTTOM" in it }, "Bottom-edge cut entries present")
    }

    // ── Test 5: Lock Propagation — Parallel Walls ──

    @Test
    fun lockPropagationParallelWallsPropagatesHorizontalAndVertical() = runTest {
        setUp()

        // GIVEN two parallel wall surfaces (same rotation bucket: 0° and 180°)
        surfaceRepo.surfaces.addAll(listOf(
            createWallSurface("s1", rotation = 0.0),
            createWallSurface("s2", rotation = 180.0),
        ))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1"),
            createSTG("stg2", "s2", "tg1"),
        ))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1", 300.0, 200.0))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // AND surface B is locked
        vm.toggleLock("s2")

        // WHEN surface A is dragged with dx=100, dy=50
        vm.onDragStart()
        vm.onDragEnd(100.0, 50.0)
        advanceUntilIdle()

        // THEN surface B's STGs get dx=100 (parallel axis propagates horizontal)
        val stgB = surfaceRepo.getSTGById("stg2")!!
        assertEquals(100.0, stgB.offsetX, 0.01, "Parallel walls: dx should propagate horizontally")

        // AND surface B's STGs get dy=50 (vertical always propagates between walls)
        assertEquals(50.0, stgB.offsetY, 0.01, "Walls: dy should always propagate vertically")

        // Sanity: the dragged surface itself moved
        val stgA = surfaceRepo.getSTGById("stg1")!!
        assertEquals(100.0, stgA.offsetX, 0.01)
        assertEquals(50.0, stgA.offsetY, 0.01)
    }

    // ── Test 6: Lock Propagation — No Wall↔Floor ──

    @Test
    fun lockPropagationWallToFloorDoesNotPropagate() = runTest {
        setUp()

        // GIVEN a wall surface and a floor surface
        surfaceRepo.surfaces.addAll(listOf(
            createWallSurface("s1", rotation = 0.0),
            createFloorSurface("s2", width = 3000.0, depth = 2000.0),
        ))
        surfaceRepo.stgs.addAll(listOf(
            createSTG("stg1", "s1", "tg1"),
            createSTG("stg2", "s2", "tg1"),
        ))
        tileGroupRepo.tileGroups.add(createTileGroup("tg1", 300.0, 200.0))
        vm.loadSurfaces("r1")
        vm.selectSurface("s1")

        // AND the floor is locked
        vm.toggleLock("s2")

        // WHEN the wall is dragged with dx=100, dy=50
        vm.onDragStart()
        vm.onDragEnd(100.0, 50.0)
        advanceUntilIdle()

        // THEN the floor's STGs are NOT modified (wall↔floor = no propagation)
        val floorStg = surfaceRepo.getSTGById("stg2")!!
        assertEquals(0.0, floorStg.offsetX, 0.01, "Wall→floor: dx must NOT propagate")
        assertEquals(0.0, floorStg.offsetY, 0.01, "Wall→floor: dy must NOT propagate")

        // Sanity: the dragged wall itself moved
        assertEquals(100.0, surfaceRepo.getSTGById("stg1")!!.offsetX, 0.01)
        assertEquals(50.0, surfaceRepo.getSTGById("stg1")!!.offsetY, 0.01)
    }
}
