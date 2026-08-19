package com.hasu.tilelayout.models

data class Room(
    val id: String = TypeId.generate("rom"),
    val projectId: String,
    val name: String,
    val width: Double,
    val depth: Double,
    val height: Double,
    val doorWall: Double? = null,      // rotation of the door wall (0/90/180/270)
    val doorWidth: Double = 900.0,
    val doorHeight: Double = 2100.0,
    val doorOffset: Double? = null,    // distance from wall anchor corner; null = centered
)
