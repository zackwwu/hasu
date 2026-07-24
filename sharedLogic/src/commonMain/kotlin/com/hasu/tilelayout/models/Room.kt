package com.hasu.tilelayout.models

data class Room(
    val id: String = TypeId.generate("rom"),
    val projectId: String,
    val name: String,
    val width: Double,
    val depth: Double,
    val height: Double,
)
