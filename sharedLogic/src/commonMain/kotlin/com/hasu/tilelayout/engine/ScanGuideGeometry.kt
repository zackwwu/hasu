package com.hasu.tilelayout.engine

import kotlin.math.abs
import kotlin.math.ln

object ScanGuideGeometry {
    /**
     * A centered guide frame in viewport coordinates.
     * [rotated] is true when the tile is framed 90° from its natural orientation —
     * the caller must rotate the corner order back before perspective correction.
     */
    data class GuideFrame(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val rotated: Boolean,
    )

    /**
     * Largest centered frame preserving the tile's aspect ratio, inset within the viewport.
     * Tries both w:h and h:w and keeps whichever covers more area, so a landscape tile
     * still gets a large target on a portrait screen. Square tiles resolve to rotated = false.
     *
     * Pass the preview rect actually available for aiming (i.e. minus any chrome drawn
     * over it) — this function does not know about safe areas.
     */
    fun frame(
        viewportWidth: Double,
        viewportHeight: Double,
        tileWidth: Double,
        tileHeight: Double,
        insetFraction: Double = 0.85,
    ): GuideFrame {
        require(viewportWidth > 0 && viewportHeight > 0) { "viewport must be positive" }
        require(tileWidth > 0 && tileHeight > 0) { "tile dimensions must be positive" }

        val natural = fit(viewportWidth, viewportHeight, tileWidth / tileHeight, insetFraction)
        val swapped = fit(viewportWidth, viewportHeight, tileHeight / tileWidth, insetFraction)

        // Strictly-greater keeps square tiles (equal areas) on the natural orientation.
        val useSwapped = swapped.first * swapped.second > natural.first * natural.second
        val (w, h) = if (useSwapped) swapped else natural

        return GuideFrame(
            x = (viewportWidth - w) / 2,
            y = (viewportHeight - h) / 2,
            width = w,
            height = h,
            rotated = useSwapped,
        )
    }

    /** Width/height of the largest [aspect]-ratio box fitting inside the inset viewport. */
    private fun fit(vw: Double, vh: Double, aspect: Double, inset: Double): Pair<Double, Double> {
        val availW = vw * inset
        val availH = vh * inset
        return if (availW / availH > aspect) {
            Pair(availH * aspect, availH)   // height-bound
        } else {
            Pair(availW, availW / aspect)   // width-bound
        }
    }

    /** Frame corners in tl, tr, br, bl order — matches Android's QuadCorners field order. */
    fun corners(frame: GuideFrame): List<Pair<Double, Double>> = listOf(
        Pair(frame.x, frame.y),
        Pair(frame.x + frame.width, frame.y),
        Pair(frame.x + frame.width, frame.y + frame.height),
        Pair(frame.x, frame.y + frame.height),
    )

    /**
     * True when a quad was framed 90° from the tile's natural orientation — i.e. its
     * aspect sits closer to the tile's reciprocal ratio than to its natural one.
     *
     * Lets the auto-rotate apply to AUTO-DETECTED quads too, not just the guide-frame
     * fallback: pass the detected quad's bounding width/height. Square tiles (natural
     * == reciprocal) resolve to false, matching [frame]'s tie-break.
     */
    fun isRotated(
        quadWidth: Double,
        quadHeight: Double,
        tileWidth: Double,
        tileHeight: Double,
    ): Boolean {
        require(quadWidth > 0 && quadHeight > 0) { "quad dimensions must be positive" }
        require(tileWidth > 0 && tileHeight > 0) { "tile dimensions must be positive" }
        val quad = ln(quadWidth / quadHeight)
        val natural = ln(tileWidth / tileHeight)
        val swapped = ln(tileHeight / tileWidth)
        // Compared in log space so "2× too wide" and "2× too tall" score equally.
        return abs(quad - swapped) < abs(quad - natural)
    }

    /**
     * Index permutation mapping a tl,tr,br,bl corner list back to the tile's natural
     * orientation, so the corrected output always reads as tileWidth × tileHeight.
     * Returns [0,1,2,3] when not rotated.
     *
     * Returns an order rather than reordered points so each platform applies it to its
     * own point type (CGPoint / PointF) without bridging a collection of pairs.
     *
     * Assumes the tile's natural top-left corner sits at the frame's top-right. The
     * opposite placement yields a 180°-rotated texture — see the phase's known limitation.
     */
    fun unrotationOrder(rotated: Boolean): List<Int> =
        if (rotated) listOf(1, 2, 3, 0) else listOf(0, 1, 2, 3)
}
