package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.math.abs
import kotlin.test.*

class LayoutEngineTest {
    private val tileGroup = TileGroup(
        id = "tg1", projectId = "p1", name = "Test",
        tileWidth = 300.0, tileHeight = 200.0,
    )
    private val groutWidth = 3.0

    // --- GRID PATTERN ---

    @Test
    fun gridProducesCorrectTileCount() {
        // have: 903x603 region, 300x200 tiles, 3mm grout
        //   unitW=303, unitH=203
        //   fullCols=floor(903/303)=2, remW=297 (>90, no sliver elim)
        //   fullRows=floor(603/203)=2, remH=197 (>60, no sliver elim)
        //   startX=148.5, startY=98.5
        //   valid cols: -1,0,1,2 (4 columns)
        //   valid rows: -1,0,1,2 (4 rows)
        val region = RegionRect(0.0, 0.0, 903.0, 603.0)

        // when: compute grid layout
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: 4 cols × 4 rows = 16 tiles total
        assertEquals(16, tiles.size, "Expected 4 cols × 4 rows = 16 tiles")

        // want: 4 full tiles (cols 0,1 × rows 0,1), 12 cut tiles
        val fullTiles = tiles.filter { !it.isCut }
        val cutTiles = tiles.filter { it.isCut }
        assertEquals(4, fullTiles.size, "Expected 2 full cols × 2 full rows = 4 full tiles")
        assertEquals(12, cutTiles.size, "Remaining 12 should be cut")
    }

    @Test
    fun gridFullTilesHaveExactDimensions() {
        // have: 903x603 region
        val region = RegionRect(0.0, 0.0, 903.0, 603.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: full tiles are exactly 300x200
        val fullTiles = tiles.filter { !it.isCut }
        for (tile in fullTiles) {
            assertEquals(300.0, tile.width, 0.01, "Full tile width should be 300")
            assertEquals(200.0, tile.height, 0.01, "Full tile height should be 200")
        }
    }

    @Test
    fun gridTilesStayWithinRegionBounds() {
        // have: region offset from origin
        val region = RegionRect(50.0, 100.0, 1200.0, 800.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: every tile fully contained within region bounds
        for (tile in tiles) {
            assertTrue(tile.x >= region.x - 0.01, "Tile x=${tile.x} below region x=${region.x}")
            assertTrue(tile.y >= region.y - 0.01, "Tile y=${tile.y} below region y=${region.y}")
            assertTrue(tile.x + tile.width <= region.x + region.width + 0.01,
                "Tile right edge ${tile.x + tile.width} exceeds region right ${region.x + region.width}")
            assertTrue(tile.y + tile.height <= region.y + region.height + 0.01,
                "Tile bottom edge ${tile.y + tile.height} exceeds region bottom ${region.y + region.height}")
        }
    }

    @Test
    fun gridSliverElimination() {
        // have: region 920x812, unitW=303, unitH=203
        //   X: fullCols=floor(920/303)=3, remW=920-909=11, 11 < 90 → sliver elim! fullCols=2, remW=920-606=314
        //       startX=157, left overscan cw=154 (>90 ✓), right edge cw=157 (>90 ✓)
        //   Y: fullRows=floor(812/203)=4, remH=812-812=0 → no sliver issue, no overscan clips
        //   Without sliver elimination, would produce 11mm cuts (well below 30% = 90mm)
        val region = RegionRect(0.0, 0.0, 920.0, 812.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: no cut tile smaller than 30% of tile dimension (sliver elimination worked)
        val minWidth = tileGroup.tileWidth * 0.30
        val minHeight = tileGroup.tileHeight * 0.30
        val slivers = tiles.filter { it.isCut && (it.width < minWidth - 0.01 || it.height < minHeight - 0.01) }
        assertTrue(slivers.isEmpty(), "No sliver cuts below 30% threshold, found ${slivers.size}")
    }

    @Test
    fun gridOffsetWrapsAround() {
        // have: offset equal to exactly one tile+grout unit (303mm)
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)
        val unitW = tileGroup.tileWidth + groutWidth

        // when: compute with zero offset vs full-unit offset
        val tilesNoOffset = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)
        val tilesFullOffset = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, unitW, 0.0)

        // want: identical layout (full unit wraps to same position)
        assertEquals(tilesNoOffset.size, tilesFullOffset.size, "Full-unit offset should produce same tile count")
        val positionsZero = tilesNoOffset.map { "${it.x},${it.y}" }.sorted()
        val positionsFull = tilesFullOffset.map { "${it.x},${it.y}" }.sorted()
        assertEquals(positionsZero, positionsFull, "Full-unit offset should produce identical positions")
    }

    @Test
    fun gridWithRegionOffset() {
        // have: region starting at (200,300) not origin
        val region = RegionRect(200.0, 300.0, 600.0, 400.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: all tiles positioned within offset region
        assertTrue(tiles.isNotEmpty())
        for (tile in tiles) {
            assertTrue(tile.x >= 200.0 - 0.01)
            assertTrue(tile.y >= 300.0 - 0.01)
        }
    }

    @Test
    fun gridCutEdgesAtBoundaries() {
        // have: small region forcing cuts on all edges
        val region = RegionRect(0.0, 0.0, 500.0, 350.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: each edge direction has at least one tile tagged, and all are at correct boundary
        val leftEdge = tiles.filter { CutEdge.LEFT in it.cutEdges }
        val rightEdge = tiles.filter { CutEdge.RIGHT in it.cutEdges }
        val topEdge = tiles.filter { CutEdge.TOP in it.cutEdges }
        val bottomEdge = tiles.filter { CutEdge.BOTTOM in it.cutEdges }
        assertTrue(leftEdge.isNotEmpty(), "Should have LEFT-edge cut tiles")
        assertTrue(rightEdge.isNotEmpty(), "Should have RIGHT-edge cut tiles")
        assertTrue(topEdge.isNotEmpty(), "Should have TOP-edge cut tiles")
        assertTrue(bottomEdge.isNotEmpty(), "Should have BOTTOM-edge cut tiles")
        assertTrue(leftEdge.all { it.x <= region.x + 0.01 })
        assertTrue(rightEdge.all { it.x + it.width >= region.x + region.width - 0.01 })
        assertTrue(topEdge.all { it.y <= region.y + 0.01 })
        assertTrue(bottomEdge.all { it.y + it.height >= region.y + region.height - 0.01 })
    }

    @Test
    fun gridWithNegativeOffset() {
        // have: negative offsets (user dragged tiles left/up)
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute with negative offset
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, -50.0, -30.0)

        // want: tiles still clipped within region (no negative positions)
        assertTrue(tiles.isNotEmpty())
        for (tile in tiles) {
            assertTrue(tile.x >= -0.01)
            assertTrue(tile.y >= -0.01)
        }
    }

    @Test
    fun tileWidthsNeverExceedOriginal() {
        // have: large region, grid and brick patterns
        val region = RegionRect(0.0, 0.0, 1500.0, 1000.0)

        // want: no tile wider/taller than original (cuts only shrink, never grow)
        for (pattern in listOf(TilePattern.GRID, TilePattern.BRICK)) {
            val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, pattern, 0.0, 0.0)
            for (tile in tiles) {
                assertTrue(tile.width <= tileGroup.tileWidth + 0.01,
                    "$pattern: tile width ${tile.width} exceeds original ${tileGroup.tileWidth}")
                assertTrue(tile.height <= tileGroup.tileHeight + 0.01,
                    "$pattern: tile height ${tile.height} exceeds original ${tileGroup.tileHeight}")
            }
        }
    }

    // --- BRICK PATTERN ---

    @Test
    fun brickProducesTiles() {
        // have: 1200x800 region
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute brick layout
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.BRICK, 0.0, 0.0)

        // want: non-empty with full tiles present
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.any { !it.isCut })
    }

    @Test
    fun brickTilesStayWithinRegion() {
        // have: standard region
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute brick
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.BRICK, 0.0, 0.0)

        // want: all tiles clipped to region bounds
        for (tile in tiles) {
            assertTrue(tile.x >= -0.01)
            assertTrue(tile.y >= -0.01)
            assertTrue(tile.x + tile.width <= region.width + 0.01)
            assertTrue(tile.y + tile.height <= region.height + 0.01)
        }
    }

    @Test
    fun brickHasOffsetRows() {
        // have: large region for multiple rows
        val region = RegionRect(0.0, 0.0, 2000.0, 1200.0)

        // when: compute brick
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.BRICK, 0.0, 0.0)

        // want: more than 2 distinct x positions (offset rows shift x by half-unit)
        val xPositions = tiles.map { it.x }.distinct().sorted()
        assertTrue(xPositions.size > 2, "Brick pattern should have varying x positions from offset rows")
    }

    // --- HERRINGBONE PATTERN ---

    @Test
    fun herringboneProducesTiles() {
        // have: standard region
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute herringbone
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.HERRINGBONE, 0.0, 0.0)

        // want: non-empty
        assertTrue(tiles.isNotEmpty())
    }

    @Test
    fun herringboneHasBothRotations() {
        // have: region large enough for multiple herringbone pairs
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)
        val tg = TileGroup(id = "tg2", projectId = "p1", name = "Herr", tileWidth = 300.0, tileHeight = 100.0)

        // when: compute herringbone
        val tiles = LayoutEngine.compute(region, tg, groutWidth, TilePattern.HERRINGBONE, 0.0, 0.0)

        // want: alternating +45° and -45° rotations present
        assertTrue(tiles.any { it.rotation == 45.0 }, "Should have 45° tiles")
        assertTrue(tiles.any { it.rotation == -45.0 }, "Should have -45° tiles")
    }

    @Test
    fun herringboneTilesWithinRegion() {
        // have: standard region
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute herringbone
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.HERRINGBONE, 0.0, 0.0)

        // want: all tiles clipped to region bounds
        for (tile in tiles) {
            assertTrue(tile.x >= -0.01)
            assertTrue(tile.y >= -0.01)
            assertTrue(tile.x + tile.width <= region.width + 0.01)
            assertTrue(tile.y + tile.height <= region.height + 0.01)
        }
    }

    // --- STACKED / COMMON ---

    @Test
    fun stackedDelegatesToGrid() {
        // have: same region, same params
        val region = RegionRect(0.0, 0.0, 900.0, 600.0)

        // when: compute both GRID and STACKED
        val gridTiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)
        val stackedTiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.STACKED, 0.0, 0.0)

        // want: identical results (STACKED uses same code path as GRID)
        assertEquals(gridTiles.size, stackedTiles.size)
        assertEquals(gridTiles, stackedTiles)
    }

    @Test
    fun allTilesHaveCorrectTileGroupId() {
        // have: all patterns computed
        val region = RegionRect(0.0, 0.0, 1000.0, 700.0)

        // want: every placed tile references the source tileGroup id
        for (pattern in TilePattern.entries) {
            val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, pattern, 0.0, 0.0)
            assertTrue(tiles.all { it.tileGroupId == tileGroup.id },
                "All tiles in $pattern should reference tileGroup id")
        }
    }

    @Test
    fun gridCoversFullRegion() {
        // have: 1200x800 region, 300x200 tiles, 3mm grout
        //   unitW=303, unitH=203
        //   tile area fraction = (300*200) / (303*203) = 60000/61509 ≈ 0.9754
        //   so tile coverage should be ≈ 97.5% of region for a perfectly fitting grid
        //   with edge cuts it may be slightly less, but should exceed 90%
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: tile area coverage ≥ 90% (grout accounts for ~2.5%, edge effects minimal)
        val totalTileArea = tiles.sumOf { it.width * it.height }
        val regionArea = region.width * region.height
        assertTrue(totalTileArea >= regionArea * 0.90,
            "Tiles should cover ≥90% of region. Got ${(totalTileArea / regionArea * 100).toInt()}%")

        // want: no horizontal gaps between adjacent tiles in same row
        //       group by y-position (same row), sort by x, verify consecutive tiles touch or overlap
        val rows = tiles.groupBy { it.y }
        for ((_, rowTiles) in rows) {
            val sorted = rowTiles.sortedBy { it.x }
            for (i in 0 until sorted.size - 1) {
                val gap = sorted[i + 1].x - (sorted[i].x + sorted[i].width)
                assertTrue(gap <= groutWidth + 0.01,
                    "Gap between tiles (${gap}mm) exceeds grout width (${groutWidth}mm)")
            }
        }
    }

    @Test
    fun gridPartialOffsetShiftsPositions() {
        // have: same region, offset=50mm vs offset=0
        val region = RegionRect(0.0, 0.0, 1200.0, 800.0)

        // when: compute with 50mm x-offset
        val tilesZero = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)
        val tilesOffset = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 50.0, 0.0)

        // want: tile positions differ (offset actually shifts)
        val xPositionsZero = tilesZero.map { it.x }.sorted()
        val xPositionsOffset = tilesOffset.map { it.x }.sorted()
        assertNotEquals(xPositionsZero, xPositionsOffset, "50mm offset should shift tile positions")
    }

    @Test
    fun gridWithSquareTiles() {
        // have: square tiles 200x200, 900x900 region
        val squareTg = TileGroup(id = "sq1", projectId = "p1", name = "Square", tileWidth = 200.0, tileHeight = 200.0)
        val region = RegionRect(0.0, 0.0, 900.0, 900.0)

        // when: compute grid
        val tiles = LayoutEngine.compute(region, squareTg, groutWidth, TilePattern.GRID, 0.0, 0.0)

        // want: symmetric layout — same number of cols and rows
        val distinctX = tiles.map { it.x }.distinct().size
        val distinctY = tiles.map { it.y }.distinct().size
        assertEquals(distinctX, distinctY, "Square tiles in square region → symmetric col/row count")
    }

    @Test
    fun herringboneCutEdgesAtBoundaries() {
        // have: region that forces herringbone tiles to be clipped
        val region = RegionRect(0.0, 0.0, 800.0, 600.0)

        // when: compute herringbone
        val tiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.HERRINGBONE, 0.0, 0.0)

        // want: cut tiles at left edge have LEFT in cutEdges, etc.
        val leftCuts = tiles.filter { CutEdge.LEFT in it.cutEdges }
        val rightCuts = tiles.filter { CutEdge.RIGHT in it.cutEdges }
        assertTrue(leftCuts.isNotEmpty(), "Herringbone should have left-edge cuts")
        assertTrue(rightCuts.isNotEmpty(), "Herringbone should have right-edge cuts")
        assertTrue(leftCuts.all { it.x <= 0.01 })
        assertTrue(rightCuts.all { it.x + it.width >= region.width - 0.01 })
    }

    @Test
    fun gridWithZeroGrout() {
        // have: grout width = 0, region exactly divisible by tile size (600x400 = 2×2 tiles)
        val region = RegionRect(0.0, 0.0, 600.0, 400.0)

        // when: compute grid with zero grout
        val tiles = LayoutEngine.compute(region, tileGroup, 0.0, TilePattern.GRID, 0.0, 0.0)

        // want: tiles fill region perfectly, all full tiles (no cuts needed)
        assertTrue(tiles.isNotEmpty())
        val fullTiles = tiles.filter { !it.isCut }
        assertEquals(4, fullTiles.size, "600/300=2 cols × 400/200=2 rows = 4 full tiles")
        val totalArea = tiles.sumOf { it.width * it.height }
        assertEquals(region.width * region.height, totalArea, 0.01, "Zero grout → tiles fill 100%")
    }

    @Test
    fun brickDiffersFromGrid() {
        // have: same region, same tiles
        val region = RegionRect(0.0, 0.0, 1500.0, 1000.0)

        // when: compute both patterns
        val gridTiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.GRID, 0.0, 0.0)
        val brickTiles = LayoutEngine.compute(region, tileGroup, groutWidth, TilePattern.BRICK, 0.0, 0.0)

        // want: different tile positions (brick shifts odd rows by half-unit)
        val gridPositions = gridTiles.map { "${it.x.toInt()},${it.y.toInt()}" }.toSet()
        val brickPositions = brickTiles.map { "${it.x.toInt()},${it.y.toInt()}" }.toSet()
        assertNotEquals(gridPositions, brickPositions, "Brick should have different positions than grid")
    }
}
