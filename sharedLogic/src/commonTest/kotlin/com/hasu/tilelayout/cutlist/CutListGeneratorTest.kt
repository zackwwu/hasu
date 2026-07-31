package com.hasu.tilelayout.cutlist

import com.hasu.tilelayout.models.*
import kotlin.test.*

class CutListGeneratorTest {

    private fun makeCutTile(
        width: Double, height: Double, edges: List<CutEdge>, tileGroupId: String = "tg1",
    ) = PlacedTile(
        x = 0.0, y = 0.0, width = width, height = height,
        isCut = true, cutEdges = edges, tileGroupId = tileGroupId,
    )

    private fun makeFullTile(tileGroupId: String = "tg1") = PlacedTile(
        x = 100.0, y = 100.0, width = 300.0, height = 200.0,
        isCut = false, cutEdges = emptyList(), tileGroupId = tileGroupId,
    )

    // --- Empty / no-cut cases ---

    @Test
    fun emptyResultsProduceEmptyCutList() {
        // have: layout result with no tiles at all
        // want: empty cut list
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf("s1" to LayoutResult(surfaceId = "s1", tiles = emptyList())),
            surfaceNames = mapOf("s1" to "Front Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun fullTilesNotIncluded() {
        // have: layout with only full (uncut) tiles
        // want: empty cut list (full tiles need no cutting)
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(makeFullTile()))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertTrue(result.isEmpty())
    }

    // --- Single cut ---

    @Test
    fun singleCutTileProducesOneEntry() {
        // have: 1 cut tile (150x200, left edge) + 1 full tile
        // want: 1 CutEntry with totalCount=1, correct name and edgesKey
        val cutTile = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cutTile, makeFullTile()))
            ),
            surfaceNames = mapOf("s1" to "Front Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(1, result.size)
        assertEquals(1, result[0].totalCount)
        assertEquals("Marble", result[0].tileGroupName)
        assertEquals("LEFT", result[0].cutEdgesKey)
        assertEquals(150.0, result[0].width)
        assertEquals(200.0, result[0].height)
    }

    // --- Grouping ---

    @Test
    fun identicalCutsGroupTogether() {
        // have: 2 cut tiles with same dimensions (150x200) and same edge (RIGHT) on same surface
        // want: 1 CutEntry with totalCount=2, single location with count=2
        val cut1 = makeCutTile(150.0, 200.0, listOf(CutEdge.RIGHT))
        val cut2 = makeCutTile(150.0, 200.0, listOf(CutEdge.RIGHT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut1, cut2))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(1, result.size)
        assertEquals(2, result[0].totalCount)
        assertEquals(1, result[0].locations.size, "Both cuts on same surface → 1 location")
        assertEquals(2, result[0].locations[0].count, "Location count should be 2")
        assertEquals("Wall", result[0].locations[0].surfaceName)
    }

    @Test
    fun differentDimensionsSeparateGroups() {
        // have: 2 cut tiles with same edge (LEFT) but different widths (150 vs 100)
        // want: 2 separate CutEntries
        val cut1 = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT))
        val cut2 = makeCutTile(100.0, 200.0, listOf(CutEdge.LEFT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut1, cut2))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun differentEdgesSeparateGroups() {
        // have: 2 cut tiles with same dimensions but different edges (LEFT vs RIGHT)
        // want: 2 separate CutEntries
        val cut1 = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT))
        val cut2 = makeCutTile(150.0, 200.0, listOf(CutEdge.RIGHT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut1, cut2))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun differentTileGroupsSeparateGroups() {
        // have: 2 cut tiles with identical dims/edges but different tileGroupIds
        // want: 2 separate CutEntries (grouping key includes tileGroupId)
        val cut1 = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT), tileGroupId = "tg1")
        val cut2 = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT), tileGroupId = "tg2")
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut1, cut2))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble", "tg2" to "Granite"),
        )
        assertEquals(2, result.size)
    }

    // --- Multi-surface aggregation ---

    @Test
    fun multipleSurfacesAggregateLocations() {
        // have: same cut (150x200, LEFT) on 2 different surfaces
        // want: 1 CutEntry, totalCount=2, 2 locations
        val cut = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut)),
                "s2" to LayoutResult(surfaceId = "s2", tiles = listOf(cut)),
            ),
            surfaceNames = mapOf("s1" to "Front Wall", "s2" to "Back Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(1, result.size)
        assertEquals(2, result[0].totalCount)
        assertEquals(2, result[0].locations.size)
        assertTrue(result[0].locations.any { it.surfaceName == "Front Wall" })
        assertTrue(result[0].locations.any { it.surfaceName == "Back Wall" })
    }

    @Test
    fun locationCountsPerSurface() {
        // have: 3 identical cuts on s1, 1 on s2
        // want: totalCount=4, s1 location count=3, s2 location count=1
        val cut = makeCutTile(150.0, 200.0, listOf(CutEdge.TOP))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut, cut, cut)),
                "s2" to LayoutResult(surfaceId = "s2", tiles = listOf(cut)),
            ),
            surfaceNames = mapOf("s1" to "Wall A", "s2" to "Wall B"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(1, result.size)
        assertEquals(4, result[0].totalCount)
        val locA = result[0].locations.find { it.surfaceId == "s1" }
        val locB = result[0].locations.find { it.surfaceId == "s2" }
        assertEquals(3, locA?.count)
        assertEquals(1, locB?.count)
    }

    // --- Sorting ---

    @Test
    fun sortedByTileGroupNameThenAreaDescending() {
        // have: 3 cuts — 2 from "Alpha" (large + small), 1 from "Beta"
        // want: sorted Alpha(large), Alpha(small), Beta
        val cutA = makeCutTile(200.0, 150.0, listOf(CutEdge.LEFT), tileGroupId = "tgA")
        val cutASmall = makeCutTile(100.0, 100.0, listOf(CutEdge.LEFT), tileGroupId = "tgA")
        val cutB = makeCutTile(150.0, 150.0, listOf(CutEdge.LEFT), tileGroupId = "tgB")
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cutA, cutASmall, cutB))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tgA" to "Alpha", "tgB" to "Beta"),
        )
        assertEquals(3, result.size)
        assertEquals("Alpha", result[0].tileGroupName)
        assertEquals("Alpha", result[1].tileGroupName)
        assertTrue(result[0].width * result[0].height >= result[1].width * result[1].height,
            "Within same group, larger area should come first")
        assertEquals("Beta", result[2].tileGroupName)
    }

    // --- Corner cuts ---

    @Test
    fun cornerCutEdgesKey() {
        // have: cut with both RIGHT and BOTTOM edges
        // want: cutEdgesKey = "RIGHT,BOTTOM" (joined by comma)
        val cut = makeCutTile(150.0, 180.0, listOf(CutEdge.RIGHT, CutEdge.BOTTOM))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals("RIGHT,BOTTOM", result[0].cutEdgesKey)
    }

    // --- cutTypeDescription ---

    @Test
    fun cutTypeDescriptionSingleEdges() {
        // have: CutEntries with each single-edge key
        // want: human-readable description
        val cases = mapOf(
            "LEFT" to "Left edge cut",
            "RIGHT" to "Right edge cut",
            "TOP" to "Top edge cut",
            "BOTTOM" to "Bottom edge cut",
        )
        for ((key, expected) in cases) {
            val entry = CutEntry(
                tileGroupId = "tg1", tileGroupName = "X",
                width = 100.0, height = 100.0, cutEdgesKey = key,
                locations = emptyList(), totalCount = 1,
            )
            assertEquals(expected, entry.cutTypeDescription, "Key '$key' should map to '$expected'")
        }
    }

    @Test
    fun cutTypeDescriptionCornerCuts() {
        // have: CutEntries with corner edge combinations
        // want: "Corner cut (x + y)" descriptions
        val cases = mapOf(
            "RIGHT,BOTTOM" to "Corner cut (right + bottom)",
            "LEFT,BOTTOM" to "Corner cut (left + bottom)",
            "RIGHT,TOP" to "Corner cut (right + top)",
            "LEFT,TOP" to "Corner cut (left + top)",
        )
        for ((key, expected) in cases) {
            val entry = CutEntry(
                tileGroupId = "tg1", tileGroupName = "X",
                width = 100.0, height = 100.0, cutEdgesKey = key,
                locations = emptyList(), totalCount = 1,
            )
            assertEquals(expected, entry.cutTypeDescription)
        }
    }

    @Test
    fun cutTypeDescriptionUnknownFallsBackToCustom() {
        // have: edge combination not in the known mapping
        // want: "Custom cut"
        val entry = CutEntry(
            tileGroupId = "tg1", tileGroupName = "X",
            width = 100.0, height = 100.0, cutEdgesKey = "LEFT,RIGHT,TOP",
            locations = emptyList(), totalCount = 1,
        )
        assertEquals("Custom cut", entry.cutTypeDescription)
    }

    // --- Fallback names ---

    @Test
    fun fallbackTileGroupNameWhenMissing() {
        // have: tileGroupId not in tileGroupNames map
        // want: raw id used as fallback name
        val cut = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT), tileGroupId = "unknown-tg")
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = emptyMap(),
        )
        assertEquals("unknown-tg", result[0].tileGroupName)
    }

    @Test
    fun fallbackSurfaceNameWhenMissing() {
        // have: surfaceId not in surfaceNames map
        // want: raw id used as surfaceName in location
        val cut = makeCutTile(150.0, 200.0, listOf(CutEdge.LEFT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "unknown-s" to LayoutResult(surfaceId = "unknown-s", tiles = listOf(cut))
            ),
            surfaceNames = emptyMap(),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals("unknown-s", result[0].locations[0].surfaceName)
    }

    // --- Dimension rounding ---

    @Test
    fun dimensionsRoundedToOneTenth() {
        // have: cut tile with width=150.37, height=199.82
        //       rounding: (150.37*10).toLong()/10.0 = 1503/10.0 = 150.3
        //                 (199.82*10).toLong()/10.0 = 1998/10.0 = 199.8
        // want: CutEntry width=150.3, height=199.8
        val cut = makeCutTile(150.37, 199.82, listOf(CutEdge.LEFT))
        val result = CutListGenerator.generate(
            resultsBySurface = mapOf(
                "s1" to LayoutResult(surfaceId = "s1", tiles = listOf(cut))
            ),
            surfaceNames = mapOf("s1" to "Wall"),
            tileGroupNames = mapOf("tg1" to "Marble"),
        )
        assertEquals(150.3, result[0].width, 0.001)
        assertEquals(199.8, result[0].height, 0.001)
    }

    @Test
    fun emptyMapProducesEmptyList() {
        // have: no surfaces at all
        // want: empty cut list
        val result = CutListGenerator.generate(
            resultsBySurface = emptyMap(),
            surfaceNames = emptyMap(),
            tileGroupNames = emptyMap(),
        )
        assertTrue(result.isEmpty())
    }
}
