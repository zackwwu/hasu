package com.hasu.tilelayout.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS-only database factory.
 *
 * Swift cannot construct SQLDelight's [NativeSqliteDriver] because sqldelight
 * classes are not re-exported in the SharedLogic ObjC header (they are only
 * `implementation` dependencies of the framework). The driver is therefore
 * created here and handed to Swift as a ready-to-use [TileLayoutDb].
 */
object DatabaseProvider {
    fun createTileLayoutDb(): TileLayoutDb {
        return TileLayoutDb(NativeSqliteDriver(TileLayoutDb.Schema, "tilelayout.db"))
    }
}
