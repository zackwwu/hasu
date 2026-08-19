# Tile Layout App — Implementation Plan (KMP)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin Multiplatform mobile app for tilers to design tile layouts, capture textures, render 2D/3D previews, and generate cut lists. Shared Kotlin logic + SwiftUI (iOS) + Jetpack Compose (Android).

**Architecture:** KMP shared module contains all business logic (models, SQLDelight storage, layout engine, render data computation, cut list generator). iOS and Android modules are thin UI layers — they observe shared StateFlows and draw via platform Canvas APIs.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, kotlinx-coroutines, SwiftUI + Canvas, Jetpack Compose + Canvas

---

## File Structure

```
shared/
  src/commonMain/kotlin/com/hasu/tilelayout/
    models/
      Enums.kt                  # UnitSystem, SurfaceType, GroutColor, TileSource, TilePattern, CutEdge
      Project.kt                # Project data class
      Room.kt                   # Room data class
      Surface.kt                # Surface data class + SurfacePosition
      SurfaceTileGroup.kt       # SurfaceTileGroup data class + RegionRect
      TileGroup.kt              # TileGroup data class
      PlacedTile.kt             # PlacedTile data class
      LayoutResult.kt           # LayoutResult data class
      CutEntry.kt               # CutEntry + CutLocation data classes

    db/
      TileLayoutDb.sq            # SQLDelight schema
      ProjectRepository.kt       # CRUD queries
      RoomRepository.kt
      SurfaceRepository.kt
      TileGroupRepository.kt
      LayoutResultRepository.kt

    engine/
      SurfacePositionCalculator.kt  # Auto-position surfaces from room dims
      RegionValidator.kt            # Overlap + coverage validation
      LayoutEngine.kt               # Core tile placement algorithm
      IsometricProjection.kt        # Shared projection math for 3D view

    cutlist/
      CutListGenerator.kt           # Walk LayoutResults → grouped CutEntry list

    viewmodel/
      SharedViewModels.kt           # StateFlow-based ViewModels consumed by both platforms

  src/commonTest/kotlin/com/hasu/tilelayout/
    engine/
      SurfacePositionCalculatorTest.kt
      RegionValidatorTest.kt
      LayoutEngineTest.kt
    cutlist/
      CutListGeneratorTest.kt

ios/
  TileLayout/
    Views/
      HomeView.swift
      ProjectDetailView.swift
      RoomEditorView.swift
      SurfaceDetailView.swift
      RegionEditorView.swift
      TileLibraryView.swift
      CameraView.swift
      HelpDiagramView.swift
      LayoutTabView.swift
      PreviewTabView.swift
      CutListTabView.swift
    Canvas/
      SurfaceCanvas.swift          # 2D elevation drawing
      IsometricCanvas.swift        # 3D room view drawing

android/
  app/src/main/java/com/hasu/tilelayout/
    ui/
      screens/
        HomeScreen.kt
        ProjectDetailScreen.kt
        RoomEditorScreen.kt
        SurfaceDetailScreen.kt
        RegionEditorScreen.kt
        TileLibraryScreen.kt
        CameraScreen.kt
        HelpDiagramDialog.kt
      tabs/
        LayoutTab.kt
        PreviewTab.kt
        CutListTab.kt
      canvas/
        SurfaceCanvas.kt           # 2D elevation Compose Canvas
        IsometricCanvas.kt         # 3D room Compose Canvas
```

---

## Phase 1: KMP Project Scaffold

### Task 1: Create KMP project structure

- [ ] **Step 1: Create Gradle project structure**

```bash
mkdir -p shared/src/{commonMain/kotlin/com/hasu/tilelayout/{models,db,engine,cutlist,viewmodel},commonTest/kotlin/com/hasu/tilelayout/{engine,cutlist}}
mkdir -p ios/TileLayout/{Views,Canvas}
mkdir -p android/app/src/main/java/com/hasu/tilelayout/ui/{screens,tabs,canvas}
```

- [ ] **Step 2: Write root build.gradle.kts**

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application").version("8.2.0").apply(false)
    id("com.android.library").version("8.2.0").apply(false)
    kotlin("android").version("2.0.0").apply(false)
    kotlin("multiplatform").version("2.0.0").apply(false)
    id("app.cash.sqldelight").version("2.0.1").apply(false)
}
```

- [ ] **Step 3: Write settings.gradle.kts**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TileLayout"
include(":shared")
include(":android")
```

- [ ] **Step 4: Write shared/build.gradle.kts**

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("TileLayoutDb") {
            packageName.set("com.hasu.tilelayout.db")
        }
    }
}
```

- [ ] **Step 5: Write android/build.gradle.kts**

```kotlin
// android/build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.hasu.tilelayout"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hasu.tilelayout"
        minSdk = 26
        targetSdk = 34
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.0" }

    dependencies {
        implementation(project(":shared"))
        implementation("androidx.compose.ui:ui:1.6.0")
        implementation("androidx.compose.material3:material3:1.2.0")
        implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
        implementation("androidx.activity:activity-compose:1.8.0")
        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: KMP project scaffold with Gradle, shared module, Android stub"
```

---

## Phase 2: Shared Data Models

### Task 2: Define enums and data classes

**Files:** All files under `shared/src/commonMain/kotlin/com/hasu/tilelayout/models/`

- [ ] **Step 1: Write Enums.kt**

```kotlin
// shared/.../models/Enums.kt
package com.hasu.tilelayout.models

enum class UnitSystem { MM, INCHES }
enum class SurfaceType { WALL, FLOOR }
enum class GroutColor { BLACK, GREY, WHITE }
enum class TileSource { CAPTURED, IMPORTED }
enum class TilePattern { GRID, BRICK, STACKED, HERRINGBONE }
enum class CutEdge { LEFT, RIGHT, TOP, BOTTOM }
```

- [ ] **Step 2: Write Project.kt**

```kotlin
// shared/.../models/Project.kt
package com.hasu.tilelayout.models

import kotlinx.datetime.Clock

data class Project(
    val id: String = uuid4(),
    val name: String,
    val units: UnitSystem = UnitSystem.MM,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
)

// Simple UUID generator
fun uuid4(): String {
    val chars = "0123456789abcdef"
    return (1..8).map { chars.random() }.joinToString("") + "-" +
           (1..4).map { chars.random() }.joinToString("") + "-4" +
           (1..3).map { chars.random() }.joinToString("") + "-" +
           listOf('8', '9', 'a', 'b').random() +
           (1..3).map { chars.random() }.joinToString("") + "-" +
           (1..12).map { chars.random() }.joinToString("")
}
```

- [ ] **Step 3: Write TileGroup.kt**

```kotlin
// shared/.../models/TileGroup.kt
package com.hasu.tilelayout.models

data class TileGroup(
    val id: String = uuid4(),
    val projectId: String,
    val name: String,
    val tileWidth: Double,
    val tileHeight: Double,
    val texturePath: String? = null,
    val source: TileSource = TileSource.IMPORTED,
)
```

- [ ] **Step 4: Write Room.kt, Surface.kt, SurfaceTileGroup.kt, PlacedTile.kt, LayoutResult.kt, CutEntry.kt**

```kotlin
// Room.kt
data class Room(
    val id: String = uuid4(),
    val projectId: String,
    val name: String,
    val width: Double,   // overall room width (X)
    val depth: Double,   // overall room depth (Z)
    val height: Double,  // overall room height (Y)
)

// Surface.kt
data class SurfacePosition(val x: Double, val y: Double, val z: Double, val rotation: Double)

data class Surface(
    val id: String = uuid4(),
    val roomId: String,
    val type: SurfaceType,
    val width: Double,
    val height: Double,
    val position: SurfacePosition,
    val groutColor: GroutColor = GroutColor.GREY,
    val groutWidth: Double = 3.0,
)

// SurfaceTileGroup.kt
data class RegionRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun overlaps(other: RegionRect): Boolean {
        if (x >= other.x + other.width) return false
        if (x + width <= other.x) return false
        if (y >= other.y + other.height) return false
        if (y + height <= other.y) return false
        return true
    }
    fun intersectionArea(other: RegionRect): Double {
        val ix = maxOf(x, other.x)
        val iy = maxOf(y, other.y)
        val iw = minOf(x + width, other.x + other.width) - ix
        val ih = minOf(y + height, other.y + other.height) - iy
        return if (iw <= 0 || ih <= 0) 0.0 else iw * ih
    }
    val area: Double get() = width * height
}

data class SurfaceTileGroup(
    val id: String = uuid4(),
    val surfaceId: String,
    val tileGroupId: String,
    val region: RegionRect,
    val pattern: TilePattern = TilePattern.GRID,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val locked: Boolean = false,
)

// PlacedTile.kt
data class PlacedTile(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double = 0.0,
    val isCut: Boolean = false,
    val cutEdges: List<CutEdge> = emptyList(),
    val tileGroupId: String,
)

// LayoutResult.kt
data class LayoutResult(
    val id: String = uuid4(),
    val surfaceId: String,
    val tiles: List<PlacedTile> = emptyList(),
    val computedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val stale: Boolean = true,
)

// CutEntry.kt
data class CutLocation(
    val surfaceId: String,
    val surfaceName: String,
    val count: Int,
)

data class CutEntry(
    val tileGroupId: String,
    val tileGroupName: String,
    val width: Double,
    val height: Double,
    val cutEdgesKey: String,
    val locations: List<CutLocation>,
    val totalCount: Int,
) {
    val cutTypeDescription: String get() = when (cutEdgesKey) {
        "LEFT" -> "Left edge cut"
        "RIGHT" -> "Right edge cut"
        "TOP" -> "Top edge cut"
        "BOTTOM" -> "Bottom edge cut"
        "RIGHT,BOTTOM" -> "Corner cut (right + bottom)"
        "LEFT,BOTTOM" -> "Corner cut (left + bottom)"
        "RIGHT,TOP" -> "Corner cut (right + top)"
        "LEFT,TOP" -> "Corner cut (left + top)"
        else -> "Custom cut"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/hasu/tilelayout/models/
git commit -m "feat: define all shared data model classes"
```

---

## Phase 3: SQLDelight Storage

### Task 3: Write SQLDelight schema

**Files:** `shared/src/commonMain/sqldelight/com/hasu/tilelayout/db/TileLayoutDb.sq`

- [ ] **Step 1: Write TileLayoutDb.sq**

```sql
CREATE TABLE projects (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    units TEXT NOT NULL DEFAULT 'MM',
    created_at INTEGER NOT NULL
);

CREATE TABLE tile_groups (
    id TEXT NOT NULL PRIMARY KEY,
    project_id TEXT NOT NULL,
    name TEXT NOT NULL,
    tile_width REAL NOT NULL,
    tile_height REAL NOT NULL,
    texture_path TEXT,
    source TEXT NOT NULL DEFAULT 'IMPORTED',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE rooms (
    id TEXT NOT NULL PRIMARY KEY,
    project_id TEXT NOT NULL,
    name TEXT NOT NULL,
    width REAL NOT NULL,
    depth REAL NOT NULL,
    height REAL NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE surfaces (
    id TEXT NOT NULL PRIMARY KEY,
    room_id TEXT NOT NULL,
    type TEXT NOT NULL,
    width REAL NOT NULL,
    height REAL NOT NULL,
    pos_x REAL NOT NULL,
    pos_y REAL NOT NULL,
    pos_z REAL NOT NULL,
    pos_rotation REAL NOT NULL,
    grout_color TEXT NOT NULL DEFAULT 'GREY',
    grout_width REAL NOT NULL DEFAULT 3.0,
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
);

CREATE TABLE surface_tile_groups (
    id TEXT NOT NULL PRIMARY KEY,
    surface_id TEXT NOT NULL,
    tile_group_id TEXT NOT NULL,
    region_x REAL NOT NULL,
    region_y REAL NOT NULL,
    region_w REAL NOT NULL,
    region_h REAL NOT NULL,
    pattern TEXT NOT NULL DEFAULT 'GRID',
    offset_x REAL NOT NULL DEFAULT 0.0,
    offset_y REAL NOT NULL DEFAULT 0.0,
    FOREIGN KEY (surface_id) REFERENCES surfaces(id) ON DELETE CASCADE,
    FOREIGN KEY (tile_group_id) REFERENCES tile_groups(id) ON DELETE CASCADE
);

CREATE TABLE layout_results (
    id TEXT NOT NULL PRIMARY KEY,
    surface_id TEXT NOT NULL,
    computed_at INTEGER NOT NULL,
    stale INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (surface_id) REFERENCES surfaces(id) ON DELETE CASCADE
);

CREATE TABLE placed_tiles (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    layout_result_id TEXT NOT NULL,
    x REAL NOT NULL,
    y REAL NOT NULL,
    w REAL NOT NULL,
    h REAL NOT NULL,
    rotation REAL NOT NULL DEFAULT 0,
    is_cut INTEGER NOT NULL DEFAULT 0,
    cut_edges TEXT NOT NULL DEFAULT '',
    tile_group_id TEXT NOT NULL,
    FOREIGN KEY (layout_result_id) REFERENCES layout_results(id) ON DELETE CASCADE
);

-- Queries
getAllProjects:
SELECT * FROM projects ORDER BY created_at DESC;

getProjectById:
SELECT * FROM projects WHERE id = ?;

insertProject:
INSERT OR REPLACE INTO projects(id, name, units, created_at) VALUES (?, ?, ?, ?);

deleteProject:
DELETE FROM projects WHERE id = ?;

getTileGroupsByProject:
SELECT * FROM tile_groups WHERE project_id = ? ORDER BY name ASC;

getTileGroupById:
SELECT * FROM tile_groups WHERE id = ?;

insertTileGroup:
INSERT OR REPLACE INTO tile_groups(id, project_id, name, tile_width, tile_height, texture_path, source)
VALUES (?, ?, ?, ?, ?, ?, ?);

deleteTileGroup:
DELETE FROM tile_groups WHERE id = ?;

getRoomsByProject:
SELECT * FROM rooms WHERE project_id = ? ORDER BY name ASC;

getRoomById:
SELECT * FROM rooms WHERE id = ?;

insertRoom:
INSERT OR REPLACE INTO rooms(id, project_id, name, width, depth, height) VALUES (?, ?, ?, ?, ?, ?);

deleteRoom:
DELETE FROM rooms WHERE id = ?;

getSurfacesByRoom:
SELECT * FROM surfaces WHERE room_id = ?;

getSurfaceById:
SELECT * FROM surfaces WHERE id = ?;

insertSurface:
INSERT OR REPLACE INTO surfaces(id, room_id, type, width, height, pos_x, pos_y, pos_z, pos_rotation, grout_color, grout_width)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteSurface:
DELETE FROM surfaces WHERE id = ?;

getSTGsBySurface:
SELECT * FROM surface_tile_groups WHERE surface_id = ?;

insertSTG:
INSERT OR REPLACE INTO surface_tile_groups(id, surface_id, tile_group_id, region_x, region_y, region_w, region_h, pattern, offset_x, offset_y)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteSTG:
DELETE FROM surface_tile_groups WHERE id = ?;

getLayoutResultBySurface:
SELECT * FROM layout_results WHERE surface_id = ?;

insertLayoutResult:
INSERT OR REPLACE INTO layout_results(id, surface_id, computed_at, stale) VALUES (?, ?, ?, ?);

deleteLayoutResult:
DELETE FROM layout_results WHERE id = ?;

getTilesByLayoutResult:
SELECT * FROM placed_tiles WHERE layout_result_id = ?;

insertPlacedTile:
INSERT INTO placed_tiles(layout_result_id, x, y, w, h, rotation, is_cut, cut_edges, tile_group_id)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

deleteTilesByLayoutResult:
DELETE FROM placed_tiles WHERE layout_result_id = ?;
```

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/sqldelight/
git commit -m "feat: SQLDelight schema with all tables and queries"
```

---

## Phase 4: Shared Engine

### Task 4: Surface position calculator

**Files:** `shared/src/commonMain/kotlin/com/hasu/tilelayout/engine/SurfacePositionCalculator.kt`

```kotlin
// SurfacePositionCalculator.kt
package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*

object SurfacePositionCalculator {
    fun generate(
        roomId: String, roomWidth: Double, roomDepth: Double, roomHeight: Double,
        includeFront: Boolean, includeBack: Boolean, includeLeft: Boolean,
        includeRight: Boolean, includeFloor: Boolean,
    ): List<Surface> = buildList {
        if (includeFront)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomWidth,
                height = roomHeight, position = SurfacePosition(0.0, 0.0, 0.0, 0.0)))

        if (includeBack)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomWidth,
                height = roomHeight, position = SurfacePosition(roomWidth, 0.0, roomDepth, 180.0)))

        if (includeLeft)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomDepth,
                height = roomHeight, position = SurfacePosition(0.0, 0.0, roomDepth, 90.0)))

        if (includeRight)
            add(Surface(roomId = roomId, type = SurfaceType.WALL, width = roomDepth,
                height = roomHeight, position = SurfacePosition(roomWidth, 0.0, 0.0, 270.0)))

        if (includeFloor)
            add(Surface(roomId = roomId, type = SurfaceType.FLOOR, width = roomWidth,
                height = roomDepth, position = SurfacePosition(0.0, 0.0, 0.0, 0.0)))
    }
}
```

### Task 5: Region validator

**Files:** `shared/.../engine/RegionValidator.kt`

```kotlin
// RegionValidator.kt
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

        // Overlap check
        for (stg in existingRegions) {
            if (excludeId != null && stg.id == excludeId) continue
            if (newRegion.intersectionArea(stg.region) > 0) {
                errors.add(RegionValidationError("Regions overlap — adjust boundaries."))
                break
            }
        }

        // Coverage check
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
```

### Task 6: Layout engine

**Files:** `shared/.../engine/LayoutEngine.kt`

```kotlin
// LayoutEngine.kt
package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.math.*

object LayoutEngine {
    private const val MIN_CUT_RATIO = 0.30

    fun compute(
        region: RegionRect,
        tileGroup: TileGroup,
        groutWidth: Double,
        pattern: TilePattern,
        offsetX: Double,
        offsetY: Double,
    ): List<PlacedTile> = when (pattern) {
        TilePattern.GRID, TilePattern.STACKED ->
            computeGrid(region, tileGroup, groutWidth, offsetX, offsetY)
        TilePattern.BRICK ->
            computeBrick(region, tileGroup, groutWidth, offsetX, offsetY)
        TilePattern.HERRINGBONE ->
            computeHerringbone(region, tileGroup, groutWidth, offsetX, offsetY)
    }

    private fun computeGrid(
        region: RegionRect, tg: TileGroup, groutW: Double, offX: Double, offY: Double,
    ): List<PlacedTile> {
        val unitW = tg.tileWidth + groutW
        val unitH = tg.tileHeight + groutW

        var fullCols = (region.width / unitW).toInt()
        var fullRows = (region.height / unitH).toInt()
        var remW = region.width - fullCols * unitW
        var remH = region.height - fullRows * unitH

        if (remW > 0 && remW < tg.tileWidth * MIN_CUT_RATIO) { fullCols--; remW = region.width - fullCols * unitW }
        if (remH > 0 && remH < tg.tileHeight * MIN_CUT_RATIO) { fullRows--; remH = region.height - fullRows * unitH }

        // Wrap-around: tiles exiting one edge reappear on opposite
        val wrappedOffX = offX % unitW
        val wrappedOffY = offY % unitH
        val startX = remW / 2 + wrappedOffX
        val startY = remH / 2 + wrappedOffY

        return buildList {
            for (row in -1..fullRows + 1) for (col in -1..fullCols + 1) {
                val x = startX + col * unitW
                val y = startY + row * unitH
                if (x >= region.width || y >= region.height) continue
                val w = min(tg.tileWidth, region.width - x)
                val h = min(tg.tileHeight, region.height - y)
                if (w <= 0 || h <= 0) continue
                val isCut = abs(w - tg.tileWidth) > 0.01 || abs(h - tg.tileHeight) > 0.01
                val edges = if (isCut) cutEdges(x, y, w, h, region.width, region.height) else emptyList()
                add(PlacedTile(region.x + x, region.y + y, w, h, isCut = isCut, cutEdges = edges, tileGroupId = tg.id))
            }
        }
    }

    private fun computeBrick(
        region: RegionRect, tg: TileGroup, groutW: Double, offX: Double, offY: Double,
    ): List<PlacedTile> {
        val unitW = tg.tileWidth + groutW
        val unitH = tg.tileHeight + groutW
        val halfW = unitW / 2

        var fullRows = (region.height / unitH).toInt()
        var remH = region.height - fullRows * unitH
        if (remH > 0 && remH < tg.tileHeight * MIN_CUT_RATIO) { fullRows--; remH = region.height - fullRows * unitH }
        val startY = remH / 2 + offY

        // Row-pair symmetry: compute cols from offset row (worst case)
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
                    if (x >= region.width || y >= region.height) continue
                    val w = min(tg.tileWidth, region.width - x)
                    val h = min(tg.tileHeight, region.height - y)
                    if (w <= 0 || h <= 0) continue
                    val isCut = abs(w - tg.tileWidth) > 0.01 || abs(h - tg.tileHeight) > 0.01
                    val edges = if (isCut) cutEdges(x, y, w, h, region.width, region.height) else emptyList()
                    add(PlacedTile(region.x + x, region.y + y, w, h, isCut = isCut, cutEdges = edges, tileGroupId = tg.id))
                }
            }
        }
    }

    private fun computeHerringbone(
        region: RegionRect, tg: TileGroup, groutW: Double, offX: Double, offY: Double,
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
                add(PlacedTile(region.x + cx, region.y + cy, cw, ch, rotation = rot, isCut = isCut, cutEdges = edges, tileGroupId = tg.id))
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
```

### Task 7: Isometric projection + Cut list generator

```kotlin
// IsometricProjection.kt
package com.hasu.tilelayout.engine

import kotlin.math.*

object IsometricProjection {
    private const val COS30 = 0.8660254
    private const val SIN30 = 0.5

    data class ScreenPoint(val x: Double, val y: Double)

    fun project(sx: Double, sy: Double, sz: Double, viewAngle: Int, originX: Double, originY: Double): ScreenPoint {
        val rad = viewAngle * PI / 180
        val cosA = cos(rad); val sinA = sin(rad)
        val rx = sx * cosA - sz * sinA
        val rz = sx * sinA + sz * cosA
        return ScreenPoint(
            x = (rx - rz) * COS30 + originX,
            y = (rx + rz) * SIN30 - sy + originY,
        )
    }

    /** Project a surface's 4 corners to screen coordinates. */
    fun projectSurfaceCorners(surface: Surface, viewAngle: Int, originX: Double, originY: Double): List<ScreenPoint> {
        val p = surface.position
        val w = surface.width; val h = surface.height
        return listOf(
            project(p.x, p.y, p.z, viewAngle, originX, originY),
            project(p.x + w, p.y, p.z, viewAngle, originX, originY),
            project(p.x + w, p.y + h, p.z, viewAngle, originX, originY),
            project(p.x, p.y + h, p.z, viewAngle, originX, originY),
        )
    }

    /** Order surfaces back-to-front for painter's algorithm. */
    fun orderSurfaces(surfaces: List<Surface>, viewAngle: Int): List<Surface> {
        val rad = viewAngle * PI / 180
        val floors = surfaces.filter { it.type == SurfaceType.FLOOR }
        val walls = surfaces.filter { it.type == SurfaceType.WALL }
            .sortedBy { it.position.x * sin(rad) + it.position.z * cos(rad) }
        return floors + walls
    }

    /** Ray-casting point-in-polygon test. */
    fun pointInPolygon(px: Double, py: Double, polygon: List<ScreenPoint>): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val yi = polygon[i].y; val yj = polygon[j].y
            val xi = polygon[i].x; val xj = polygon[j].x
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
```

```kotlin
// CutListGenerator.kt
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
```

- [ ] **Commit engine phase**

```bash
git add shared/src/commonMain/kotlin/com/hasu/tilelayout/engine/ shared/src/commonMain/kotlin/com/hasu/tilelayout/cutlist/
git commit -m "feat: layout engine, position calculator, region validator, isometric projection, cut list generator"
```

---

## Phase 5: Shared ViewModels

### Task 8: StateFlow-based ViewModels

**Files:** `shared/.../viewmodel/SharedViewModels.kt`

```kotlin
// SharedViewModels.kt
package com.hasu.tilelayout.viewmodel

import com.hasu.tilelayout.models.*
import com.hasu.tilelayout.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProjectListViewModel(private val repo: ProjectRepository) {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects

    suspend fun load() { _projects.value = repo.getAll() }
    suspend fun create(name: String): Project {
        val p = Project(name = name)
        repo.insert(p)
        load(); return p
    }
    suspend fun delete(id: String) { repo.delete(id); load() }
}

class RoomEditorViewModel(
    private val roomRepo: RoomRepository,
    private val surfaceRepo: SurfaceRepository,
    private val tileGroupRepo: TileGroupRepository,
    private val layoutRepo: LayoutResultRepository,
) {
    private val _surfaces = MutableStateFlow<List<Surface>>(emptyList())
    val surfaces: StateFlow<List<Surface>> = _surfaces

    private val _selectedSurfaceId = MutableStateFlow<String?>(null)
    val selectedSurfaceId: StateFlow<String?> = _selectedSurfaceId

    private val _viewAngle = MutableStateFlow(0)
    val viewAngle: StateFlow<Int> = _viewAngle

    // Lock state (transient, not persisted)
    private val _lockedSurfaceIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedSurfaceIds: StateFlow<Set<String>> = _lockedSurfaceIds

    // Undo buffer: stgId → prior offset. Single-level, transient.
    private val _undoBuffer = MutableStateFlow<Map<String, Pair<Double, Double>>?>(null)
    val undoBuffer: StateFlow<Map<String, Pair<Double, Double>>?> = _undoBuffer

    suspend fun loadSurfaces(roomId: String) {
        _surfaces.value = surfaceRepo.getByRoom(roomId)
    }
    fun selectSurface(id: String?) { _selectedSurfaceId.value = id }
    fun rotateView(delta: Int) { _viewAngle.value = (_viewAngle.value + delta) % 360 }
    fun toggleLock(surfaceId: String) {
        val current = _lockedSurfaceIds.value
        _lockedSurfaceIds.value = if (surfaceId in current) current - surfaceId else current + surfaceId
    }

    /** Snapshot offsets before drag begins (for undo). */
    suspend fun onDragStart() {
        val selectedId = _selectedSurfaceId.value ?: return
        val affectedIds = setOf(selectedId) + _lockedSurfaceIds.value
        val priors = mutableMapOf<String, Pair<Double, Double>>()
        for (id in affectedIds) {
            val stgs = surfaceRepo.getSTGsBySurface(id)
            stgs.forEach { priors[it.id] = Pair(it.offsetX, it.offsetY) }
        }
        _undoBuffer.value = priors
    }

    /**
     * Apply drag offset with axis-aware lock propagation.
     * dx/dy are in surface-local units.
     */
    suspend fun onDragEnd(dx: Double, dy: Double) {
        val selectedId = _selectedSurfaceId.value ?: return
        val selected = _surfaces.value.find { it.id == selectedId } ?: return

        // Apply to selected surface
        applyOffset(selectedId, dx, dy)

        // Lock propagation
        for (lockedId in _lockedSurfaceIds.value) {
            val locked = _surfaces.value.find { it.id == lockedId } ?: continue
            val (propDx, propDy) = propagateDelta(selected, locked, dx, dy)
            if (propDx != 0.0 || propDy != 0.0) applyOffset(lockedId, propDx, propDy)
        }
    }

    /**
     * Axis-aware propagation per spec:
     * Walls: horizontal only to parallel walls; vertical to all walls.
     * Floors: both axes to other floors. Wall↔floor: no propagation.
     */
    private fun propagateDelta(source: Surface, target: Surface, dx: Double, dy: Double): Pair<Double, Double> {
        if (source.type == SurfaceType.FLOOR && target.type == SurfaceType.FLOOR) return Pair(dx, dy)
        if (source.type == SurfaceType.WALL && target.type == SurfaceType.WALL) {
            val parallel = (source.position.rotation.toInt() % 180) == (target.position.rotation.toInt() % 180)
            return Pair(if (parallel) dx else 0.0, dy)
        }
        return Pair(0.0, 0.0) // wall↔floor: no propagation in MVP
    }

    private suspend fun applyOffset(surfaceId: String, dx: Double, dy: Double) {
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)
        for (stg in stgs) {
            surfaceRepo.updateSTG(stg.copy(offsetX = stg.offsetX + dx, offsetY = stg.offsetY + dy))
        }
        computeLayout(surfaceId)
    }

    fun undo() {
        val buffer = _undoBuffer.value ?: return
        // Restore prior offsets — platform triggers recompute
        _undoBuffer.value = null
        // Platform calls undoRestore() with these values
    }

    suspend fun undoRestore(priors: Map<String, Pair<Double, Double>>) {
        for ((stgId, offset) in priors) {
            val stg = surfaceRepo.getSTGById(stgId) ?: continue
            surfaceRepo.updateSTG(stg.copy(offsetX = offset.first, offsetY = offset.second))
        }
        _selectedSurfaceId.value?.let { computeLayout(it) }
    }

    suspend fun computeLayout(surfaceId: String) {
        val surface = surfaceRepo.getById(surfaceId) ?: return
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)
        val tiles = stgs.flatMap { stg ->
            val tg = tileGroupRepo.getById(stg.tileGroupId) ?: return@flatMap emptyList()
            LayoutEngine.compute(stg.region, tg, surface.groutWidth, stg.pattern, stg.offsetX, stg.offsetY)
        }
        layoutRepo.save(LayoutResult(surfaceId = surfaceId, tiles = tiles, stale = false))
    }

    /** 3D hit testing: find which surface was tapped at screen coords. */
    fun hitTest(tapX: Double, tapY: Double, canvasWidth: Double, canvasHeight: Double): String? {
        val originX = canvasWidth / 2; val originY = canvasHeight * 0.6
        // Check front-to-back (reverse painter's order)
        val ordered = IsometricProjection.orderSurfaces(_surfaces.value, _viewAngle.value).reversed()
        for (surface in ordered) {
            val corners = IsometricProjection.projectSurfaceCorners(surface, _viewAngle.value, originX, originY)
            if (IsometricProjection.pointInPolygon(tapX, tapY, corners)) return surface.id
        }
        return null
    }
}

/** Loads and caches texture images from disk. Platform-specific image type via expect/actual. */
expect class TextureLoader() {
    suspend fun loadTexture(tileGroup: TileGroup): Any?
    fun evict(tileGroupId: String)
    fun clear()
}
```

- [ ] **Commit**

```bash
git add shared/src/commonMain/kotlin/com/hasu/tilelayout/viewmodel/
git commit -m "feat: shared ViewModels with StateFlow for platform consumption"
```


---

## Phase 6: Shared Tests

### Task 9: Write engine tests

**Files:** Tests under `shared/src/commonTest/`

```kotlin
// LayoutEngineTest.kt
package com.hasu.tilelayout.engine

import com.hasu.tilelayout.models.*
import kotlin.test.*

class LayoutEngineTest {
    private val tileGroup = TileGroup(projectId = "p1", name = "Test", tileWidth = 300.0, tileHeight = 200.0)
    private val region = RegionRect(0.0, 0.0, 903.0, 603.0)

    @Test
    fun gridProducesTiles() {
        val tiles = LayoutEngine.compute(region, tileGroup, 3.0, TilePattern.GRID, 0.0, 0.0)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.any { it.isCut })
        assertTrue(tiles.any { !it.isCut })
    }

    @Test
    fun sliverElimination() {
        // 303*3 + 20 = 929 → 20 remainder < 30% of 300 (=90)
        val region = RegionRect(0.0, 0.0, 929.0, 303.0)
        val tiles = LayoutEngine.compute(region, tileGroup, 3.0, TilePattern.GRID, 0.0, 0.0)
        val tinyCuts = tiles.filter { it.isCut && (it.width < 90 || it.height < 60) }
        assertTrue(tinyCuts.isEmpty(), "No sliver cuts should remain")
    }

    @Test
    fun herringboneHasRotations() {
        val tiles = LayoutEngine.compute(
            RegionRect(0.0, 0.0, 1200.0, 800.0),
            TileGroup(projectId = "p1", name = "Test", tileWidth = 300.0, tileHeight = 100.0),
            3.0, TilePattern.HERRINGBONE, 0.0, 0.0,
        )
        assertTrue(tiles.any { it.rotation == 45.0 })
        assertTrue(tiles.any { it.rotation == -45.0 })
    }
}
```

```kotlin
// RegionValidatorTest.kt
class RegionValidatorTest {
    @Test
    fun fullRegionPasses() {
        val result = RegionValidator.validate(
            RegionRect(0.0, 0.0, 2000.0, 1200.0),
            emptyList(), 2000.0, 1200.0,
        )
        assertTrue(result.isValid)
    }

    @Test
    fun overlapFails() {
        val existing = listOf(
            SurfaceTileGroup(surfaceId = "s1", tileGroupId = "tg1", region = RegionRect(0.0, 0.0, 2000.0, 1200.0))
        )
        val result = RegionValidator.validate(
            RegionRect(1000.0, 0.0, 1000.0, 1200.0),
            existing, 2000.0, 1200.0,
        )
        assertFalse(result.isValid)
    }
}
```

- [ ] **Run tests**

```bash
./gradlew :shared:test
```
Expected: all tests pass.

- [ ] **Commit**

```bash
git add shared/src/commonTest/
git commit -m "test: engine and region validator tests"
```

---

## Phase 7: iOS UI (SwiftUI)

Pattern: Each View observes shared StateFlows. Shared module imported as `shared` framework.

### Task 10: iOS project setup + Home/Project screens

- [ ] **Step 1: Create Xcode project, add shared framework dependency**

Configure KMP framework export in `shared/build.gradle.kts`:
```kotlin
iosX64(); iosArm64(); iosSimulatorArm64()
cocoapods { framework { baseName = "shared" } }
```

- [ ] **Step 2: Write HomeView.swift + ProjectDetailView.swift**

```swift
// HomeView.swift
import SwiftUI
import shared

struct HomeView: View {
    @StateObject private var vm = IOSProjectListViewModel()

    var body: some View {
        NavigationStack {
            List {
                ForEach(vm.projects, id: \.id) { project in
                    NavigationLink(project.name, destination: ProjectDetailView(project: project))
                }
                .onDelete { vm.delete(at: $0) }
            }
            .navigationTitle("Projects")
            .toolbar { Button("+") { vm.showCreate = true } }
            .sheet(isPresented: $vm.showCreate) { CreateProjectSheet(vm: vm) }
        }
        .task { await vm.load() }
    }
}

// ProjectDetailView.swift — segmented [Rooms | Tile Library]
struct ProjectDetailView: View {
    let project: Project
    @State private var tab = 0
    var body: some View {
        VStack {
            Picker("", selection: $tab) {
                Text("Rooms").tag(0)
                Text("Tile Library").tag(1)
            }.pickerStyle(.segmented).padding(.horizontal)
            if tab == 0 { RoomListView(projectId: project.id) }
            else { TileLibraryView(projectId: project.id) }
        }.navigationTitle(project.name)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ios/
git commit -m "feat(ios): home + project detail views"
```

---

### Task 11: iOS Room Editor + Surface Detail

- [ ] **Step 1: Write RoomEditorView.swift (tab scaffold)**

```swift
// RoomEditorView.swift
struct RoomEditorView: View {
    let roomId: String
    @StateObject private var vm = IOSRoomEditorViewModel()
    @State private var selectedTab = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $selectedTab) {
                Text("Surfaces").tag(0)
                Text("Layout").tag(1)
                Text("Preview").tag(2)
                Text("Cut List").tag(3)
            }.pickerStyle(.segmented).padding(.horizontal)

            switch selectedTab {
            case 0: SurfacesListView(vm: vm)
            case 1: LayoutTabView(vm: vm)
            case 2: PreviewTabView(vm: vm)
            case 3: CutListTabView(vm: vm)
            default: EmptyView()
            }
        }
        .task { await vm.load(roomId: roomId) }
    }
}
```

- [ ] **Step 2: Write SurfaceDetailView.swift (grout, tile groups, region editor)**

SurfaceDetailView shows: read-only dimensions, grout picker (3 color circles + width), tile group list with region/pattern chips, add tile group button.

- [ ] **Step 3: Write RegionEditorView.swift + HelpDiagramView.swift**

- [ ] **Step 4: Commit**

```bash
git add ios/TileLayout/Views/
git commit -m "feat(ios): room editor tabs, surface detail, region editor, help diagram"
```

---

### Task 12: iOS Layout Tab (2D Canvas + drag + undo)

- [ ] **Step 1: Write LayoutTabView.swift with Canvas rendering**

```swift
// LayoutTabView.swift
struct LayoutTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    @GestureState private var dragOffset = CGSize.zero

    var body: some View {
        VStack {
            // Surface selector chips
            ScrollView(.horizontal) {
                HStack {
                    ForEach(vm.surfaces, id: \.id) { surface in
                        Button(surface.name) { vm.selectSurface(surface.id) }
                            .buttonStyle(.bordered)
                            .tint(surface.id == vm.selectedSurfaceId ? .blue : .gray)
                    }
                }
            }.padding(.horizontal)

            // 2D Canvas with drag gesture
            Canvas { context, size in
                // Draw tiles from LayoutResult using vm.currentTiles
                drawSurface(context: context, size: size, tiles: vm.currentTiles, vm: vm)
            }
            .gesture(
                DragGesture()
                    .updating($dragOffset) { value, state, _ in state = value.translation }
                    .onChanged { _ in if !vm.isDragging { Task { await vm.onDragStart() } }; vm.isDragging = true }
                    .onEnded { value in
                        vm.isDragging = false
                        let dx = Double(value.translation.width) / vm.scale
                        let dy = Double(value.translation.height) / vm.scale
                        Task { await vm.onDragEnd(dx: dx, dy: dy) }
                    }
            )

            // Lock chips + action buttons
            HStack {
                ForEach(vm.otherSurfaces, id: \.id) { s in
                    Button(s.name) { vm.toggleLock(s.id) }
                        .buttonStyle(.bordered)
                        .tint(vm.isLocked(s.id) ? .purple : .gray)
                }
            }
            HStack {
                Button("Reset") { Task { await vm.resetToAuto() } }
                Button("Snap Center") { Task { await vm.snapToCenter() } }
                Button("Undo") { Task { await vm.undo() } }.disabled(vm.undoBuffer == nil)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add ios/TileLayout/Views/LayoutTabView.swift
git commit -m "feat(ios): layout tab with 2D canvas, drag, lock chips, undo"
```

---

### Task 13: iOS Preview Tab (3D Canvas + hit test)

- [ ] **Step 1: Write PreviewTabView.swift with isometric Canvas**

```swift
// PreviewTabView.swift
struct PreviewTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel

    var body: some View {
        VStack {
            Canvas { context, size in
                drawIsometricRoom(context: context, size: size, surfaces: vm.surfaces,
                                  viewAngle: vm.viewAngle, selectedId: vm.selectedSurfaceId)
            }
            .onTapGesture { location in
                if let id = vm.hitTest(tapX: location.x, tapY: location.y,
                                       canvasWidth: canvasSize.width, canvasHeight: canvasSize.height) {
                    vm.selectSurface(id)
                }
            }

            // Rotation buttons
            HStack {
                Button("◀") { vm.rotateView(-90) }
                Text("\(vm.viewAngle)°")
                Button("▶") { vm.rotateView(90) }
                Toggle("Top-Down", isOn: $vm.topDown)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add ios/TileLayout/Views/PreviewTabView.swift
git commit -m "feat(ios): 3D preview tab with isometric canvas, hit testing, rotation"
```

---

### Task 14: iOS Tile Library + Camera + Cut List

- [ ] **Step 1: Write TileLibraryView.swift (list + add/edit)**
- [ ] **Step 2: Write CameraView.swift (AVFoundation capture + edge detect)**

Uses platform-specific `actual class TextureLoader` on iOS:
```swift
// TextureLoaderIOS.swift
actual class TextureLoader actual constructor() {
    private var cache: [String: UIImage] = [:]
    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        if let cached = cache[tileGroup.id] { return cached }
        guard let path = tileGroup.texturePath else { return nil }
        guard let img = UIImage(contentsOfFile: path) else { return nil }
        cache[tileGroup.id] = img
        return img
    }
    actual fun evict(tileGroupId: String) { cache.removeValue(forKey: tileGroupId) }
    actual fun clear() { cache.removeAll() }
}
```

- [ ] **Step 3: Write CutListTabView.swift (grouped list with mini diagrams)**
- [ ] **Step 4: Commit**

```bash
git add ios/TileLayout/
git commit -m "feat(ios): tile library, camera capture, cut list tab"
```

---

## Phase 8: Android UI (Jetpack Compose)

### Task 15: Android project setup + Home/Project screens

- [ ] **Step 1: Write HomeScreen.kt + ProjectDetailScreen.kt**

```kotlin
// HomeScreen.kt
@Composable
fun HomeScreen(vm: ProjectListViewModel = remember { ProjectListViewModel(ProjectRepository(AppDatabase.instance)) }) {
    val projects by vm.projects.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Projects") }) },
        floatingActionButton = { FloatingActionButton(onClick = { /* show create dialog */ }) { Icon(Icons.Default.Add, "") } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(projects, key = { it.id }) { project ->
                ListItem(headlineContent = { Text(project.name) },
                    modifier = Modifier.clickable { navController.navigate("project/${project.id}") })
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/
git commit -m "feat(android): home + project detail screens"
```

---

### Task 16: Android Room Editor + Surface Detail

- [ ] **Step 1: Write RoomEditorScreen.kt with tab scaffold (Surfaces/Layout/Preview/Cut List)**
- [ ] **Step 2: Write SurfaceDetailScreen.kt + RegionEditorScreen.kt + HelpDiagramDialog.kt**
- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/hasu/tilelayout/ui/
git commit -m "feat(android): room editor tabs, surface detail, region editor"
```

---

### Task 17: Android Layout Tab (Compose Canvas + drag + undo)

- [ ] **Step 1: Write LayoutTab.kt with Compose Canvas**

```kotlin
// LayoutTab.kt
@Composable
fun LayoutTab(vm: RoomEditorViewModel) {
    val surfaces by vm.surfaces.collectAsState()
    val selectedId by vm.selectedSurfaceId.collectAsState()
    val lockedIds by vm.lockedSurfaceIds.collectAsState()
    val undoBuffer by vm.undoBuffer.collectAsState()

    Column {
        // Surface selector chips
        LazyRow(Modifier.padding(horizontal = 12.dp)) {
            items(surfaces) { s ->
                FilterChip(selected = s.id == selectedId,
                    onClick = { vm.selectSurface(s.id) },
                    label = { Text(s.name, fontSize = 11.sp) })
            }
        }

        // 2D Canvas with drag
        var dragStart by remember { mutableStateOf(Offset.Zero) }
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { runBlocking { vm.onDragStart() } },
                    onDragEnd = {
                        val dx = (dragStart.x / scale).toDouble()
                        val dy = (dragStart.y / scale).toDouble()
                        runBlocking { vm.onDragEnd(dx, dy) }
                        dragStart = Offset.Zero
                    },
                    onDrag = { _, offset -> dragStart += offset }
                )
            }
        ) {
            // Draw tiles from LayoutResult
            drawSurfaceTiles(vm.currentTiles, size, vm.groutColor, vm.groutWidth)
        }

        // Lock chips
        Row(Modifier.padding(horizontal = 12.dp)) {
            surfaces.filter { it.id != selectedId }.forEach { s ->
                FilterChip(selected = s.id in lockedIds,
                    onClick = { vm.toggleLock(s.id) },
                    label = { Text(s.name, fontSize = 10.sp) })
            }
        }

        // Action buttons
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { runBlocking { vm.resetToAuto() } }) { Text("Reset") }
            OutlinedButton(onClick = { runBlocking { vm.snapToCenter() } }) { Text("Snap Center") }
            OutlinedButton(onClick = { runBlocking { vm.undoRestore(undoBuffer!!) } },
                enabled = undoBuffer != null) { Text("Undo") }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/hasu/tilelayout/ui/tabs/LayoutTab.kt
git commit -m "feat(android): layout tab with compose canvas, drag, lock, undo"
```

---

### Task 18: Android Preview Tab (3D Canvas + hit test)

- [ ] **Step 1: Write PreviewTab.kt with isometric Compose Canvas + tap hit testing**

Same pattern as iOS: observe viewAngle StateFlow, draw surfaces via shared IsometricProjection, call `vm.hitTest()` on tap.

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/hasu/tilelayout/ui/tabs/PreviewTab.kt
git commit -m "feat(android): 3D preview tab with isometric canvas, hit test, rotation"
```

---

### Task 19: Android Tile Library + Camera + Cut List

- [ ] **Step 1: Write TileLibraryScreen.kt + CameraScreen.kt (CameraX)**
- [ ] **Step 2: Write TextureLoader actual (Android)**

```kotlin
// TextureLoaderAndroid.kt
actual class TextureLoader actual constructor() {
    private val cache = mutableMapOf<String, Bitmap>()
    actual suspend fun loadTexture(tileGroup: TileGroup): Any? {
        cache[tileGroup.id]?.let { return it }
        val path = tileGroup.texturePath ?: return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        cache[tileGroup.id] = bitmap
        return bitmap
    }
    actual fun evict(tileGroupId: String) { cache.remove(tileGroupId) }
    actual fun clear() { cache.clear() }
}
```

- [ ] **Step 3: Write CutListTab.kt**
- [ ] **Step 4: Commit**

```bash
git add android/
git commit -m "feat(android): tile library, camera, texture loader, cut list"
```

---

## Phase 9: Integration & Export

### Task 20: Compute pipeline + export

- [ ] **Step 1: Wire full compute pipeline**

Verify: editing offset → `onDragEnd()` → `applyOffset()` → `computeLayout()` → StateFlow update → Canvas redraws. Both platforms.

- [ ] **Step 2: Implement PNG export (platform-specific)**

iOS: Render Canvas to `UIImage` via `UIGraphicsImageRenderer`, save to Photos.
Android: Render Compose Canvas to `Bitmap` via `Picture` + `Canvas`, save via MediaStore.

Default DPI: 200. Configurable 150–300.

- [ ] **Step 3: End-to-end test on both platforms**

Verify: create project → add room → assign tiles → auto-layout → fine-tune with lock → export 2D/3D → cut list correct.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: integration pipeline, PNG export on both platforms"
```

---

## Phase 10: Door Wall Feature

**Goal:** Let the user specify which wall the room's door is on. Wall names become relative to the door ("Door Wall", "Front Wall" = the wall you face when entering, sides "Left/Right Wall"), the door renders as an opening in the 3D preview, and the layout engine skips tiles in the door area (with cut edges for the cut list).

**Scope decisions:**
- One door per room. Door defaults: 900×2100 mm, offset centered on the wall at selection time.
- Wall picker labels use coordinate names ("Front Wall" = z=0 wall) because door-relative names only exist once a door is set.
- Door sits on the floor: in surface-local coordinates the door rect is always `(doorOffset, 0, doorWidth, doorHeight)`.

### Task 21: Room door fields — schema migration + model

- [ ] **Step 1: Write `2.sqm` migration**

`sharedLogic/src/commonMain/sqldelight/com/hasu/tilelayout/db/2.sqm`:
```sql
ALTER TABLE rooms ADD COLUMN door_wall TEXT;
ALTER TABLE rooms ADD COLUMN door_width REAL;
ALTER TABLE rooms ADD COLUMN door_height REAL;
ALTER TABLE rooms ADD COLUMN door_offset REAL;
```

- [ ] **Step 2: Update `rooms` queries in TileLayoutDb.sq**

`getRoomsByProject` / `getRoomById` select the new columns; `insertRoom` writes them. Add `updateRoomDoor`:
```sql
updateRoomDoor:
UPDATE rooms SET door_wall = ?, door_width = ?, door_height = ?, door_offset = ?
WHERE id = ?;
```

- [ ] **Step 3: Update Room model + RoomRepository**

```kotlin
// Room.kt
data class Room(
    val id: String = uuid4(),
    val projectId: String,
    val name: String,
    val width: Double, val depth: Double, val height: Double,
    val doorWall: Double? = null,      // rotation of the door wall (0/90/180/270)
    val doorWidth: Double = 900.0,
    val doorHeight: Double = 2100.0,
    val doorOffset: Double? = null,    // distance from wall anchor corner; null = centered
)
```

RoomRepository gains `suspend fun updateDoor(roomId: String, doorWall: Double?, doorWidth: Double, doorHeight: Double, doorOffset: Double?)`.

- [ ] **Step 4: Wire migration callbacks**

- Android `AppDatabase.kt`: `AndroidSqliteDriver(TileLayoutDb.Schema, context, "tilelayout.db", callback = AndroidSqliteDriver.Callback(TileLayoutDb.Schema) { _, oldV, newV -> TileLayoutDb.Schema.migrate(it, oldV, newV) })`
- iOS `DatabaseProvider.kt`: same via `NativeSqliteDriver` callback.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: room door fields with SQLDelight 1→2 migration"
```

### Task 22: Door-relative wall naming

- [ ] **Step 1: Surface gains a transient doorRotation**

```kotlin
// Surface.kt
data class Surface(
    ...
    val doorRotation: Double? = null,  // transient: populated from the room at query time
) {
    fun displayName(): String = when (type) {
        SurfaceType.FLOOR -> "Floor"
        SurfaceType.WALL -> {
            val r = normalizedRotation(position.rotation)
            val d = doorRotation
            if (d == null) coordinateName(r)  // current Front/Back/Left/Right by convention
            else when (normalizedRotation(r - d)) {
                0 -> "Door Wall"
                90 -> "Left Wall"
                180 -> "Front Wall"
                270 -> "Right Wall"
                else -> coordinateName(r)
            }
        }
    }
}
```

- [ ] **Step 2: Repository JOIN**

`getSurfacesByRoom` joins rooms:
```sql
getSurfacesByRoom:
SELECT s.*, r.door_wall FROM surfaces s
JOIN rooms r ON r.id = s.room_id
WHERE s.room_id = ?;
```
`SqlDelightSurfaceRepository.getByRoom()` maps `door_wall` → `Surface.doorRotation`.

- [ ] **Step 3: Update tests**

`SurfaceDisplayNameTest` gains door-relative cases: door on 0 → wall 0 "Door Wall", wall 180 "Front Wall", wall 90 "Left Wall", wall 270 "Right Wall"; no door → coordinate names.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: door-relative wall names"
```

### Task 23: Door geometry + 3D preview rendering

- [ ] **Step 1: Shared DoorGeometry + DoorPickerGeometry helpers**

```kotlin
// engine/DoorGeometry.kt
object DoorGeometry {
    /** Door rect in surface-local coords (x along wall, y from floor up). */
    fun surfaceLocalRect(room: Room, surface: Surface): RegionRect? {
        val wall = room.doorWall ?: return null
        if (normalize(wall) != normalize(surface.position.rotation)) return null
        val offset = room.doorOffset ?: (surface.width - room.doorWidth) / 2.0
        return RegionRect(offset, 0.0, room.doorWidth, room.doorHeight)
    }

    /** Door rectangle as world-space corners, for the 3D preview. */
    fun worldCorners(room: Room, surface: Surface): List<Triple<Double, Double, Double>>?
}
```

```kotlin
// engine/DoorPickerGeometry.kt — edge-hit logic for the mini top-down diagram
object DoorPickerGeometry {
    /** Which wall edge a tap hits in a top-down diagram (rotation 0/90/180/270), or null. */
    fun wallAtTap(x: Double, y: Double, diagramW: Double, diagramH: Double,
                  roomWidth: Double, roomDepth: Double): Double?
}
```

World corners reuse the wall's rotation rules: the door offset runs along the wall's span direction from the anchor corner. `wallAtTap` maps a tap near an edge (within a fixed margin, e.g. 24dp scaled by the diagram size) to the wall rotation: Front edge = z=0 → 0, Back = 180, Left = 90, Right = 270.

- [ ] **Step 2: Draw door opening on both canvases**

After the wall loop in Android `drawIsometricRoom` and iOS `IsometricCanvas.drawIsometricRoom`: if the surface is the door wall, project the door's world corners, fill a dark opening rect (`0xFF3A3A3A` / dark gray), stroke it lighter. Draw the opening after the wall fill so it reads as a cutout.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: door opening rendered in 3D preview"
```

### Task 24: Layout exclusion

- [ ] **Step 1: LayoutEngine exclusions parameter**

```kotlin
fun compute(
    region: RegionRect,
    tileGroup: TileGroup,
    groutWidth: Double,
    pattern: TilePattern,
    offsetX: Double,
    offsetY: Double,
    exclusions: List<RegionRect> = emptyList(),
): List<PlacedTile>
```

All three algorithms (grid, brick, herringbone): after computing a tile's rect, skip it if it intersects any exclusion; otherwise add cut-edge flags for edges adjacent to an exclusion boundary (LEFT/RIGHT/TOP/BOTTOM as appropriate), merged with existing region-boundary cuts.

- [ ] **Step 2: Wire into computeLayout**

`RoomEditorViewModel.computeLayout()`: load the room (`roomRepo.getById(surface.roomId)`), compute the door's surface-local rect via `DoorGeometry.surfaceLocalRect(room, surface)`, pass it as the exclusion.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: layout engine skips door area with cut edges"
```

### Task 25: Door configuration UI (both platforms)

<<<<<<< HEAD
- [ ] **Step 1: Android — Door section in SurfacesListView**

Below the room dimensions text / above the surface list: a "Door" card with:
- Wall picker: FilterChips — None / Front Wall / Back Wall / Left Wall / Right Wall (coordinate names)
- Width + Height fields (defaults 900 / 2100 mm)
- Offset field (auto-centers on wall selection; editable)
- Save via `RoomRepository.updateDoor()`, then reload surfaces so names refresh
=======
Full UI spec (mockups, states, validation): `docs/superpowers/specs/2026-08-18-door-wall-feature-design.md` → "UI — Door Configuration".

- [ ] **Step 1: Android — mini diagram in the add-room dialog + "Door" card in SurfacesListView**

Per the spec mockups:
- **Add-room dialog** (ProjectDetailScreen.kt): under the dimension fields, a top-down mini diagram (~180dp, aspect = width/depth, labeled edges); tap → shared `DoorPickerGeometry.wallAtTap` → sets the door wall for the new room (no save button; part of creation). Door defaults to Front (z=0) unless the user taps elsewhere or "None".
- **"Door" card** in SurfacesListView: same diagram (tap to change wall, "None" `TextButton` below), Width/Height/Offset `OutlinedTextField`s (number keyboard, mm), full-width "Save Door" enabled only when dirty & valid. Offset auto-fills with the centered value on wall change. Inline validation captions per the spec. Save → `RoomRepository.updateDoor()` → reload surfaces.
>>>>>>> d7b7491 (docs: diagram-based door wall picker in spec and plan)

- [ ] **Step 2: iOS — mini diagram in AddRoomSheet + Door section in SurfacesListView**

<<<<<<< HEAD
Same section as a SwiftUI `Section("Door")` in the surfaces list: Picker (None/Front/Back/Left/Right), dimension fields, offset field; persists via `RoomRepository`.
=======
Per the spec mockups:
- **AddRoomSheet** (RoomListView.swift): `ZStack` diagram (rectangle + 4 tappable edge overlays) under the dimension fields; taps call the shared `wallAtTap`; door part of room creation, default Front.
- **`Section("Door")`** in the surfaces `List`: same diagram picker + "None" button, Width/Height/Offset `TextField`s (`.numberPad`), `.borderedProminent` Save button, same dirty/valid gating and validation captions. Save → `RoomRepository.updateDoor()` → reload surfaces.
>>>>>>> d7b7491 (docs: diagram-based door wall picker in spec and plan)

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: door configuration UI with diagram picker on both platforms"
```

### Task 26: Door feature verification

- [ ] **Step 1: Shared tests**

- `SurfaceDisplayNameTest`: door-relative names for all 4 door positions
- `DoorGeometryTest`: surface-local rect for each wall rotation; centering when offset null
- `LayoutEngineTest` additions: grid/brick/herringbone drop tiles inside the exclusion; door-adjacent tiles carry cut edges

- [ ] **Step 2: Maestro + checklist**

New `phase-10-door.yaml`: create room with dims → tap the Front edge of the diagram → save → generate surfaces → verify surfaces list shows "Door Wall" and "Front Wall" renamed → Layout tab shows the door gap + dashed outline → Preview shows the door opening. Update `docs/phase-9-verification-checklist.md`.

- [ ] **Step 3: Commit**

```bash
git commit -m "test: door feature tests + maestro flow"
```

---

## Phase 11: Edge Detection (Tile Texture Scanning)

**Goal:** Scan tile textures using the device camera — detect the tile's rectangular edges in real-time, apply perspective correction, and extract a clean top-down texture image. Works like document scanning: find the rectangle → review/adjust corners → correct perspective → crop → save.

**Scope:** iOS (Vision framework + Core Image) + Android (CameraX 1.3+ ImageAnalysis + OpenCV Android SDK 4.8).

**Key design decisions:**
- Output texture aspect ratio matches the tile's `tileWidth/tileHeight` ratio (not hardcoded square)
- Both platforms include a "Review Corners" screen where users can drag corner handles before perspective correction (auto-detection fails on busy backgrounds)
- Stability threshold: 10 consecutive frames (~330ms at 30fps) where detected corners move < 15pts

---

### Task 27: Edge Detection — iOS

- [ ] **Step 1: Replace CameraView with EdgeDetectionCameraView**

Replace the photo-only `CameraView.swift` with a real-time rectangle-detection pipeline:

```swift
// EdgeDetectionCameraView.swift
import SwiftUI
import AVFoundation
import Vision

struct EdgeDetectionCameraView: View {
    let tileGroup: TileGroup  // needed for aspect ratio
    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = EdgeDetectionModel()
    @State private var showCornerReview = false

    var body: some View {
        ZStack {
            EdgeDetectionPreview(session: model.session)
                .ignoresSafeArea()

            // Semi-transparent overlay with rectangle cutout
            if let rect = model.detectedRect {
                EdgeOverlayShape(detectedRect: rect)
                    .fill(Color.black.opacity(0.4))
                    .allowsHitTesting(false)
            }

            VStack {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title).foregroundStyle(.white).shadow(radius: 4)
                    }.padding()
                    Spacer()
                    Button { model.toggleFlash() } label: {
                        Image(systemName: model.flashOn ? "bolt.fill" : "bolt.slash.fill")
                            .font(.title3).foregroundStyle(.white)
                    }.padding()
                }
                Spacer()
                if model.isStable {
                    Text("Hold steady…").font(.caption).foregroundStyle(.green)
                        .padding(8).background(.black.opacity(0.5)).clipShape(Capsule())
                }
                Button { showCornerReview = true } label: {
                    ZStack {
                        Circle().stroke(.white, lineWidth: 4).frame(width: 72, height: 72)
                        Circle().fill(model.isStable ? Color.green : Color.white)
                            .frame(width: 60, height: 60)
                    }
                }
                .disabled(model.latestObservation == nil)
                .padding(.bottom, 40)
            }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .fullScreenCover(isPresented: $showCornerReview) {
            CornerReviewView(
                model: model,
                tileWidth: tileGroup.tileWidth,
                tileHeight: tileGroup.tileHeight,
                onAccept: { image in
                    onCapture(image)
                    dismiss()
                }
            )
        }
    }
}
```

- [ ] **Step 2: Implement EdgeDetectionModel with Vision**

```swift
// EdgeDetectionModel.swift
import AVFoundation
import Vision
import CoreImage

/// Handles camera session and rectangle detection on the proc queue.
/// Published properties are updated on MainActor.
/// latestPixelBuffer and latestObservation are accessed ONLY on procQueue
/// and copied to main-safe snapshots for the UI via captureSnapshot().
final class EdgeDetectionModel: NSObject, ObservableObject {
    let session = AVCaptureSession()
    @Published var detectedRect: CGRect?
    @Published var isStable = false
    @Published var flashOn = false
    @Published var viewSize: CGSize = .zero

    // Main-thread-safe snapshot for capture (set atomically from proc queue)
    @Published var latestObservation: VNRectangleObservation?
    private(set) var snapshotBuffer: CVPixelBuffer?

    private let videoOutput = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "edge.session")
    private let procQueue = DispatchQueue(label: "edge.proc")

    private lazy var rectRequest: VNDetectRectanglesRequest = {
        let req = VNDetectRectanglesRequest { [weak self] r, e in
            self?.handleRectangles(r, e)
        }
        req.minimumAspectRatio = 0.3
        req.maximumAspectRatio = 1.0
        req.minimumSize = 0.15
        req.maximumObservations = 1
        req.minimumConfidence = 0.7
        return req
    }()

    private var lastRect: CGRect?
    private var stableCount = 0
    // ~330ms at 30fps — enough to confirm user is holding still
    private let stableFrameThreshold = 10

    // Proc-queue-only working state
    private var _procPixelBuffer: CVPixelBuffer?
    private var _procObservation: VNRectangleObservation?

    func start() { /* AVCaptureSession setup — same as current CameraModel */ }
    func stop() { sessionQueue.async { self.session.stopRunning() } }
    func toggleFlash() { /* torch on/off */ }

    /// Snapshot the current detection state for the corner review screen.
    func captureSnapshot() -> (CIImage, VNRectangleObservation)? {
        // Called from main; reads the atomically-published snapshot
        guard let obs = latestObservation, let buf = snapshotBuffer else { return nil }
        return (CIImage(cvPixelBuffer: buf), obs)
    }

    private func handleRectangles(_ request: VNRequest, _ error: Error?) {
        guard let results = request.results as? [VNRectangleObservation],
              let best = results.first else {
            DispatchQueue.main.async { self.isStable = false; self.stableCount = 0; self.detectedRect = nil }
            return
        }
        // Publish snapshot atomically
        _procObservation = best
        let vr = normalizedRect(best)

        DispatchQueue.main.async {
            self.latestObservation = best
            self.snapshotBuffer = self._procPixelBuffer
            self.detectedRect = vr
            if let last = self.lastRect, self.rectsClose(last, vr) {
                self.stableCount += 1
                self.isStable = self.stableCount >= self.stableFrameThreshold
            } else {
                self.stableCount = 0
                self.isStable = false
            }
            self.lastRect = vr
        }
    }

    private func normalizedRect(_ obs: VNRectangleObservation) -> CGRect {
        // Vision [0,1] bottom-left origin → UIKit top-left origin
        let invY = 1.0 - obs.boundingBox.origin.y - obs.boundingBox.height
        return CGRect(
            x: obs.boundingBox.origin.x * viewSize.width,
            y: invY * viewSize.height,
            width: obs.boundingBox.width * viewSize.width,
            height: obs.boundingBox.height * viewSize.height
        )
    }

    private func rectsClose(_ a: CGRect, _ b: CGRect) -> Bool {
        abs(a.midX - b.midX) < 15 && abs(a.midY - b.midY) < 15 &&
        abs(a.width - b.width) < 30 && abs(a.height - b.height) < 30
    }
}

extension EdgeDetectionModel: AVCaptureVideoDataOutputSampleBufferDelegate {
    nonisolated func captureOutput(_ output: AVCaptureOutput,
                      didOutput sampleBuffer: CMSampleBuffer,
                      from connection: AVCaptureConnection) {
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        _procPixelBuffer = pb
        try? VNImageRequestHandler(cvPixelBuffer: pb, orientation: .up, options: [:])
            .perform([rectRequest])
    }
}
```

- [ ] **Step 3: Implement CornerReviewView (manual corner adjustment)**

```swift
// CornerReviewView.swift
import SwiftUI
import Vision

/// Allows user to drag 4 corner handles to adjust detected rectangle
/// before perspective correction. Handles the case where auto-detection
/// is imperfect (tiles on busy floors, partial occlusion, etc.).
struct CornerReviewView: View {
    let model: EdgeDetectionModel
    let tileWidth: Double
    let tileHeight: Double
    let onAccept: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var topLeft: CGPoint = .zero
    @State private var topRight: CGPoint = .zero
    @State private var bottomLeft: CGPoint = .zero
    @State private var bottomRight: CGPoint = .zero
    @State private var previewImage: UIImage?

    var body: some View {
        VStack {
            Text("Adjust Corners").font(.headline).padding(.top)
            Text("Drag corners to align with tile edges")
                .font(.caption).foregroundStyle(.secondary)

            GeometryReader { geometry in
                ZStack {
                    if let img = previewImage {
                        Image(uiImage: img).resizable().scaledToFit()
                    }
                    // 4 draggable corner handles
                    CornerHandle(position: $topLeft)
                    CornerHandle(position: $topRight)
                    CornerHandle(position: $bottomLeft)
                    CornerHandle(position: $bottomRight)
                    // Lines connecting corners
                    CornerLinesShape(tl: topLeft, tr: topRight, bl: bottomLeft, br: bottomRight)
                        .stroke(Color.green, lineWidth: 2)
                }
            }

            HStack(spacing: 20) {
                Button("Re-scan") { dismiss() }
                    .buttonStyle(.bordered)
                Button("Accept") { applyCorrection() }
                    .buttonStyle(.borderedProminent)
            }
            .padding()
        }
        .onAppear { initializeCorners() }
    }

    private func initializeCorners() {
        guard let (ciImage, obs) = model.captureSnapshot() else { dismiss(); return }
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(ciImage, from: ciImage.extent) else { return }
        previewImage = UIImage(cgImage: cg)
        // Map observation normalized corners to view coordinates
        let w = ciImage.extent.width
        let h = ciImage.extent.height
        topLeft = CGPoint(x: obs.topLeft.x * w, y: (1 - obs.topLeft.y) * h)
        topRight = CGPoint(x: obs.topRight.x * w, y: (1 - obs.topRight.y) * h)
        bottomLeft = CGPoint(x: obs.bottomLeft.x * w, y: (1 - obs.bottomLeft.y) * h)
        bottomRight = CGPoint(x: obs.bottomRight.x * w, y: (1 - obs.bottomRight.y) * h)
    }

    private func applyCorrection() {
        guard let (ciImage, _) = model.captureSnapshot() else { return }
        let outputSize = PerspectiveCorrector.outputSize(
            tileWidth: tileWidth, tileHeight: tileHeight, maxDimension: 512
        )
        guard let result = PerspectiveCorrector.correctWithCorners(
            image: ciImage,
            topLeft: topLeft, topRight: topRight,
            bottomLeft: bottomLeft, bottomRight: bottomRight,
            outputSize: outputSize
        ) else { return }
        onAccept(result)
    }
}
```

- [ ] **Step 4: Implement PerspectiveCorrector**

```swift
// PerspectiveCorrector.swift
import UIKit
import CoreImage
import Vision

struct PerspectiveCorrector {
    /// Compute output size preserving the tile's aspect ratio.
    /// Fits within maxDimension on the longest side.
    static func outputSize(tileWidth: Double, tileHeight: Double, maxDimension: CGFloat = 512) -> CGSize {
        let aspect = tileWidth / tileHeight
        if aspect >= 1.0 {
            return CGSize(width: maxDimension, height: maxDimension / aspect)
        } else {
            return CGSize(width: maxDimension * aspect, height: maxDimension)
        }
    }

    /// Correct using a VNRectangleObservation (auto-detected corners).
    /// IMPORTANT: Vision corners are in normalized [0,1] coordinates.
    /// CIPerspectiveCorrection expects pixel coordinates in the image extent.
    static func correct(
        image: CIImage,
        observation: VNRectangleObservation,
        outputSize: CGSize
    ) -> UIImage? {
        let w = image.extent.width
        let h = image.extent.height
        return correctWithCorners(
            image: image,
            topLeft: CGPoint(x: observation.topLeft.x * w, y: observation.topLeft.y * h),
            topRight: CGPoint(x: observation.topRight.x * w, y: observation.topRight.y * h),
            bottomLeft: CGPoint(x: observation.bottomLeft.x * w, y: observation.bottomLeft.y * h),
            bottomRight: CGPoint(x: observation.bottomRight.x * w, y: observation.bottomRight.y * h),
            outputSize: outputSize
        )
    }

    /// Correct using explicit pixel-coordinate corners (from manual adjustment).
    static func correctWithCorners(
        image: CIImage,
        topLeft: CGPoint, topRight: CGPoint,
        bottomLeft: CGPoint, bottomRight: CGPoint,
        outputSize: CGSize
    ) -> UIImage? {
        guard let filter = CIFilter(name: "CIPerspectiveCorrection") else { return nil }
        filter.setValue(image, forKey: kCIInputImageKey)
        // CIPerspectiveCorrection uses bottom-left origin (same as CIImage)
        filter.setValue(CIVector(cgPoint: topLeft), forKey: "inputTopLeft")
        filter.setValue(CIVector(cgPoint: topRight), forKey: "inputTopRight")
        filter.setValue(CIVector(cgPoint: bottomLeft), forKey: "inputBottomLeft")
        filter.setValue(CIVector(cgPoint: bottomRight), forKey: "inputBottomRight")
        guard let corrected = filter.outputImage else { return nil }
        let scaleX = outputSize.width / corrected.extent.width
        let scaleY = outputSize.height / corrected.extent.height
        let scaled = corrected.transformed(by: CGAffineTransform(scaleX: scaleX, y: scaleY))
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
```

- [ ] **Step 5: Save texture and update TileGroup**

```swift
// In the calling view after onCapture:
func saveTexture(image: UIImage, tileGroupId: String, tileGroupRepo: TileGroupRepository) async {
    let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("textures")
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    let fileURL = dir.appendingPathComponent("\(tileGroupId).png")
    try? image.pngData()?.write(to: fileURL)

    // Update tile group in DB
    if let tg = try? await tileGroupRepo.getById(id: tileGroupId) {
        let updated = TileGroup(
            id: tg.id, projectId: tg.projectId, name: tg.name,
            tileWidth: tg.tileWidth, tileHeight: tg.tileHeight,
            texturePath: fileURL.path, source: TileSource.captured
        )
        try? await tileGroupRepo.insert(tileGroup: updated)
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add iosApp/iosApp/Views/EdgeDetectionCameraView.swift \
        iosApp/iosApp/Views/EdgeDetectionModel.swift \
        iosApp/iosApp/Views/CornerReviewView.swift \
        iosApp/iosApp/Views/PerspectiveCorrector.swift
git commit -m "feat(ios): edge detection tile scanner with Vision + corner review + perspective correction"
```

---

### Task 28: Edge Detection — Android

**Requires:** CameraX 1.3+ (for `ImageProxy.toBitmap()`), OpenCV Android SDK 4.8.0.

- [ ] **Step 1: Add dependencies**

```kotlin
// androidApp/build.gradle.kts — add:
dependencies {
    // CameraX 1.3+ required for ImageProxy.toBitmap()
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    // OpenCV Android SDK — ~30MB, includes native libs for arm64/x86_64
    implementation("org.opencv:opencv:4.8.0")
}
```

- [ ] **Step 2: Implement EdgeDetectionScreen with CameraX**

```kotlin
// EdgeDetectionScreen.kt
@Composable
fun EdgeDetectionScreen(
    tileGroup: TileGroup,  // needed for aspect ratio
    onCapture: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedCorners by remember { mutableStateOf<QuadCorners?>(null) }
    var isStable by remember { mutableStateOf(false) }
    var showCornerReview by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Stability tracking
    var stableCount by remember { mutableIntStateOf(0) }
    var lastCorners by remember { mutableStateOf<QuadCorners?>(null) }
    val stableFrameThreshold = 10  // ~330ms at 30fps

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        val corners = EdgeDetector.detectQuadCorners(bitmap)
                        imageProxy.close()
                        if (corners != null) {
                            if (lastCorners != null && cornersClose(lastCorners!!, corners)) {
                                stableCount++
                                isStable = stableCount >= stableFrameThreshold
                            } else {
                                stableCount = 0
                                isStable = false
                            }
                            lastCorners = corners
                            detectedCorners = corners
                            capturedBitmap = bitmap
                        } else {
                            stableCount = 0
                            isStable = false
                            detectedCorners = null
                        }
                    }
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay + controls
        EdgeOverlay(corners = detectedCorners)
        CaptureControls(isStable = isStable, onCapture = {
            showCornerReview = true
        }, onDismiss = onDismiss)
    }

    if (showCornerReview) {
        CornerReviewScreen(
            bitmap = capturedBitmap,
            initialCorners = detectedCorners,
            tileWidth = tileGroup.tileWidth,
            tileHeight = tileGroup.tileHeight,
            onAccept = { corrected -> onCapture(corrected); onDismiss() },
            onRetry = { showCornerReview = false }
        )
    }
}

private fun cornersClose(a: QuadCorners, b: QuadCorners): Boolean {
    fun dist(p1: PointF, p2: PointF) = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble())
    return dist(a.tl, b.tl) < 15 && dist(a.tr, b.tr) < 15 &&
           dist(a.bl, b.bl) < 15 && dist(a.br, b.br) < 15
}
```

- [ ] **Step 3: Implement OpenCV edge detection returning QuadCorners**

```kotlin
// EdgeDetector.kt
import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object EdgeDetector {
    /**
     * Detect the largest quadrilateral in the image.
     * Returns 4 corner points (not an axis-aligned rect) for perspective correction.
     */
    fun detectQuadCorners(bitmap: Bitmap): QuadCorners? {
        val src = Mat(); Utils.bitmapToMat(bitmap, src)
        val gray = Mat(); Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat(); Imgproc.Canny(gray, edges, 75.0, 200.0)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestApprox: MatOfPoint2f? = null
        var maxArea = 0.0
        val minArea = src.rows() * src.cols() * 0.15

        for (c in contours) {
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(approx)
                if (area > maxArea && area > minArea) {
                    maxArea = area
                    bestApprox = approx
                }
            }
        }

        val points = bestApprox?.toArray() ?: return null
        if (points.size != 4) return null

        // Order points: top-left, top-right, bottom-right, bottom-left
        val sorted = orderCorners(points)
        return QuadCorners(
            tl = PointF(sorted[0].x.toFloat(), sorted[0].y.toFloat()),
            tr = PointF(sorted[1].x.toFloat(), sorted[1].y.toFloat()),
            br = PointF(sorted[2].x.toFloat(), sorted[2].y.toFloat()),
            bl = PointF(sorted[3].x.toFloat(), sorted[3].y.toFloat()),
        )
    }

    /** Order 4 points as: top-left, top-right, bottom-right, bottom-left. */
    private fun orderCorners(pts: Array<Point>): List<Point> {
        val sorted = pts.sortedBy { it.x + it.y }
        val tl = sorted.first()
        val br = sorted.last()
        val remaining = pts.filter { it != tl && it != br }
        val tr = remaining.minByOrNull { it.y - it.x }!!
        val bl = remaining.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }
}

data class QuadCorners(val tl: PointF, val tr: PointF, val br: PointF, val bl: PointF)
```

- [ ] **Step 4: CornerReviewScreen (manual adjustment)**

```kotlin
// CornerReviewScreen.kt
@Composable
fun CornerReviewScreen(
    bitmap: Bitmap?,
    initialCorners: QuadCorners?,
    tileWidth: Double,
    tileHeight: Double,
    onAccept: (Bitmap) -> Unit,
    onRetry: () -> Unit
) {
    var corners by remember { mutableStateOf(initialCorners ?: return) }
    val bmp = bitmap ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Adjust Corners", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp))
        Text("Drag corners to align with tile edges",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Draw bitmap + draggable corner handles
            Canvas(modifier = Modifier.fillMaxSize()) { /* draw bitmap + lines */ }
            // 4 draggable handles (DragGesture on each corner)
        }

        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Re-scan") }
            Button(onClick = {
                val outputSize = PerspectiveCorrector.outputSize(tileWidth, tileHeight, maxDimension = 512)
                val corrected = PerspectiveCorrector.correct(bmp, corners, outputSize)
                onAccept(corrected)
            }) { Text("Accept") }
        }
    }
}
```

- [ ] **Step 5: Perspective correction with aspect-ratio-aware output**

```kotlin
// PerspectiveCorrector.kt
import android.graphics.*
import kotlin.math.roundToInt

object PerspectiveCorrector {
    /** Compute output size preserving tile aspect ratio, fitting within maxDimension. */
    fun outputSize(tileWidth: Double, tileHeight: Double, maxDimension: Int = 512): Size {
        val aspect = tileWidth / tileHeight
        return if (aspect >= 1.0) {
            Size(maxDimension, (maxDimension / aspect).roundToInt())
        } else {
            Size((maxDimension * aspect).roundToInt(), maxDimension)
        }
    }

    fun correct(source: Bitmap, corners: QuadCorners, outputSize: Size): Bitmap {
        val w = outputSize.width.toFloat()
        val h = outputSize.height.toFloat()
        val src = floatArrayOf(
            corners.tl.x, corners.tl.y, corners.tr.x, corners.tr.y,
            corners.br.x, corners.br.y, corners.bl.x, corners.bl.y)
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        val output = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(output).drawBitmap(source, matrix, paint)
        return output
    }
}
```

Save to internal storage and update `TileGroup.texturePath` + `source = TileSource.CAPTURED`.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/main/java/com/hasu/tilelayout/ui/screens/EdgeDetectionScreen.kt \
        androidApp/src/main/java/com/hasu/tilelayout/ui/screens/CornerReviewScreen.kt \
        androidApp/src/main/java/com/hasu/tilelayout/ui/screens/EdgeDetector.kt \
        androidApp/src/main/java/com/hasu/tilelayout/ui/screens/PerspectiveCorrector.kt
git commit -m "feat(android): edge detection tile scanner with OpenCV + corner review + CameraX 1.3"
```

---

### Task 29: Shared Texture Storage Path

- [ ] **Step 1: Add `expect` texture base directory to shared module**

```kotlin
// sharedLogic/src/commonMain/.../texture/TextureStorage.kt
package com.hasu.tilelayout.texture

expect fun textureBaseDirectory(): String

object TextureStorage {
    fun pathFor(tileGroupId: String): String =
        "${textureBaseDirectory()}/$tileGroupId.png"
}
```

- [ ] **Step 2: Platform actuals**

```kotlin
// sharedLogic/src/iosMain/.../texture/TextureStorage.ios.kt
actual fun textureBaseDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    )
    return "${paths.first()}/textures"
}
```

```kotlin
// sharedLogic/src/androidMain/.../texture/TextureStorage.android.kt
// Requires ApplicationContext — pass via init or DI
actual fun textureBaseDirectory(): String {
    return "${ApplicationContextHolder.context.filesDir.absolutePath}/textures"
}
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: shared TextureStorage helper for captured texture file paths"
```

---

**Plan updated.** 29 tasks across 11 phases. Phases 1-6 shared Kotlin. Phases 7-8 thin platform UIs. Phase 9 integration. Phase 10 door wall feature (door config, door-relative names, preview rendering, layout exclusion). Phase 11 edge detection with manual corner review fallback on both platforms.
