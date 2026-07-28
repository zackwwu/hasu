package com.hasu.tilelayout.cutlist

import com.hasu.tilelayout.models.*

object CutListGenerator {
    fun generate(
        resultsBySurface: Map<String, LayoutResult>,
        surfaceNames: Map<String, String>,
        tileGroupNames: Map<String, String>,
    ): List<CutEntry> {
        data class RawCut(val surfaceId: String, val surfaceName: String, val tile: PlacedTile)

        val allCuts = resultsBySurface.flatMap { (sid, result) ->
            val sname = surfaceNames[sid] ?: sid
            result.tiles.filter { it.isCut }.map { RawCut(sid, sname, it) }
        }

        val grouped = allCuts.groupBy { cut ->
            val edges = cut.tile.cutEdges.joinToString(",") { it.name }
            val w = (cut.tile.width * 10).toLong() / 10.0
            val h = (cut.tile.height * 10).toLong() / 10.0
            "${cut.tile.tileGroupId}|$w|$h|$edges"
        }

        return grouped.map { (_, cuts) ->
            val first = cuts.first().tile
            val edgesKey = first.cutEdges.joinToString(",") { it.name }
            val w = (first.width * 10).toLong() / 10.0
            val h = (first.height * 10).toLong() / 10.0

            val locations = cuts.groupBy { it.surfaceId }.map { (sid, cs) ->
                CutLocation(sid, cs.first().surfaceName, cs.size)
            }

            CutEntry(
                tileGroupId = first.tileGroupId,
                tileGroupName = tileGroupNames[first.tileGroupId] ?: first.tileGroupId,
                width = w, height = h, cutEdgesKey = edgesKey,
                locations = locations, totalCount = cuts.size,
            )
        }.sortedWith(compareBy({ it.tileGroupName }, { -(it.width * it.height) }))
    }
}
