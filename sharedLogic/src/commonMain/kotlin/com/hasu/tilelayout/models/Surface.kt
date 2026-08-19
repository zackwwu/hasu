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
    val doorRotation: Double? = null, // transient: populated from the room at query time
) {
    /**
     * Orientation-aware name: "Front Wall", "Left Wall", "Back Wall", "Right Wall", "Floor".
     * With a door set (doorRotation != null), names become door-relative:
     * delta 0 → "Door Wall", 90 → "Left Wall", 180 → "Front Wall", 270 → "Right Wall".
     */
    fun displayName(): String = when (type) {
        SurfaceType.FLOOR -> "Floor"
        SurfaceType.WALL -> {
            val r = normalizedRotation(position.rotation)
            val d = doorRotation
            if (d == null) coordinateName(r)
            else when (normalizedRotation(r - d)) {
                0 -> "Door Wall"
                90 -> "Left Wall"
                180 -> "Front Wall"
                270 -> "Right Wall"
                else -> coordinateName(r)
            }
        }
    }
}

private fun normalizedRotation(rotation: Double): Int = ((rotation.toInt() % 360) + 360) % 360

private fun coordinateName(rotation: Int): String = when (rotation) {
    90 -> "Left Wall"
    180 -> "Back Wall"
    270 -> "Right Wall"
    else -> "Front Wall"
}
