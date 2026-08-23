package com.hasu.tilelayout.db

import com.hasu.tilelayout.models.Room

interface RoomRepository {
    suspend fun getByProject(projectId: String): List<Room>
    suspend fun getById(id: String): Room?
    suspend fun insert(room: Room)
    suspend fun updateDoor(roomId: String, doorWall: Double?, doorWidth: Double, doorHeight: Double, doorOffset: Double?)
    suspend fun delete(id: String)
}

class SqlDelightRoomRepository(private val queries: TileLayoutDbQueries) : RoomRepository {

    override suspend fun getByProject(projectId: String): List<Room> {
        return queries.getRoomsByProject(projectId).executeAsList().map { it.toRoom() }
    }

    override suspend fun getById(id: String): Room? {
        return queries.getRoomById(id).executeAsOneOrNull()?.toRoom()
    }

    override suspend fun insert(room: Room) {
        queries.insertRoom(
            id = room.id,
            project_id = room.projectId,
            name = room.name,
            width = room.width,
            depth = room.depth,
            height = room.height,
            door_wall = room.doorWall?.toString(),
            door_width = room.doorWidth,
            door_height = room.doorHeight,
            door_offset = room.doorOffset,
        )
    }

    override suspend fun updateDoor(
        roomId: String,
        doorWall: Double?,
        doorWidth: Double,
        doorHeight: Double,
        doorOffset: Double?,
    ) {
        queries.updateRoomDoor(
            door_wall = doorWall?.toString(),
            door_width = doorWidth,
            door_height = doorHeight,
            door_offset = doorOffset,
            id = roomId,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteRoom(id)
    }
}

internal fun Rooms.toRoom(): Room = Room(
    id = id,
    projectId = project_id,
    name = name,
    width = width,
    depth = depth,
    height = height,
    doorWall = door_wall?.toDoubleOrNull(),
    doorWidth = door_width ?: 900.0,
    doorHeight = door_height ?: 2100.0,
    doorOffset = door_offset,
)
