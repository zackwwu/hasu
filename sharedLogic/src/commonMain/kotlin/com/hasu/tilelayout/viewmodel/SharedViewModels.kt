package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.cutlist.CutListGenerator
import com.hasu.tilelayout.db.LayoutResultRepository
import com.hasu.tilelayout.db.ProjectRepository
import com.hasu.tilelayout.db.RoomRepository
import com.hasu.tilelayout.db.SurfaceRepository
import com.hasu.tilelayout.db.TileGroupRepository
import com.hasu.tilelayout.engine.IsometricProjection
import com.hasu.tilelayout.engine.LayoutEngine
import com.hasu.tilelayout.models.CutEntry
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.Project
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ──────────────────────────────────────────────
// ProjectListViewModel
// ──────────────────────────────────────────────

class ProjectListViewModel(private val repo: ProjectRepository) {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects

    suspend fun load() {
        _projects.value = repo.getAll()
    }

    suspend fun create(name: String): Project {
        val p = Project(name = name)
        repo.insert(p)
        load()
        return p
    }

    suspend fun delete(id: String) {
        repo.delete(id)
        load()
    }
}

// ──────────────────────────────────────────────
// RoomEditorViewModel
// ──────────────────────────────────────────────

class RoomEditorViewModel(
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository,
    private val tileGroupRepo: TileGroupRepository,
    private val layoutRepo: LayoutResultRepository,
    private val scope: CoroutineScope,
    private val debounceMs: Long = LAYOUT_DEBOUNCE_MS,
) {
    companion object {
        const val LAYOUT_DEBOUNCE_MS = 100L
    }

    // -- StateFlows --

    private val _surfaces = MutableStateFlow<List<Surface>>(emptyList())
    val surfaces: StateFlow<List<Surface>> = _surfaces

    private val _selectedSurfaceId = MutableStateFlow<String?>(null)
    val selectedSurfaceId: StateFlow<String?> = _selectedSurfaceId

    private val _viewAngle = MutableStateFlow(0)
    val viewAngle: StateFlow<Int> = _viewAngle

    private val _lockedSurfaceIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedSurfaceIds: StateFlow<Set<String>> = _lockedSurfaceIds

    private val _undoBuffer = MutableStateFlow<Map<String, Pair<Double, Double>>?>(null)
    val undoBuffer: StateFlow<Map<String, Pair<Double, Double>>?> = _undoBuffer

    private var dragLockedIds: Set<String> = emptySet()

    private val _currentTiles = MutableStateFlow<List<PlacedTile>>(emptyList())
    val currentTiles: StateFlow<List<PlacedTile>> = _currentTiles

    private val _cutEntries = MutableStateFlow<List<CutEntry>>(emptyList())
    val cutEntries: StateFlow<List<CutEntry>> = _cutEntries

    private val pendingLayoutJobs = mutableMapOf<String, Job>()

    // -- Public API --

    suspend fun loadSurfaces(roomId: String) {
        _surfaces.value = surfaceRepo.getByRoom(roomId)
        _selectedSurfaceId.value?.let { loadLayoutForSurface(it) }
        recomputeCutEntries()
    }

    suspend fun selectSurface(id: String?) {
        _selectedSurfaceId.value = id
        if (id != null) {
            loadLayoutForSurface(id)
        } else {
            _currentTiles.value = emptyList()
        }
    }

    fun rotateView(delta: Int) {
        _viewAngle.value = ((_viewAngle.value + delta) % 360 + 360) % 360
    }

    fun toggleLock(surfaceId: String) {
        val current = _lockedSurfaceIds.value
        _lockedSurfaceIds.value = if (surfaceId in current) {
            current - surfaceId
        } else {
            current + surfaceId
        }
    }

    // -- Drag / Offset --

    suspend fun onDragStart() {
        val selectedId = _selectedSurfaceId.value ?: return
        dragLockedIds = _lockedSurfaceIds.value
        val affectedIds = setOf(selectedId) + dragLockedIds
        val priors = mutableMapOf<String, Pair<Double, Double>>()

        for (sid in affectedIds) {
            val stgs = surfaceRepo.getSTGsBySurface(sid)
            for (stg in stgs) {
                priors[stg.id] = Pair(stg.offsetX, stg.offsetY)
            }
        }

        _undoBuffer.value = priors
    }

    suspend fun onDragEnd(dx: Double, dy: Double) {
        val selectedId = _selectedSurfaceId.value ?: return
        val selected = _surfaces.value.find { it.id == selectedId } ?: return

        val affectedIds = mutableSetOf(selectedId)
        applyOffset(selectedId, dx, dy)

        for (lockedId in dragLockedIds) {
            val locked = _surfaces.value.find { it.id == lockedId } ?: continue
            val (propDx, propDy) = propagateDelta(selected, locked, dx, dy)
            if (propDx != 0.0 || propDy != 0.0) {
                affectedIds.add(lockedId)
                applyOffset(lockedId, propDx, propDy)
            }
        }
        dragLockedIds = emptySet()

        // onDragEnd is the final drag position — bypass the debounce and compute immediately
        // so platform wrappers can refresh reactively without a fixed-delay hack.
        for (surfaceId in affectedIds) {
            cancelPendingLayout(surfaceId)
            computeLayout(surfaceId)
        }
    }

    private fun normalizedRotationBucket(rotation: Double): Int {
        val intRot = rotation.toInt()
        return ((intRot % 180) + 180) % 180
    }

    private fun propagateDelta(
        source: Surface,
        target: Surface,
        dx: Double,
        dy: Double,
    ): Pair<Double, Double> {
        if (source.type == SurfaceType.FLOOR && target.type == SurfaceType.FLOOR) {
            return Pair(dx, dy)
        }
        if (source.type == SurfaceType.WALL && target.type == SurfaceType.WALL) {
            val parallel = normalizedRotationBucket(source.position.rotation) ==
                normalizedRotationBucket(target.position.rotation)
            return Pair(if (parallel) dx else 0.0, dy)
        }
        return Pair(0.0, 0.0)
    }

    private suspend fun applyOffset(surfaceId: String, dx: Double, dy: Double) {
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)
        for (stg in stgs) {
            surfaceRepo.updateSTG(
                stg.copy(
                    offsetX = stg.offsetX + dx,
                    offsetY = stg.offsetY + dy,
                )
            )
        }
        scheduleLayoutCompute(surfaceId)
    }

    // -- Debounced Layout Scheduling --

    private fun scheduleLayoutCompute(surfaceId: String) {
        pendingLayoutJobs[surfaceId]?.cancel()
        pendingLayoutJobs[surfaceId] = scope.launch {
            delay(debounceMs)
            computeLayout(surfaceId)
            pendingLayoutJobs.remove(surfaceId)
        }
    }

    fun cancelPendingLayouts() {
        for ((_, job) in pendingLayoutJobs) {
            job.cancel()
        }
        pendingLayoutJobs.clear()
    }

    // -- Undo --

    suspend fun undo() {
        val priors = _undoBuffer.value ?: return
        _undoBuffer.value = null

        val affectedSurfaceIds = mutableSetOf<String>()
        for ((stgId, offset) in priors) {
            val stg = surfaceRepo.getSTGById(stgId) ?: continue
            affectedSurfaceIds.add(stg.surfaceId)
            surfaceRepo.updateSTG(
                stg.copy(offsetX = offset.first, offsetY = offset.second)
            )
        }

        for (surfaceId in affectedSurfaceIds) {
            cancelPendingLayout(surfaceId)
            computeLayout(surfaceId)
        }
    }

    // -- Reset --

    suspend fun resetToAuto(surfaceId: String) {
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)
        for (stg in stgs) {
            surfaceRepo.updateSTG(stg.copy(offsetX = 0.0, offsetY = 0.0))
        }
        cancelPendingLayout(surfaceId)
        computeLayout(surfaceId)
        if (surfaceId == _selectedSurfaceId.value) {
            loadLayoutForSurface(surfaceId)
        }
    }

    suspend fun snapToCenter(surfaceId: String) {
        val surface = surfaceRepo.getById(surfaceId) ?: return
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)
        for (stg in stgs) {
            val tg = tileGroupRepo.getById(stg.tileGroupId) ?: continue
            val tileW = tg.tileWidth + surface.groutWidth
            val tileH = tg.tileHeight + surface.groutWidth
            val centeredX = (stg.region.width % tileW) / 2.0
            val centeredY = (stg.region.height % tileH) / 2.0
            surfaceRepo.updateSTG(stg.copy(offsetX = centeredX, offsetY = centeredY))
        }
        cancelPendingLayout(surfaceId)
        computeLayout(surfaceId)
        if (surfaceId == _selectedSurfaceId.value) {
            loadLayoutForSurface(surfaceId)
        }
    }

    private fun cancelPendingLayout(surfaceId: String) {
        pendingLayoutJobs.remove(surfaceId)?.cancel()
    }

    // -- Layout computation --

    suspend fun computeLayout(surfaceId: String) {
        val surface = surfaceRepo.getById(surfaceId) ?: return
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)

        val tiles = withContext(Dispatchers.Default) {
            stgs.flatMap { stg ->
                val tg = tileGroupRepo.getById(stg.tileGroupId) ?: return@flatMap emptyList<PlacedTile>()
                LayoutEngine.compute(
                    region = stg.region,
                    tileGroup = tg,
                    groutWidth = surface.groutWidth,
                    pattern = stg.pattern,
                    offsetX = stg.offsetX,
                    offsetY = stg.offsetY,
                )
            }
        }

        val result = LayoutResult(surfaceId = surfaceId, tiles = tiles, stale = false)
        layoutRepo.save(result)

        if (surfaceId == _selectedSurfaceId.value) {
            _currentTiles.value = tiles
        }
        recomputeCutEntries()
    }

    private suspend fun loadLayoutForSurface(surfaceId: String) {
        val result = layoutRepo.getBySurface(surfaceId)
        _currentTiles.value = result?.tiles ?: emptyList()
    }

    // -- Cut list --

    /**
     * Regenerates the room-wide cut list from the latest layout results for all
     * loaded surfaces. Called after every [computeLayout] so the cut list tab
     * stays fresh without any per-platform refresh logic.
     */
    private suspend fun recomputeCutEntries() {
        val surfaces = _surfaces.value
        if (surfaces.isEmpty()) {
            _cutEntries.value = emptyList()
            return
        }

        val resultsBySurface = mutableMapOf<String, LayoutResult>()
        val surfaceNames = mutableMapOf<String, String>()
        val tileGroupNames = mutableMapOf<String, String>()

        for (surface in surfaces) {
            surfaceNames[surface.id] =
                "${if (surface.type == SurfaceType.WALL) "Wall" else "Floor"} ${surface.width.toInt()}×${surface.height.toInt()}"
            val result = layoutRepo.getBySurface(surface.id) ?: continue
            resultsBySurface[surface.id] = result
            for (tile in result.tiles) {
                if (!tileGroupNames.containsKey(tile.tileGroupId)) {
                    tileGroupNames[tile.tileGroupId] =
                        tileGroupRepo.getById(tile.tileGroupId)?.name ?: tile.tileGroupId
                }
            }
        }

        _cutEntries.value = CutListGenerator.generate(resultsBySurface, surfaceNames, tileGroupNames)
    }

    // -- 3D Hit Testing --

    fun hitTest(
        tapX: Double,
        tapY: Double,
        canvasWidth: Double,
        canvasHeight: Double,
    ): String? {
        val fit = IsometricProjection.fitViewport(
            _surfaces.value, _viewAngle.value, canvasWidth, canvasHeight,
        )

        val ordered = IsometricProjection.orderSurfaces(_surfaces.value, _viewAngle.value)
        for (surface in ordered.reversed()) {
            val corners = IsometricProjection.projectSurfaceCorners(
                surface, _viewAngle.value, fit.originX, fit.originY, fit.scale,
            )
            if (IsometricProjection.pointInPolygon(tapX, tapY, corners)) {
                return surface.id
            }
        }
        return null
    }
}

// ──────────────────────────────────────────────
// Platform Texture Loader (expect declaration)
// ──────────────────────────────────────────────

expect class TextureLoader() {
    suspend fun loadTexture(tileGroup: TileGroup): Any?
    fun evict(tileGroupId: String)
    fun clear()
}
