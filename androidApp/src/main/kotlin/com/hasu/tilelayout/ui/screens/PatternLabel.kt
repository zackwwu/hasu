package com.hasu.tilelayout.ui.screens

import com.hasu.tilelayout.models.TilePattern

/** Shared label mapping for tile patterns (used by SurfaceDetailScreen + RegionEditorScreen). */
internal fun patternLabel(pattern: TilePattern): String = when (pattern) {
    TilePattern.GRID -> "Grid"
    TilePattern.BRICK -> "Brick"
    TilePattern.STACKED -> "Stacked"
    TilePattern.HERRINGBONE -> "Herringbone"
}
