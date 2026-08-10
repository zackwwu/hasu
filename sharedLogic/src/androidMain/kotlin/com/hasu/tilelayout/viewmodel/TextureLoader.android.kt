package com.hasu.tilelayout.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hasu.tilelayout.models.TileGroup

actual class TextureLoader {
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (size > MAX_CACHE_SIZE) {
                eldest?.value?.recycle()
                return true
            }
            return false
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 32
    }

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        synchronized(lock) {
            cache[tileGroup.id]?.let { return it }
        }
        val path = tileGroup.texturePath ?: return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        synchronized(lock) {
            cache.putIfAbsent(tileGroup.id, bitmap) ?: return bitmap
            bitmap.recycle()
            return cache[tileGroup.id]
        }
    }

    actual fun evict(tileGroupId: String) {
        synchronized(lock) {
            cache.remove(tileGroupId)?.recycle()
        }
    }

    actual fun clear() {
        synchronized(lock) {
            cache.values.forEach { it.recycle() }
            cache.clear()
        }
    }
}
