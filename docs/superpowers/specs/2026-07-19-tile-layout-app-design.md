# Tile Layout App — Design Spec

**Date:** 2026-07-19
**Status:** Draft
**Platform:** Flutter (cross-platform: iOS + Android)
**Scope:** MVP — local-only, single-user

## Overview

A mobile app for professional tilers to design tile layouts on walls and floors, and generate rendered preview images to show clients. The app captures tile textures via camera, computes optimal tile placement with symmetry and balance, renders precise 2D elevation views with dimension markings, and produces isometric 3D room previews.

---

## Core Features (MVP)

1. **Room modeling** — tilers add walls and floors with measurements (width, height, depth) and relative 3D positions
2. **Tile groups** — tilers define tile groups (name, size per tile) and assign them to wall/floor regions
3. **Texture capture** — camera-based tile scanning (edge detect + perspective correct) to capture color + texture
4. **Texture import** — alternative: pick an existing image for the tile pattern
5. **Grout color** — per surface: black, grey, or white
6. **Auto-layout** — algorithm computes initial layout with symmetry, visual balance, and sliver-cut elimination
7. **Dimensioned render** — precise 2D elevation with wall/floor dimensions, tile sizes, and cut-tile labels
8. **Fine-tuning** — tiler selects a surface, drags to shift tile layout, optionally locks parallel surfaces to move together

### Stretch Goal

- Align grout lines where walls meet floors (requires computing shared-edge intersection in 3D)

---

## Architecture

### Approach: Canvas-first custom rendering

All rendering via Flutter's `CustomPainter` on Canvas. No widget-per-tile (doesn't scale). No 3D engine (isometric is a 2D matrix transform).

### Module Diagram

```
Room Modeler ──▶ Layout Engine ──▶ Render Engine ──▶ Preview / Export
                     ▲                    │
Tile Library ────────┘                    │
     ▲                                    │
Texture Capture ────┘                     ▼
                                    Local Storage (SQLite)
```

### 5 Core Modules

| Module | Responsibility |
|--------|---------------|
| **Room Modeler** | Define walls/floors, dimensions, positions in 3D space, assign tile groups to surface regions |
| **Tile Library** | Define tile groups (name, tileWidth, tileHeight, texture), manage captured/imported textures |
| **Texture Capture** | Camera → edge detection → perspective correction → save texture to Tile Library |
| **Layout Engine** | Pure Dart: compute optimal tile placement per surface region. Symmetry, sliver-cut elimination, pattern variants. |
| **Render Engine** | CustomPainter: draw tiles with textures, grout lines, dimension annotations, cut markings. 2D elevation + 3D isometric. Export to high-res PNG. |

### Key Architectural Rules

- Layout Engine outputs data (`List<PlacedTile>`), Render Engine paints pixels. No coupling.
- 2D and 3D rendering share the same `drawSurface()` code. Isometric just wraps it in `canvas.transform(matrix)`.
- Textures skew correctly under the isometric transform automatically (Canvas handles it).

---

## Data Model

### Entity Relationship

```
Room ──▶ Surface ──▶ SurfaceTileGroup ◀── TileGroup
                         │
                         ▼
                    LayoutResult (computed, cached)
```

### Room

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| name | String | e.g. "Bathroom 3F" |
| units | enum | `mm` or `in` |
| surfaces | List\<Surface\> | |

### Surface

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| type | enum | `wall` or `floor` |
| width | double | in room units |
| height | double | in room units (for floors: depth) |
| position | {x,y,z} + rotation | relative to room origin (floor corner where front + left walls meet), for 3D assembly |
| groutColor | enum | `black`, `grey`, `white` |
| groutWidth | double | default 3mm |
| tileAssignments | List\<SurfaceTileGroup\> | |

### SurfaceTileGroup (join)

Links a tile group to a region of a surface. One surface can have multiple tile groups (e.g., border + field).

| Field | Type | Notes |
|-------|------|-------|
| surfaceId | UUID | |
| tileGroupId | UUID | |
| region | {x, y, w, h} | which part of the surface this occupies, in room units (mm/in) |
| pattern | enum | `grid`, `brick`, `stacked`, `herringbone` |
| offset | {x, y} | manual fine-tune drag offset |
| locked | bool | transient: sync movement with selected surface |

Non-overlapping regions per surface. Each region is laid out independently by the Layout Engine.

### TileGroup

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
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
| tiles | List\<PlacedTile\> | each: {x, y, w, h, isCut, cutEdge, tileGroupId} |
| computedAt | timestamp | |
| stale | bool | true when params changed since last compute |

Stale is set true when any input changes (surface dims, tile size, offset, region). Recomputation is debounced (~100ms after last change during fine-tuning).

---

## Layout Engine

### Core Algorithm

For a given region (regionW × regionH) and tile (tileW × tileH) with grout width g:

```
unitW = tileW + groutW
unitH = tileH + groutH
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
```

### Pattern Variants

| Pattern | Behavior |
|---------|----------|
| **Grid** | Standard columns × rows. Symmetry check on both axes. |
| **Brick** | 50% horizontal offset on every other row. Symmetry check on row pairs. |
| **Stacked** | No horizontal offset between rows. Vertical grout lines run straight. |
| **Herringbone** | Tiles at 45°. Placed as rotated bounding boxes in a staggered grid. |

### Multi-Group Surfaces

Each `SurfaceTileGroup` defines a rectangular region. The Layout Engine runs independently per region. Regions do not overlap. The Render Engine stitches all regions together on the final canvas.

---

## Rendering

### 2D Elevation View

Paint order (back to front):

1. Surface background fill
2. Per PlacedTile: clipped texture image at tile rect
3. Cut tiles: red dashed line on cut edge + dimension annotation (arrow + mm label)
4. Grout lines between all tiles
5. Surface dimension arrows + mm labels on outer edges
6. Legend: grout width

**Cut annotations:** Horizontal arrow ↔ across the cut tile labeled with its width (e.g., "91mm") for width cuts. Vertical arrow ↕ for height cuts. Both arrows for corner cuts.

### 3D Isometric View

No 3D engine. Isometric projection via 2D matrix:

```
ix = (sx - sy) * cos30° + ox
iy = (sx + sy) * sin30° - sz + oy
```

**Draw order** (painter's algorithm, back-to-front): floor → back walls → side walls → front wall. Static isometric angle — no camera rotation, so ordering is deterministic.

The same `drawSurface()` code runs through `canvas.transform(matrix)` — textures skew correctly, grout lines stay aligned.

No dimension labels on 3D view. Clean preview for client approval.

### Export

- **2D:** Render to `ui.Image` via `PictureRecorder`. Save as PNG at 150–300 DPI. One image per surface. Full dimension labels. For tilers/crew.
- **3D:** Single isometric room render as PNG. No labels. For client approval.

---

## Fine-Tuning UX

### Interaction Flow

1. **Select surface** — tap in sidebar or in 3D room view. 2D elevation fills the main canvas.
2. **Drag** — one-finger drag anywhere on canvas shifts the tile grid. Offset {x, y} updates live. Wrap-around: tiles exiting one edge reappear on the opposite edge. Cuts recalculate in real-time.
3. **Lock + drag** — toggle lock on other surfaces. Drag selected surface → locked surfaces shift per the axis rules below.

### Lock Propagation Rules

Walls only lock with walls. Floors only lock with floors. Wall↔floor locking has no effect for MVP.

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

### What Drag Changes

Dragging modifies `SurfaceTileGroup.offset`. This triggers `LayoutResult.stale = true`, then a debounced recompute (~100ms after finger lifts). The Layout Engine reruns with the new offset; the Canvas repaints.

Fine-tuning does not change tile sizes or pattern. It only shifts the grid origin.

---

## Texture Capture

### Flow

1. Tiler opens camera (fullscreen, guides overlay)
2. Positions tile within edge-detection guides (like document scanner)
3. App detects rectangular edges, highlights the detected rectangle
4. Tiler confirms → app perspective-corrects to a flat square image
5. Shows preview. Tiler can retake or save.
6. Saved texture is stored in Tile Library, assigned a TileGroup.

### Implementation

Uses Flutter's `camera` plugin + edge detection package (e.g., `edge_detection` or custom Canny edge detection via OpenCV FFI). Perspective correction uses a 4-point homography transform — same math as every document scanner app.

Captured images are stored in the app's local document directory. SQLite stores the file path.

### Import Alternative

Tiler can pick an existing image from the device gallery. Imported images skip edge detection but can be cropped to a square manually.

---

## Room Modeling UX

### Adding Surfaces

1. Tiler creates a room: gives it a name, selects units (mm/in)
2. Taps "Add Wall" or "Add Floor"
3. Enters dimensions: width, height (for walls) or width, depth (for floors)
4. Positions the surface relative to the room origin:
   - For MVP: preset positions (front, back, left, right wall + floor) with auto-computed 3D coordinates based on room dimensions
   - Stretch: free-form 3D positioning
5. Tiler assigns tile groups to the surface (creates SurfaceTileGroup records)

### Surface → Tile Group Assignment

1. Select a surface
2. Tap "Add Tile Group"
3. Choose from Tile Library
4. Define the region on the surface this group occupies (default: full surface)
5. Select pattern type (default: grid)
6. Select grout color for the surface

---

## App Navigation (Screen Flow)

```
Home (Project List)
  └── Room Editor
        ├── Surfaces tab (list of walls/floors)
        │     └── Surface detail (dimensions, tile groups, grout)
        ├── Tile Library tab (all tile groups)
        │     └── Tile detail / texture capture
        ├── Layout tab (2D elevation + fine-tuning)
        └── Preview tab (3D isometric + export)
```

Bottom navigation bar with 4 tabs. Room-level context (which room is being edited).

---

## Technology Choices

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Framework | Flutter | Cross-platform, strong CustomPainter for rendering |
| State management | Riverpod or Provider | Standard Flutter patterns, sufficient for local-only |
| Local DB | sqflite (SQLite) | Mature, well-supported, no network needed |
| Image storage | App documents directory | Files referenced by path in SQLite |
| Camera | `camera` plugin + edge_detection | Standard Flutter camera stack |
| 3D (isometric) | Custom math + Canvas transform | No 3D engine dependency |

## Out of Scope (MVP)

- Cloud sync / multi-device
- User accounts / auth
- PDF export (PNG only)
- Free-form 3D room positioning (preset positions only)
- Wall↔floor grout alignment (stretch goal)
- Complex tile patterns beyond grid/brick/stacked/herringbone
- Undo/redo history
- Price estimation / materials calculation
