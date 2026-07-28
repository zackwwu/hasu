package com.hasu.tilelayout.models

data class TileGroup(
    val id: String = TypeId.generate("tlg"),
    val projectId: String,
    val name: String,
    val tileWidth: Double,
    val tileHeight: Double,
    val texturePath: String? = null,
    val source: TileSource = TileSource.IMPORTED,
)
