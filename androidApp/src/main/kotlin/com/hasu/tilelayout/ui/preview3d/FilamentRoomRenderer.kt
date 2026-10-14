package com.hasu.tilelayout.ui.preview3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.UbershaderProvider
import com.hasu.tilelayout.scene.CameraPose
import com.hasu.tilelayout.scene.MeshPart
import com.hasu.tilelayout.scene.OrbitCamera
import com.hasu.tilelayout.scene.RoomScene
import com.hasu.tilelayout.scene.SceneColors
import com.hasu.tilelayout.scene.SceneLighting
import com.hasu.tilelayout.scene.SceneMaterial
import com.hasu.tilelayout.scene.SurfaceMesh
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Renders a [RoomScene] into a [SurfaceView] with Filament.
 *
 * Frames are drawn on demand (only after the scene, camera, visibility or
 * selection changes) rather than in a continuous loop, which keeps the GPU
 * idle while the user is just looking. Materials are gltfio's precompiled
 * unlit ubershaders; lighting comes from [SceneLighting] baked into vertex
 * colours, so shading matches iOS exactly and stays cheap on mobile GPUs.
 * Every Filament call happens on the main thread.
 */
class FilamentRoomRenderer(private val surfaceView: SurfaceView) {

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val skybox = Skybox.Builder().color(linear(SceneColors.BACKGROUND, 1f)).build(engine)
    private val colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
    private val materials = UbershaderProvider(engine)
    private val sampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(surfaceView.context)
    private val choreographer = Choreographer.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val textureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var swapChain: SwapChain? = null
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var frameScheduled = false
    private var destroyed = false

    private var roomScene = RoomScene.EMPTY
    private var orbit = OrbitCamera()
    private var hiddenIds: Set<String> = emptySet()
    private var selectedId: String? = null

    private val surfaces = linkedMapOf<String, GpuSurface>()
    private val textures = mutableMapOf<String, Texture>()
    private val loadingTextures = mutableSetOf<String>()
    private val captureMutex = Mutex()
    private var pendingCapture: PendingCapture? = null

    private class GpuPart(
        val entity: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
        var material: MaterialInstance,
        val sceneMaterial: SceneMaterial,
        /** Linear RGB base colour before any selection tint. */
        var baseColor: FloatArray,
    )

    private class GpuSurface(val mesh: SurfaceMesh, val parts: List<GpuPart>) {
        var visible = false
    }

    /** Offscreen resources of one export, alive until Filament has finished reading them back. */
    private class PendingCapture(
        val view: View,
        val cameraEntity: Int,
        val target: RenderTarget,
        val color: Texture,
        val depth: Texture,
    ) {
        val done = CompletableDeferred<Boolean>()
        var released = false
    }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameScheduled = false
        renderFrame(frameTimeNanos)
    }

    init {
        configureView(view, camera)
        view.scene = scene
        scene.skybox = skybox

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                surfaceView.display?.let { displayHelper.attach(renderer, it) }
                requestFrame()
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                viewportWidth = width
                viewportHeight = height
                view.viewport = Viewport(0, 0, width, height)
                updateCamera()
                requestFrame()
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    // -- State updates (cheap no-ops when nothing changed) --

    fun setScene(newScene: RoomScene) {
        if (newScene === roomScene) return
        val boundsChanged = newScene.bounds != roomScene.bounds
        roomScene = newScene

        val incoming = newScene.surfaces.associateBy { it.surfaceId }
        val stale = surfaces.filter { (id, gpu) -> incoming[id] !== gpu.mesh }.keys
        stale.forEach { id -> surfaces.remove(id)?.let(::destroySurface) }
        for (mesh in newScene.surfaces) {
            if (mesh.surfaceId !in surfaces) surfaces[mesh.surfaceId] = createSurface(mesh)
        }
        releaseUnusedTextures()
        applyVisibility()
        applySelection(selectedId)
        if (boundsChanged) updateCamera()
        requestFrame()
    }

    fun setCamera(camera: OrbitCamera) {
        if (camera == orbit) return
        orbit = camera
        updateCamera()
        requestFrame()
    }

    fun setHiddenSurfaces(ids: Set<String>) {
        if (ids == hiddenIds) return
        hiddenIds = ids
        applyVisibility()
        requestFrame()
    }

    fun setSelectedSurface(id: String?) {
        if (id == selectedId) return
        selectedId = id
        applySelection(id)
        requestFrame()
    }

    /**
     * Render the current view offscreen at [width]×[height] for export, without
     * the selection highlight. The camera keeps its orbit but adapts to the
     * export aspect ratio. Captures run one at a time; cancelling the caller
     * abandons the result but never frees the offscreen target while Filament
     * may still be reading it back.
     */
    suspend fun capture(width: Int, height: Int): Bitmap? = captureMutex.withLock {
        if (destroyed || width <= 0 || height <= 0) return@withLock null
        val pixels = withContext(NonCancellable) { renderOffscreen(width, height) } ?: return@withLock null
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.Default) { pixelsToBitmap(pixels, width, height) }
    }

    private suspend fun renderOffscreen(width: Int, height: Int): ByteBuffer? {
        if (destroyed) return null
        val color = Texture.Builder()
            .width(width).height(height).levels(1)
            .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE or Texture.Usage.BLIT_SRC)
            .format(Texture.InternalFormat.RGBA8)
            .build(engine)
        val depth = Texture.Builder()
            .width(width).height(height).levels(1)
            .usage(Texture.Usage.DEPTH_ATTACHMENT)
            .format(Texture.InternalFormat.DEPTH24)
            .build(engine)
        val target = RenderTarget.Builder()
            .texture(RenderTarget.AttachmentPoint.COLOR, color)
            .texture(RenderTarget.AttachmentPoint.DEPTH, depth)
            .build(engine)
        val captureEntity = EntityManager.get().create()
        val captureCamera = engine.createCamera(captureEntity)
        applyPose(captureCamera, orbit.pose(roomScene.bounds, width.toDouble() / height), width.toDouble() / height)
        val captureView = engine.createView().also {
            configureView(it, captureCamera)
            it.scene = scene
            it.viewport = Viewport(0, 0, width, height)
            it.renderTarget = target
        }
        val capture = PendingCapture(captureView, captureEntity, target, color, depth)
        pendingCapture = capture

        val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        // Material parameters are committed when the view is rendered, so the
        // live highlight can come back before the next on-screen frame.
        applySelection(null)
        renderer.renderStandaloneView(captureView)
        applySelection(selectedId)
        renderer.readPixels(
            target, 0, 0, width, height,
            Texture.PixelBufferDescriptor(
                pixels, Texture.Format.RGBA, Texture.Type.UBYTE,
                1, 0, 0, 0, mainHandler,
            ) { capture.done.complete(true) },
        )
        engine.flush()
        // Filament dispatches the read-back callback from later frames, so keep them coming until it lands
        requestFrame()

        val completed = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { capture.done.await() } ?: false
        if (!capture.released) {
            // Without the callback the GPU may still be busy with the target: wait before freeing it
            if (!completed) engine.flushAndWait()
            releaseCapture(capture)
        }
        if (pendingCapture === capture) pendingCapture = null
        requestFrame()
        return if (completed) pixels else null
    }

    private fun releaseCapture(capture: PendingCapture) {
        if (capture.released) return
        capture.released = true
        engine.destroyView(capture.view)
        engine.destroyCameraComponent(capture.cameraEntity)
        EntityManager.get().destroy(capture.cameraEntity)
        engine.destroyRenderTarget(capture.target)
        engine.destroyTexture(capture.color)
        engine.destroyTexture(capture.depth)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(frameCallback)
        textureScope.cancel()
        pendingCapture?.let { capture ->
            capture.done.complete(false)
            engine.flushAndWait()
            releaseCapture(capture)
        }
        pendingCapture = null
        uiHelper.detach()
        surfaces.values.forEach(::destroySurface)
        surfaces.clear()
        textures.values.forEach(engine::destroyTexture)
        textures.clear()
        materials.destroyMaterials()
        materials.destroy()
        engine.destroySkybox(skybox)
        engine.destroyColorGrading(colorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    // -- Frame loop --

    private fun requestFrame() {
        if (frameScheduled || destroyed) return
        frameScheduled = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun renderFrame(frameTimeNanos: Long) {
        if (destroyed) return
        val target = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        if (renderer.beginFrame(target, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            if (pendingCapture != null) requestFrame()
        } else {
            // Filament skipped this vsync to keep pace with the GPU; try again on the next one
            requestFrame()
        }
    }

    private fun configureView(target: View, viewCamera: Camera) {
        viewCamera.setExposure(1f)
        target.camera = viewCamera
        target.colorGrading = colorGrading
        target.setShadowingEnabled(false)
        target.setScreenSpaceRefractionEnabled(false)
        target.antiAliasing = View.AntiAliasing.NONE
        target.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply {
            enabled = true
            sampleCount = 4
        }
    }

    private fun updateCamera() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val aspect = viewportWidth.toDouble() / viewportHeight
        applyPose(camera, orbit.pose(roomScene.bounds, aspect), aspect)
    }

    private fun applyPose(target: Camera, pose: CameraPose, aspect: Double) {
        target.setProjection(pose.fovYDegrees, aspect, pose.near, pose.far, Camera.Fov.VERTICAL)
        target.lookAt(
            pose.eye.x, pose.eye.y, pose.eye.z,
            pose.target.x, pose.target.y, pose.target.z,
            pose.up.x, pose.up.y, pose.up.z,
        )
    }

    // -- Scene graph --

    private fun applyVisibility() {
        for ((id, gpu) in surfaces) {
            val visible = id !in hiddenIds
            if (visible == gpu.visible) continue
            gpu.visible = visible
            for (part in gpu.parts) {
                if (visible) scene.addEntity(part.entity) else scene.remove(part.entity)
            }
        }
    }

    private fun applySelection(id: String?) {
        val tint = linear(SceneColors.SELECTION_TINT, 1f)
        for ((surfaceId, gpu) in surfaces) {
            val selected = surfaceId == id
            for (part in gpu.parts) {
                val c = part.baseColor
                if (selected) {
                    part.material.setParameter("baseColorFactor", c[0] * tint[0], c[1] * tint[1], c[2] * tint[2], 1f)
                } else {
                    part.material.setParameter("baseColorFactor", c[0], c[1], c[2], 1f)
                }
            }
        }
    }

    private fun createSurface(mesh: SurfaceMesh): GpuSurface {
        val box = Box(
            mesh.bounds.center.x.toFloat(), mesh.bounds.center.y.toFloat(), mesh.bounds.center.z.toFloat(),
            ((mesh.bounds.max.x - mesh.bounds.min.x) / 2).toFloat(),
            ((mesh.bounds.max.y - mesh.bounds.min.y) / 2).toFloat(),
            ((mesh.bounds.max.z - mesh.bounds.min.z) / 2).toFloat(),
        )
        return GpuSurface(mesh, mesh.parts.filter { it.indexCount > 0 }.map { createPart(it, box) })
    }

    private fun createPart(part: MeshPart, box: Box): GpuPart {
        val vertexCount = part.vertexCount
        val shades = ByteBuffer.allocateDirect(vertexCount * 4).order(ByteOrder.nativeOrder())
        val vertexShades = part.vertexShades
        for (i in 0 until vertexCount) {
            val b = (vertexShades[i] * 255).roundToInt().coerceIn(0, 255).toByte()
            shades.put(i * 4, b).put(i * 4 + 1, b).put(i * 4 + 2, b).put(i * 4 + 3, 0xFF.toByte())
        }

        val vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 1, VertexBuffer.AttributeType.UBYTE4, 0, 4)
            .normalized(VertexBuffer.VertexAttribute.COLOR)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, floatBuffer(part.positions))
        vertexBuffer.setBufferAt(engine, 1, shades)
        vertexBuffer.setBufferAt(engine, 2, floatBuffer(part.uvs))

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(part.indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        indexBuffer.setBuffer(engine, intBuffer(part.indices))

        val texture = texturePath(part.material)?.let { path -> textures[path] ?: run { loadTexture(path); null } }
        val (material, baseColor) = createMaterial(part.material, texture)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(box)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, part.indexCount)
            .material(0, material)
            .culling(true)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, entity)
        return GpuPart(entity, vertexBuffer, indexBuffer, material, part.material, baseColor)
    }

    private fun createMaterial(sceneMaterial: SceneMaterial, texture: Texture?): Pair<MaterialInstance, FloatArray> {
        val key = MaterialProvider.MaterialKey().apply {
            unlit = true
            hasVertexColors = true
            hasBaseColorTexture = texture != null
            baseColorUV = 0
        }
        val instance = checkNotNull(materials.createMaterialInstance(key, IntArray(8), "room", null)) {
            "No ubershader variant for room material"
        }
        val baseColor = when {
            texture != null -> floatArrayOf(1f, 1f, 1f)
            sceneMaterial is SceneMaterial.Solid -> linear(sceneMaterial.color, 1f).copyOf(3)
            sceneMaterial is SceneMaterial.Tile -> linear(sceneMaterial.color, 1f).copyOf(3)
            else -> floatArrayOf(1f, 1f, 1f)
        }
        if (texture != null) instance.setParameter("baseColorMap", texture, sampler)
        instance.setParameter("baseColorFactor", baseColor[0], baseColor[1], baseColor[2], 1f)
        return instance to baseColor
    }

    private fun destroySurface(gpu: GpuSurface) {
        for (part in gpu.parts) {
            scene.remove(part.entity)
            engine.destroyEntity(part.entity)
            EntityManager.get().destroy(part.entity)
            engine.destroyVertexBuffer(part.vertexBuffer)
            engine.destroyIndexBuffer(part.indexBuffer)
            engine.destroyMaterialInstance(part.material)
        }
    }

    // -- Textures --

    private fun texturePath(material: SceneMaterial): String? = (material as? SceneMaterial.Tile)?.texturePath

    private fun loadTexture(path: String) {
        if (!loadingTextures.add(path)) return
        textureScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeTexture(path) }
            loadingTextures.remove(path)
            if (destroyed || bitmap == null) return@launch
            if (path !in usedTexturePaths()) {
                bitmap.recycle()
                return@launch
            }
            val texture = uploadTexture(bitmap)
            textures[path] = texture
            swapInTexture(path, texture)
            requestFrame()
        }
    }

    private fun usedTexturePaths(): Set<String> =
        surfaces.values.flatMapTo(mutableSetOf()) { gpu -> gpu.parts.mapNotNull { texturePath(it.sceneMaterial) } }

    /** Free textures no remaining part samples, e.g. after a tile group's image was replaced. */
    private fun releaseUnusedTextures() {
        val used = usedTexturePaths()
        val unused = textures.keys.filter { it !in used }
        for (path in unused) textures.remove(path)?.let(engine::destroyTexture)
    }

    /** Give every untextured part that is waiting on [path] a textured material. */
    private fun swapInTexture(path: String, texture: Texture) {
        val renderables = engine.renderableManager
        for (gpu in surfaces.values) for (part in gpu.parts) {
            if (texturePath(part.sceneMaterial) != path) continue
            val (material, baseColor) = createMaterial(part.sceneMaterial, texture)
            renderables.setMaterialInstanceAt(renderables.getInstance(part.entity), 0, material)
            engine.destroyMaterialInstance(part.material)
            part.material = material
            part.baseColor = baseColor
        }
        applySelection(selectedId)
    }

    private fun uploadTexture(bitmap: Bitmap): Texture {
        val levels = floor(ln(max(bitmap.width, bitmap.height).toDouble()) / ln(2.0)).toInt() + 1
        val texture = Texture.Builder()
            .width(bitmap.width)
            .height(bitmap.height)
            .levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .build(engine)
        TextureHelper.setBitmap(engine, texture, 0, bitmap, mainHandler) { bitmap.recycle() }
        texture.generateMipmaps(engine)
        return texture
    }

    companion object {
        init {
            Gltfio.init()
        }

        /** Upper bound on waiting for an export read-back (e.g. while the surface is detached and no frames run). */
        private const val CAPTURE_TIMEOUT_MS = 5_000L

        /** Longest side of decoded tile textures; tiles are small on screen, so this bounds GPU memory. */
        private const val MAX_TEXTURE_SIZE = 512

        private fun decodeTexture(path: String): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_TEXTURE_SIZE) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(path, options)
        }

        /** sRGB `0xRRGGBB` → linear RGBA, as Filament material parameters expect. */
        private fun linear(rgb: Int, alpha: Float): FloatArray {
            fun channel(shift: Int): Float {
                val c = ((rgb shr shift) and 0xFF) / 255.0
                val l = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
                return l.toFloat()
            }
            return floatArrayOf(channel(16), channel(8), channel(0), alpha)
        }

        // Views write through to the backing ByteBuffer without moving its position,
        // so the returned buffer is ready to upload as-is.
        private fun floatBuffer(values: FloatArray): ByteBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
                .also { it.asFloatBuffer().put(values) }

        private fun intBuffer(values: IntArray): ByteBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
                .also { it.asIntBuffer().put(values) }

        /** Filament reads back bottom-up RGBA; Android bitmaps are top-down ARGB. */
        private fun pixelsToBitmap(pixels: ByteBuffer, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val flipped = ByteBuffer.allocateDirect(width * height * 4)
            val row = ByteArray(width * 4)
            // Positioned through java.nio.Buffer: ByteBuffer's covariant overrides don't exist on older Android
            for (y in height - 1 downTo 0) {
                (pixels as Buffer).position(y * width * 4)
                pixels.get(row)
                flipped.put(row)
            }
            (flipped as Buffer).rewind()
            bitmap.copyPixelsFromBuffer(flipped)
            return bitmap
        }
    }
}
