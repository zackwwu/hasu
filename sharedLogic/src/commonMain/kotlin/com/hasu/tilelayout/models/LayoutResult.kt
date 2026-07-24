package com.hasu.tilelayout.models

data class LayoutResult(
    val id: String = TypeId.generate("lay"),
    val surfaceId: String,
    val tiles: List<PlacedTile>,
    val computedAt: Long = currentTimeMillis(),
    val stale: Boolean = false,
)
