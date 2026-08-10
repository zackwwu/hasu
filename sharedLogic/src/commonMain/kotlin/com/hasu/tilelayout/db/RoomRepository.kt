package com.hasu.tilelayout.db

import com.hasu.tilelayout.models.Room

interface RoomRepository {
    suspend fun getByProject(projectId: String): List<Room>
    suspend fun getById(id: String): Room?
    suspend fun insert(room: Room)
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
)
