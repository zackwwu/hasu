package com.hasu.tilelayout.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * iOS-only database and ViewModel factory.
 *
 * Swift cannot construct SQLDelight's [NativeSqliteDriver] or
 * kotlinx.coroutines [CoroutineScope] because these classes are not
 * re-exported in the SharedLogic ObjC header. They are therefore
 * created here and handed to Swift as ready-to-use objects.
 */
object DatabaseProvider {
    private val db: TileLayoutDb by lazy {
        // SQLDelight 2.0.2's NativeSqliteDriver wires TileLayoutDb.Schema.migrate into
        // the sqliter DatabaseConfiguration.upgrade hook automatically, so a schema
        // version bump (1 -> 2 via 1.sqm) runs migrations with no explicit callback.
        TileLayoutDb(NativeSqliteDriver(TileLayoutDb.Schema, "tilelayout.db"))
    }

    fun createTileLayoutDb(): TileLayoutDb = db

    /**
     * Creates a [RoomEditorViewModel] wired with the default Kotlin
     * [CoroutineScope] backed by [Dispatchers.Main]. Uses [SupervisorJob]
     * so a failed child coroutine does not cancel the entire scope.
     */
    fun createRoomEditorViewModel(
        roomRepo: RoomRepository,
        surfaceRepo: SurfaceRepository,
        tileGroupRepo: TileGroupRepository,
        layoutRepo: LayoutResultRepository,
    ): RoomEditorViewModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        return RoomEditorViewModel(roomRepo, surfaceRepo, tileGroupRepo, layoutRepo, scope)
    }
}
