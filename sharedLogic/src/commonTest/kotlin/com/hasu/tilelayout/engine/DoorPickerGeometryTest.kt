package com.hasu.tilelayout.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DoorPickerGeometryTest {

    // Diagram 200×200, room 3000×4000 (non-square):
    // scale = min(200/3000, 200/4000) = 0.05 → rect 150×200 at left=25, top=0.
    private fun hit(x: Double, y: Double): Double? = DoorPickerGeometry.wallAtTap(
        x = x, y = y,
        diagramW = 200.0, diagramH = 200.0,
        roomWidth = 3000.0, roomDepth = 4000.0,
    )

    @Test
    fun frontEdgeBottomReturnsZero() {
        assertEquals(0.0, hit(100.0, 190.0))
    }

    @Test
    fun backEdgeTopReturnsOneEighty() {
        assertEquals(180.0, hit(100.0, 10.0))
    }

    @Test
    fun leftEdgeReturnsNinety() {
        assertEquals(90.0, hit(30.0, 100.0))
    }

    @Test
    fun rightEdgeReturnsTwoSeventy() {
        assertEquals(270.0, hit(170.0, 100.0))
    }

    @Test
    fun centerInsideReturnsNull() {
        assertNull(hit(100.0, 100.0))
    }

    @Test
    fun paddingMarginOutsideRectReturnsNull() {
        // Outside the room rectangle (left padding strip) → no wall
        assertNull(hit(5.0, 100.0))
        // Outside below the front edge → no wall
        assertNull(hit(100.0, 205.0))
    }

    @Test
    fun squareRoomDiagramHitAllEdges() {
        fun squareHit(x: Double, y: Double): Double? = DoorPickerGeometry.wallAtTap(
            x = x, y = y,
            diagramW = 180.0, diagramH = 180.0,
            roomWidth = 3000.0, roomDepth = 3000.0,
        )
        // scale = 0.06 → rect 180×180 fills the diagram entirely
        assertEquals(0.0, squareHit(90.0, 175.0))
        assertEquals(180.0, squareHit(90.0, 5.0))
        assertEquals(90.0, squareHit(5.0, 90.0))
        assertEquals(270.0, squareHit(175.0, 90.0))
        assertNull(squareHit(90.0, 90.0))
    }

    @Test
    fun degenerateInputsReturnNull() {
        assertNull(
            DoorPickerGeometry.wallAtTap(10.0, 10.0, 0.0, 200.0, 3000.0, 4000.0)
        )
        assertNull(
            DoorPickerGeometry.wallAtTap(10.0, 10.0, 200.0, 200.0, 0.0, 4000.0)
        )
    }
}
