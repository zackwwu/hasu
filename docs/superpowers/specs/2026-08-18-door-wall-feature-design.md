# Door Wall Feature — Design

**Date:** 2026-08-18
**Status:** Approved design, awaiting implementation (plan: Phase 10 of `docs/superpowers/plans/2026-07-20-tile-layout-app-plan.md`, Tasks 21–26)

## Goal

Let the user specify which wall the room's door is on. This unlocks three capabilities:

1. **Door-relative wall names** — walls are named from the perspective of someone entering the room
2. **Door opening in the 3D preview** — the door renders as a visible cutout
3. **Layout exclusion** — the layout engine leaves the door area untiled, with cut edges for the cut list

## Scope Decisions

- One door per room (MVP)
- Door defaults: 900 × 2100 mm, horizontally centered on the wall at selection time
- Wall picker labels use coordinate names ("Front Wall" = z=0 wall) because door-relative names only exist once a door is set
- The door sits on the floor: in surface-local coordinates the door rect is always `(doorOffset, 0, doorWidth, doorHeight)`

## Naming Convention

| Door-relative position | Rotation delta `normalize(wall - door)` | Name |
|---|---|---|
| The wall with the door | 0 | Door Wall |
| Wall you face when entering | 180 | Front Wall |
| Your left, standing at the door facing in | 90 | Left Wall |
| Your right, standing at the door facing in | 270 | Right Wall |

No door set → existing coordinate names (Front = z=0, Back = z=depth, Left = x=0, Right = x=width) remain unchanged. Floor is always "Floor".

## Data Model

### Room (persisted)

```kotlin
data class Room(
    val id: String,
    val projectId: String,
    val name: String,
    val width: Double, val depth: Double, val height: Double,
    val doorWall: Double? = null,      // rotation of the door wall (0/90/180/270), null = no door
    val doorWidth: Double = 900.0,
    val doorHeight: Double = 2100.0,
    val doorOffset: Double? = null,    // distance from wall anchor corner; null = centered
)
```

### Surface (transient addition)

```kotlin
data class Surface(
    // ... existing fields ...
    val doorRotation: Double? = null,  // populated at query time via JOIN rooms
)
```

`doorRotation` is NOT stored in the surfaces table — it is derived from the room. `SqlDelightSurfaceRepository.getByRoom()` joins rooms and fills it in. This keeps door config in one place (the room) and lets `displayName()` stay self-contained.

## Storage & Migration

- New SQLDelight migration file `2.sqm`: `ALTER TABLE rooms ADD COLUMN door_wall / door_width / door_height / door_offset`
- Update `getRoomsByProject`, `getRoomById`, `insertRoom`; add `updateRoomDoor`
- Both drivers get a migration callback (`TileLayoutDb.Schema.migrate(driver, oldVersion, newVersion)`) — existing installs upgrade in place, no data loss

## Door Geometry

### Coordinate convention for doorOffset

The offset is measured from the wall's **anchor corner** along the wall's **span direction**, using the same rotation rules as `IsometricProjection.wallWorldCorners`:

- rotation 0 (Front): span +X from anchor
- rotation 180 (Back): span −X from anchor
- rotation 90 (Left): span −Z from anchor
- rotation 270 (Right): span +Z from anchor

`doorOffset = null` → centered: `(surface.width - doorWidth) / 2`.

### Surface-local rect (for layout exclusion)

`DoorGeometry.surfaceLocalRect(room, surface)`: returns `RegionRect(doorOffset, 0, doorWidth, doorHeight)` when `surface` is the door wall, else null. This is the same coordinate space as `PlacedTile` positions, so it plugs directly into the layout engine.

### World corners (for 3D preview)

`DoorGeometry.worldCorners(room, surface)`: the door rectangle as four world-space `(x, y, z)` corners on the wall face, computed from the wall's rotation rules + the offset. Platform canvases project them with `IsometricProjection.project()`.

## 3D Preview Rendering

After drawing the wall polygons, both canvases draw the door on the door wall:

1. Fill the projected door quad with a dark color (`0xFF3A3A3A`) — reads as an opening
2. Stroke it with a lighter outline
3. Drawn after the wall fill so it reads as a cutout; before the wall's label

## Layout Exclusion Semantics

`LayoutEngine.compute(..., exclusions: List<RegionRect> = emptyList())`:

- A tile whose rect **intersects** any exclusion rect is **dropped** entirely (nothing is tiled behind the door)
- A tile **adjacent** to an exclusion rect gets the corresponding cut-edge flags (LEFT/RIGHT/TOP/BOTTOM) merged with existing region-boundary cuts — these appear in the cut list
- Applies to all three patterns (grid, brick, herringbone)

`RoomEditorViewModel.computeLayout()` loads the room and passes the door rect as the exclusion for the door wall's surface.

## UI — Door Configuration

### Placement

Both platforms: **room editor → Surfaces tab**, between the room dimensions line and the surface list. On iOS the section scrolls with the list; on Android the card sits above the list. The section is always visible (also when surfaces are generated).

### Android (Compose) — "Door" card

An `OutlinedCard` below the room-dimensions row, above the surfaces area:

```
┌──────────────────────────────────────────────┐
│ 🚪 Door                                        │
│                                                │
│ Wall:  (None) (Front) (Back) (Left) (Right)    │   ← FilterChips
│                                                │
│ Width (mm)   Height (mm)                       │   ← OutlinedTextFields, number keyboard
│ [ 900      ] [ 2100     ]                      │
│                                                │
│ Offset from wall corner (mm)                   │
│ [ 1050     ]  caption: "Auto-centered on wall  │   ← shown until user edits offset
│                selection"                      │
│                                                │
│ [ Save Door ]  (enabled when dirty & valid)    │
└──────────────────────────────────────────────┘
```

Component notes:
- **Title icon**: `Text("🚪")` — no new icon dependency (material-icons-core is not in the project)
- **Wall picker**: `FlowRow` of 5 `FilterChip`s — None, Front Wall, Back Wall, Left Wall, Right Wall (coordinate names; see caption below)
- **Picker caption** (small, onSurfaceVariant): "Walls are listed by position — names change after saving."
- **Width/Height/Offset**: `OutlinedTextField`s with `KeyboardType.Number`; values in mm
- **Save Door**: `Button`, full width, enabled only when the door is dirty AND valid

### iOS (SwiftUI) — "Door" section

A `Section("Door")` inside the surfaces `List`:

```
DOOR
  Wall        None ▾                        ← Menu picker
  Width       900   mm                      ← TextField (numberPad)
  Height      2100  mm
  Offset      1050  mm  (Auto-centered)
  [ Save Door ]                             ← disabled when not dirty/invalid
```

Component notes:
- Picker: `Picker` with `.menu` style, 5 options (None / Front Wall / Back Wall / Left Wall / Right Wall)
- Fields: `TextField` with `.keyboardType(.numberPad)` inside an `HStack` label + value
- Same "Walls are listed by position" caption under the picker
- Save button styled `.borderedProminent`, disabled unless dirty & valid

### Interaction states

| State | Picker | Fields | Save button |
|---|---|---|---|
| **No door set** | "None" selected | Disabled, empty | Disabled |
| **Wall selected (dirty)** | wall chip selected | Enabled; offset auto-fills with centered value the first time the wall changes | Enabled if valid |
| **Door saved** | wall chip selected | Show persisted values | Disabled (not dirty) |
| **Field edited after save (dirty)** | unchanged | Edited value | Enabled if valid |
| **"None" selected with door set** | "None" | Disabled | Enabled — clears the door |

Save behavior: `RoomRepository.updateDoor(...)` → reload surfaces → names in the surfaces list and preview labels update immediately (visible feedback; no toast/snackbar needed). Selecting "None" and saving clears the door; walls revert to coordinate names.

### Validation rules (inline, both platforms)

- `doorWidth`: 400 … wall width (mm). Error caption below the field when out of range: "Door width must be 400–{wallWidth} mm"
- `doorHeight`: 1500 … wall height. Error: "Door height must be 1500–{wallHeight} mm"
- `doorOffset`: empty = auto-center (treated as valid); otherwise clamped to `0 … wallWidth − doorWidth`
- Save disabled while any error is shown

### Preview & layout door indicators

- **3D preview**: the canvases receive the door quads from the shared layer — `RoomEditorViewModel` exposes `doorWorldRects: StateFlow<Map<String, List<Triple<Double,Double,Double>>>>` (surfaceId → world corners, from `DoorGeometry.worldCorners`). The canvases project them with the same `fitViewport` as the walls and draw the dark opening after the wall fills.
- **2D layout tab**: after drawing tiles, draw a dashed outline rectangle where the door is (the gap tiles already leave it visually open); the outline makes the door explicit. Dashed stroke, gray, 1.5px.

## Error Handling

- Door wider than the wall: clamp offset so the door fits (`0 .. wallWidth - doorWidth`); reject widths larger than the wall with an inline validation message
- Door taller than the wall: clamp height to wall height
- Migration failure (schema 0 or unknown): fall back to fresh schema only for dev installs; production data is version 1 → 2

## Testing

### Shared unit tests

- `SurfaceDisplayNameTest`: door-relative names for all 4 door rotations; no-door fallback; full-turn normalization
- `DoorGeometryTest`: surface-local rect per wall rotation; centering when offset is null; null when surface is not the door wall
- `LayoutEngineTest`: all 3 patterns drop tiles inside the exclusion; door-adjacent tiles carry cut edges

### E2E

- New Maestro flow `phase-10-door.yaml`: set door → verify renamed walls in the surfaces list → Layout tab shows the door gap → Preview shows the opening
- Update `docs/phase-9-verification-checklist.md` with a door section

## Out of Scope (Future)

- Multiple doors / windows
- Door swing direction or open/closed state in preview
- Door trim/frame rendering
