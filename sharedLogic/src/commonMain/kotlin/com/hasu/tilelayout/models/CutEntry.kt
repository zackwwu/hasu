package com.hasu.tilelayout.models

data class CutLocation(
    val surfaceId: String,
    val surfaceName: String,
    val count: Int,
)

data class CutEntry(
    val tileGroupId: String,
    val tileGroupName: String,
    val width: Double,
    val height: Double,
    val cutEdgesKey: String,
    val locations: List<CutLocation>,
    val totalCount: Int,
) {
    val cutTypeDescription: String get() = when (cutEdgesKey) {
        "LEFT" -> "Left edge cut"
        "RIGHT" -> "Right edge cut"
        "TOP" -> "Top edge cut"
        "BOTTOM" -> "Bottom edge cut"
        "RIGHT,BOTTOM" -> "Corner cut (right + bottom)"
        "LEFT,BOTTOM" -> "Corner cut (left + bottom)"
        "RIGHT,TOP" -> "Corner cut (right + top)"
        "LEFT,TOP" -> "Corner cut (left + top)"
        else -> "Custom cut"
    }
}
