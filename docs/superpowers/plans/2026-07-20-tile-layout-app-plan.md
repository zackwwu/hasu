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

        val startX = remW / 2 + offX
        val startY = remH / 2 + offY

        return buildList {
            for (row in 0..fullRows) for (col in 0..fullCols) {
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

        return buildList {
            for (row in 0..fullRows) {
                val y = startY + row * unitH
                val rowOff = if (row % 2 == 0) 0.0 else halfW
                val effStart = rowOff + offX
                val shiftedW = region.width - effStart
                var fullCols = (shiftedW / unitW).toInt()
                var remW = shiftedW - fullCols * unitW
                if (remW > 0 && remW < tg.tileWidth * MIN_CUT_RATIO) { fullCols--; remW = shiftedW - fullCols * unitW }
                val startX = effStart + remW / 2

                for (col in 0..fullCols) {
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

    suspend fun loadSurfaces(roomId: String) {
        _surfaces.value = surfaceRepo.getByRoom(roomId)
    }
    fun selectSurface(id: String?) { _selectedSurfaceId.value = id }
    fun rotateView(delta: Int) { _viewAngle.value = (_viewAngle.value + delta) % 360 }

    suspend fun computeLayout(surfaceId: String) {
        val surface = surfaceRepo.getById(surfaceId) ?: return
        val stgs = surfaceRepo.getSTGsBySurface(surfaceId)
        val tiles = stgs.flatMap { stg ->
            val tg = tileGroupRepo.getById(stg.tileGroupId) ?: return@flatMap emptyList()
            LayoutEngine.compute(stg.region, tg, surface.groutWidth, stg.pattern, stg.offsetX, stg.offsetY)
        }
        layoutRepo.save(LayoutResult(surfaceId = surfaceId, tiles = tiles, stale = false))
    }
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

## Phase 7: Platform UI

### Task 10-14: iOS (SwiftUI)

Each iOS screen is a SwiftUI View that observes shared StateFlows. Pattern for every screen:

```swift
// Example: HomeView.swift
import SwiftUI
import shared // KMP shared module

struct HomeView: View {
    @StateObject private var vm = ProjectListViewModel(repo: ProjectRepository(db: AppDatabase.shared))

    var body: some View {
        // Standard SwiftUI List/Form using vm.projects
    }
}
```

iOS tasks: HomeView, ProjectDetailView, RoomEditorView (tab scaffold), SurfaceDetailView, RegionEditorView, TileLibraryView, CameraView, HelpDiagramView, LayoutTabView (2D Canvas), PreviewTabView (3D Canvas), CutListTabView.

### Task 15-19: Android (Jetpack Compose)

Each screen is a `@Composable` function observing shared StateFlows via `collectAsState()`:

```kotlin
// Example: HomeScreen.kt
@Composable
fun HomeScreen(vm: ProjectListViewModel = remember { ProjectListViewModel(ProjectRepository(AppDatabase.instance)) }) {
    val projects by vm.projects.collectAsState()
    // Standard Material3 Scaffold with LazyColumn
}
```

Android tasks: same screens as iOS, using Compose Canvas for 2D/3D rendering.

---

## Phase 8: Integration & Export

### Task 20: Compute pipeline + export

- Wire shared ViewModels to platform Canvas renderers
- Export: platform Canvas → UIImage/Bitmap → save to photos or share sheet
- Final integration test on both platforms

---

**Plan complete.** ~20 tasks across 8 phases for KMP (shared Kotlin + SwiftUI + Jetpack Compose). The shared module is the heavy lift (Phases 1-6); platform UIs are thin wrappers.
