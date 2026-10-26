package com.hasu.tilelayout.scene

/** Axis-aligned rectangle in surface-local millimetres. */
internal data class Rect2(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    val isEmpty: Boolean get() = x1 - x0 <= EPS || y1 - y0 <= EPS

    fun intersect(o: Rect2): Rect2? {
        val r = Rect2(maxOf(x0, o.x0), maxOf(y0, o.y0), minOf(x1, o.x1), minOf(y1, o.y1))
        return if (r.isEmpty) null else r
    }

    fun contains(x: Double, y: Double): Boolean = x in x0..x1 && y in y0..y1

    /** This rectangle minus [hole], as up to four non-overlapping rectangles. */
    fun minus(hole: Rect2?): List<Rect2> {
        val h = hole?.let { intersect(it) } ?: return listOf(this)
        return listOf(
            Rect2(x0, y0, h.x0, y1),
            Rect2(h.x1, y0, x1, y1),
            Rect2(h.x0, y0, h.x1, h.y0),
            Rect2(h.x0, h.y1, h.x1, y1),
        ).filterNot { it.isEmpty }
    }

    companion object {
        private const val EPS = 1e-6
    }
}

/** Texture window of a face: u across local x, v at the low-y and high-y edges (v = 0 is the image top). */
internal data class UvRect(val u0: Double, val u1: Double, val vLow: Double, val vHigh: Double) {
    companion object {
        val FULL = UvRect(0.0, 1.0, 0.0, 1.0)
    }
}

/**
 * Collects quads expressed in a [SurfaceFrame]'s local (x, y, depth)
 * millimetres and emits a [MeshPart] in world metres. Winding is chosen per
 * quad so the front face always points along the requested normal.
 */
internal class MeshAccumulator(private val frame: SurfaceFrame) {
    private var positions = FloatArray(INITIAL_VERTICES * 3)
    private var normals = FloatArray(INITIAL_VERTICES * 3)
    private var uvs = FloatArray(INITIAL_VERTICES * 2)
    private var indices = IntArray(INITIAL_VERTICES * 3 / 2)
    private var vertexCount = 0
    private var indexCount = 0

    private var minX = Double.MAX_VALUE
    private var minY = Double.MAX_VALUE
    private var minZ = Double.MAX_VALUE
    private var maxX = -Double.MAX_VALUE
    private var maxY = -Double.MAX_VALUE
    private var maxZ = -Double.MAX_VALUE

    private val qx = DoubleArray(4)
    private val qy = DoubleArray(4)
    private val qd = DoubleArray(4)
    private val qu = DoubleArray(4)
    private val qv = DoubleArray(4)
    private val px = DoubleArray(4)
    private val py = DoubleArray(4)
    private val pz = DoubleArray(4)

    val isEmpty: Boolean get() = indexCount == 0

    val bounds: Bounds?
        get() = if (vertexCount == 0) null else Bounds(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))

    /** Face parallel to the surface at [depth], facing into the room when [front], else away. */
    fun face(r: Rect2, depth: Double, front: Boolean, uv: UvRect = UvRect.FULL) {
        corner(0, r.x0, r.y0, depth, uv.u0, uv.vLow)
        corner(1, r.x1, r.y0, depth, uv.u1, uv.vLow)
        corner(2, r.x1, r.y1, depth, uv.u1, uv.vHigh)
        corner(3, r.x0, r.y1, depth, uv.u0, uv.vHigh)
        emitQuad(0.0, 0.0, if (front) 1.0 else -1.0)
    }

    /** Face perpendicular to local x at [x], spanning [y0]..[y1] × [d0]..[d1]; faces +x when [positive]. */
    fun sideX(
        x: Double, y0: Double, y1: Double, d0: Double, d1: Double, positive: Boolean,
        u: Double = 0.0, vLow: Double = 0.0, vHigh: Double = 0.0,
    ) {
        corner(0, x, y0, d0, u, vLow)
        corner(1, x, y1, d0, u, vHigh)
        corner(2, x, y1, d1, u, vHigh)
        corner(3, x, y0, d1, u, vLow)
        emitQuad(if (positive) 1.0 else -1.0, 0.0, 0.0)
    }

    /** Face perpendicular to local y at [y], spanning [x0]..[x1] × [d0]..[d1]; faces +y when [positive]. */
    fun sideY(
        y: Double, x0: Double, x1: Double, d0: Double, d1: Double, positive: Boolean,
        u0: Double = 0.0, u1: Double = 0.0, v: Double = 0.0,
    ) {
        corner(0, x0, y, d0, u0, v)
        corner(1, x1, y, d0, u1, v)
        corner(2, x1, y, d1, u1, v)
        corner(3, x0, y, d1, u0, v)
        emitQuad(0.0, if (positive) 1.0 else -1.0, 0.0)
    }

    fun toMeshPart(material: SceneMaterial) = MeshPart(
        material = material,
        positions = positions.copyOf(vertexCount * 3),
        normals = normals.copyOf(vertexCount * 3),
        uvs = uvs.copyOf(vertexCount * 2),
        indices = indices.copyOf(indexCount),
    )

    private fun corner(i: Int, x: Double, y: Double, d: Double, u: Double, v: Double) {
        qx[i] = x; qy[i] = y; qd[i] = d; qu[i] = u; qv[i] = v
    }

    private fun emitQuad(nx: Double, ny: Double, nd: Double) {
        val f = frame
        val wnx = f.u.x * nx + f.v.x * ny + f.normal.x * nd
        val wny = f.u.y * nx + f.v.y * ny + f.normal.y * nd
        val wnz = f.u.z * nx + f.v.z * ny + f.normal.z * nd

        ensureCapacity(4, 6)
        val base = vertexCount
        for (i in 0 until 4) {
            px[i] = (f.origin.x + f.u.x * qx[i] + f.v.x * qy[i] + f.normal.x * qd[i]) * MM_TO_M
            py[i] = (f.origin.y + f.u.y * qx[i] + f.v.y * qy[i] + f.normal.y * qd[i]) * MM_TO_M
            pz[i] = (f.origin.z + f.u.z * qx[i] + f.v.z * qy[i] + f.normal.z * qd[i]) * MM_TO_M
            val p = (base + i) * 3
            positions[p] = px[i].toFloat()
            positions[p + 1] = py[i].toFloat()
            positions[p + 2] = pz[i].toFloat()
            normals[p] = wnx.toFloat()
            normals[p + 1] = wny.toFloat()
            normals[p + 2] = wnz.toFloat()
            val t = (base + i) * 2
            uvs[t] = qu[i].toFloat()
            uvs[t + 1] = qv[i].toFloat()
            minX = minOf(minX, px[i]); maxX = maxOf(maxX, px[i])
            minY = minOf(minY, py[i]); maxY = maxOf(maxY, py[i])
            minZ = minOf(minZ, pz[i]); maxZ = maxOf(maxZ, pz[i])
        }
        vertexCount += 4

        // (p1 − p0) × (p2 − p0) · n decides whether 0-1-2-3 is counter-clockwise
        val ax = px[1] - px[0]; val ay = py[1] - py[0]; val az = pz[1] - pz[0]
        val bx = px[2] - px[0]; val by = py[2] - py[0]; val bz = pz[2] - pz[0]
        val facing = (ay * bz - az * by) * wnx + (az * bx - ax * bz) * wny + (ax * by - ay * bx) * wnz
        val order = if (facing >= 0) CCW else CW
        for (k in order) indices[indexCount++] = base + k
    }

    private fun ensureCapacity(extraVertices: Int, extraIndices: Int) {
        val neededVertices = vertexCount + extraVertices
        if (neededVertices * 3 > positions.size) {
            val newVertices = maxOf(neededVertices, positions.size / 3 * 2)
            positions = positions.copyOf(newVertices * 3)
            normals = normals.copyOf(newVertices * 3)
            uvs = uvs.copyOf(newVertices * 2)
        }
        val neededIndices = indexCount + extraIndices
        if (neededIndices > indices.size) {
            indices = indices.copyOf(maxOf(neededIndices, indices.size * 2))
        }
    }

    companion object {
        const val MM_TO_M = 0.001
        private const val INITIAL_VERTICES = 64
        private val CCW = intArrayOf(0, 1, 2, 0, 2, 3)
        private val CW = intArrayOf(0, 2, 1, 0, 3, 2)
    }
}
