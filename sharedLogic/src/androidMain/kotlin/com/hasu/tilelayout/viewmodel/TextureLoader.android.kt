package com.hasu.tilelayout.viewmodel

import android.graphics.BitmapFactory
import com.hasu.tilelayout.models.TileGroup

/**
 * Android actual for [TextureLoader].
 * Loads textures via [BitmapFactory] and caches [android.graphics.Bitmap] in memory.
 */
actual class TextureLoader {
    private val cache = mutableMapOf<String, android.graphics.Bitmap>()

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        cache[tileGroup.id]?.let { return it }
        val path = tileGroup.texturePath ?: return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        cache[tileGroup.id] = bitmap
        return bitmap
    }

    actual fun evict(tileGroupId: String) {
        cache.remove(tileGroupId)
    }

    actual fun clear() {
        cache.clear()
    }
}
