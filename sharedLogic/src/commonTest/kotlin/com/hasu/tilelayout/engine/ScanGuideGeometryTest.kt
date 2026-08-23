package com.hasu.tilelayout.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanGuideGeometryTest {

    private val inset = 0.85

    private fun frame(vw: Double, vh: Double, tw: Double, th: Double) =
        ScanGuideGeometry.frame(vw, vh, tw, th, inset)

    @Test
    fun landscapeTileInPortraitViewportRotates() {
        val f = frame(400.0, 800.0, 600.0, 300.0)
        assertTrue(f.rotated)
        assertTrue(f.height > f.width, "frame should be taller than wide")
        assertEquals(340.0, f.width, 0.01)
        assertEquals(680.0, f.height, 0.01)
    }

    @Test
    fun portraitTileInPortraitViewportNotRotated() {
        val f = frame(400.0, 800.0, 300.0, 600.0)
        assertFalse(f.rotated)
        assertEquals(340.0, f.width, 0.01)
        assertEquals(680.0, f.height, 0.01)
    }

    @Test
    fun landscapeTileInLandscapeViewportNotRotated() {
        val f = frame(800.0, 400.0, 600.0, 300.0)
        assertFalse(f.rotated)
        assertEquals(680.0, f.width, 0.01)
        assertEquals(340.0, f.height, 0.01)
    }

    @Test
    fun squareTileTieBreakStaysNatural() {
        val f = frame(400.0, 800.0, 300.0, 300.0)
        assertFalse(f.rotated)
        assertEquals(f.width, f.height, 0.01)
    }

    @Test
    fun frameAlwaysCentered() {
        val cases = listOf(
            Triple(400.0, 800.0, 600.0 to 300.0),
            Triple(800.0, 400.0, 600.0 to 300.0),
            Triple(500.0, 900.0, 400.0 to 300.0),
            Triple(1000.0, 600.0, 200.0 to 400.0),
            Triple(200.0, 300.0, 800.0 to 400.0),
        )
        for ((vw, vh, tile) in cases) {
            val f = frame(vw, vh, tile.first, tile.second)
            assertEquals(vw, f.x * 2 + f.width, 0.01)
            assertEquals(vh, f.y * 2 + f.height, 0.01)
        }
    }

    @Test
    fun frameNeverExceedsInset() {
        val cases = listOf(
            Triple(400.0, 800.0, 600.0 to 300.0),
            Triple(800.0, 400.0, 600.0 to 300.0),
            Triple(500.0, 900.0, 400.0 to 300.0),
            Triple(1000.0, 600.0, 200.0 to 400.0),
            Triple(200.0, 300.0, 800.0 to 400.0),
        )
        for ((vw, vh, tile) in cases) {
            val f = frame(vw, vh, tile.first, tile.second)
            assertTrue(f.width <= vw * inset + 0.01, "width ${f.width} exceeds inset")
            assertTrue(f.height <= vh * inset + 0.01, "height ${f.height} exceeds inset")
        }
    }

    @Test
    fun framePreservesTileAspectRatio() {
        val cases = listOf(
            Triple(400.0, 800.0, 600.0 to 300.0),
            Triple(800.0, 400.0, 600.0 to 300.0),
            Triple(500.0, 900.0, 400.0 to 300.0),
            Triple(1000.0, 600.0, 200.0 to 400.0),
            Triple(400.0, 800.0, 300.0 to 300.0),
        )
        for ((vw, vh, tile) in cases) {
            val f = frame(vw, vh, tile.first, tile.second)
            val frameAspect = f.width / f.height
            val natural = tile.first / tile.second
            val swapped = tile.second / tile.first
            assertTrue(
                abs(frameAspect - natural) <= 0.01 || abs(frameAspect - swapped) <= 0.01,
                "aspect $frameAspect not within 0.01 of $natural or $swapped"
            )
        }
    }

    @Test
    fun cornersReturnsTlTrBrBl() {
        val f = frame(400.0, 800.0, 600.0, 300.0)
        val c = ScanGuideGeometry.corners(f)
        assertEquals(4, c.size)
        assertEquals(Pair(f.x, f.y), c[0])
        assertEquals(Pair(f.x + f.width, f.y), c[1])
        assertEquals(Pair(f.x + f.width, f.y + f.height), c[2])
        assertEquals(Pair(f.x, f.y + f.height), c[3])
    }

    @Test
    fun unrotationOrderMatchesRotation() {
        assertEquals(listOf(0, 1, 2, 3), ScanGuideGeometry.unrotationOrder(false))
        assertEquals(listOf(1, 2, 3, 0), ScanGuideGeometry.unrotationOrder(true))
    }

    @Test
    fun isRotatedAgainstLandscapeTile() {
        // Quad framed portrait (100×200) of a landscape tile (600×300) → rotated
        assertTrue(ScanGuideGeometry.isRotated(100.0, 200.0, 600.0, 300.0))
        // Quad framed landscape (200×100) → natural
        assertFalse(ScanGuideGeometry.isRotated(200.0, 100.0, 600.0, 300.0))
    }

    @Test
    fun isRotatedSymmetricInLogSpace() {
        assertTrue(ScanGuideGeometry.isRotated(100.0, 200.0, 600.0, 300.0))
        assertFalse(ScanGuideGeometry.isRotated(200.0, 100.0, 600.0, 300.0))
    }

    @Test
    fun isRotatedSquareTileAlwaysFalse() {
        // natural == reciprocal, so both distances are equal → never rotated
        assertFalse(ScanGuideGeometry.isRotated(100.0, 200.0, 300.0, 300.0))
        assertFalse(ScanGuideGeometry.isRotated(200.0, 100.0, 300.0, 300.0))
        assertFalse(ScanGuideGeometry.isRotated(150.0, 150.0, 300.0, 300.0))
    }

    @Test
    fun isRotatedAgreesWithFrame() {
        // Landscape tile framed rotated in a portrait viewport
        val rotated = frame(400.0, 800.0, 600.0, 300.0)
        assertEquals(
            rotated.rotated,
            ScanGuideGeometry.isRotated(rotated.width, rotated.height, 600.0, 300.0)
        )
        // Portrait tile framed naturally in a portrait viewport
        val natural = frame(400.0, 800.0, 300.0, 600.0)
        assertEquals(
            natural.rotated,
            ScanGuideGeometry.isRotated(natural.width, natural.height, 300.0, 600.0)
        )
    }

    @Test
    fun zeroOrNegativeDimensionsThrow() {
        assertFailsWith<IllegalArgumentException> { frame(0.0, 800.0, 600.0, 300.0) }
        assertFailsWith<IllegalArgumentException> { frame(400.0, -1.0, 600.0, 300.0) }
        assertFailsWith<IllegalArgumentException> { frame(400.0, 800.0, 0.0, 300.0) }
        assertFailsWith<IllegalArgumentException> { frame(400.0, 800.0, 600.0, 0.0) }
        assertFailsWith<IllegalArgumentException> {
            ScanGuideGeometry.isRotated(0.0, 200.0, 600.0, 300.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ScanGuideGeometry.isRotated(100.0, -200.0, 600.0, 300.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ScanGuideGeometry.isRotated(100.0, 200.0, 0.0, 300.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ScanGuideGeometry.isRotated(100.0, 200.0, 600.0, -300.0)
        }
    }
}
