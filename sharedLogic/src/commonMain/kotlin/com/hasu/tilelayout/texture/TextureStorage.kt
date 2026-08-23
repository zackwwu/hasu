package com.hasu.tilelayout.texture

expect fun textureBaseDirectory(): String

object TextureStorage {
    fun pathFor(tileGroupId: String): String =
        "${textureBaseDirectory()}/$tileGroupId.png"
}
