package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.test.*

class RegionValidatorTest {

    @Test
    fun fullCoverageNoOverlapPasses() {
        // have: single region covering entire 2000x1200 surface
        // want: valid result, no errors
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2000.0, 1200.0),
            emptyList(), 2000.0, 1200.0,
        )
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun partialCoverageReportsUncovered() {
        // have: region covers only left half (1000x1200 = 1,200,000 sq mm)
        //       surface total = 2,400,000 sq mm → 1,200,000 uncovered
        // want: failure with "Uncovered" error mentioning ~1200000 sq mm
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 1000.0, 1200.0),
            emptyList(), 2000.0, 1200.0,
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "Uncovered" in it.message })
    }

    @Test
    fun overlapDetected() {
        // have: existing region 0–1500 wide, new region 1000–2000 → 500mm overlap
        // want: failure with overlap error
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 1500.0, 1200.0),
            )
        )
        val result = RegionValidator.validate(
            RegionRect(1000.0, 0.0, 1000.0, 1200.0),
            existing, 2000.0, 1200.0,
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "overlap" in it.message.lowercase() })
    }

    @Test
    fun adjacentRegionsNoOverlap() {
        // have: existing covers [0,1000), new covers [1000,2000) — touching but not overlapping
        //       combined area = 1000*1200 + 1000*1200 = 2,400,000 = surface area
        // want: valid (no overlap, full coverage)
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 1000.0, 1200.0),
            )
        )
        val result = RegionValidator.validate(
            RegionRect(1000.0, 0.0, 1000.0, 1200.0),
            existing, 2000.0, 1200.0,
        )
        assertTrue(result.isValid)
    }

    @Test
    fun excludeIdSkipsOverlapCheck() {
        // have: existing region fully overlaps new region, but excludeId matches existing
        // want: valid (self-overlap excluded during edit of same region)
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 2000.0, 1200.0),
            )
        )
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2000.0, 1200.0),
            existing, 2000.0, 1200.0,
            excludeId = "stg1",
        )
        assertTrue(result.isValid)
    }

    @Test
    fun multipleRegionsFullCoverage() {
        // have: 2 existing regions cover top half (1000x600 + 1000x600)
        //       new region covers bottom half (2000x600)
        //       total = 600000 + 600000 + 1200000 = 2400000 = 2000*1200
        // want: valid
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 1000.0, 600.0),
            ),
            SurfaceTileGroup(
                id = "stg2", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(1000.0, 0.0, 1000.0, 600.0),
            ),
        )
        val result = RegionValidator.validate(
            RegionRect(0.0, 600.0, 2000.0, 600.0),
            existing, 2000.0, 1200.0,
        )
        assertTrue(result.isValid)
    }

    @Test
    fun overCoverageDoesNotError() {
        // have: region 2500x1500 larger than surface 2000x1200
        //       coveredArea > totalArea
        // want: no error (validator only flags under-coverage)
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2500.0, 1500.0),
            emptyList(), 2000.0, 1200.0,
        )
        assertTrue(result.isValid, "Over-coverage should not produce errors")
    }

    @Test
    fun tinyGapBelowThresholdPasses() {
        // have: region 2000x1199.995, gap = 2000*(1200-1199.995) = 10 sq mm
        //       but abs(coveredArea - totalArea) = 10 > 0.01, so this WILL trigger
        //       Actually: 2000*1199.995 = 2399990, totalArea=2400000, diff=10 > 0.01
        // want: failure (gap is real, just small)
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2000.0, 1199.995),
            emptyList(), 2000.0, 1200.0,
        )
        assertFalse(result.isValid, "10 sq mm gap exceeds 0.01 threshold")
    }

    @Test
    fun negligibleFloatingPointDiffPasses() {
        // have: region area = 2000 * 1199.999999 = 2399999.998, surface = 2400000
        //       diff = 0.002 < 0.01 threshold
        // want: valid (within floating point tolerance)
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2000.0, 1199.999999),
            emptyList(), 2000.0, 1200.0,
        )
        assertTrue(result.isValid, "Diff of 0.002 should be within 0.01 tolerance")
    }

    @Test
    fun excludeIdCorrectlyExcludesFromAreaCalc() {
        // have: existing region covers full surface, excludeId matches it
        //       new region covers full surface too
        //       without exclude: coveredArea = 2*2400000 = 4800000 (over-coverage, passes)
        //       with exclude: coveredArea = newRegion only = 2400000 = surface (passes)
        // want: valid (excluded region not double-counted)
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 2000.0, 1200.0),
            )
        )
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2000.0, 1200.0),
            existing, 2000.0, 1200.0,
            excludeId = "stg1",
        )
        assertTrue(result.isValid)

        // have: same setup but new region is only half → with exclude, under-coverage
        val resultHalf = RegionValidator.validate(
            RegionRect(0.0, 0.0, 1000.0, 1200.0),
            existing, 2000.0, 1200.0,
            excludeId = "stg1",
        )
        assertFalse(resultHalf.isValid, "Excluded region not counted → half coverage should fail")
    }

    @Test
    fun overlapWithoutUncoveredReportsOnlyOverlap() {
        // have: existing 1500x1200 (area=1,800,000), new 1000x600 (area=600,000)
        //       intersection: x[1000,1500]=500, y[0,600]=600 → area > 0 → overlap
        //       coveredArea = 600,000 + 1,800,000 = 2,400,000 = totalArea → no uncovered error
        // want: exactly 1 error (overlap only), no uncovered error
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 1500.0, 1200.0),
            )
        )
        val result = RegionValidator.validate(
            RegionRect(1000.0, 0.0, 1000.0, 600.0),
            existing, 2000.0, 1200.0,
        )
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size, "Should have exactly 1 error")
        assertTrue(result.errors[0].message.lowercase().contains("overlap"))
    }

    @Test
    fun overlapWithUncoveredReportsBothErrors() {
        // have: existing 500x600 (area=300,000), new 500x600 overlapping it
        //       surface = 2000x1200 = 2,400,000
        //       coveredArea = 300,000 + 300,000 = 600,000 < 2,400,000 → uncovered
        //       intersection area > 0 → overlap
        // want: 2 errors (overlap + uncovered)
        val existing = listOf(
            SurfaceTileGroup(
                id = "stg1", surfaceId = "s1", tileGroupId = "tg1",
                region = RegionRect(0.0, 0.0, 500.0, 600.0),
            )
        )
        val result = RegionValidator.validate(
            RegionRect(200.0, 0.0, 500.0, 600.0),
            existing, 2000.0, 1200.0,
        )
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size, "Should have both overlap and uncovered errors")
        assertTrue(result.errors.any { "overlap" in it.message.lowercase() })
        assertTrue(result.errors.any { "Uncovered" in it.message })
    }
}
