package com.hasu.tilelayout.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.LayoutResult
import com.hasu.tilelayout.models.PlacedTile
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Door-convention marker on saved layouts against a real in-memory SQLDelight database. */
class LayoutResultRepositoryTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @AfterTest
    fun teardown() {
        driver.close()
    }

    private val tile = PlacedTile(10.0, 20.0, 300.0, 200.0, isCut = true, cutEdges = listOf(CutEdge.TOP), tileGroupId = "tg1")

    @Test
    fun newLayoutsAreSavedWithTheFloorDoorConvention() = runTest {
        TileLayoutDb.Schema.create(driver)
        val repo = SqlDelightLayoutResultRepository(TileLayoutDb(driver).tileLayoutDbQueries)
        val result = LayoutResult(surfaceId = "s1", tiles = listOf(tile), computedAt = 42L)

        repo.save(result)

        assertEquals(result, repo.getBySurface("s1"))
        assertEquals(LayoutResult.DOOR_CONVENTION_FLOOR, repo.getBySurface("s1")!!.doorConvention)
    }

    @Test
    fun layoutsSavedBeforeTheMigrationReadAsCeilingConvention() = runTest {
        // Recreate the version-2 table shape, save a row the way the old app did, then migrate
        TileLayoutDb.Schema.create(driver)
        driver.execute(null, "ALTER TABLE layout_results DROP COLUMN door_convention", 0)
        driver.execute(null, "INSERT INTO layout_results(id, surface_id, computed_at, stale) VALUES ('lay1', 's1', 1, 0)", 0)

        TileLayoutDb.Schema.migrate(driver, 2, TileLayoutDb.Schema.version)

        val repo = SqlDelightLayoutResultRepository(TileLayoutDb(driver).tileLayoutDbQueries)
        assertEquals(LayoutResult.DOOR_CONVENTION_CEILING, repo.getBySurface("s1")!!.doorConvention)
        assertEquals(3L, TileLayoutDb.Schema.version)
    }
}
