package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.models.TileGroup
import platform.UIKit.UIImage

/**
 * iOS actual for [TextureLoader].
 * Loads textures via [UIImage] and caches them in memory.
 */
actual class TextureLoader {
    private val cache = mutableMapOf<String, UIImage>()

    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        cache[tileGroup.id]?.let { return it }
        val path = tileGroup.texturePath ?: return null
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
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
