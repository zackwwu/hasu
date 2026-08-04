package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.db.LayoutResultRepository
import com.hasu.tilelayout.db.ProjectRepository
import com.hasu.tilelayout.db.RoomRepository
import com.hasu.tilelayout.db.SurfaceRepository
import com.hasu.tilelayout.db.TileGroupRepository
import com.hasu.tilelayout.engine.IsometricProjection
import com.hasu.tilelayout.engine.LayoutEngine
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.Project
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    private val _currentTiles = MutableStateFlow<List<PlacedTile>>(emptyList())
    val currentTiles: StateFlow<List<PlacedTile>> = _currentTiles

    private val pendingLayoutJobs = mutableMapOf<String, Job>()

    // -- Public API --

    suspend fun loadSurfaces(roomId: String) {
        _surfaces.value = surfaceRepo.getByRoom(roomId)
        _selectedSurfaceId.value?.let { loadLayoutForSurface(it) }
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
        val affectedIds = setOf(selectedId) + _lockedSurfaceIds.value
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

        applyOffset(selectedId, dx, dy)

        for (lockedId in _lockedSurfaceIds.value) {
            val locked = _surfaces.value.find { it.id == lockedId } ?: continue
            val (propDx, propDy) = propagateDelta(selected, locked, dx, dy)
            if (propDx != 0.0 || propDy != 0.0) {
                applyOffset(lockedId, propDx, propDy)
            }
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

    fun flushPendingLayouts() {
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

        val tiles = stgs.flatMap { stg ->
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

        val result = LayoutResult(surfaceId = surfaceId, tiles = tiles, stale = false)
        layoutRepo.save(result)

        if (surfaceId == _selectedSurfaceId.value) {
            _currentTiles.value = tiles
        }
    }

    private suspend fun loadLayoutForSurface(surfaceId: String) {
        val result = layoutRepo.getBySurface(surfaceId)
        _currentTiles.value = result?.tiles ?: emptyList()
    }

    // -- 3D Hit Testing --

    fun hitTest(
        tapX: Double,
        tapY: Double,
        canvasWidth: Double,
        canvasHeight: Double,
    ): String? {
        val originX = canvasWidth / 2.0
        val originY = canvasHeight * 0.6

        val ordered = IsometricProjection.orderSurfaces(_surfaces.value, _viewAngle.value)
        for (surface in ordered.reversed()) {
            val corners = IsometricProjection.projectSurfaceCorners(
                surface, _viewAngle.value, originX, originY,
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
