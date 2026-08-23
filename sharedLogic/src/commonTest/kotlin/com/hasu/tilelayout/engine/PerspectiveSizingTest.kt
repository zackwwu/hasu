package com.hasu.tilelayout.engine

import kotlin.test.*

class PerspectiveSizingTest {
    @Test
    fun landscapeTileFitsMaxDimensionOnLongSide() {
        val s = PerspectiveSizing.outputSize(600.0, 300.0, 512)
        assertEquals(512, s.width)
        assertEquals(256, s.height)
    }

    @Test
    fun portraitTileFitsMaxDimensionOnLongSide() {
        val s = PerspectiveSizing.outputSize(300.0, 600.0, 512)
        assertEquals(256, s.width)
        assertEquals(512, s.height)
    }

    @Test
    fun squareTileFillsBothSides() {
        val s = PerspectiveSizing.outputSize(300.0, 300.0, 512)
        assertEquals(512, s.width)
        assertEquals(512, s.height)
    }

    @Test
    fun customMaxDimensionRespected() {
        val s = PerspectiveSizing.outputSize(600.0, 300.0, 256)
        assertEquals(256, s.width)
        assertEquals(128, s.height)
    }

    @Test
    fun extremeAspectNeverProducesZeroDimension() {
        // 300mm × 0.25mm — absurd but typable input; the warp must not crash on a
        // zero-dimension output bitmap.
        val landscape = PerspectiveSizing.outputSize(300.0, 0.25, 512)
        assertEquals(512, landscape.width)
        assertEquals(1, landscape.height)
        val portrait = PerspectiveSizing.outputSize(0.25, 300.0, 512)
        assertEquals(1, portrait.width)
        assertEquals(512, portrait.height)
    }

    @Test
    fun nonPositiveDimensionsThrow() {
        assertFailsWith<IllegalArgumentException> { PerspectiveSizing.outputSize(0.0, 300.0, 512) }
        assertFailsWith<IllegalArgumentException> { PerspectiveSizing.outputSize(300.0, -1.0, 512) }
        assertFailsWith<IllegalArgumentException> { PerspectiveSizing.outputSize(300.0, 300.0, 0) }
    }
}
