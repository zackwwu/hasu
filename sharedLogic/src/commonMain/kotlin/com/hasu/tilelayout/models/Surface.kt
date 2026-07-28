package com.hasu.tilelayout.models

data class SurfacePosition(
    val x: Double,
    val y: Double,
    val z: Double,
    val rotation: Double,
)

data class Surface(
    val id: String = TypeId.generate("srf"),
    val roomId: String,
    val type: SurfaceType,
    val width: Double,
    val height: Double,
    val position: SurfacePosition,
    val groutColor: GroutColor = GroutColor.GREY,
    val groutWidth: Double = 3.0,
)
