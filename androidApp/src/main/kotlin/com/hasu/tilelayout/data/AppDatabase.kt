package com.hasu.tilelayout.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.hasu.tilelayout.db.TileLayoutDb

object AppDatabase {
    lateinit var instance: TileLayoutDb
        private set

    fun init(context: Context) {
        instance = TileLayoutDb(
            AndroidSqliteDriver(
                TileLayoutDb.Schema,
                context,
                "tilelayout.db",
                callback = AndroidSqliteDriver.Callback(TileLayoutDb.Schema),
            )
        )
    }
}
