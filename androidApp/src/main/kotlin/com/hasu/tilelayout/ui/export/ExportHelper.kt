package com.hasu.tilelayout.ui.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.hasu.tilelayout.engine.IsometricProjection
import com.hasu.tilelayout.models.CutEdge
import com.hasu.tilelayout.models.GroutColor
import com.hasu.tilelayout.models.PlacedTile
import com.hasu.tilelayout.models.Surface
import com.hasu.tilelayout.models.SurfaceType
import java.io.File
import java.io.FileOutputStream

object ExportHelper {
    const val DEFAULT_DPI = 200
    const val MIN_DPI = 150
    const val MAX_DPI = 300

    /**
     * Render a Composable drawing lambda to a Bitmap at the given pixel size.
     * The background is cleared to white so exported PNGs have a clean,
     * non-transparent backdrop.
     */
    fun renderToBitmap(
        widthPx: Int,
        heightPx: Int,
        drawBlock: (android.graphics.Canvas) -> Unit
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        drawBlock(canvas)
        return bitmap
    }

    /**
     * Save bitmap to MediaStore (API 29+) or legacy external storage.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TileLayout")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES + "/TileLayout"
            )
            dir.mkdirs()
            val file = File(dir, "$displayName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            // Notify media scanner
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
            true
        }
    }

    /**
     * Share bitmap via system share sheet.
     */
    fun shareBitmap(context: Context, bitmap: Bitmap, displayName: String) {
        val cacheDir = File(context.cacheDir, "exports")
        cacheDir.mkdirs()
        val file = File(cacheDir, "$displayName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Layout"))
    }
}

// Grout color mapping for android.graphics (mirrors GroutColor.toComposeColor)
fun GroutColor.toArgb(): Int = when (this) {
    GroutColor.BLACK -> android.graphics.Color.BLACK
    GroutColor.GREY -> android.graphics.Color.GRAY
    GroutColor.WHITE -> android.graphics.Color.WHITE
}

// Shade an RGB color by a 0..1 light factor for isometric face shading
private fun shadedArgb(r: Int, g: Int, b: Int, factor: Float): Int =
    android.graphics.Color.rgb(
        (r * factor).toInt().coerceIn(0, 255),
        (g * factor).toInt().coerceIn(0, 255),
        (b * factor).toInt().coerceIn(0, 255),
    )

/**
 * Mirrors [com.hasu.tilelayout.ui.canvas.drawTiles] using android.graphics.Canvas
 * so the 2D layout can be rendered offscreen to a Bitmap for export.
 */
fun drawTilesToCanvas(
    canvas: android.graphics.Canvas,
    tiles: List<PlacedTile>,
    groutColor: GroutColor = GroutColor.GREY,
    groutWidth: Double = 3.0,
    scale: Float = 1f,
) {
    val groutPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = groutColor.toArgb()
    }
    val tilePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
        color = android.graphics.Color.RED
    }

    for (tile in tiles) {
        val x = (tile.x * scale).toFloat()
        val y = (tile.y * scale).toFloat()
        val w = (tile.width * scale).toFloat()
        val h = (tile.height * scale).toFloat()

        // Draw grout (slightly larger rect)
        val g = groutWidth.toFloat() * scale
        canvas.drawRect(x - g, y - g, x + w + g, y + h + g, groutPaint)

        // Draw tile body (lighter for cuts)
        tilePaint.color = if (tile.isCut) 0xFFE8D5B7.toInt() else 0xFFD4A574.toInt()
        canvas.drawRect(x, y, x + w, y + h, tilePaint)

        // Draw cut edge indicators
        for (edge in tile.cutEdges) {
            when (edge) {
                CutEdge.LEFT -> canvas.drawLine(x, y, x, y + h, linePaint)
                CutEdge.RIGHT -> canvas.drawLine(x + w, y, x + w, y + h, linePaint)
                CutEdge.TOP -> canvas.drawLine(x, y, x + w, y, linePaint)
                CutEdge.BOTTOM -> canvas.drawLine(x, y + h, x + w, y + h, linePaint)
            }
        }
    }
}

/**
 * Mirrors [com.hasu.tilelayout.ui.canvas.drawIsometricRoom] using
 * android.graphics.Canvas so the 3D preview can be rendered offscreen.
 */
fun drawIsometricRoomToCanvas(
    canvas: android.graphics.Canvas,
    surfaces: List<Surface>,
    viewAngle: Int,
    selectedSurfaceId: String?,
    widthPx: Float,
    heightPx: Float,
) {
    val fit = IsometricProjection.fitViewport(
        surfaces, viewAngle, widthPx.toDouble(), heightPx.toDouble(),
    )
    val ordered = IsometricProjection.orderSurfaces(surfaces, viewAngle)

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
        color = android.graphics.Color.GRAY
    }

    for (surface in ordered) {
        val corners = IsometricProjection.projectSurfaceCorners(
            surface, viewAngle, fit.originX, fit.originY, fit.scale
        )
        val path = android.graphics.Path().apply {
            moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
            for (i in 1 until corners.size) {
                lineTo(corners[i].x.toFloat(), corners[i].y.toFloat())
            }
            close()
        }

        val isSelected = surface.id == selectedSurfaceId
        val isWall = surface.type == SurfaceType.WALL

        val light = IsometricProjection.faceLightFactor(surface, viewAngle).toFloat()
        val fillColor = when {
            isSelected -> 0x5500BCD4  // cyan highlight
            isWall -> shadedArgb(0xE8, 0xE0, 0xD8, light)
            else -> shadedArgb(0xD4, 0xC8, 0xB8, 0.9f + 0.1f * light)
        }
        fillPaint.color = fillColor
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }
}
