package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.models.TileGroup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSLock
import platform.UIKit.UIImage

actual class TextureLoader {
    private val mutex = Mutex()
    private val nsLock = NSLock()
    private val cache = linkedMapOf<String, UIImage>()

    companion object {
        private const val MAX_CACHE_SIZE = 32
    }

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        mutex.withLock {
            cache[tileGroup.id]?.let { return it }
        }
        val path = tileGroup.texturePath ?: return null
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        mutex.withLock {
            cache[tileGroup.id]?.let { return it }
            if (cache.size >= MAX_CACHE_SIZE) {
                cache.remove(cache.keys.first())
            }
            cache[tileGroup.id] = image
        }
        return image
    }

    actual fun evict(tileGroupId: String) {
        nsLock.lock()
        try {
            cache.remove(tileGroupId)
        } finally {
            nsLock.unlock()
        }
    }

    actual fun clear() {
        nsLock.lock()
        try {
            cache.clear()
        } finally {
            nsLock.unlock()
        }
    }
}
