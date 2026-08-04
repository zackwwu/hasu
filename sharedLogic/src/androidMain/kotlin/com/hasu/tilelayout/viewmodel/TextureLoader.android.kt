package com.hasu.tilelayout.viewmodel

import android.graphics.BitmapFactory
import com.hasu.tilelayout.models.TileGroup
import java.util.concurrent.ConcurrentHashMap

actual class TextureLoader {
    private val cache = ConcurrentHashMap<String, android.graphics.Bitmap>(MAX_CACHE_SIZE)

    companion object {
        private const val MAX_CACHE_SIZE = 32
    }

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        cache[tileGroup.id]?.let { return it }
        val path = tileGroup.texturePath ?: return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
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
