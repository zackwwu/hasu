package com.hasu.tilelayout.db

import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.PlacedTile

interface LayoutResultRepository {
    suspend fun getBySurface(surfaceId: String): LayoutResult?
    suspend fun save(layoutResult: LayoutResult)
    suspend fun delete(id: String)
}

class SqlDelightLayoutResultRepository(private val queries: TileLayoutDbQueries) : LayoutResultRepository {

    override suspend fun getBySurface(surfaceId: String): LayoutResult? {
        val row = queries.getLayoutResultBySurface(surfaceId).executeAsOneOrNull()
            ?: return null
        val tiles = queries.getTilesByLayoutResult(row.id).executeAsList().map { it.toPlacedTile() }
        return row.toLayoutResult(tiles)
    }

    override suspend fun save(layoutResult: LayoutResult) {
        queries.transaction {
            val old = queries.getLayoutResultBySurface(layoutResult.surfaceId).executeAsOneOrNull()
            if (old != null) {
                queries.deleteTilesByLayoutResult(old.id)
                queries.deleteLayoutResult(old.id)
            }

            queries.insertLayoutResult(
                id = layoutResult.id,
                surface_id = layoutResult.surfaceId,
                computed_at = layoutResult.computedAt,
                stale = if (layoutResult.stale) 1L else 0L,
            )

            for (tile in layoutResult.tiles) {
                queries.insertPlacedTile(
                    layout_result_id = layoutResult.id,
                    x = tile.x,
                    y = tile.y,
                    w = tile.width,
                    h = tile.height,
                    rotation = tile.rotation,
                    is_cut = if (tile.isCut) 1L else 0L,
                    cut_edges = tile.cutEdges.joinToString(",") { it.name },
                    tile_group_id = tile.tileGroupId,
                )
            }
        }
    }

    override suspend fun delete(id: String) {
        queries.deleteTilesByLayoutResult(id)
        queries.deleteLayoutResult(id)
    }
}

internal fun Layout_results.toLayoutResult(tiles: List<PlacedTile>): LayoutResult = LayoutResult(
    id = id,
    surfaceId = surface_id,
    tiles = tiles,
    computedAt = computed_at,
    stale = stale != 0L,
)

internal fun Placed_tiles.toPlacedTile(): PlacedTile = PlacedTile(
    x = x,
    y = y,
    width = w,
    height = h,
    rotation = rotation,
    isCut = is_cut != 0L,
    cutEdges = if (cut_edges.isBlank()) emptyList()
    else cut_edges.split(",").mapNotNull { name ->
        try {
            CutEdge.valueOf(name.trim())
        } catch (_: IllegalArgumentException) {
            null
        }
    },
    tileGroupId = tile_group_id,
)
