package com.hasu.tilelayout.models

data class RegionRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    fun overlaps(other: RegionRect): Boolean {
        if (x >= other.x + other.width) return false
        if (x + width <= other.x) return false
        if (y >= other.y + other.height) return false
        if (y + height <= other.y) return false
        return true
    }

    fun intersectionArea(other: RegionRect): Double {
        val ix = maxOf(x, other.x)
        val iy = maxOf(y, other.y)
        val iw = minOf(x + width, other.x + other.width) - ix
        val ih = minOf(y + height, other.y + other.height) - iy
        return if (iw > 0 && ih > 0) iw * ih else 0.0
    }

    val area: Double get() = width * height
}

data class SurfaceTileGroup(
    val id: String = TypeId.generate("stg"),
    val surfaceId: String,
    val tileGroupId: String,
    val region: RegionRect,
    val pattern: TilePattern = TilePattern.GRID,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val locked: Boolean = false,
)
