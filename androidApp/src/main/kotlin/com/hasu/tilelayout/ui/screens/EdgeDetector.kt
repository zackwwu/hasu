package com.hasu.tilelayout.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object EdgeDetector {
    private var loaded = false

    /** Load the OpenCV native library once; idempotent and thread-safe. */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (!loaded) {
            loaded = OpenCVLoader.initLocal()
        }
        return loaded
    }

    /**
     * Detect the largest quadrilateral in the image.
     * Returns 4 corner points (not an axis-aligned rect) for perspective correction.
     * Returns null when OpenCV is unavailable or no quad meets the area threshold —
     * the caller falls back to the guide frame, so capture never dead-ends.
     */
    fun detectQuadCorners(bitmap: Bitmap): QuadCorners? {
        if (!ensureLoaded()) return null

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestApprox: MatOfPoint2f? = null
        var maxArea = 0.0
        val minArea = src.rows() * src.cols() * 0.15

        for (c in contours) {
            val contour2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(approx)
                if (area > maxArea && area > minArea) {
                    maxArea = area
                    bestApprox = approx
                }
            }
        }

        val points = bestApprox?.toArray() ?: return null
        if (points.size != 4) return null

        // Order points: top-left, top-right, bottom-right, bottom-left
        val sorted = orderCorners(points)
        return QuadCorners(
            tl = PointF(sorted[0].x.toFloat(), sorted[0].y.toFloat()),
            tr = PointF(sorted[1].x.toFloat(), sorted[1].y.toFloat()),
            br = PointF(sorted[2].x.toFloat(), sorted[2].y.toFloat()),
            bl = PointF(sorted[3].x.toFloat(), sorted[3].y.toFloat()),
        )
    }

    /** Order 4 points as: top-left, top-right, bottom-right, bottom-left. */
    private fun orderCorners(pts: Array<Point>): List<Point> {
        val sorted = pts.sortedBy { it.x + it.y }
        val tl = sorted.first()
        val br = sorted.last()
        val remaining = pts.filter { it != tl && it != br }
        val tr = remaining.minByOrNull { it.y - it.x }!!
        val bl = remaining.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }
}

/** Quad corners in bitmap pixel coordinates, in tl, tr, br, bl order. */
data class QuadCorners(val tl: PointF, val tr: PointF, val br: PointF, val bl: PointF)
