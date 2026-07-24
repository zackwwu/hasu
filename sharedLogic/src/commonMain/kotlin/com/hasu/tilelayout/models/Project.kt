package com.hasu.tilelayout.models

data class Project(
    val id: String = TypeId.generate("prj"),
    val name: String,
    val units: UnitSystem = UnitSystem.MM,
    val createdAt: Long = currentTimeMillis(),
)
