package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.models.TileGroup
import platform.UIKit.UIImage

actual class TextureLoader {
    private val cache = linkedMapOf<String, UIImage>()

    companion object {
        private const val MAX_CACHE_SIZE = 32
    }

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        cache[tileGroup.id]?.let { return it }
        val path = tileGroup.texturePath ?: return null
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.remove(cache.keys.first())
        }
        cache[tileGroup.id] = image
        return image
    }

    actual fun evict(tileGroupId: String) {
        cache.remove(tileGroupId)
    }

    actual fun clear() {
        cache.clear()
    }
}
