# Tile Layout App — Design Spec

**Date:** 2026-07-19
**Status:** Draft
**Platform:** Kotlin Multiplatform — shared logic + native UI (SwiftUI on iOS, Jetpack Compose on Android)
**Scope:** MVP — local-only, single-user

## Overview

A mobile app for professional tilers to design tile layouts on walls and floors, and generate rendered preview images to show clients. The app captures tile textures via camera, computes optimal tile placement with symmetry and balance, renders precise 2D elevation views with dimension markings, produces isometric 3D room previews, and generates a cut list for on-site work.

---

## Core Features (MVP)

1. **Project & room modeling** — tilers create projects (e.g., "Smith Residence") containing multiple rooms. Each room has overall width × depth × height. Surfaces (walls, floors) are auto-positioned from room dimensions. A help diagram shows front/back/left/right from the doorway perspective.
2. **Tile groups** — defined at the project level, reusable across rooms. Each group has a name, tile size (width × height), and a captured or imported texture.
3. **Texture capture** — camera-based tile scanning (edge detect + perspective correct) to capture color + texture. Corner guides like a document scanner.
4. **Texture import** — alternative: pick an existing image from the device gallery, manually crop to a square.
5. **Grout color** — per surface: black, grey, or white. Configurable grout width (default 3mm).
6. **Auto-layout** — algorithm computes initial layout with symmetry, visual balance, and sliver-cut elimination (no cut piece < 30% of a full tile).
7. **Dimensioned render** — precise 2D elevation with surface dimensions, tile sizes, and cut-tile labels (horizontal/vertical arrows with mm dimensions).
8. **Fine-tuning** — tiler selects a surface, drags to shift tile layout. Lock parallel surfaces to move together (axis-aware: horizontal only propagates to parallel walls, vertical propagates to all walls since they share "up" in 3D). Walls and floors are adjusted independently.
9. **Cut list** — generated from approved layouts. Groups identical cuts by tile group + dimensions + cut shape, showing per-surface quantities and locations.

### Stretch Goals

- Align grout lines where walls meet floors (requires computing shared-edge intersection in 3D)
- Swipe-to-orbit in 3D view (instead of discrete 90° rotation buttons)
- Notch cuts for obstacles (pipes, outlets)

---

## Architecture

### Approach: KMP shared logic + native platform UI

All business logic lives in a shared Kotlin module: models, storage (SQLDelight), layout engine, cut list generator. No shared UI — each platform gets native UI: SwiftUI (iOS) with Canvas for rendering, Jetpack Compose (Android) with Canvas for rendering. The shared module exposes Kotlin StateFlows that platform UIs observe.

### Module Diagram

```
┌─────────────────────────────────────────────┐
│              SHARED KOTLIN MODULE            │
│                                             │
│  Room Modeler ──▶ Layout Engine ──▶ Render   │
│       │                  ▲           Engine  │
│       │                  │            │      │
│       │   Tile Library ──┘            │      │
│       │        ▲                      │      │
│       │        │                      ▼      │
│       │   Texture Capture         Cut List   │
│       │                           Generator  │
│       │                                      │
│       └───────────────┐                      │
│                       ▼                      │
│               SQLDelight (SQLite)            │
└─────────────────────────────────────────────┘
         │                      │
         ▼                      ▼
    SwiftUI (iOS)     Jetpack Compose (Android)
    - Canvas 2D/3D    - Canvas 2D/3D
    - Camera capture   - Camera capture
    - Image picker     - Image picker
```

### 6 Core Modules (all in shared Kotlin)

| Module | Responsibility |
|--------|---------------|
| **Room Modeler** | Define projects, rooms, surfaces. Auto-position walls/floors from room dimensions. Assign tile groups to surface regions. |
| **Tile Library** | Project-level. Define tile groups (name, tileWidth, tileHeight, texture). Texture file management. Reusable across rooms. |
| **Texture Capture** | Platform-specific camera/image-picker. Shared: edge detection + perspective correction (pure Kotlin). |
| **Layout Engine** | Pure Kotlin: compute optimal tile placement per surface region. Symmetry, sliver-cut elimination, pattern variants. |
| **Render Engine** | Pure Kotlin: output tile positions + metadata. Platform UIs translate to Canvas draw calls. Isometric transform is shared math. |
| **Cut List Generator** | Walk all LayoutResults, group identical cuts, list per-surface locations and quantities. |

### Key Architectural Rules

- Shared module is pure Kotlin — no Android or iOS dependencies. Platform UIs consume it via StateFlow observables.
- Layout Engine outputs data (`List<PlacedTile>`), platform Canvas code paints pixels. No coupling.
- 2D and 3D rendering share the same projection math in shared code. Platform UIs each implement Canvas drawing using those coordinates.
- Isometric projection math is in shared Kotlin; the Canvas transform is platform-specific (SwiftUI Canvas, Compose Canvas).
- Cut List Generator is read-only — it walks LayoutResults, no modification.
- Rendering pipeline: shared engine computes tile rects + metadata → platform observes StateFlow → platform Canvas draws.

---

## Data Model

### Entity Relationship

```
Project ──▶ Room ──▶ Surface ──▶ SurfaceTileGroup ◀── TileGroup
  │                                    │                 │
  │                                    ▼                 │
  │                               LayoutResult            │
  │                                    │                  │
  │                                    ▼                  │
  └────────────────────────── CutList (generated) ◀───────┘
```

### Project

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| name | String | e.g. "Smith Residence" |
| units | enum | `mm` or `in` (consistent across all rooms in the project) |
| createdAt | timestamp | |
| rooms | List\<Room\> | |
| tileGroups | List\<TileGroup\> | project-level tile library, reusable across rooms |

### Room

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| projectId | UUID | |
| name | String | e.g. "Bathroom 3F" |
| width | double | overall room width, for auto-positioning walls |
| depth | double | overall room depth, for auto-positioning walls |
| height | double | overall room height, for auto-positioning walls |
| surfaces | List\<Surface\> | |

### Surface

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| type | enum | `wall` or `floor` |
| width | double | in room units |
| height | double | in room units (for floors: depth) |
| position | {x,y,z} + rotation | auto-computed from room dims + surface type. Room origin = floor corner where front + left walls meet. |
| groutColor | enum | `black`, `grey`, `white` |
| groutWidth | double | default 3mm |
| tileAssignments | List\<SurfaceTileGroup\> | |

### SurfaceTileGroup (join)

Links a tile group to a rectangular region of a surface. One surface can have multiple tile groups (e.g., a border strip + center band + field tile). Regions must not overlap and must cover every pixel of the surface.

**Region validation rules:**

1. **Overlap check** — when adding/editing a region, compute intersection with all other regions on the same surface. If any intersection area > 0, reject with error: "Regions overlap — adjust boundaries."
2. **Coverage check** — sum of all region areas must equal surface area (W × H). If uncovered gaps remain after adding/editing, show warning: "Uncovered area on surface" with a highlight overlay on the gap. The tiler must assign a tile group to all areas before layout can compute.
3. **Validation timing** — validate on region save, not live during editing. Show errors inline on the Surface Detail screen next to the region list.
4. **Presets guarantee coverage** — when using "Full surface" preset with a single tile group, validation always passes. Multi-group configurations require manual sizing or preset combinations that tile the surface.

| Field | Type | Notes |
|-------|------|-------|
| surfaceId | UUID | |
| tileGroupId | UUID | |
| region | {x, y, w, h} | which part of the surface this occupies, in room units (mm/in) |
| pattern | enum | `grid`, `brick`, `stacked`, `herringbone` |
| offset | {x, y} | manual fine-tune drag offset |
| locked | bool | transient: sync movement with selected surface |

### Region Presets

Regions are defined as a rectangle `{x, y, w, h}` on the surface. The app provides presets as shortcuts (W = surface width, H = surface height, s = tiler-specified strip size):

| Preset | Rectangle |
|--------|-----------|
| Full surface | `{0, 0, W, H}` |
| Top strip | `{0, 0, W, s}` |
| Bottom strip | `{0, H-s, W, s}` |
| Left strip | `{0, 0, s, H}` |
| Right strip | `{W-s, 0, s, H}` |
| Horizontal center band | `{0, (H-s)/2, W, s}` |
| Vertical center band | `{(W-s)/2, 0, s, H}` |
| Custom | `{x, y, w, h}` — tiler enters all values manually |

Common combinations: 4 edge strips + center field (5 tile groups), or center band + top/bottom field (3 tile groups).

### TileGroup

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| projectId | UUID | tile groups belong to a project, reusable across rooms |
| name | String | e.g. "White subway tile" |
| tileWidth | double | single tile width |
| tileHeight | double | single tile height |
| texturePath | String | file path to captured/imported image |
| source | enum | `captured` or `imported` |

### LayoutResult (computed, cached)

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| surfaceId | UUID | |
| tiles | List\<PlacedTile\> | each: {x, y, w, h, rotation, isCut, cutEdges[], tileGroupId} |
| computedAt | timestamp | |
| stale | bool | true when params changed since last compute |

`rotation` is degrees (0, 45, 90, etc.). 0 for grid/brick/stacked patterns. 45 or -45 for herringbone.

`cutEdges` is an array of edges that are cut: `["left"]`, `["right"]`, `["bottom"]`, `["right", "bottom"]` (corner cut), etc. Edges are relative to the tile's local axes (pre-rotation).

Stale is set true when any input changes (surface dims, tile size, offset, region). Recomputation is debounced (~100ms after last change during fine-tuning).

---

## Layout Engine

### Core Algorithm

For a given region (regionW × regionH) and tile (tileW × tileH) with grout width g:

```
g = surface.groutWidth
unitW = tileW + g
unitH = tileH + g
fullCols = floor(regionW / unitW)
fullRows = floor(regionH / unitH)
remainderW = regionW - (fullCols * unitW)
remainderH = regionH - (fullRows * unitH)
```

### Sliver-Cut Elimination

Threshold: 30% of tile dimension. If a cut piece would be less than 30% of a full tile:

```
minCutRatio = 0.30
if remainderW > 0 AND remainderW < tileW * minCutRatio:
    fullCols -= 1
    remainderW = regionW - (fullCols * unitW)
// same for height
```

This redistributes the remainder into larger cuts on both sides.

### Symmetry

After sliver check, center the layout:

```
offsetX = remainderW / 2
offsetY = remainderH / 2
```

Result: equal cut sizes on left/right edges, and on top/bottom edges.

### Tile Placement

```
for row in 0..fullRows:
    for col in 0..fullCols:
        x = offsetX + col * unitW
        y = offsetY + row * unitH
        w = min(tileW, regionW - x)
        h = min(tileH, regionH - y)
        isCut = (w < tileW) or (h < tileH)
        cutEdges = determineCutEdges(x, y, w, h, regionW, regionH)
```

### Pattern Variants

| Pattern | Behavior |
|---------|----------|
| **Grid** | Standard columns × rows. Symmetry check on both axes. |
| **Brick** | 50% horizontal offset on every other row. Symmetry check on row pairs. |
| **Stacked** | No horizontal offset between rows. Vertical grout lines run straight. |
| **Herringbone** | Tiles at ±45°. See Herringbone Algorithm below. |

### Herringbone Algorithm

Tiles alternate between +45° and -45° rotation, forming a V or zigzag pattern. Each tile's bounding box (the axis-aligned rect that contains the rotated tile) determines placement spacing.

```
diagW = tileW * cos45° + tileH * cos45°   // bounding box width of rotated tile
diagH = tileW * sin45° + tileH * sin45°   // bounding box height (same for square-ish tiles)
stepX = tileH * cos45°                     // horizontal step between adjacent tiles
stepY = tileH * sin45°                     // vertical step

for row in grid:
    for col in grid:
        if (row + col) % 2 == 0:
            rotation = +45°
        else:
            rotation = -45°
        x = col * stepX
        y = row * stepY
        // clip to region bounds, mark isCut if clipped
```

**Symmetry:** Center the bounding grid within the region. Edge tiles are clipped to the region boundary — these become trapezoidal or triangular cuts.

**Sliver-cut elimination:** Apply the 30% threshold to the clipped dimension measured along the tile's local axis (not the region axis). If a clipped piece is < 30% of the tile's length along that axis, shift the grid origin inward by half-step.

**cutEdges for herringbone:** Edges are in the tile's local coordinate space (pre-rotation). A tile clipped by the region's left boundary gets `cutEdges: ["left"]` relative to its own orientation — the Render Engine applies the rotation transform when drawing the cut annotation.

### Multi-Group Surfaces

Each `SurfaceTileGroup` defines a rectangular region. The Layout Engine runs independently per region. Regions do not overlap. The Render Engine stitches all regions together on the final canvas.

---

## Rendering

### 2D Elevation View

Paint order (back to front), same on both platforms:

1. Surface background fill
2. Per PlacedTile: clipped texture image at tile rect
3. Cut tiles: red dashed line on cut edge + dimension annotation (arrow + mm label)
4. Grout lines between all tiles
5. Surface dimension arrows + mm labels on outer edges
6. Legend: grout width

Shared code computes all tile rects, cut annotations, and dimension labels as data. Platform Canvas code draws them. No layout logic in the UI layer.

**Cut annotations:** Horizontal arrow ↔ across the cut tile labeled with its width (e.g., "91mm") for width cuts. Vertical arrow ↕ for height cuts. Both arrows for corner cuts.

### 3D Isometric View

No 3D engine. Isometric projection computed in shared Kotlin math:

```
ix = (sx - sy) * cos30° + ox
iy = (sx + sy) * sin30° - sz + oy
```

Platforms apply the projection via Canvas transforms: `CGAffineTransform` on iOS, `graphicsLayer` or matrix transform on Android.

**Rotation:** ◀ and ▶ arrow buttons overlaid on the 3D view. Each tap rotates the room by 90°, showing a different wall pair:

| Angle | Visible walls |
|-------|---------------|
| 0° | Front + Left |
| 90° | Front + Right |
| 180° | Back + Right |
| 270° | Back + Left |

Plus a "Top-Down" toggle for the floor perspective.

**Tap to select:** Tap a surface in the 3D view to select it. Hit testing: shared code projects surface corners to screen coords; platform UI tests point-in-polygon in front-to-back order. An info card appears with an "Edit Layout" button.

**Draw order** (painter's algorithm, back-to-front): floor → back walls → side walls → front wall. Ordering recalculates when the view angle changes.

No dimension labels on 3D view. Clean preview for client approval.

### Export

- **2D:** Platform Canvas renders to platform image format (UIImage / Bitmap). Save as PNG at 150–300 DPI. One image per surface.
- **3D:** Single isometric room render as PNG. No labels. For client approval.
- **Cut List:** Share as text or screenshot. Grouped by tile group, with per-surface location breakdowns.

---

## Fine-Tuning UX

### Interaction Flow

1. **Select surface** — tap in 3D room view (primary) or via surface selector chips on Layout tab (secondary). 2D elevation fills the main canvas.
2. **Drag** — one-finger drag anywhere on canvas shifts the tile grid. Offset {x, y} overlay updates live in the top-left corner. Wrap-around: tiles exiting one edge reappear on the opposite edge. Cuts recalculate in real-time. Blue drag handles on edges for single-axis adjustment.
3. **Lock + drag** — toggle lock chips below the canvas. Drag selected surface → locked surfaces shift per the axis rules below. Lock state is transient, not saved to the project.

### Lock Propagation Rules

Walls only lock with walls. Floors only lock with floors. Wall↔floor locking has no effect for MVP (tilers align them manually).

**Walls:**

| Drag direction | Parallel locked walls | Perpendicular locked walls |
|---|---|---|
| Horizontal (dx) | ✅ same dx (shared axis) | ❌ no effect (different 3D axes) |
| Vertical (dy) | ✅ same dy | ✅ same dy (all walls share "up" in 3D) |

**Floors:**

| Drag direction | Parallel locked floors |
|---|---|
| Horizontal (dx) | ✅ same dx |
| Vertical (dy — depth) | ✅ same dy |

### Undo

Single-level undo for fine-tuning drag. Each drag gesture (finger down → finger up) snapshots the prior offset before applying the new one. Tapping "Undo" restores that snapshot. Only the most recent drag is undoable — a second drag overwrites the undo buffer. This is lightweight (one `{x, y}` per SurfaceTileGroup) and covers the most common mistake: overshooting a drag.

Undo state is transient — not persisted to SQLite. Navigating away from the Layout tab clears it.

### Reset Options

- **Reset to auto layout** — discard all manual offsets, revert to Layout Engine's computed positions
- **Snap to center** — set offset to {0, 0} (centered layout)

### What Drag Changes

Dragging modifies `SurfaceTileGroup.offset`. This triggers `LayoutResult.stale = true`, then a debounced recompute (~100ms after finger lifts). The Layout Engine reruns with the new offset; the Canvas repaints.

Fine-tuning does not change tile sizes or pattern. It only shifts the grid origin.

### Cut Summary

Below the 2D canvas, a summary lists every cut on the current surface — which edge, how many tiles, and the cut dimension. Updates live as the tiler drags.

---

## Cut List

### Generation

Generated from all `LayoutResult.tiles` across all surfaces in a room:

1. Walk all tiles where `isCut == true`
2. Group by compound key: `{tileGroupId, width, height, cutEdges}`
3. For each group, track per-surface locations and counts: `[{surfaceId, surfaceName, count}]`
4. Sort: by tile group, then by dimensions (largest first)

### Display

Each row shows:
- **Mini shape diagram** — small rectangle with red dashed lines on cut edges, annotated with dimensions
- **Dimensions** — e.g., "91 × 200mm"
- **Cut type** — e.g., "Right edge cut", "Corner cut (right + bottom)"
- **Tile group name** — e.g., "White subway"
- **Applied to** — chip badges showing each surface and per-surface count: "Front wall ×5", "Left wall ×5"
- **Total quantity badge** — red pill, e.g., "×15"

Group headers show the tile group name, full tile size, and total full tile count.

### Summary Bar

At the top: total full tiles, total cut tiles, and count of unique cut sizes.

### Cut Shape Types

| Type | Description |
|------|-------------|
| Edge cut | One side narrower or shorter than the full tile |
| Corner cut (L-shape) | Cut on two adjacent edges — tile is narrower AND shorter |
| Notch cut | Stretch: cutout for pipes, outlets, etc. |

---

## Texture Capture

### Camera Flow

1. Tiler taps "Capture" from the Tile Library → fullscreen camera opens
2. Corner guides overlaid (like document scanner) — position the tile within the guides on a contrasting surface
3. Edge detection finds the 4 corners of the tile and snaps guides to them
4. Tiler taps capture button → app perspective-corrects to a flat square image
5. Review screen: shows the corrected square result, with fields for tile name, width, and height
6. "Save to Tile Library" → texture saved, TileGroup created

### Import Flow

1. Tiler taps "Import" from the Tile Library → device gallery opens
2. Select image → manual crop to square
3. Enter tile name, width, height
4. Save to Tile Library

### Technical

Uses Flutter's `camera` plugin + edge detection (e.g., `edge_detection` package or custom Canny via OpenCV FFI). Perspective correction uses a 4-point homography transform. Captured images stored in the app's local document directory. SQLite stores the file path.

---

## Room Modeling UX

### Creating a Project and Room

1. Tiler creates a project: gives it a name, selects units (mm/in)
2. Inside the project, tiler creates a room: name + overall dimensions (width × depth × height)
3. Tiler selects which surfaces to include (front/back/left/right wall + floor, all default on)
4. A **help button (!)** next to the "SURFACES" label opens a perspective diagram overlay
5. Room dimensions auto-compute surface positions — no coordinate entry needed

### Help Diagram: "Where are you standing?"

Tapping the `!` icon opens a modal showing a first-person perspective of someone standing at the room's doorway, looking into the room. This avoids any technical coordinate language:

- **Front wall** — the wall directly ahead, facing you
- **Back wall** — the wall behind you (with the doorway)
- **Left wall** — the wall to your left
- **Right wall** — the wall to your right
- **Floor** — under your feet

A small top-down inset also shows the room layout from above, with a "You" marker and a direction arrow. The tiler simply checks which surfaces they need, and the app handles all 3D coordinate math internally.

This diagram appears as a one-time reference — after the first use, the convention is established. The surface toggle chips on the create screen serve as a quick reminder.

### Surface Positioning

Surfaces are auto-positioned from room dimensions:
- Front wall at z=0, facing inward, spanning full width × height
- Back wall at z=depth, facing inward
- Left wall at x=0, facing inward, spanning full depth × height
- Right wall at x=width, facing inward
- Floor at y=0, facing up, spanning full width × depth

Tiler can toggle off unused surfaces (e.g., only tiling 2 walls). For MVP, surface dimensions and positions are derived from room dimensions — not freely editable.

### Surface Detail Screen

Editing a surface shows:
- **Dimensions** — width, height (read-only for MVP since they derive from room)
- **Grout** — color picker (3 circles: white/grey/black) + width input
- **Tile groups on this surface** — each shows thumbnail, name, a tappable REGION chip (▸ opens region editor), and a tappable PATTERN chip (▸ opens pattern picker)
- **+ Add tile group from library** — opens tile group picker
- **Delete this surface** — with confirmation

### Region Editor

Tapping a REGION chip opens:
- **Preset picker** — Full surface / Top strip / Bottom strip / Left strip / Right strip / H-center band / V-center band / Custom
- **Size input** — for strips/bands: the strip width/height in mm. For custom: x, y, w, h.
- **Mini preview** — live wall preview showing how this region fits with other groups on the surface

---

## App Navigation (Screen Flow)

```
Home (Project List)
  └── Project detail
        ├── Segmented control: [Rooms] | [Tile Library]
        │
        ├── [Rooms tab] → tap room → Room Editor
        │     ├── Surfaces tab (list of walls/floors)
        │     │     └── Surface detail (dimensions, tile groups, grout, region/pattern editors)
        │     ├── Layout tab (2D elevation + fine-tuning + cut summary)
        │     ├── Preview tab (3D isometric + rotation + export)
        │     └── Cut List tab (grouped cuts with per-surface locations)
        │
        └── [Tile Library tab] (project-level)
              ├── List of tile groups with texture thumbnails, sizes, usage info
              ├── + Add (Capture or Import)
              └── Edit tile group (name, dimensions, recapture/replace texture)
```

---

## Technology Choices

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Shared logic | Kotlin Multiplatform (KMP) | Single codebase for all business logic, models, engine |
| iOS UI | SwiftUI + Canvas | Native iOS look and feel, Canvas API for tile rendering |
| Android UI | Jetpack Compose + Canvas | Native Android look and feel, Canvas API for tile rendering |
| State exposure | kotlinx-coroutines StateFlow | Shared module emits state; both platforms observe natively |
| Local DB | SQLDelight | KMP-native SQLite wrapper, generates type-safe Kotlin from SQL |
| Image storage | Platform document directories | File paths stored in DB; images on disk |
| Camera | Platform-specific (AVFoundation / CameraX) | Native camera APIs called from platform UI |
| Image processing | Shared Kotlin (custom edge-detect + homography) | Pure Kotlin math, no platform dependency |
| 3D (isometric) | Shared math + platform Canvas transform | Projection computed in shared code; platform draws |

## Out of Scope (MVP)

- Cloud sync / multi-device
- User accounts / auth
- PDF export (PNG only)
- Free-form 3D room positioning (auto-computed from room dims only)
- Wall↔floor grout alignment (stretch goal)
- Swipe-to-orbit in 3D view (discrete 90° buttons only)
- Complex tile patterns beyond grid/brick/stacked/herringbone
- Notch cuts for obstacles (stretch goal)
- Undo/redo history
- Price estimation / materials calculation
