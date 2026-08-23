package com.hasu.tilelayout.texture

// jvm target is test-only in this repo; a temp dir keeps tests hermetic.
actual fun textureBaseDirectory(): String =
    "${System.getProperty("java.io.tmpdir")}/tilelayout/textures"
