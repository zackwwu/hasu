package com.hasu.tilelayout.db

import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.models.TileSource

interface TileGroupRepository {
    suspend fun getByProject(projectId: String): List<TileGroup>
    suspend fun getById(id: String): TileGroup?
    suspend fun insert(tileGroup: TileGroup)
    suspend fun delete(id: String)
}

class SqlDelightTileGroupRepository(private val queries: TileLayoutDbQueries) : TileGroupRepository {

    override suspend fun getByProject(projectId: String): List<TileGroup> {
        return queries.getTileGroupsByProject(projectId).executeAsList().map { it.toTileGroup() }
    }

    override suspend fun getById(id: String): TileGroup? {
        return queries.getTileGroupById(id).executeAsOneOrNull()?.toTileGroup()
    }

    override suspend fun insert(tileGroup: TileGroup) {
        queries.insertTileGroup(
            id = tileGroup.id,
            project_id = tileGroup.projectId,
            name = tileGroup.name,
            tile_width = tileGroup.tileWidth,
            tile_height = tileGroup.tileHeight,
            texture_path = tileGroup.texturePath,
            source = tileGroup.source.name,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteTileGroup(id)
    }
}

internal fun Tile_groups.toTileGroup(): TileGroup = TileGroup(
    id = id,
    projectId = project_id,
    name = name,
    tileWidth = tile_width,
    tileHeight = tile_height,
    texturePath = texture_path,
    source = try {
        TileSource.valueOf(source)
    } catch (_: IllegalArgumentException) {
        TileSource.IMPORTED
    },
)
