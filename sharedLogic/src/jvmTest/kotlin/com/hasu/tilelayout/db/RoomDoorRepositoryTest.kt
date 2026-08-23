package com.hasu.tilelayout.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hasu.tilelayout.models.Room
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Repository-level coverage for the Phase 10 door fields against a real
 * in-memory SQLDelight database (schema version 2).
 */
class RoomDoorRepositoryTest {

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
    fun updateDoorPersistsAndReadsBack() = runTest {
        val repo = SqlDelightRoomRepository(db.tileLayoutDbQueries)
        val room = Room(projectId = "p1", name = "Bathroom", width = 3000.0, depth = 4000.0, height = 2400.0)
        repo.insert(room)

        repo.updateDoor(
            roomId = room.id,
            doorWall = 90.0,
            doorWidth = 800.0,
            doorHeight = 2000.0,
            doorOffset = 300.0,
        )

        val loaded = repo.getById(room.id)!!
        assertEquals(90.0, loaded.doorWall)
        assertEquals(800.0, loaded.doorWidth)
        assertEquals(2000.0, loaded.doorHeight)
        assertEquals(300.0, loaded.doorOffset)
    }

    @Test
    fun updateDoorClearsTheDoor() = runTest {
        val repo = SqlDelightRoomRepository(db.tileLayoutDbQueries)
        val room = Room(
            projectId = "p1", name = "Bathroom",
            width = 3000.0, depth = 4000.0, height = 2400.0,
            doorWall = 0.0, doorOffset = 1050.0,
        )
        repo.insert(room)

        repo.updateDoor(
            roomId = room.id,
            doorWall = null,
            doorWidth = 900.0,
            doorHeight = 2100.0,
            doorOffset = null,
        )

        val loaded = repo.getById(room.id)!!
        assertNull(loaded.doorWall)
        assertNull(loaded.doorOffset)
        assertEquals(900.0, loaded.doorWidth)
        assertEquals(2100.0, loaded.doorHeight)
    }

    @Test
    fun roomsWithoutDoorGetModelDefaults() = runTest {
        val repo = SqlDelightRoomRepository(db.tileLayoutDbQueries)
        repo.insert(Room(projectId = "p1", name = "NoDoor", width = 3000.0, depth = 4000.0, height = 2400.0))

        val loaded = repo.getByProject("p1").single()

        assertNull(loaded.doorWall)
        assertEquals(900.0, loaded.doorWidth)
        assertEquals(2100.0, loaded.doorHeight)
        assertNull(loaded.doorOffset)
    }
}
