package com.hasu.tilelayout.models

data class LayoutResult(
    val id: String = TypeId.generate("lay"),
    val surfaceId: String,
    val tiles: List<PlacedTile>,
    val computedAt: Long = currentTimeMillis(),
    val stale: Boolean = false,
    /** Door-rect convention the tiles were computed with; see [DOOR_CONVENTION_FLOOR]. */
    val doorConvention: Int = DOOR_CONVENTION_FLOOR,
) {
    companion object {
        /** Saved before the door moved to the floor line: the door gap was excluded at the top of the wall. */
        const val DOOR_CONVENTION_CEILING = 0

        /** Door rect anchored to the floor line in top-down layout coordinates (current). */
        const val DOOR_CONVENTION_FLOOR = 1
    }
}
