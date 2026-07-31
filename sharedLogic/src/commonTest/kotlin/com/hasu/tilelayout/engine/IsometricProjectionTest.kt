package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.math.abs
import kotlin.test.*

class IsometricProjectionTest {

    // --- project() ---

    @Test
    fun originProjectsToScreenOrigin() {
        // have: world point (0,0,0), screen origin at (100,200), viewAngle=0
        // want: screen point exactly at (100, 200)
        val pt = IsometricProjection.project(0.0, 0.0, 0.0, 0, 100.0, 200.0)
        assertEquals(100.0, pt.x, 0.001)
        assertEquals(200.0, pt.y, 0.001)
    }

    @Test
    fun yAxisGoesUpOnScreen() {
        // have: two points at same XZ, one elevated by 100 in Y
        // want: elevated point has LOWER screen Y (renders higher on screen)
        val base = IsometricProjection.project(0.0, 0.0, 0.0, 0, 0.0, 0.0)
        val elevated = IsometricProjection.project(0.0, 100.0, 0.0, 0, 0.0, 0.0)
        assertTrue(elevated.y < base.y, "Higher world Y → lower screen Y")
    }

    @Test
    fun xAxisProjectsRightAndDown() {
        // have: point at (100,0,0) vs origin, viewAngle=0
        // want: positive X in world → moves right on screen (COS30 factor)
        //       and slightly down (SIN30 factor)
        val origin = IsometricProjection.project(0.0, 0.0, 0.0, 0, 0.0, 0.0)
        val xPoint = IsometricProjection.project(100.0, 0.0, 0.0, 0, 0.0, 0.0)
        assertTrue(xPoint.x > origin.x, "Positive world X → right on screen")
        assertTrue(xPoint.y > origin.y, "Positive world X → down on screen (iso perspective)")
    }

    @Test
    fun zAxisProjectsLeftAndDown() {
        // have: point at (0,0,100) vs origin, viewAngle=0
        // want: positive Z in world → moves left on screen (-COS30 factor)
        //       and slightly down (SIN30 factor)
        val origin = IsometricProjection.project(0.0, 0.0, 0.0, 0, 0.0, 0.0)
        val zPoint = IsometricProjection.project(0.0, 0.0, 100.0, 0, 0.0, 0.0)
        assertTrue(zPoint.x < origin.x, "Positive world Z → left on screen")
        assertTrue(zPoint.y > origin.y, "Positive world Z → down on screen (iso perspective)")
    }

    @Test
    fun rotationChangesProjection() {
        // have: same world point (100,0,0), different view angles (0° vs 90°)
        // want: different screen positions
        val pt0 = IsometricProjection.project(100.0, 0.0, 0.0, 0, 0.0, 0.0)
        val pt90 = IsometricProjection.project(100.0, 0.0, 0.0, 90, 0.0, 0.0)
        assertFalse(abs(pt0.x - pt90.x) < 0.001 && abs(pt0.y - pt90.y) < 0.001,
            "Different view angles should produce different screen positions")
    }

    @Test
    fun fullRotationReturnsToOriginal() {
        // have: same point projected at 0° and 360°
        // want: identical screen positions (full rotation is identity)
        val pt0 = IsometricProjection.project(100.0, 50.0, 75.0, 0, 200.0, 300.0)
        val pt360 = IsometricProjection.project(100.0, 50.0, 75.0, 360, 200.0, 300.0)
        assertEquals(pt0.x, pt360.x, 0.001)
        assertEquals(pt0.y, pt360.y, 0.001)
    }

    @Test
    fun rotation180FlipsXZ() {
        // have: point (100,0,0) at 0° and 180°
        // want: 180° rotation negates both rx and rz → screen x flips sign relative to origin
        val pt0 = IsometricProjection.project(100.0, 0.0, 0.0, 0, 0.0, 0.0)
        val pt180 = IsometricProjection.project(100.0, 0.0, 0.0, 180, 0.0, 0.0)
        assertEquals(-pt0.x, pt180.x, 0.001)
    }

    // --- projectSurfaceCorners() ---

    @Test
    fun wallCornersReturns4Points() {
        // have: wall surface with width=1000, height=800
        // want: 4 corner screen points
        val surface = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 800.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val corners = IsometricProjection.projectSurfaceCorners(surface, 0, 500.0, 400.0)
        assertEquals(4, corners.size)
    }

    @Test
    fun wallCornersFormNonDegenerateQuad() {
        // have: wall with non-zero dimensions
        // want: at least 3 distinct screen positions (non-degenerate polygon)
        val surface = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 800.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val corners = IsometricProjection.projectSurfaceCorners(surface, 0, 500.0, 400.0)
        val distinctCorners = corners.distinctBy { "${it.x.toInt()},${it.y.toInt()}" }
        assertTrue(distinctCorners.size >= 3, "Wall corners should form non-degenerate quad")
    }

    @Test
    fun floorCornersSpreadInXZPlane() {
        // have: floor surface (extends in XZ, not XY)
        // want: non-degenerate quad on screen (floor projects differently than wall)
        val surface = Surface(
            roomId = "r1", type = SurfaceType.FLOOR,
            width = 1000.0, height = 800.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val corners = IsometricProjection.projectSurfaceCorners(surface, 0, 500.0, 400.0)
        val distinctCorners = corners.distinctBy { "${it.x.toInt()},${it.y.toInt()}" }
        assertTrue(distinctCorners.size >= 3)
    }

    @Test
    fun wallAndFloorProjectDifferently() {
        // have: wall and floor with same dimensions and position
        // want: different corner projections (wall extends in Y, floor in Z)
        val wall = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 800.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val floor = Surface(
            roomId = "r1", type = SurfaceType.FLOOR,
            width = 1000.0, height = 800.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val wallCorners = IsometricProjection.projectSurfaceCorners(wall, 0, 500.0, 400.0)
        val floorCorners = IsometricProjection.projectSurfaceCorners(floor, 0, 500.0, 400.0)
        assertNotEquals(wallCorners, floorCorners)
    }

    // --- orderSurfaces() ---

    @Test
    fun orderSurfacesFloorsBeforeWalls() {
        // have: list with wall first, floor second
        // want: floors sorted before walls (painter's algorithm: draw back-to-front)
        val floor = Surface(
            roomId = "r1", type = SurfaceType.FLOOR,
            width = 1000.0, height = 800.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val wall = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 2400.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val ordered = IsometricProjection.orderSurfaces(listOf(wall, floor), 0)
        assertEquals(SurfaceType.FLOOR, ordered[0].type)
        assertEquals(SurfaceType.WALL, ordered[1].type)
    }

    @Test
    fun orderSurfacesSortsWallsByDepthAtAngle0() {
        // have: two walls at z=0 and z=2000, viewAngle=0
        //       sort key = x*sin(0) + z*cos(0) = z
        // want: z=0 wall first (back-to-front from camera)
        val wallFront = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 2400.0,
            position = SurfacePosition(0.0, 0.0, 0.0, 0.0),
        )
        val wallBack = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 2400.0,
            position = SurfacePosition(0.0, 0.0, 2000.0, 180.0),
        )
        val ordered = IsometricProjection.orderSurfaces(listOf(wallBack, wallFront), 0)
        assertEquals(0.0, ordered[0].position.z, 0.01)
        assertEquals(2000.0, ordered[1].position.z, 0.01)
    }

    @Test
    fun orderSurfacesPreservesAllSurfaces() {
        // have: 3 walls + 1 floor
        // want: ordered list contains all 4
        val surfaces = listOf(
            Surface(roomId = "r1", type = SurfaceType.WALL, width = 1000.0, height = 2400.0,
                position = SurfacePosition(0.0, 0.0, 0.0, 0.0)),
            Surface(roomId = "r1", type = SurfaceType.FLOOR, width = 1000.0, height = 800.0,
                position = SurfacePosition(0.0, 0.0, 0.0, 0.0)),
            Surface(roomId = "r1", type = SurfaceType.WALL, width = 1000.0, height = 2400.0,
                position = SurfacePosition(1000.0, 0.0, 0.0, 90.0)),
            Surface(roomId = "r1", type = SurfaceType.WALL, width = 1000.0, height = 2400.0,
                position = SurfacePosition(0.0, 0.0, 1000.0, 180.0)),
        )
        val ordered = IsometricProjection.orderSurfaces(surfaces, 45)
        assertEquals(4, ordered.size)
    }

    // --- pointInPolygon() ---

    @Test
    fun pointInsideSquare() {
        // have: 100x100 square polygon, point at center (50,50)
        // want: true (inside)
        val square = listOf(
            IsometricProjection.ScreenPoint(0.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 100.0),
            IsometricProjection.ScreenPoint(0.0, 100.0),
        )
        assertTrue(IsometricProjection.pointInPolygon(50.0, 50.0, square))
    }

    @Test
    fun pointOutsideSquare() {
        // have: 100x100 square, point at (150, 50) — beyond right edge
        // want: false (outside)
        val square = listOf(
            IsometricProjection.ScreenPoint(0.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 100.0),
            IsometricProjection.ScreenPoint(0.0, 100.0),
        )
        assertFalse(IsometricProjection.pointInPolygon(150.0, 50.0, square))
    }

    @Test
    fun pointInsideTriangle() {
        // have: triangle with vertices (50,0), (100,100), (0,100)
        //       centroid ≈ (50, 66.7) is inside
        // want: true
        val triangle = listOf(
            IsometricProjection.ScreenPoint(50.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 100.0),
            IsometricProjection.ScreenPoint(0.0, 100.0),
        )
        assertTrue(IsometricProjection.pointInPolygon(50.0, 60.0, triangle))
    }

    @Test
    fun pointOutsideTriangle() {
        // have: same triangle, point at (0,0) — outside
        // want: false
        val triangle = listOf(
            IsometricProjection.ScreenPoint(50.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 100.0),
            IsometricProjection.ScreenPoint(0.0, 100.0),
        )
        assertFalse(IsometricProjection.pointInPolygon(0.0, 0.0, triangle))
    }

    @Test
    fun pointAboveSquare() {
        // have: 100x100 square, point at (50, -10) — above top edge
        // want: false
        val square = listOf(
            IsometricProjection.ScreenPoint(0.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 100.0),
            IsometricProjection.ScreenPoint(0.0, 100.0),
        )
        assertFalse(IsometricProjection.pointInPolygon(50.0, -10.0, square))
    }

    @Test
    fun pointInConcavePolygon() {
        // have: L-shaped concave polygon (notch at top-right)
        //       vertices: (0,0), (100,0), (100,50), (50,50), (50,100), (0,100)
        // want: (75, 25) inside top-right arm, (75, 75) outside (in the notch)
        val lShape = listOf(
            IsometricProjection.ScreenPoint(0.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 0.0),
            IsometricProjection.ScreenPoint(100.0, 50.0),
            IsometricProjection.ScreenPoint(50.0, 50.0),
            IsometricProjection.ScreenPoint(50.0, 100.0),
            IsometricProjection.ScreenPoint(0.0, 100.0),
        )
        assertTrue(IsometricProjection.pointInPolygon(75.0, 25.0, lShape), "(75,25) inside top-right arm")
        assertFalse(IsometricProjection.pointInPolygon(75.0, 75.0, lShape), "(75,75) in concave notch")
        assertTrue(IsometricProjection.pointInPolygon(25.0, 75.0, lShape), "(25,75) inside bottom-left")
    }

    @Test
    fun projectNumericPrecision() {
        // have: point (100, 0, 0), viewAngle=0, origin=(0,0)
        //   rx = 100*cos(0) - 0*sin(0) = 100
        //   rz = 100*sin(0) + 0*cos(0) = 0
        //   screenX = (100 - 0) * COS30 = 100 * 0.8660254 = 86.60254
        //   screenY = (100 + 0) * SIN30 - 0 = 100 * 0.5 = 50.0
        // want: exact values matching formula
        val pt = IsometricProjection.project(100.0, 0.0, 0.0, 0, 0.0, 0.0)
        assertEquals(86.60254, pt.x, 0.001)
        assertEquals(50.0, pt.y, 0.001)
    }

    @Test
    fun projectWithElevation() {
        // have: point (100, 200, 0), viewAngle=0, origin=(0,0)
        //   screenX = 100 * COS30 = 86.60254
        //   screenY = 100 * SIN30 - 200 = 50 - 200 = -150
        // want: Y negative (high above origin)
        val pt = IsometricProjection.project(100.0, 200.0, 0.0, 0, 0.0, 0.0)
        assertEquals(86.60254, pt.x, 0.001)
        assertEquals(-150.0, pt.y, 0.001)
    }

    @Test
    fun orderSurfacesAtAngle90() {
        // have: two walls, viewAngle=90
        //   sort key = x*sin(90°) + z*cos(90°) = x*1 + z*0 = x
        // want: sorted by x ascending
        val wallAtX0 = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 2400.0,
            position = SurfacePosition(0.0, 0.0, 500.0, 0.0),
        )
        val wallAtX2000 = Surface(
            roomId = "r1", type = SurfaceType.WALL,
            width = 1000.0, height = 2400.0,
            position = SurfacePosition(2000.0, 0.0, 500.0, 180.0),
        )
        val ordered = IsometricProjection.orderSurfaces(listOf(wallAtX2000, wallAtX0), 90)
        assertEquals(0.0, ordered[0].position.x, 0.01)
        assertEquals(2000.0, ordered[1].position.x, 0.01)
    }
}
