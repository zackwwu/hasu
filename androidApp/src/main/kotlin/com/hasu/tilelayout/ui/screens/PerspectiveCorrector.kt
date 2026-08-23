package com.hasu.tilelayout.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size
import com.hasu.tilelayout.engine.PerspectiveSizing
import com.hasu.tilelayout.engine.ScanGuideGeometry
import kotlin.math.abs

object PerspectiveCorrector {
    /** Compute output size preserving tile aspect ratio, fitting within maxDimension. */
    fun outputSize(tileWidth: Double, tileHeight: Double, maxDimension: Int = 512): Size {
        val s = PerspectiveSizing.outputSize(tileWidth, tileHeight, maxDimension)
        return Size(s.width, s.height)
    }

    /**
     * Warp [corners] onto an [outputSize] bitmap.
     *
     * When the tile was framed 90° from its natural orientation, the corner ORDER is
     * rotated back rather than the output image — the warp already resamples, so the
     * rotation costs nothing and [outputSize] stays natural (tileWidth × tileHeight).
     * Rotation is derived from the corners themselves, so it covers both the
     * auto-detected and guide-seeded paths.
     */
    fun correct(
        source: Bitmap,
        corners: QuadCorners,
        tileWidth: Double,
        tileHeight: Double,
        outputSize: Size,
    ): Bitmap {
        val ordered = listOf(corners.tl, corners.tr, corners.br, corners.bl)
        val quadW = maxOf(
            abs(corners.tr.x - corners.tl.x), abs(corners.br.x - corners.bl.x)
        ).toDouble()
        val quadH = maxOf(
            abs(corners.bl.y - corners.tl.y), abs(corners.br.y - corners.tr.y)
        ).toDouble()
        val rotated = quadW > 0 && quadH > 0 &&
            ScanGuideGeometry.isRotated(quadW, quadH, tileWidth, tileHeight)
        val c = ScanGuideGeometry.unrotationOrder(rotated).map { ordered[it] }

        val w = outputSize.width.toFloat()
        val h = outputSize.height.toFloat()
        val src = floatArrayOf(
            c[0].x, c[0].y, c[1].x, c[1].y,
            c[2].x, c[2].y, c[3].x, c[3].y)
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        val output = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(output).drawBitmap(source, matrix, paint)
        return output
    }
}
