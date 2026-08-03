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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
) {
    // -- StateFlows --

    private val _surfaces = MutableStateFlow<List<Surface>>(emptyList())
    val surfaces: StateFlow<List<Surface>> = _surfaces

    private val _selectedSurfaceId = MutableStateFlow<String?>(null)
    val selectedSurfaceId: StateFlow<String?> = _selectedSurfaceId

    private val _viewAngle = MutableStateFlow(0)
    val viewAngle: StateFlow<Int> = _viewAngle

    /** Transient set of surface ids whose tile offsets move in lockstep with the selected surface. */
    private val _lockedSurfaceIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedSurfaceIds: StateFlow<Set<String>> = _lockedSurfaceIds

    /**
     * Snapshot of STG offsets before a drag begins.
     * Map key = stgId, value = (offsetX, offsetY).
     * Single-level undo — set to null after undo is consumed.
     */
    private val _undoBuffer = MutableStateFlow<Map<String, Pair<Double, Double>>?>(null)
    val undoBuffer: StateFlow<Map<String, Pair<Double, Double>>?> = _undoBuffer

    /** Tiles currently displayed for the selected surface. */
    private val _currentTiles = MutableStateFlow<List<PlacedTile>>(emptyList())
    val currentTiles: StateFlow<List<PlacedTile>> = _currentTiles

    // -- Public API --

    suspend fun loadSurfaces(roomId: String) {
        _surfaces.value = surfaceRepo.getByRoom(roomId)
        // Also load layout for the currently selected surface if any
        _selectedSurfaceId.value?.let { loadLayoutForSurface(it) }
    }

    fun selectSurface(id: String?) {
        _selectedSurfaceId.value = id
        // When selection changes, reload tiles for the new surface
        if (id != null) {
            // Actual reload happens via coroutine in platform layer
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

    /**
     * Snapshot current offsets for the selected surface and all locked surfaces
     * before a drag begins. Stored in [_undoBuffer] for single-level undo.
     */
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

    /**
     * Apply a drag offset to the selected surface with lock propagation.
     *
     * @param dx horizontal displacement in surface-local units
     * @param dy vertical displacement in surface-local units
     */
    suspend fun onDragEnd(dx: Double, dy: Double) {
        val selectedId = _selectedSurfaceId.value ?: return
        val selected = _surfaces.value.find { it.id == selectedId } ?: return

        // Apply to the selected surface
        applyOffset(selectedId, dx, dy)

        // Propagate to locked surfaces using axis-aware rules
        for (lockedId in _lockedSurfaceIds.value) {
            val locked = _surfaces.value.find { it.id == lockedId } ?: continue
            val (propDx, propDy) = propagateDelta(selected, locked, dx, dy)
            if (propDx != 0.0 || propDy != 0.0) {
                applyOffset(lockedId, propDx, propDy)
            }
        }
    }

    /**
     * Axis-aware lock propagation per spec:
     * - Floor → Floor : both axes pass through
     * - Wall → Wall (parallel) : dx + dy both pass through
     * - Wall → Wall (non-parallel) : only dy (vertical) passes through
     * - Wall ↔ Floor : no propagation (MVP limitation)
     */
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
            val parallel =
                (source.position.rotation.toInt() % 180) == (target.position.rotation.toInt() % 180)
            return Pair(if (parallel) dx else 0.0, dy)
        }
        // Wall ↔ Floor: no propagation
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
        computeLayout(surfaceId)
    }

    // -- Undo --

    /** Consume the undo buffer. Caller should have captured [_undoBuffer.value] before calling. */
    fun undo() {
        _undoBuffer.value = null
    }

    /**
     * Restore STG offsets from a previously captured undo buffer.
     * Called by the platform layer after [undo] has been invoked.
     */
    suspend fun undoRestore(priors: Map<String, Pair<Double, Double>>) {
        for ((stgId, offset) in priors) {
            val stg = surfaceRepo.getSTGById(stgId) ?: continue
            surfaceRepo.updateSTG(
                stg.copy(offsetX = offset.first, offsetY = offset.second)
            )
        }
        _selectedSurfaceId.value?.let { computeLayout(it) }
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

        // Update current tiles if this is the selected surface
        if (surfaceId == _selectedSurfaceId.value) {
            _currentTiles.value = tiles
        }
    }

    /** Load cached layout tiles for a surface without recomputing. */
    private suspend fun loadLayoutForSurface(surfaceId: String) {
        val result = layoutRepo.getBySurface(surfaceId)
        _currentTiles.value = result?.tiles ?: emptyList()
    }

    // -- 3D Hit Testing --

    /**
     * Find which surface was tapped at the given screen coordinates.
     * Uses painter's algorithm in reverse (front-to-back).
     *
     * @return the id of the tapped surface, or null if none matched
     */
    fun hitTest(
        tapX: Double,
        tapY: Double,
        canvasWidth: Double,
        canvasHeight: Double,
    ): String? {
        val originX = canvasWidth / 2.0
        val originY = canvasHeight * 0.6

        val ordered = IsometricProjection.orderSurfaces(_surfaces.value, _viewAngle.value)
        // Check front-to-back: reverse of painter's order
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

/**
 * Platform-specific texture image loader.
 *
 * iOS: wraps UIImage (loaded from file path).
 * Android: wraps Bitmap (loaded via BitmapFactory).
 *
 * Each platform provides an `actual class TextureLoader` that
 * caches loaded textures in memory.
 */
expect class TextureLoader() {
    /**
     * Load the texture image for [tileGroup] from disk.
     * Returns `null` when no [TileGroup.texturePath] is set
     * or the file cannot be decoded.
     */
    suspend fun loadTexture(tileGroup: TileGroup): Any?

    /** Remove a single texture from the in-memory cache. */
    fun evict(tileGroupId: String)

    /** Clear the entire in-memory texture cache. */
    fun clear()
}
