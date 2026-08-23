package com.hasu.tilelayout.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hasu.tilelayout.models.Room
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfacePosition
import com.hasu.tilelayout.models.SurfaceType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Repository-level coverage for the getSurfacesByRoom JOIN that populates
 * the transient Surface.doorRotation from the room's door_wall column.
 */
class SurfaceRepositoryDoorJoinTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: TileLayoutDb

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TileLayoutDb.Schema.create(driver)
        db = TileLayoutDb(driver)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun getByRoomJoinsDoorWallIntoDoorRotation() = runTest {
        val roomRepo = SqlDelightRoomRepository(db.tileLayoutDbQueries)
        val surfaceRepo = SqlDelightSurfaceRepository(db.tileLayoutDbQueries)
        val room = Room(
            projectId = "p1", name = "R",
            width = 3000.0, depth = 4000.0, height = 2400.0,
            doorWall = 90.0,
        )
        roomRepo.insert(room)
        surfaceRepo.insert(
            Surface(
                roomId = room.id,
                type = SurfaceType.WALL,
                width = 4000.0, height = 2400.0,
                position = SurfacePosition(0.0, 0.0, 4000.0, 90.0),
            )
        )
        surfaceRepo.insert(
            Surface(
                roomId = room.id,
                type = SurfaceType.WALL,
                width = 3000.0, height = 2400.0,
                position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
            )
        )

        val surfaces = surfaceRepo.getByRoom(room.id)
        assertEquals(2, surfaces.size)

        val left = surfaces.first { it.position.rotation == 90.0 }
        val front = surfaces.first { it.position.rotation == 0.0 }
        assertEquals(90.0, left.doorRotation)
        assertEquals(90.0, front.doorRotation, "doorRotation mirrors the room's door wall for every surface")
        assertEquals("Door Wall", left.displayName())
        assertEquals("Right Wall", front.displayName(), "rotation 0 relative to door 90 → delta 270 → Right Wall")
    }

    @Test
    fun getByIdDoesNotJoinDoor() = runTest {
        val roomRepo = SqlDelightRoomRepository(db.tileLayoutDbQueries)
        val surfaceRepo = SqlDelightSurfaceRepository(db.tileLayoutDbQueries)
        val room = Room(
            projectId = "p1", name = "R",
            width = 3000.0, depth = 4000.0, height = 2400.0,
            doorWall = 0.0,
        )
        roomRepo.insert(room)
        val front = Surface(
            roomId = room.id,
            type = SurfaceType.WALL,
            width = 3000.0, height = 2400.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        surfaceRepo.insert(front)

        val loaded = surfaceRepo.getById(front.id)!!

        assertNull(loaded.doorRotation, "getSurfaceById has no rooms JOIN → coordinate-name fallback")
        assertEquals("Front Wall", loaded.displayName())
    }

    @Test
    fun getByRoomWithNoDoorKeepsNullDoorRotation() = runTest {
        val roomRepo = SqlDelightRoomRepository(db.tileLayoutDbQueries)
        val surfaceRepo = SqlDelightSurfaceRepository(db.tileLayoutDbQueries)
        val room = Room(projectId = "p1", name = "R", width = 3000.0, depth = 4000.0, height = 2400.0)
        roomRepo.insert(room)
        surfaceRepo.insert(
            Surface(
                roomId = room.id,
                type = SurfaceType.WALL,
                width = 3000.0, height = 2400.0,
                position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
            )
        )

        val loaded = surfaceRepo.getByRoom(room.id).single()

        assertNull(loaded.doorRotation, "legacy NULL door_wall maps to null")
        assertEquals("Front Wall", loaded.displayName())
    }
}
