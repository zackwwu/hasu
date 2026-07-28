package com.hasu.tilelayout.models

data class PlacedTile(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double = 0.0,
    val isCut: Boolean = false,
    val cutEdges: List<CutEdge> = emptyList(),
    val tileGroupId: String,
)
