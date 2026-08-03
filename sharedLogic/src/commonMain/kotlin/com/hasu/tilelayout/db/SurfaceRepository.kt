package com.hasu.tilelayout.db

import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.RegionRect
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfacePosition
import com.hasu.tilelayout.models.SurfaceTileGroup
import com.hasu.tilelayout.models.SurfaceType
import com.hasu.tilelayout.models.TilePattern

interface SurfaceRepository {
    suspend fun getByRoom(roomId: String): List<Surface>
    suspend fun getById(id: String): Surface?
    suspend fun insert(surface: Surface)
    suspend fun delete(id: String)

    suspend fun getSTGsBySurface(surfaceId: String): List<SurfaceTileGroup>
    suspend fun getSTGById(id: String): SurfaceTileGroup?
    suspend fun insertSTG(stg: SurfaceTileGroup)
    suspend fun updateSTG(stg: SurfaceTileGroup)
    suspend fun deleteSTG(id: String)
}

class SqlDelightSurfaceRepository(private val queries: TileLayoutDbQueries) : SurfaceRepository {

    override suspend fun getByRoom(roomId: String): List<Surface> {
        return queries.getSurfacesByRoom(roomId).executeAsList().map { it.toSurface() }
    }

    override suspend fun getById(id: String): Surface? {
        return queries.getSurfaceById(id).executeAsOneOrNull()?.toSurface()
    }

    override suspend fun insert(surface: Surface) {
        queries.insertSurface(
            id = surface.id,
            room_id = surface.roomId,
            type = surface.type.name,
            width = surface.width,
            height = surface.height,
            pos_x = surface.position.x,
            pos_y = surface.position.y,
            pos_z = surface.position.z,
            pos_rotation = surface.position.rotation,
            grout_color = surface.groutColor.name,
            grout_width = surface.groutWidth,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteSurface(id)
    }

    override suspend fun getSTGsBySurface(surfaceId: String): List<SurfaceTileGroup> {
        return queries.getSTGsBySurface(surfaceId).executeAsList().map { it.toSurfaceTileGroup() }
    }

    override suspend fun getSTGById(id: String): SurfaceTileGroup? {
        return queries.getSTGById(id).executeAsOneOrNull()?.toSurfaceTileGroup()
    }

    override suspend fun insertSTG(stg: SurfaceTileGroup) {
        queries.insertSTG(
            id = stg.id,
            surface_id = stg.surfaceId,
            tile_group_id = stg.tileGroupId,
            region_x = stg.region.x,
            region_y = stg.region.y,
            region_w = stg.region.width,
            region_h = stg.region.height,
            pattern = stg.pattern.name,
            offset_x = stg.offsetX,
            offset_y = stg.offsetY,
        )
    }

    override suspend fun updateSTG(stg: SurfaceTileGroup) {
        queries.updateSTGOffset(
            offset_x = stg.offsetX,
            offset_y = stg.offsetY,
            id = stg.id,
        )
    }

    override suspend fun deleteSTG(id: String) {
        queries.deleteSTG(id)
    }
}

internal fun Surfaces.toSurface(): Surface = Surface(
    id = id,
    roomId = room_id,
    type = try {
        SurfaceType.valueOf(type)
    } catch (_: IllegalArgumentException) {
        SurfaceType.WALL
    },
    width = width,
    height = height,
    position = SurfacePosition(
        x = pos_x,
        y = pos_y,
        z = pos_z,
        rotation = pos_rotation,
    ),
    groutColor = try {
        GroutColor.valueOf(grout_color)
    } catch (_: IllegalArgumentException) {
        GroutColor.GREY
    },
    groutWidth = grout_width,
)

internal fun Surface_tile_groups.toSurfaceTileGroup(): SurfaceTileGroup = SurfaceTileGroup(
    id = id,
    surfaceId = surface_id,
    tileGroupId = tile_group_id,
    region = RegionRect(
        x = region_x,
        y = region_y,
        width = region_w,
        height = region_h,
    ),
    pattern = try {
        TilePattern.valueOf(pattern)
    } catch (_: IllegalArgumentException) {
        TilePattern.GRID
    },
    offsetX = offset_x,
    offsetY = offset_y,
)
