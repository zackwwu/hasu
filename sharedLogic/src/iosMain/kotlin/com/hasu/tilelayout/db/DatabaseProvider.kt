package com.hasu.tilelayout.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.hasu.tilelayout.viewmodel.RoomEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * iOS-only database and ViewModel factory.
 *
 * Swift cannot construct SQLDelight's [NativeSqliteDriver] or
 * kotlinx.coroutines [CoroutineScope] because these classes are not
 * re-exported in the SharedLogic ObjC header. They are therefore
 * created here and handed to Swift as ready-to-use objects.
 */
object DatabaseProvider {
    fun createTileLayoutDb(): TileLayoutDb {
        return TileLayoutDb(NativeSqliteDriver(TileLayoutDb.Schema, "tilelayout.db"))
    }

    /**
     * Creates a [RoomEditorViewModel] wired with the default Kotlin
     * [CoroutineScope] backed by [Dispatchers.Main]. The scope is used
     * for debounced layout recomputation.
     */
    fun createRoomEditorViewModel(
        roomRepo: RoomRepository,
        surfaceRepo: SurfaceRepository,
        tileGroupRepo: TileGroupRepository,
        layoutRepo: LayoutResultRepository,
    ): RoomEditorViewModel {
        val scope = CoroutineScope(Dispatchers.Main)
        return RoomEditorViewModel(roomRepo, surfaceRepo, tileGroupRepo, layoutRepo, scope)
    }
}
