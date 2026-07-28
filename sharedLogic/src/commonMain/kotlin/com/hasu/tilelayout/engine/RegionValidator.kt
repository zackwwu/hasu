package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*

data class RegionValidationError(val message: String)

data class RegionValidationResult(
    val isValid: Boolean,
    val errors: List<RegionValidationError> = emptyList(),
)

object RegionValidator {
    fun validate(
        newRegion: RegionRect,
        existingRegions: List<SurfaceTileGroup>,
        surfaceWidth: Double,
        surfaceHeight: Double,
        excludeId: String? = null,
    ): RegionValidationResult {
        val errors = mutableListOf<RegionValidationError>()

        for (stg in existingRegions) {
            if (excludeId != null && stg.id == excludeId) continue
            if (newRegion.intersectionArea(stg.region) > 0) {
                errors.add(RegionValidationError("Regions overlap — adjust boundaries."))
                break
            }
        }

        val coveredArea = newRegion.area + existingRegions
            .filter { excludeId == null || it.id != excludeId }
            .sumOf { it.region.area }
        val totalArea = surfaceWidth * surfaceHeight

        if (kotlin.math.abs(coveredArea - totalArea) > 0.01) {
            if (coveredArea < totalArea) {
                errors.add(RegionValidationError(
                    "Uncovered area on surface (${(totalArea - coveredArea).toInt()} sq mm). Assign a tile group to cover all areas."
                ))
            }
        }

        return RegionValidationResult(isValid = errors.isEmpty(), errors = errors)
    }
}
