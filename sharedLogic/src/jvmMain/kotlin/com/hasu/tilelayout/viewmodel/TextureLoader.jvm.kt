package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.models.TileGroup

/**
 * JVM actual for [TextureLoader].
 * Desktop/JVM has no in-app texture rendering; loading is a no-op.
 */
actual class TextureLoader {

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? = null

    actual fun evict(tileGroupId: String) = Unit

    actual fun clear() = Unit
}
