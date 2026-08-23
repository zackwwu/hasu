package com.hasu.tilelayout.engine

import kotlin.math.roundToInt

/** Output bitmap size for perspective correction, preserving a tile's aspect ratio. */
data class PerspectiveOutputSize(val width: Int, val height: Int)

object PerspectiveSizing {
    /**
     * Largest output size preserving tileWidth/tileHeight, with the longest side
     * fitting within [maxDimension]. Never smaller than 1px per side — extreme
     * (but typable) aspect ratios must not produce a zero-dimension output bitmap.
     */
    fun outputSize(tileWidth: Double, tileHeight: Double, maxDimension: Int = 512): PerspectiveOutputSize {
        require(tileWidth > 0 && tileHeight > 0) { "tile dimensions must be positive" }
        require(maxDimension > 0) { "maxDimension must be positive" }
        val aspect = tileWidth / tileHeight
        return if (aspect >= 1.0) {
            PerspectiveOutputSize(
                width = maxDimension,
                height = (maxDimension / aspect).roundToInt().coerceAtLeast(1),
            )
        } else {
            PerspectiveOutputSize(
                width = (maxDimension * aspect).roundToInt().coerceAtLeast(1),
                height = maxDimension,
            )
        }
    }
}
