package com.hasu.tilelayout.texture

import android.content.Context

/** Holds the application context; set once at startup from AppDatabase.init. */
object TextureStorageContext {
    lateinit var context: Context
}

actual fun textureBaseDirectory(): String =
    "${TextureStorageContext.context.filesDir.absolutePath}/textures"
