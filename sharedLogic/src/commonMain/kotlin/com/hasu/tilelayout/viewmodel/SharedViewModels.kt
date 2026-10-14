package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.cutlist.CutListGenerator
import com.hasu.tilelayout.db.LayoutResultRepository
import com.hasu.tilelayout.db.ProjectRepository
import com.hasu.tilelayout.db.RoomRepository
import com.hasu.tilelayout.db.SurfaceRepository
import com.hasu.tilelayout.db.TileGroupRepository
import com.hasu.tilelayout.engine.DoorGeometry
import com.hasu.tilelayout.engine.LayoutEngine
import com.hasu.tilelayout.models.CutEntry
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.Project
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.scene.OrbitCamera
import com.hasu.tilelayout.scene.PickTarget
import com.hasu.tilelayout.scene.RoomScene
import com.hasu.tilelayout.scene.RoomSceneBuilder
import com.hasu.tilelayout.scene.ScenePicker
import com.hasu.tilelayout.scene.SurfaceSceneInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    // -- StateFlows --

    private val _surfaces = MutableStateFlow<List<Surface>>(emptyList())
    val surfaces: StateFlow<List<Surface>> = _surfaces

    private val _selectedSurfaceId = MutableStateFlow<String?>(null)
    val selectedSurfaceId: StateFlow<String?> = _selectedSurfaceId

    // -- 3D preview state (transient, not persisted) --

    private val _previewCamera = MutableStateFlow(OrbitCamera())
    val previewCamera: StateFlow<OrbitCamera> = _previewCamera

    /** Walls hidden in the 3D preview so the user can see inside the room. */
    private val _hiddenSurfaceIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenSurfaceIds: StateFlow<Set<String>> = _hiddenSurfaceIds

    /** Meshes for every loaded surface, rebuilt per surface whenever its layout changes. */
    private val _previewScene = MutableStateFlow(RoomScene.EMPTY)
    val previewScene: StateFlow<RoomScene> = _previewScene

    fun orbitPreview(deltaYawDegrees: Double, deltaPitchDegrees: Double) {
        _previewCamera.update { it.orbitBy(deltaYawDegrees, deltaPitchDegrees) }
    }

    fun zoomPreviewBy(factor: Double) {
        _previewCamera.update { it.zoomBy(factor) }
    }

    fun resetPreviewCamera() {
        _previewCamera.value = OrbitCamera()
    }

    /** Hide or show a wall in the 3D preview. Floors always stay visible. */
    fun toggleSurfaceHidden(surfaceId: String) {
        val surface = _surfaces.value.find { it.id == surfaceId } ?: return
        if (surface.type != SurfaceType.WALL) return
        _hiddenSurfaceIds.update { if (surfaceId in it) it - surfaceId else it + surfaceId }
    }

    fun showAllSurfaces() {
        _hiddenSurfaceIds.value = emptySet()
    }

    companion object {
        const val LAYOUT_DEBOUNCE_MS = 100L
    }

    private val _lockedSurfaceIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedSurfaceIds: StateFlow<Set<String>> = _lockedSurfaceIds

    private val _undoBuffer = MutableStateFlow<Map<String, Pair<Double, Double>>?>(null)
    val undoBuffer: StateFlow<Map<String, Pair<Double, Double>>?> = _undoBuffer

    private var dragLockedIds: Set<String> = emptySet()

    private val _currentTiles = MutableStateFlow<List<PlacedTile>>(emptyList())
    val currentTiles: StateFlow<List<PlacedTile>> = _currentTiles

    /** surfaceId → door surface-local rect, for the 2D layout canvases and the 3D preview. */
    private val _doorLocalRects = MutableStateFlow<Map<String, RegionRect>>(emptyMap())
    val doorLocalRects: StateFlow<Map<String, RegionRect>> = _doorLocalRects

    private val _cutEntries = MutableStateFlow<List<CutEntry>>(emptyList())
    val cutEntries: StateFlow<List<CutEntry>> = _cutEntries

    private val pendingLayoutJobs = mutableMapOf<String, Job>()

    // -- Public API --

    suspend fun loadSurfaces(roomId: String) {
        _surfaces.value = surfaceRepo.getByRoom(roomId)
        val ids = _surfaces.value.map { it.id }.toSet()
        _hiddenSurfaceIds.update { it intersect ids }
        refreshDoorRects(roomId)
        _selectedSurfaceId.value?.let { loadLayoutForSurface(it) }
        recomputeCutEntries()
        refreshPreviewScene()
    }

    /** Recompute surface-local door rects for every loaded surface. */
    private suspend fun refreshDoorRects(roomId: String) {
        val room = roomRepo.getById(roomId)
        if (room == null) {
            _doorLocalRects.value = emptyMap()
            return
        }
        val localRects = mutableMapOf<String, RegionRect>()
        for (surface in _surfaces.value) {
            DoorGeometry.surfaceLocalRect(room, surface)?.let { localRects[surface.id] = it }
        }
        _doorLocalRects.value = localRects
    }

    suspend fun selectSurface(id: String?) {
        _selectedSurfaceId.value = id
        if (id != null) {
            loadLayoutForSurface(id)
        } else {
            _currentTiles.value = emptyList()
        }
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
        val room = roomRepo.getById(surface.roomId)
        val doorRect = room?.let { DoorGeometry.surfaceLocalRect(it, surface) }

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
                    exclusions = listOfNotNull(
                        doorRect?.let { exclusionForRegion(it, stg.region) }
                    ),
                )
            }
        }

        val result = LayoutResult(surfaceId = surfaceId, tiles = tiles, stale = false)
        layoutRepo.save(result)

        if (surfaceId == _selectedSurfaceId.value) {
            _currentTiles.value = tiles
        }
        recomputeCutEntries()
        refreshPreviewScene(surfaceId)
    }

    /**
     * Translate a surface-local door rect into an STG-region-local exclusion,
     * or null when the door does not touch this region (e.g. sub-regions that
     * don't cover the door wall's door area).
     */
    private fun exclusionForRegion(doorRect: RegionRect, region: RegionRect): RegionRect? {
        val local = RegionRect(
            x = doorRect.x - region.x,
            y = doorRect.y - region.y,
            width = doorRect.width,
            height = doorRect.height,
        )
        val regionLocal = RegionRect(0.0, 0.0, region.width, region.height)
        return if (local.overlaps(regionLocal)) local else null
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

    // -- 3D preview --

    /**
     * Visible surface under a viewport point (pixels, origin top-left), using
     * the same camera pose the renderers derive from [previewCamera] and the
     * scene bounds.
     */
    fun pickSurface(
        x: Double,
        y: Double,
        viewportWidth: Double,
        viewportHeight: Double,
    ): String? {
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        val pose = _previewCamera.value.pose(_previewScene.value.bounds, viewportWidth / viewportHeight)
        val ray = pose.rayThrough(x, y, viewportWidth, viewportHeight)
        val hidden = _hiddenSurfaceIds.value
        val doors = _doorLocalRects.value
        val targets = _surfaces.value
            .filter { it.id !in hidden }
            .map { PickTarget(it, doors[it.id]) }
        return ScenePicker.pick(ray, targets)
    }

    /** Rebuild the preview meshes for [surfaceId], or for every surface when null. */
    private suspend fun refreshPreviewScene(surfaceId: String? = null) {
        val surfaces = _surfaces.value
        val doors = _doorLocalRects.value
        val inputs = surfaces
            .filter { surfaceId == null || it.id == surfaceId }
            .map { surface ->
                val tiles = layoutRepo.getBySurface(surface.id)?.tiles.orEmpty()
                val groups = tiles.map { it.tileGroupId }.distinct()
                    .mapNotNull { tileGroupRepo.getById(it) }
                    .associateBy { it.id }
                SurfaceSceneInput(surface, tiles, groups, doors[surface.id])
            }
        val rebuilt = withContext(Dispatchers.Default) {
            inputs.map(RoomSceneBuilder::buildSurface)
        }.associateBy { it.surfaceId }

        val previous = if (surfaceId == null) emptyMap()
        else _previewScene.value.surfaces.associateBy { it.surfaceId }
        _previewScene.value = RoomScene(
            _surfaces.value.mapNotNull { rebuilt[it.id] ?: previous[it.id] }
        )
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
