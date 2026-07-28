package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*

object SurfacePositionCalculator {
    fun generate(
        roomId: String,
        roomWidth: Double,
        roomDepth: Double,
        roomHeight: Double,
        includeFront: Boolean,
        includeBack: Boolean,
        includeLeft: Boolean,
        includeRight: Boolean,
        includeFloor: Boolean,
    ): List<Surface> = buildList {
        if (includeFront)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomWidth,
                height = roomHeight, position = SurfacePosition(0.0, 0.0, 0.0, 0.0)))

        if (includeBack)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomWidth,
                height = roomHeight, position = SurfacePosition(roomWidth, 0.0, roomDepth, 180.0)))

        if (includeLeft)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomDepth,
                height = roomHeight, position = SurfacePosition(0.0, 0.0, roomDepth, 90.0)))

        if (includeRight)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomDepth,
                height = roomHeight, position = SurfacePosition(roomWidth, 0.0, 0.0, 270.0)))

        if (includeFloor)
            add(Surface(roomId = roomId, type = SurfaceType.FLOOR, width = roomWidth,
                height = roomDepth, position = SurfacePosition(0.0, 0.0, 0.0, 0.0)))
    }
}
