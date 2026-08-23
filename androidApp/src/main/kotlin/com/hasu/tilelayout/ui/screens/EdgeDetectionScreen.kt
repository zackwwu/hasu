package com.hasu.tilelayout.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hasu.tilelayout.data.AppDatabase
import com.hasu.tilelayout.db.SqlDelightTileGroupRepository
import com.hasu.tilelayout.engine.ScanGuideGeometry
import com.hasu.tilelayout.models.TileGroup
import com.hasu.tilelayout.models.TileSource
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CameraX scanner for capturing a tile texture. Shows a live preview with an
 * aspect-ratio guide frame and an overlay of the OpenCV-detected quad; capture
 * opens a corner-review screen (seeded from the detection, or from the guide
 * frame when nothing was found) and saves the perspective-corrected result to
 * <filesDir>/textures/<tileGroupId>.png, updating the TileGroup in place.
 */
@Composable
fun EdgeDetectionScreen(
    tileGroupId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tileGroupRepo = remember {
        SqlDelightTileGroupRepository(queries = AppDatabase.instance.tileLayoutDbQueries)
    }
    val scope = rememberCoroutineScope()
    val analyzerExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var tileGroup by remember { mutableStateOf<TileGroup?>(null) }
    var tileGroupLoaded by remember { mutableStateOf(false) }
    var detectedCorners by remember { mutableStateOf<QuadCorners?>(null) }
    var isStable by remember { mutableStateOf(false) }
    var showCornerReview by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // True while the corner review is up: freezes the analyzer so the frame under
    // review can't be replaced behind the review screen.
    var paused by remember { mutableStateOf(false) }

    // Stability tracking
    var stableCount by remember { mutableIntStateOf(0) }
    var lastCorners by remember { mutableStateOf<QuadCorners?>(null) }
    val stableFrameThreshold = 10  // ~330ms at 30fps

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(tileGroupId) {
        tileGroup = tileGroupRepo.getById(tileGroupId)
        tileGroupLoaded = true
    }

    // Unbind the camera when leaving the screen; otherwise the use cases stay bound
    // to the activity lifecycle and the camera keeps streaming behind other screens.
    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            runCatching {
                if (cameraProviderFuture.isDone) cameraProviderFuture.get().unbindAll()
            }
        }
    }

    val tg = tileGroup
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            tileGroupLoaded && tg == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Tile group not found", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onDismiss) { Text("Back") }
                }
            }

            tg == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            !permissionGranted -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Camera access needed to capture tile textures",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDismiss) { Text("Back") }
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant access")
                        }
                    }
                }
            }

            else -> {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build()
                                .also { it.setSurfaceProvider(pv.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                if (paused) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val bitmap = imageProxy.toBitmap()
                                val corners = EdgeDetector.detectQuadCorners(bitmap)
                                imageProxy.close()
                                // Kept on EVERY frame, not just detected ones — capture must
                                // work when detection finds nothing.
                                capturedBitmap = bitmap
                                if (corners != null) {
                                    if (lastCorners != null && cornersClose(lastCorners!!, corners)) {
                                        stableCount++
                                        isStable = stableCount >= stableFrameThreshold
                                    } else {
                                        stableCount = 0
                                        isStable = false
                                    }
                                    lastCorners = corners
                                    detectedCorners = corners
                                } else {
                                    stableCount = 0
                                    isStable = false
                                    detectedCorners = null
                                }
                            }
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Guide frame first, so a live detection draws on top of it.
                ScanGuideOverlay(
                    tileWidth = tg.tileWidth,
                    tileHeight = tg.tileHeight,
                )
                EdgeOverlay(corners = detectedCorners, bitmap = capturedBitmap)
                // Capture is NOT gated on a detection: with no quad found, Review Corners
                // opens seeded from the guide frame instead of dead-ending.
                CaptureControls(
                    isStable = isStable,
                    hint = when {
                        detectedCorners == null -> "Align the tile with the frame"
                        !isStable -> "Hold steady…"
                        else -> null
                    },
                    canCapture = capturedBitmap != null,
                    onCapture = {
                        paused = true
                        showCornerReview = true
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }

    if (showCornerReview && tg != null) {
        CornerReviewScreen(
            bitmap = capturedBitmap,
            initialCorners = detectedCorners,   // nullable — screen seeds from the guide
            tileWidth = tg.tileWidth,
            tileHeight = tg.tileHeight,
            onAccept = { corrected ->
                scope.launch {
                    val tileGroup = tileGroupRepo.getById(tileGroupId) ?: return@launch
                    val dir = File(context.filesDir, "textures").apply { mkdirs() }
                    val path = File(dir, "$tileGroupId.png")
                    FileOutputStream(path).use {
                        corrected.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    tileGroupRepo.insert(
                        tileGroup.copy(
                            texturePath = path.absolutePath,
                            source = TileSource.CAPTURED,
                        )
                    )
                    onDismiss()
                }
            },
            onRetry = {
                paused = false
                showCornerReview = false
            },
        )
    }
}

/**
 * Dashed frame at the tile's aspect ratio, so the user can square the tile up before
 * shooting. Geometry comes from the shared ScanGuideGeometry, so Android and iOS frame
 * identically. Padding reserves room for the scanner's own chrome.
 */
@Composable
private fun ScanGuideOverlay(tileWidth: Double, tileHeight: Double) {
    val label = "${tileWidth.roundToInt()} × ${tileHeight.roundToInt()} mm"
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 56.dp, bottom = 140.dp)
    ) {
        val guide = ScanGuideGeometry.frame(
            viewportWidth = size.width.toDouble(),
            viewportHeight = size.height.toDouble(),
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            insetFraction = 0.85,
        )
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(guide.x.toFloat(), guide.y.toFloat()),
            size = Size(guide.width.toFloat(), guide.height.toFloat()),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                ),
            ),
        )
        // Confirms which tile group is being scanned.
        val measured = textMeasurer.measure(label, labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            style = labelStyle,
            topLeft = Offset(
                (guide.x + guide.width / 2).toFloat() - measured.size.width / 2f,
                (guide.y + guide.height).toFloat() + 8.dp.toPx(),
            ),
        )
    }
}

/**
 * Live overlay of the detector's current quad. Detector corners are in bitmap pixel
 * space; the preview fills the view with a center-crop, so corners map to view space
 * with the same scale/offset PreviewView applies. Not pixel-perfect when the analysis
 * and preview streams differ in aspect ratio, but close enough to aim by — the corner
 * review is the precise step.
 */
@Composable
private fun EdgeOverlay(corners: QuadCorners?, bitmap: Bitmap?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val bmp = bitmap ?: return@Canvas
        val quad = corners ?: return@Canvas
        val scale = max(size.width / bmp.width, size.height / bmp.height)
        val offset = Offset(
            (size.width - bmp.width * scale) / 2f,
            (size.height - bmp.height * scale) / 2f,
        )
        fun view(p: PointF) = Offset(offset.x + p.x * scale, offset.y + p.y * scale)
        val color = Color(0xFF4CAF50)
        val points = listOf(quad.tl, quad.tr, quad.br, quad.bl)
        for (i in 0..3) {
            val a = view(points[i])
            val b = view(points[(i + 1) % 4])
            drawLine(color, a, b, strokeWidth = 2.dp.toPx())
        }
        points.forEach {
            drawCircle(color, radius = 5.dp.toPx(), center = view(it))
        }
    }
}

@Composable
private fun BoxScope.CaptureControls(
    isStable: Boolean,
    hint: String?,
    canCapture: Boolean,
    onCapture: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val status = when {
            isStable -> "Steady — corners locked"
            hint != null -> hint
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = if (isStable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss) { Text("Back") }
            Button(onClick = onCapture, enabled = canCapture) { Text("Capture") }
        }
    }
}

private fun cornersClose(a: QuadCorners, b: QuadCorners): Boolean {
    fun dist(p1: PointF, p2: PointF) = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble())
    return dist(a.tl, b.tl) < 15 && dist(a.tr, b.tr) < 15 &&
           dist(a.bl, b.bl) < 15 && dist(a.br, b.br) < 15
}
