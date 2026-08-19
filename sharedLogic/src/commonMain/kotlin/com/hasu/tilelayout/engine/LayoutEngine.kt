package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.math.*

object LayoutEngine {
    private const val MIN_CUT_RATIO = 0.30
    private const val EDGE_EPS = 0.01

    fun compute(
        region: RegionRect,
        tileGroup: TileGroup,
        groutWidth: Double,
        pattern: TilePattern,
        offsetX: Double,
        offsetY: Double,
        exclusions: List<RegionRect> = emptyList(),
    ): List<PlacedTile> = when (pattern) {
        TilePattern.GRID, TilePattern.STACKED ->
            computeGrid(region, tileGroup, groutWidth, offsetX, offsetY, exclusions)
        TilePattern.BRICK ->
            computeBrick(region, tileGroup, groutWidth, offsetX, offsetY, exclusions)
        TilePattern.HERRINGBONE ->
            computeHerringbone(region, tileGroup, groutWidth, offsetX, offsetY, exclusions)
    }

    /**
     * Emit a tile unless it intersects an exclusion (door) area — dropped
     * entirely, nothing tiles behind the door. Tiles whose edge coincides
     * with an exclusion boundary get the matching cut-edge flag merged with
     * their region-boundary flags, so door-adjacent tiles show up in the
     * cut list. All coordinates are region-local.
     */
    private fun placeTile(
        region: RegionRect,
        tg: TileGroup,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        rotation: Double,
        boundaryEdges: List<CutEdge>,
        exclusions: List<RegionRect>,
    ): PlacedTile? {
        val rect = RegionRect(x, y, w, h)
        if (exclusions.any { it.overlaps(rect) }) return null

        val exclusionEdges = buildList {
            for (e in exclusions) {
                val sharesVertical = y < e.y + e.height && y + h > e.y
                val sharesHorizontal = x < e.x + e.width && x + w > e.x
                if (sharesVertical && abs(x - (e.x + e.width)) <= EDGE_EPS) add(CutEdge.LEFT)
                if (sharesVertical && abs(x + w - e.x) <= EDGE_EPS) add(CutEdge.RIGHT)
                if (sharesHorizontal && abs(y - (e.y + e.height)) <= EDGE_EPS) add(CutEdge.TOP)
                if (sharesHorizontal && abs(y + h - e.y) <= EDGE_EPS) add(CutEdge.BOTTOM)
            }
        }.distinct()

        val edges = boundaryEdges + exclusionEdges.filterNot { it in boundaryEdges }
        return PlacedTile(
            region.x + x, region.y + y, w, h,
            rotation = rotation,
            isCut = edges.isNotEmpty(),
            cutEdges = edges,
            tileGroupId = tg.id,
        )
    }

    private fun computeGrid(
        region: RegionRect, tg: TileGroup, groutW: Double, offX: Double, offY: Double,
        exclusions: List<RegionRect>,
    ): List<PlacedTile> {
        val unitW = tg.tileWidth + groutW
        val unitH = tg.tileHeight + groutW

        var fullCols = (region.width / unitW).toInt()
        var fullRows = (region.height / unitH).toInt()
        var remW = region.width - fullCols * unitW
        var remH = region.height - fullRows * unitH

        if (remW > 0 && remW < tg.tileWidth * MIN_CUT_RATIO) { fullCols--; remW = region.width - fullCols * unitW }
        if (remH > 0 && remH < tg.tileHeight * MIN_CUT_RATIO) { fullRows--; remH = region.height - fullRows * unitH }

        val wrappedOffX = ((offX % unitW) + unitW) % unitW
        val wrappedOffY = ((offY % unitH) + unitH) % unitH
        val startX = remW / 2 + wrappedOffX
        val startY = remH / 2 + wrappedOffY

        return buildList {
            for (row in -1..fullRows + 1) for (col in -1..fullCols + 1) {
                val x = startX + col * unitW
                val y = startY + row * unitH
                if (x + tg.tileWidth <= 0 || y + tg.tileHeight <= 0) continue
                if (x >= region.width || y >= region.height) continue
                val cx = max(0.0, x)
                val cy = max(0.0, y)
                val cw = min(x + tg.tileWidth, region.width) - cx
                val ch = min(y + tg.tileHeight, region.height) - cy
                if (cw <= 0 || ch <= 0) continue
                val isCut = abs(cw - tg.tileWidth) > 0.01 || abs(ch - tg.tileHeight) > 0.01
                val edges = if (isCut) cutEdges(cx, cy, cw, ch, region.width, region.height) else emptyList()
                placeTile(region, tg, cx, cy, cw, ch, 0.0, edges, exclusions)?.let { add(it) }
            }
        }
    }

    private fun computeBrick(
        region: RegionRect, tg: TileGroup, groutW: Double, offX: Double, offY: Double,
        exclusions: List<RegionRect>,
    ): List<PlacedTile> {
        val unitW = tg.tileWidth + groutW
        val unitH = tg.tileHeight + groutW
        val halfW = unitW / 2

        var fullRows = (region.height / unitH).toInt()
        var remH = region.height - fullRows * unitH
        if (remH > 0 && remH < tg.tileHeight * MIN_CUT_RATIO) { fullRows--; remH = region.height - fullRows * unitH }
        val startY = remH / 2 + offY

        val offsetRowW = region.width - halfW
        var fullCols = (offsetRowW / unitW).toInt()
        var remW = offsetRowW - fullCols * unitW
        if (remW > 0 && remW < tg.tileWidth * MIN_CUT_RATIO) { fullCols--; remW = offsetRowW - fullCols * unitW }
        val offsetRowStartX = halfW + remW / 2
        val evenRowStartX = remW / 2

        return buildList {
            for (row in 0..fullRows) {
                val y = startY + row * unitH
                val isOffsetRow = row % 2 != 0
                val startX = (if (isOffsetRow) offsetRowStartX else evenRowStartX) + offX
                val colCount = if (isOffsetRow) fullCols else fullCols + 1

                for (col in 0..colCount) {
                    val x = startX + col * unitW
                    if (x + tg.tileWidth <= 0 || y + tg.tileHeight <= 0) continue
                    if (x >= region.width || y >= region.height) continue
                    val cx = max(0.0, x)
                    val cy = max(0.0, y)
                    val cw = min(x + tg.tileWidth, region.width) - cx
                    val ch = min(y + tg.tileHeight, region.height) - cy
                    if (cw <= 0 || ch <= 0) continue
                    val isCut = abs(cw - tg.tileWidth) > 0.01 || abs(ch - tg.tileHeight) > 0.01
                    val edges = if (isCut) cutEdges(cx, cy, cw, ch, region.width, region.height) else emptyList()
                    placeTile(region, tg, cx, cy, cw, ch, 0.0, edges, exclusions)?.let { add(it) }
                }
            }
        }
    }

    private fun computeHerringbone(
        region: RegionRect, tg: TileGroup, groutW: Double, offX: Double, offY: Double,
        exclusions: List<RegionRect>,
    ): List<PlacedTile> {
        val cos45 = cos(PI / 4); val sin45 = sin(PI / 4)
        val diagW = tg.tileWidth * cos45 + tg.tileHeight * sin45
        val diagH = tg.tileWidth * sin45 + tg.tileHeight * cos45
        val stepX = tg.tileHeight * cos45 + groutW
        val stepY = tg.tileHeight * sin45 + groutW

        val cols = (region.width / stepX).toInt() + 2
        val rows = (region.height / stepY).toInt() + 2

        return buildList {
            for (row in 0 until rows) for (col in 0 until cols) {
                val rot = if ((row + col) % 2 == 0) 45.0 else -45.0
                val rx = col * stepX + offX; val ry = row * stepY + offY
                val bx = rx - diagW / 2; val by = ry - diagH / 2
                if (bx + diagW <= 0 || by + diagH <= 0) continue
                if (bx >= region.width || by >= region.height) continue
                val cx = max(0.0, bx); val cy = max(0.0, by)
                val cw = min(bx + diagW, region.width) - cx
                val ch = min(by + diagH, region.height) - cy
                if (cw <= 0 || ch <= 0) continue
                val isCut = abs(cw - diagW) > 0.01 || abs(ch - diagH) > 0.01
                val edges = if (isCut) herringboneCutEdges(bx, by, diagW, diagH, region.width, region.height) else emptyList()
                placeTile(region, tg, cx, cy, cw, ch, rot, edges, exclusions)?.let { add(it) }
            }
        }
    }

    private fun cutEdges(x: Double, y: Double, w: Double, h: Double, rw: Double, rh: Double) = buildList {
        if (x <= 0.01) add(CutEdge.LEFT)
        if (y <= 0.01) add(CutEdge.TOP)
        if (x + w >= rw - 0.01) add(CutEdge.RIGHT)
        if (y + h >= rh - 0.01) add(CutEdge.BOTTOM)
    }

    private fun herringboneCutEdges(bx: Double, by: Double, bw: Double, bh: Double, rw: Double, rh: Double) = buildList {
        if (bx < 0) add(CutEdge.LEFT)
        if (by < 0) add(CutEdge.TOP)
        if (bx + bw > rw) add(CutEdge.RIGHT)
        if (by + bh > rh) add(CutEdge.BOTTOM)
    }
}
