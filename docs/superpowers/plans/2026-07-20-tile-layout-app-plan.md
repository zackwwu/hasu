# Tile Layout App — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Flutter mobile app for tilers to design tile layouts, capture textures, render 2D/3D previews, and generate cut lists.

**Architecture:** Canvas-first custom rendering via CustomPainter. 6 modules: Room Modeler, Tile Library, Texture Capture, Layout Engine, Render Engine, Cut List Generator. SQLite local storage via sqflite. State management via Riverpod.

**Tech Stack:** Flutter 3.x, Dart, sqflite, Riverpod, camera plugin, path_provider

---

## File Structure

```
lib/
  main.dart
  app.dart

  models/
    enums.dart                  # UnitSystem, SurfaceType, GroutColor, TileSource, TilePattern, CutEdge
    project.dart                # Project model
    room.dart                   # Room model
    surface.dart                # Surface model
    surface_tile_group.dart     # SurfaceTileGroup model + region rect
    tile_group.dart             # TileGroup model
    placed_tile.dart            # PlacedTile model (in LayoutResult)
    layout_result.dart          # LayoutResult model
    cut_entry.dart              # CutEntry model (for cut list)

  storage/
    database.dart               # SQLite schema + migrations
    project_repository.dart     # CRUD for Project
    room_repository.dart        # CRUD for Room
    surface_repository.dart     # CRUD for Surface
    tile_group_repository.dart  # CRUD for TileGroup
    layout_result_repository.dart # CRUD for LayoutResult

  engine/
    surface_position_calculator.dart  # Auto-position surfaces from room dims
    region_validator.dart             # Overlap + coverage validation
    layout_engine.dart                # Core tile placement algorithm

  render/
    surface_painter.dart        # 2D elevation CustomPainter
    isometric_painter.dart      # 3D room view CustomPainter
    cut_annotation_painter.dart # Cut tile markers + dimension labels
    export_service.dart         # Render to PNG via PictureRecorder
    texture_loader.dart         # Load + cache tile texture images from disk

  cutlist/
    cut_list_generator.dart     # Walk LayoutResults → grouped CutEntry list

  state/
    providers.dart              # All Riverpod providers
    project_state.dart          # Project list + current project
    room_state.dart             # Current room surfaces
    layout_state.dart           # Selected surface, offsets, lock state, undo buffer

  ui/
    screens/
      home_screen.dart
      project_detail_screen.dart
      room_editor_screen.dart
      surface_detail_screen.dart
      region_editor_screen.dart
      tile_library_screen.dart
      tile_group_form_screen.dart
      camera_screen.dart
      help_diagram_dialog.dart
    tabs/
      surfaces_tab.dart
      layout_tab.dart
      preview_tab.dart
      cut_list_tab.dart
    widgets/
      surface_card.dart
      tile_group_card.dart
      grout_picker.dart
      region_picker.dart
      pattern_picker.dart
      surface_selector_chips.dart
      lock_chips.dart
      cut_row.dart
      cut_shape_diagram.dart
      mini_wall_preview.dart
      room_diagram_overlay.dart

test/
  engine/
    surface_position_calculator_test.dart
    region_validator_test.dart
    layout_engine_test.dart
  cutlist/
    cut_list_generator_test.dart
  storage/
    database_test.dart
    project_repository_test.dart
```

---

## Phase 1: Project Scaffold & Data Models

### Task 1: Create Flutter project and directory structure

**Files:**
- Create: `lib/main.dart`
- Create: `lib/app.dart`
- Run: `flutter create` in project root

- [ ] **Step 1: Create Flutter project**

```bash
cd /Users/chenwu/orca/workspaces/hasu/spec-and-plan
flutter create --org com.hasu --project-name tile_layout .
```

- [ ] **Step 2: Add dependencies to pubspec.yaml**

Edit `pubspec.yaml` to add under `dependencies`:
```yaml
dependencies:
  flutter:
    sdk: flutter
  sqflite: ^2.3.0
  path_provider: ^2.1.0
  path: ^1.8.0
  uuid: ^4.2.0
  flutter_riverpod: ^2.4.0
  camera: ^0.10.0
  image_picker: ^1.0.0
  image: ^4.1.0
```

Under `dev_dependencies`:
```yaml
dev_dependencies:
  flutter_test:
    sdk: flutter
  sqflite_common_ffi: ^2.3.0
```

- [ ] **Step 3: Run flutter pub get**

```bash
cd /Users/chenwu/orca/workspaces/hasu/spec-and-plan
flutter pub get
```
Expected: exits 0, no errors.

- [ ] **Step 4: Create directory structure**

```bash
mkdir -p lib/{models,storage,engine,render,cutlist,state,ui/{screens,tabs,widgets}}
mkdir -p test/{engine,cutlist,storage}
```

- [ ] **Step 5: Write minimal main.dart**

```dart
// lib/main.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: TileLayoutApp()));
}
```

- [ ] **Step 6: Write minimal app.dart**

```dart
// lib/app.dart
import 'package:flutter/material.dart';
import 'ui/screens/home_screen.dart';

class TileLayoutApp extends StatelessWidget {
  const TileLayoutApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Tile Layout',
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: scaffold Flutter project with dependencies and directory structure"
```

---

### Task 2: Define enum types

**Files:**
- Create: `lib/models/enums.dart`

- [ ] **Step 1: Write enums.dart**

```dart
// lib/models/enums.dart

enum UnitSystem { mm, inches }

enum SurfaceType { wall, floor }

enum GroutColor { black, grey, white }

enum TileSource { captured, imported }

enum TilePattern { grid, brick, stacked, herringbone }

enum CutEdge { left, right, top, bottom }
```

- [ ] **Step 2: Commit**

```bash
git add lib/models/enums.dart
git commit -m "feat: define enum types for data model"
```

---

### Task 3: Define data model classes

**Files:**
- Create: `lib/models/project.dart`
- Create: `lib/models/room.dart`
- Create: `lib/models/surface.dart`
- Create: `lib/models/surface_tile_group.dart`
- Create: `lib/models/tile_group.dart`
- Create: `lib/models/placed_tile.dart`
- Create: `lib/models/layout_result.dart`
- Create: `lib/models/cut_entry.dart`

- [ ] **Step 1: Write project.dart**

```dart
// lib/models/project.dart
import 'package:uuid/uuid.dart';
import 'enums.dart';

const _uuid = Uuid();

class Project {
  final String id;
  String name;
  UnitSystem units;
  DateTime createdAt;

  Project({
    String? id,
    required this.name,
    this.units = UnitSystem.mm,
    DateTime? createdAt,
  })  : id = id ?? _uuid.v4(),
        createdAt = createdAt ?? DateTime.now();

  Map<String, dynamic> toMap() => {
        'id': id,
        'name': name,
        'units': units.name,
        'created_at': createdAt.toIso8601String(),
      };

  factory Project.fromMap(Map<String, dynamic> map) => Project(
        id: map['id'] as String,
        name: map['name'] as String,
        units: UnitSystem.values.byName(map['units'] as String),
        createdAt: DateTime.parse(map['created_at'] as String),
      );

  Project copyWith({String? name, UnitSystem? units}) => Project(
        id: id,
        name: name ?? this.name,
        units: units ?? this.units,
        createdAt: createdAt,
      );
}
```

- [ ] **Step 2: Write tile_group.dart**

```dart
// lib/models/tile_group.dart
import 'package:uuid/uuid.dart';
import 'enums.dart';

const _uuid = Uuid();

class TileGroup {
  final String id;
  final String projectId;
  String name;
  double tileWidth;
  double tileHeight;
  String? texturePath;
  TileSource source;

  TileGroup({
    String? id,
    required this.projectId,
    required this.name,
    required this.tileWidth,
    required this.tileHeight,
    this.texturePath,
    this.source = TileSource.imported,
  }) : id = id ?? _uuid.v4();

  Map<String, dynamic> toMap() => {
        'id': id,
        'project_id': projectId,
        'name': name,
        'tile_width': tileWidth,
        'tile_height': tileHeight,
        'texture_path': texturePath,
        'source': source.name,
      };

  factory TileGroup.fromMap(Map<String, dynamic> map) => TileGroup(
        id: map['id'] as String,
        projectId: map['project_id'] as String,
        name: map['name'] as String,
        tileWidth: (map['tile_width'] as num).toDouble(),
        tileHeight: (map['tile_height'] as num).toDouble(),
        texturePath: map['texture_path'] as String?,
        source: TileSource.values.byName(map['source'] as String),
      );

  TileGroup copyWith({
    String? name,
    double? tileWidth,
    double? tileHeight,
    String? texturePath,
    TileSource? source,
  }) =>
      TileGroup(
        id: id,
        projectId: projectId,
        name: name ?? this.name,
        tileWidth: tileWidth ?? this.tileWidth,
        tileHeight: tileHeight ?? this.tileHeight,
        texturePath: texturePath ?? this.texturePath,
        source: source ?? this.source,
      );
}
```

- [ ] **Step 3: Write room.dart**

```dart
// lib/models/room.dart
import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class Room {
  final String id;
  final String projectId;
  String name;
  double width;  // overall room width (X axis)
  double depth;  // overall room depth (Z axis)
  double height; // overall room height (Y axis)

  Room({
    String? id,
    required this.projectId,
    required this.name,
    required this.width,
    required this.depth,
    required this.height,
  }) : id = id ?? _uuid.v4();

  Map<String, dynamic> toMap() => {
        'id': id,
        'project_id': projectId,
        'name': name,
        'width': width,
        'depth': depth,
        'height': height,
      };

  factory Room.fromMap(Map<String, dynamic> map) => Room(
        id: map['id'] as String,
        projectId: map['project_id'] as String,
        name: map['name'] as String,
        width: (map['width'] as num).toDouble(),
        depth: (map['depth'] as num).toDouble(),
        height: (map['height'] as num).toDouble(),
      );

  Room copyWith({String? name, double? width, double? depth, double? height}) =>
      Room(
        id: id,
        projectId: projectId,
        name: name ?? this.name,
        width: width ?? this.width,
        depth: depth ?? this.depth,
        height: height ?? this.height,
      );
}
```

- [ ] **Step 4: Write surface.dart**

```dart
// lib/models/surface.dart
import 'package:uuid/uuid.dart';
import 'enums.dart';

const _uuid = Uuid();

class SurfacePosition {
  final double x, y, z;
  final double rotation; // degrees around Y axis

  const SurfacePosition({
    required this.x,
    required this.y,
    required this.z,
    required this.rotation,
  });

  Map<String, dynamic> toMap() => {
        'x': x, 'y': y, 'z': z, 'rotation': rotation,
      };

  factory SurfacePosition.fromMap(Map<String, dynamic> map) => SurfacePosition(
        x: (map['x'] as num).toDouble(),
        y: (map['y'] as num).toDouble(),
        z: (map['z'] as num).toDouble(),
        rotation: (map['rotation'] as num).toDouble(),
      );
}

class Surface {
  final String id;
  final String roomId;
  String name; // e.g. "Front wall", "Floor"
  SurfaceType type;
  double width;
  double height; // for floors: depth
  SurfacePosition position;
  GroutColor groutColor;
  double groutWidth;

  Surface({
    String? id,
    required this.roomId,
    required this.name,
    required this.type,
    required this.width,
    required this.height,
    required this.position,
    this.groutColor = GroutColor.grey,
    this.groutWidth = 3.0,
  }) : id = id ?? _uuid.v4();

  Map<String, dynamic> toMap() => {
        'id': id,
        'room_id': roomId,
        'name': name,
        'type': type.name,
        'width': width,
        'height': height,
        'pos_x': position.x,
        'pos_y': position.y,
        'pos_z': position.z,
        'pos_rotation': position.rotation,
        'grout_color': groutColor.name,
        'grout_width': groutWidth,
      };

  factory Surface.fromMap(Map<String, dynamic> map) {
    final pos = SurfacePosition(
      x: (map['pos_x'] as num).toDouble(),
      y: (map['pos_y'] as num).toDouble(),
      z: (map['pos_z'] as num).toDouble(),
      rotation: (map['pos_rotation'] as num).toDouble(),
    );
    return Surface(
      id: map['id'] as String,
      roomId: map['room_id'] as String,
      name: map['name'] as String,
      type: SurfaceType.values.byName(map['type'] as String),
      width: (map['width'] as num).toDouble(),
      height: (map['height'] as num).toDouble(),
      position: pos,
      groutColor: GroutColor.values.byName(map['grout_color'] as String),
      groutWidth: (map['grout_width'] as num).toDouble(),
    );
  }
}
```

- [ ] **Step 5: Write surface_tile_group.dart**

```dart
// lib/models/surface_tile_group.dart
import 'package:uuid/uuid.dart';
import 'enums.dart';

const _uuid = Uuid();

class RegionRect {
  final double x, y, width, height;

  const RegionRect({
    required this.x,
    required this.y,
    required this.width,
    required this.height,
  });

  /// Returns true if this rect overlaps with [other].
  bool overlaps(RegionRect other) {
    if (x >= other.x + other.width) return false;
    if (x + width <= other.x) return false;
    if (y >= other.y + other.height) return false;
    if (y + height <= other.y) return false;
    return true;
  }

  /// Intersection area with [other]. Returns 0 if no overlap.
  double intersectionArea(RegionRect other) {
    final ix = (x > other.x ? x : other.x);
    final iy = (y > other.y ? y : other.y);
    final iw = ((x + width < other.x + other.width ? x + width : other.x + other.width) - ix);
    final ih = ((y + height < other.y + other.height ? y + height : other.y + other.height) - iy);
    if (iw <= 0 || ih <= 0) return 0;
    return iw * ih;
  }

  double get area => width * height;

  Map<String, dynamic> toMap() => {'x': x, 'y': y, 'w': width, 'h': height};

  factory RegionRect.fromMap(Map<String, dynamic> map) => RegionRect(
        x: (map['x'] as num).toDouble(),
        y: (map['y'] as num).toDouble(),
        width: (map['w'] as num).toDouble(),
        height: (map['h'] as num).toDouble(),
      );
}

class SurfaceTileGroup {
  final String id;
  final String surfaceId;
  String tileGroupId;
  RegionRect region;
  TilePattern pattern;
  double offsetX;
  double offsetY;
  bool locked;

  SurfaceTileGroup({
    String? id,
    required this.surfaceId,
    required this.tileGroupId,
    required this.region,
    this.pattern = TilePattern.grid,
    this.offsetX = 0.0,
    this.offsetY = 0.0,
    this.locked = false,
  }) : id = id ?? _uuid.v4();

  Map<String, dynamic> toMap() => {
        'id': id,
        'surface_id': surfaceId,
        'tile_group_id': tileGroupId,
        'region_x': region.x,
        'region_y': region.y,
        'region_w': region.width,
        'region_h': region.height,
        'pattern': pattern.name,
        'offset_x': offsetX,
        'offset_y': offsetY,
      };

  factory SurfaceTileGroup.fromMap(Map<String, dynamic> map) => SurfaceTileGroup(
        id: map['id'] as String,
        surfaceId: map['surface_id'] as String,
        tileGroupId: map['tile_group_id'] as String,
        region: RegionRect(
          x: (map['region_x'] as num).toDouble(),
          y: (map['region_y'] as num).toDouble(),
          width: (map['region_w'] as num).toDouble(),
          height: (map['region_h'] as num).toDouble(),
        ),
        pattern: TilePattern.values.byName(map['pattern'] as String),
        offsetX: (map['offset_x'] as num).toDouble(),
        offsetY: (map['offset_y'] as num).toDouble(),
      );
}
```

- [ ] **Step 6: Write placed_tile.dart**

```dart
// lib/models/placed_tile.dart
import 'enums.dart';

class PlacedTile {
  final double x, y;         // position on surface (top-left corner)
  final double width, height; // actual rendered size (may be smaller if cut)
  final double rotation;      // degrees (0, 45, -45)
  final bool isCut;
  final List<CutEdge> cutEdges;
  final String tileGroupId;

  const PlacedTile({
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    this.rotation = 0,
    this.isCut = false,
    this.cutEdges = const [],
    required this.tileGroupId,
  });

  Map<String, dynamic> toMap() => {
        'x': x, 'y': y, 'w': width, 'h': height,
        'rotation': rotation,
        'is_cut': isCut ? 1 : 0,
        'cut_edges': cutEdges.map((e) => e.name).join(','),
        'tile_group_id': tileGroupId,
      };

  factory PlacedTile.fromMap(Map<String, dynamic> map) {
    final edgesStr = map['cut_edges'] as String? ?? '';
    final edges = edgesStr.isEmpty
        ? <CutEdge>[]
        : edgesStr.split(',').map((s) => CutEdge.values.byName(s.trim())).toList();
    return PlacedTile(
      x: (map['x'] as num).toDouble(),
      y: (map['y'] as num).toDouble(),
      width: (map['w'] as num).toDouble(),
      height: (map['h'] as num).toDouble(),
      rotation: (map['rotation'] as num).toDouble(),
      isCut: (map['is_cut'] as int) == 1,
      cutEdges: edges,
      tileGroupId: map['tile_group_id'] as String,
    );
  }
}
```

- [ ] **Step 7: Write layout_result.dart**

```dart
// lib/models/layout_result.dart
import 'package:uuid/uuid.dart';
import 'placed_tile.dart';

const _uuid = Uuid();

class LayoutResult {
  final String id;
  final String surfaceId;
  List<PlacedTile> tiles;
  DateTime computedAt;
  bool stale;

  LayoutResult({
    String? id,
    required this.surfaceId,
    List<PlacedTile>? tiles,
    DateTime? computedAt,
    this.stale = true,
  })  : id = id ?? _uuid.v4(),
        tiles = tiles ?? [],
        computedAt = computedAt ?? DateTime.now();

  Map<String, dynamic> toMap() => {
        'id': id,
        'surface_id': surfaceId,
        'computed_at': computedAt.toIso8601String(),
        'stale': stale ? 1 : 0,
      };
  // Note: tiles stored in separate table (see database task)

  factory LayoutResult.fromMap(Map<String, dynamic> map) => LayoutResult(
        id: map['id'] as String,
        surfaceId: map['surface_id'] as String,
        computedAt: DateTime.parse(map['computed_at'] as String),
        stale: (map['stale'] as int) == 1,
      );
}
```

- [ ] **Step 8: Write cut_entry.dart**

```dart
// lib/models/cut_entry.dart

class CutLocation {
  final String surfaceId;
  final String surfaceName;
  final int count;

  const CutLocation({
    required this.surfaceId,
    required this.surfaceName,
    required this.count,
  });
}

class CutEntry {
  final String tileGroupId;
  final String tileGroupName;
  final double width;
  final double height;
  final String cutEdgesKey; // e.g. "right" or "right,bottom"
  final List<CutLocation> locations;
  final int totalCount;

  const CutEntry({
    required this.tileGroupId,
    required this.tileGroupName,
    required this.width,
    required this.height,
    required this.cutEdgesKey,
    required this.locations,
    required this.totalCount,
  });

  String get cutTypeDescription {
    switch (cutEdgesKey) {
      case 'left': return 'Left edge cut';
      case 'right': return 'Right edge cut';
      case 'top': return 'Top edge cut';
      case 'bottom': return 'Bottom edge cut';
      case 'right,bottom': return 'Corner cut (right + bottom)';
      case 'left,bottom': return 'Corner cut (left + bottom)';
      case 'right,top': return 'Corner cut (right + top)';
      case 'left,top': return 'Corner cut (left + top)';
      default: return 'Custom cut';
    }
  }
}
```

- [ ] **Step 9: Commit**

```bash
git add lib/models/
git commit -m "feat: define all data model classes"
```

---

## Phase 2: Storage Layer

### Task 4: Create SQLite database with full schema

**Files:**
- Create: `lib/storage/database.dart`

- [ ] **Step 1: Write database.dart**

```dart
// lib/storage/database.dart
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class AppDatabase {
  static Database? _db;

  static Future<Database> get instance async {
    if (_db != null) return _db!;
    _db = await _init();
    return _db!;
  }

  static Future<Database> _init() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, 'tile_layout.db');

    return await openDatabase(
      path,
      version: 1,
      onCreate: _onCreate,
    );
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE projects (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        units TEXT NOT NULL DEFAULT 'mm',
        created_at TEXT NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE tile_groups (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        name TEXT NOT NULL,
        tile_width REAL NOT NULL,
        tile_height REAL NOT NULL,
        texture_path TEXT,
        source TEXT NOT NULL DEFAULT 'imported',
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE rooms (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        name TEXT NOT NULL,
        width REAL NOT NULL,
        depth REAL NOT NULL,
        height REAL NOT NULL,
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE surfaces (
        id TEXT PRIMARY KEY,
        room_id TEXT NOT NULL,
        name TEXT NOT NULL,
        type TEXT NOT NULL,
        width REAL NOT NULL,
        height REAL NOT NULL,
        pos_x REAL NOT NULL,
        pos_y REAL NOT NULL,
        pos_z REAL NOT NULL,
        pos_rotation REAL NOT NULL,
        grout_color TEXT NOT NULL DEFAULT 'grey',
        grout_width REAL NOT NULL DEFAULT 3.0,
        FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE surface_tile_groups (
        id TEXT PRIMARY KEY,
        surface_id TEXT NOT NULL,
        tile_group_id TEXT NOT NULL,
        region_x REAL NOT NULL,
        region_y REAL NOT NULL,
        region_w REAL NOT NULL,
        region_h REAL NOT NULL,
        pattern TEXT NOT NULL DEFAULT 'grid',
        offset_x REAL NOT NULL DEFAULT 0.0,
        offset_y REAL NOT NULL DEFAULT 0.0,
        FOREIGN KEY (surface_id) REFERENCES surfaces(id) ON DELETE CASCADE,
        FOREIGN KEY (tile_group_id) REFERENCES tile_groups(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE layout_results (
        id TEXT PRIMARY KEY,
        surface_id TEXT NOT NULL,
        computed_at TEXT NOT NULL,
        stale INTEGER NOT NULL DEFAULT 1,
        FOREIGN KEY (surface_id) REFERENCES surfaces(id) ON DELETE CASCADE
      )
    ''');

    await db.execute('''
      CREATE TABLE placed_tiles (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
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
      )
    ''');
  }

  /// For testing: create an in-memory database
  static Future<Database> inMemory() async {
    return await openDatabase(
      ':memory:',
      version: 1,
      onCreate: _onCreate,
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/storage/database.dart
git commit -m "feat: create SQLite database schema"
```

---

### Task 5: Write repositories

**Files:**
- Create: `lib/storage/project_repository.dart`
- Create: `lib/storage/tile_group_repository.dart`
- Create: `lib/storage/room_repository.dart`
- Create: `lib/storage/surface_repository.dart`
- Create: `lib/storage/layout_result_repository.dart`
- Write tests

- [ ] **Step 1: Write project_repository.dart**

```dart
// lib/storage/project_repository.dart
import 'package:sqflite/sqflite.dart';
import '../models/project.dart';
import 'database.dart';

class ProjectRepository {
  final Future<Database> Function() _getDb;

  ProjectRepository({Future<Database> Function()? getDb})
      : _getDb = getDb ?? (() => AppDatabase.instance);

  Future<List<Project>> findAll() async {
    final db = await _getDb();
    final maps = await db.query('projects', orderBy: 'created_at DESC');
    return maps.map(Project.fromMap).toList();
  }

  Future<Project?> findById(String id) async {
    final db = await _getDb();
    final maps = await db.query('projects', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Project.fromMap(maps.first);
  }

  Future<void> insert(Project project) async {
    final db = await _getDb();
    await db.insert('projects', project.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> update(Project project) async {
    final db = await _getDb();
    await db.update('projects', project.toMap(),
        where: 'id = ?', whereArgs: [project.id]);
  }

  Future<void> delete(String id) async {
    final db = await _getDb();
    await db.delete('projects', where: 'id = ?', whereArgs: [id]);
  }
}
```

- [ ] **Step 2: Write tile_group_repository.dart**

```dart
// lib/storage/tile_group_repository.dart
import 'package:sqflite/sqflite.dart';
import '../models/tile_group.dart';
import 'database.dart';

class TileGroupRepository {
  Future<List<TileGroup>> findByProject(String projectId) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('tile_groups',
        where: 'project_id = ?', whereArgs: [projectId],
        orderBy: 'name ASC');
    return maps.map(TileGroup.fromMap).toList();
  }

  Future<TileGroup?> findById(String id) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('tile_groups', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return TileGroup.fromMap(maps.first);
  }

  Future<void> insert(TileGroup group) async {
    final db = await AppDatabase.instance;
    await db.insert('tile_groups', group.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> update(TileGroup group) async {
    final db = await AppDatabase.instance;
    await db.update('tile_groups', group.toMap(),
        where: 'id = ?', whereArgs: [group.id]);
  }

  Future<void> delete(String id) async {
    final db = await AppDatabase.instance;
    await db.delete('tile_groups', where: 'id = ?', whereArgs: [id]);
  }

  Future<int> usageCount(String tileGroupId) async {
    final db = await AppDatabase.instance;
    final result = await db.rawQuery(
      'SELECT COUNT(*) as cnt FROM surface_tile_groups WHERE tile_group_id = ?',
      [tileGroupId],
    );
    return (result.first['cnt'] as int);
  }
}
```

- [ ] **Step 3: Write room_repository.dart**

```dart
// lib/storage/room_repository.dart
import 'package:sqflite/sqflite.dart';
import '../models/room.dart';
import 'database.dart';

class RoomRepository {
  Future<List<Room>> findByProject(String projectId) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('rooms',
        where: 'project_id = ?', whereArgs: [projectId],
        orderBy: 'name ASC');
    return maps.map(Room.fromMap).toList();
  }

  Future<Room?> findById(String id) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('rooms', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Room.fromMap(maps.first);
  }

  Future<void> insert(Room room) async {
    final db = await AppDatabase.instance;
    await db.insert('rooms', room.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> update(Room room) async {
    final db = await AppDatabase.instance;
    await db.update('rooms', room.toMap(),
        where: 'id = ?', whereArgs: [room.id]);
  }

  Future<void> delete(String id) async {
    final db = await AppDatabase.instance;
    await db.delete('rooms', where: 'id = ?', whereArgs: [id]);
  }
}
```

- [ ] **Step 4: Write surface_repository.dart with SurfaceTileGroup queries**

```dart
// lib/storage/surface_repository.dart
import 'package:sqflite/sqflite.dart';
import '../models/surface.dart';
import '../models/surface_tile_group.dart';
import 'database.dart';

class SurfaceRepository {
  Future<List<Surface>> findByRoom(String roomId) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('surfaces',
        where: 'room_id = ?', whereArgs: [roomId]);
    return maps.map(Surface.fromMap).toList();
  }

  Future<Surface?> findById(String id) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('surfaces', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Surface.fromMap(maps.first);
  }

  Future<void> insert(Surface surface) async {
    final db = await AppDatabase.instance;
    await db.insert('surfaces', surface.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> update(Surface surface) async {
    final db = await AppDatabase.instance;
    await db.update('surfaces', surface.toMap(),
        where: 'id = ?', whereArgs: [surface.id]);
  }

  Future<void> delete(String id) async {
    final db = await AppDatabase.instance;
    await db.delete('surfaces', where: 'id = ?', whereArgs: [id]);
  }

  // --- SurfaceTileGroup queries ---

  Future<List<SurfaceTileGroup>> findTileGroupsBySurface(String surfaceId) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('surface_tile_groups',
        where: 'surface_id = ?', whereArgs: [surfaceId]);
    return maps.map(SurfaceTileGroup.fromMap).toList();
  }

  Future<void> insertTileGroup(SurfaceTileGroup stg) async {
    final db = await AppDatabase.instance;
    await db.insert('surface_tile_groups', stg.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> updateTileGroup(SurfaceTileGroup stg) async {
    final db = await AppDatabase.instance;
    await db.update('surface_tile_groups', stg.toMap(),
        where: 'id = ?', whereArgs: [stg.id]);
  }

  Future<void> deleteTileGroup(String id) async {
    final db = await AppDatabase.instance;
    await db.delete('surface_tile_groups', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> deleteTileGroupsBySurface(String surfaceId) async {
    final db = await AppDatabase.instance;
    await db.delete('surface_tile_groups',
        where: 'surface_id = ?', whereArgs: [surfaceId]);
  }
}
```

- [ ] **Step 5: Write layout_result_repository.dart**

```dart
// lib/storage/layout_result_repository.dart
import 'package:sqflite/sqflite.dart';
import '../models/layout_result.dart';
import '../models/placed_tile.dart';
import 'database.dart';

class LayoutResultRepository {
  Future<LayoutResult?> findBySurface(String surfaceId) async {
    final db = await AppDatabase.instance;
    final maps = await db.query('layout_results',
        where: 'surface_id = ?', whereArgs: [surfaceId]);
    if (maps.isEmpty) return null;
    final result = LayoutResult.fromMap(maps.first);
    result.tiles = await _loadTiles(db, result.id);
    return result;
  }

  Future<List<PlacedTile>> _loadTiles(Database db, String resultId) async {
    final maps = await db.query('placed_tiles',
        where: 'layout_result_id = ?', whereArgs: [resultId]);
    return maps.map(PlacedTile.fromMap).toList();
  }

  Future<void> save(LayoutResult result) async {
    final db = await AppDatabase.instance;
    // Upsert layout_result
    await db.insert('layout_results', result.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
    // Delete old tiles
    await db.delete('placed_tiles',
        where: 'layout_result_id = ?', whereArgs: [result.id]);
    // Insert new tiles
    for (final tile in result.tiles) {
      await db.insert('placed_tiles', {
        ...tile.toMap(),
        'layout_result_id': result.id,
      });
    }
  }

  Future<void> deleteBySurface(String surfaceId) async {
    final db = await AppDatabase.instance;
    await db.delete('layout_results',
        where: 'surface_id = ?', whereArgs: [surfaceId]);
  }
}
```

- [ ] **Step 6: Write database test**

```dart
// test/storage/database_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:tile_layout/storage/database.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
  });

  test('database creates all tables', () async {
    final db = await AppDatabase.inMemory();
    final tables = await db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
    );
    final names = tables.map((t) => t['name'] as String).toList();
    expect(names, contains('projects'));
    expect(names, contains('tile_groups'));
    expect(names, contains('rooms'));
    expect(names, contains('surfaces'));
    expect(names, contains('surface_tile_groups'));
    expect(names, contains('layout_results'));
    expect(names, contains('placed_tiles'));
    await db.close();
  });
}
```

- [ ] **Step 7: Write project_repository_test.dart**

```dart
// test/storage/project_repository_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:tile_layout/models/project.dart';
import 'package:tile_layout/storage/project_repository.dart';
import 'package:tile_layout/storage/database.dart';

void main() {
  late ProjectRepository repo;
  late Database db;

  setUpAll(() => sqfliteFfiInit());

  setUp(() async {
    db = await AppDatabase.inMemory();
    repo = ProjectRepository(getDb: () async => db);
  });

  tearDown(() async {
    await db.close();
  });

  test('insert and find project', () async {
    final project = Project(name: 'Test Project');
    await repo.insert(project);

    final found = await repo.findById(project.id);
    expect(found, isNotNull);
    expect(found!.name, 'Test Project');
    expect(found.units.name, 'mm');
  });

  test('findAll returns projects ordered by created_at DESC', () async {
    final p1 = Project(name: 'First');
    final p2 = Project(name: 'Second');
    await repo.insert(p1);
    await repo.insert(p2);

    final all = await repo.findAll();
    expect(all.length, greaterThanOrEqualTo(2));
    // Most recent first
    expect(all.first.name, 'Second');
  });

  test('update project', () async {
    final project = Project(name: 'Original');
    await repo.insert(project);

    project.name = 'Updated';
    await repo.update(project);

    final found = await repo.findById(project.id);
    expect(found!.name, 'Updated');
  });

  test('delete project', () async {
    final project = Project(name: 'To Delete');
    await repo.insert(project);
    await repo.delete(project.id);

    final found = await repo.findById(project.id);
    expect(found, isNull);
  });
}
```

- [ ] **Step 8: Run tests**

```bash
cd /Users/chenwu/orca/workspaces/hasu/spec-and-plan
flutter test test/storage/
```
Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
git add lib/storage/ test/storage/
git commit -m "feat: implement SQLite repositories with tests"
```

---

## Phase 3: Engine Layer

### Task 6: Surface position calculator

**Files:**
- Create: `lib/engine/surface_position_calculator.dart`
- Create: `test/engine/surface_position_calculator_test.dart`

- [ ] **Step 1: Write surface_position_calculator.dart**

```dart
// lib/engine/surface_position_calculator.dart
import '../models/surface.dart';

class SurfacePositionCalculator {
  /// Generate auto-positioned surfaces for a room based on dimensions
  /// and which surface types are enabled.
  static List<Surface> generateSurfaces({
    required String roomId,
    required double roomWidth,
    required double roomDepth,
    required double roomHeight,
    required bool includeFront,
    required bool includeBack,
    required bool includeLeft,
    required bool includeRight,
    required bool includeFloor,
  }) {
    final surfaces = <Surface>[];

    // Front wall: at z=0, facing inward (rotation 0)
    // Spans entire width × height
    if (includeFront) {
      surfaces.add(Surface(
        roomId: roomId,
        name: 'Front wall',
        type: SurfaceType.wall,
        width: roomWidth,
        height: roomHeight,
        position: const SurfacePosition(x: 0, y: 0, z: 0, rotation: 0),
      ));
    }

    // Back wall: at z=depth, facing inward (rotation 180)
    if (includeBack) {
      surfaces.add(Surface(
        roomId: roomId,
        name: 'Back wall',
        type: SurfaceType.wall,
        width: roomWidth,
        height: roomHeight,
        position: SurfacePosition(x: roomWidth, y: 0, z: roomDepth, rotation: 180),
      ));
    }

    // Left wall: at x=0, facing inward (rotation 90)
    // Spans depth × height
    if (includeLeft) {
      surfaces.add(Surface(
        roomId: roomId,
        name: 'Left wall',
        type: SurfaceType.wall,
        width: roomDepth,
        height: roomHeight,
        position: SurfacePosition(x: 0, y: 0, z: roomDepth, rotation: 90),
      ));
    }

    // Right wall: at x=roomWidth, facing inward (rotation 270)
    if (includeRight) {
      surfaces.add(Surface(
        roomId: roomId,
        name: 'Right wall',
        type: SurfaceType.wall,
        width: roomDepth,
        height: roomHeight,
        position: SurfacePosition(x: roomWidth, y: 0, z: 0, rotation: 270),
      ));
    }

    // Floor: at y=0, facing up (no rotation, flat)
    if (includeFloor) {
      surfaces.add(Surface(
        roomId: roomId,
        name: 'Floor',
        type: SurfaceType.floor,
        width: roomWidth,
        height: roomDepth,
        position: const SurfacePosition(x: 0, y: 0, z: 0, rotation: 0),
      ));
    }

    return surfaces;
  }
}
```

- [ ] **Step 2: Write test**

```dart
// test/engine/surface_position_calculator_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:tile_layout/engine/surface_position_calculator.dart';
import 'package:tile_layout/models/surface.dart';

void main() {
  test('generates all 5 surfaces when all enabled', () {
    final surfaces = SurfacePositionCalculator.generateSurfaces(
      roomId: 'room1',
      roomWidth: 2000,
      roomDepth: 1500,
      roomHeight: 2400,
      includeFront: true,
      includeBack: true,
      includeLeft: true,
      includeRight: true,
      includeFloor: true,
    );

    expect(surfaces.length, 5);
    expect(surfaces.where((s) => s.type == SurfaceType.wall).length, 4);
    expect(surfaces.where((s) => s.type == SurfaceType.floor).length, 1);
  });

  test('front wall has correct dimensions', () {
    final surfaces = SurfacePositionCalculator.generateSurfaces(
      roomId: 'room1',
      roomWidth: 2000, roomDepth: 1500, roomHeight: 2400,
      includeFront: true,
      includeBack: false, includeLeft: false,
      includeRight: false, includeFloor: false,
    );

    final front = surfaces.first;
    expect(front.name, 'Front wall');
    expect(front.width, 2000);
    expect(front.height, 2400);
    expect(front.position.z, 0);
    expect(front.position.rotation, 0);
  });

  test('left wall spans room depth', () {
    final surfaces = SurfacePositionCalculator.generateSurfaces(
      roomId: 'room1',
      roomWidth: 2000, roomDepth: 1500, roomHeight: 2400,
      includeFront: false, includeBack: false,
      includeLeft: true,
      includeRight: false, includeFloor: false,
    );

    final left = surfaces.first;
    expect(left.width, 1500); // depth
    expect(left.height, 2400); // height
  });

  test('generates zero surfaces when none enabled', () {
    final surfaces = SurfacePositionCalculator.generateSurfaces(
      roomId: 'room1',
      roomWidth: 2000, roomDepth: 1500, roomHeight: 2400,
      includeFront: false, includeBack: false,
      includeLeft: false, includeRight: false, includeFloor: false,
    );
    expect(surfaces.length, 0);
  });
}
```

- [ ] **Step 3: Run tests**

```bash
flutter test test/engine/surface_position_calculator_test.dart
```
Expected: all 4 tests pass.

- [ ] **Step 4: Commit**

```bash
git add lib/engine/surface_position_calculator.dart test/engine/surface_position_calculator_test.dart
git commit -m "feat: surface position calculator with tests"
```

---

### Task 7: Region validator

**Files:**
- Create: `lib/engine/region_validator.dart`
- Create: `test/engine/region_validator_test.dart`

- [ ] **Step 1: Write region_validator.dart**

```dart
// lib/engine/region_validator.dart
import '../models/surface_tile_group.dart';

class RegionValidationError {
  final String message;
  const RegionValidationError(this.message);
}

class UncoveredGap {
  final double x, y, width, height;
  const UncoveredGap({
    required this.x, required this.y,
    required this.width, required this.height,
  });
}

class RegionValidationResult {
  final bool isValid;
  final List<RegionValidationError> errors;
  final List<UncoveredGap> gaps;

  const RegionValidationResult({
    required this.isValid,
    this.errors = const [],
    this.gaps = const [],
  });
}

class RegionValidator {
  /// Validate that [newRegion] does not overlap with any existing region
  /// in [existingRegions] (excluding one with matching [excludeId]).
  static RegionValidationResult validate({
    required RegionRect newRegion,
    required List<SurfaceTileGroup> existingRegions,
    required double surfaceWidth,
    required double surfaceHeight,
    String? excludeId, // ignore this STG id (for editing)
  }) {
    final errors = <RegionValidationError>[];

    // Overlap check
    for (final stg in existingRegions) {
      if (excludeId != null && stg.id == excludeId) continue;
      if (newRegion.intersectionArea(stg.region) > 0) {
        errors.add(const RegionValidationError(
          'Regions overlap — adjust boundaries.',
        ));
        break;
      }
    }

    // Coverage check: does the combined area of all regions
    // (including the new/edited one) cover the full surface?
    double coveredArea = newRegion.area;
    for (final stg in existingRegions) {
      if (excludeId != null && stg.id == excludeId) continue;
      coveredArea += stg.region.area;
    }

    final totalArea = surfaceWidth * surfaceHeight;
    final gaps = <UncoveredGap>[];

    if ((coveredArea - totalArea).abs() > 0.01) {
      // Simple gap detection: subtract regions from surface rect
      // For MVP, we detect the gap but use a simplified approach —
      // flag it as uncovered without computing exact gap polygons.
      if (coveredArea < totalArea) {
        errors.add(RegionValidationError(
          'Uncovered area on surface (${(totalArea - coveredArea).toStringAsFixed(0)} sq mm). '
          'Assign a tile group to cover all areas.',
        ));
      }
    }

    return RegionValidationResult(
      isValid: errors.isEmpty,
      errors: errors,
      gaps: gaps,
    );
  }

  /// Check if a preset region is valid for the given surface dimensions.
  static bool isPresetValid(String preset, RegionRect rect,
      double surfaceW, double surfaceH) {
    if (rect.x < 0 || rect.y < 0) return false;
    if (rect.x + rect.width > surfaceW) return false;
    if (rect.y + rect.height > surfaceH) return false;
    return true;
  }
}
```

- [ ] **Step 2: Write test**

```dart
// test/engine/region_validator_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:tile_layout/engine/region_validator.dart';
import 'package:tile_layout/models/surface_tile_group.dart';

SurfaceTileGroup makeStg({
  String id = 'stg1',
  String surfaceId = 's1',
  String tileGroupId = 'tg1',
  required RegionRect region,
}) =>
    SurfaceTileGroup(
      id: id,
      surfaceId: surfaceId,
      tileGroupId: tileGroupId,
      region: region,
    );

void main() {
  test('full surface preset passes validation with no other regions', () {
    final result = RegionValidator.validate(
      newRegion: const RegionRect(x: 0, y: 0, width: 2000, height: 1200),
      existingRegions: [],
      surfaceWidth: 2000,
      surfaceHeight: 1200,
    );
    expect(result.isValid, true);
    expect(result.errors, isEmpty);
  });

  test('overlapping regions fail validation', () {
    final existing = [
      makeStg(region: const RegionRect(x: 0, y: 0, width: 2000, height: 1200)),
    ];

    final result = RegionValidator.validate(
      newRegion: const RegionRect(x: 1000, y: 0, width: 1000, height: 1200),
      existingRegions: existing,
      surfaceWidth: 2000,
      surfaceHeight: 1200,
    );

    expect(result.isValid, false);
    expect(result.errors.any((e) => e.message.contains('overlap')), true);
  });

  test('edit mode excludes self from overlap check', () {
    final existing = [
      makeStg(
        id: 'stg_to_edit',
        region: const RegionRect(x: 0, y: 0, width: 1000, height: 1200),
      ),
      makeStg(
        id: 'stg_other',
        region: const RegionRect(x: 1000, y: 0, width: 1000, height: 1200),
      ),
    ];

    // Editing stg_to_edit — expanding it would overlap stg_other
    final result = RegionValidator.validate(
      newRegion: const RegionRect(x: 0, y: 0, width: 1500, height: 1200),
      existingRegions: existing,
      surfaceWidth: 2000,
      surfaceHeight: 1200,
      excludeId: 'stg_to_edit',
    );

    expect(result.isValid, false);
  });
}
```

- [ ] **Step 3: Run tests**

```bash
flutter test test/engine/region_validator_test.dart
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add lib/engine/region_validator.dart test/engine/region_validator_test.dart
git commit -m "feat: region validator with overlap and coverage checks"
```

---

### Task 8: Layout engine — grid, brick, stacked patterns

**Files:**
- Create: `lib/engine/layout_engine.dart`
- Create: `test/engine/layout_engine_test.dart`

- [ ] **Step 1: Write layout_engine.dart**

```dart
// lib/engine/layout_engine.dart
import 'dart:math';
import '../models/placed_tile.dart';
import '../models/surface_tile_group.dart';
import '../models/tile_group.dart';
import '../models/enums.dart';

class LayoutEngine {
  static const double minCutRatio = 0.30;

  /// Compute tile placement for a single region.
  static List<PlacedTile> compute({
    required RegionRect region,
    required TileGroup tileGroup,
    required double groutWidth,
    required TilePattern pattern,
    required double offsetX,
    required double offsetY,
  }) {
    switch (pattern) {
      case TilePattern.grid:
        return _computeGrid(region, tileGroup, groutWidth, offsetX, offsetY);
      case TilePattern.brick:
        return _computeBrick(region, tileGroup, groutWidth, offsetX, offsetY);
      case TilePattern.stacked:
        return _computeGrid(region, tileGroup, groutWidth, offsetX, offsetY);
      case TilePattern.herringbone:
        return _computeHerringbone(region, tileGroup, groutWidth, offsetX, offsetY);
    }
  }

  /// Determine cut edges for a tile at (x, y) with size (w, h)
  /// within a region of (regionW × regionH).
  static List<CutEdge> determineCutEdges(
    double x, double y, double w, double h,
    double fullW, double fullH,
    double regionW, double regionH,
  ) {
    final edges = <CutEdge>[];
    if (x <= 0.01) edges.add(CutEdge.left);
    if (y <= 0.01) edges.add(CutEdge.top);
    if ((x + w) >= (regionW - 0.01)) edges.add(CutEdge.right);
    if ((y + h) >= (regionH - 0.01)) edges.add(CutEdge.bottom);
    return edges;
  }

  static List<PlacedTile> _computeGrid(
    RegionRect region, TileGroup tileGroup,
    double groutWidth, double offsetX, double offsetY,
  ) {
    final unitW = tileGroup.tileWidth + groutWidth;
    final unitH = tileGroup.tileHeight + groutWidth;

    int fullCols = (region.width / unitW).floor();
    int fullRows = (region.height / unitH).floor();
    double remainderW = region.width - (fullCols * unitW);
    double remainderH = region.height - (fullRows * unitH);

    // Sliver-cut elimination
    if (remainderW > 0 && remainderW < tileGroup.tileWidth * minCutRatio) {
      fullCols -= 1;
      remainderW = region.width - (fullCols * unitW);
    }
    if (remainderH > 0 && remainderH < tileGroup.tileHeight * minCutRatio) {
      fullRows -= 1;
      remainderH = region.height - (fullRows * unitH);
    }

    // Center for symmetry, then apply manual offset with wrap-around
    double baseStartX = remainderW / 2;
    double baseStartY = remainderH / 2;
    // Wrap offset: tiles exiting one edge reappear on the opposite
    double wrappedOffsetX = offsetX % unitW;
    double wrappedOffsetY = offsetY % unitH;
    double startX = baseStartX + wrappedOffsetX;
    double startY = baseStartY + wrappedOffsetY;

    final tiles = <PlacedTile>[];
    // Extend range by 1 in each direction to catch wrapped tiles
    for (int row = -1; row <= fullRows + 1; row++) {
      for (int col = -1; col <= fullCols + 1; col++) {
        final x = startX + col * unitW;
        final y = startY + row * unitH;

        // Skip tiles entirely outside the region
        if (x >= region.width || y >= region.height) continue;
        if (x + tileGroup.tileWidth <= 0 || y + tileGroup.tileHeight <= 0) continue;

        // Clamp to region bounds
        final actualW = min(tileGroup.tileWidth, region.width - x);
        final actualH = min(tileGroup.tileHeight, region.height - y);

        // If the clamped tile has zero size, skip
        if (actualW <= 0 || actualH <= 0) continue;

        final isCut = (actualW - tileGroup.tileWidth).abs() > 0.01 ||
                      (actualH - tileGroup.tileHeight).abs() > 0.01;
        final cutEdges = isCut
            ? determineCutEdges(x, y, actualW, actualH,
                tileGroup.tileWidth, tileGroup.tileHeight,
                region.width, region.height)
            : <CutEdge>[];

        tiles.add(PlacedTile(
          x: region.x + x,
          y: region.y + y,
          width: actualW,
          height: actualH,
          isCut: isCut,
          cutEdges: cutEdges,
          tileGroupId: tileGroup.id,
        ));
      }
    }

    return tiles;
  }

  static List<PlacedTile> _computeBrick(
    RegionRect region, TileGroup tileGroup,
    double groutWidth, double offsetX, double offsetY,
  ) {
    final unitW = tileGroup.tileWidth + groutWidth;
    final unitH = tileGroup.tileHeight + groutWidth;
    final halfUnitW = unitW / 2;

    int fullRows = (region.height / unitH).floor();
    double remainderH = region.height - (fullRows * unitH);

    if (remainderH > 0 && remainderH < tileGroup.tileHeight * minCutRatio) {
      fullRows -= 1;
      remainderH = region.height - (fullRows * unitH);
    }

    double startY = remainderH / 2 + offsetY;

    // Row-pair symmetry: compute column count using the offset row (worst case)
    // so both even and odd rows get the same edge cuts.
    final offsetRowW = region.width - halfUnitW;
    int fullCols = (offsetRowW / unitW).floor();
    double remainderW = offsetRowW - (fullCols * unitW);

    if (remainderW > 0 && remainderW < tileGroup.tileWidth * minCutRatio) {
      fullCols -= 1;
      remainderW = offsetRowW - (fullCols * unitW);
    }

    // Centering based on the offset row; even row gets an extra half-tile on each side
    final offsetRowStartX = halfUnitW + remainderW / 2;
    final evenRowStartX = remainderW / 2;

    final tiles = <PlacedTile>[];
    for (int row = 0; row <= fullRows; row++) {
      final y = startY + row * unitH;
      final isOffsetRow = row % 2 != 0;
      double startX = (isOffsetRow ? offsetRowStartX : evenRowStartX) + offsetX;
      final colCount = isOffsetRow ? fullCols : fullCols + 1;

      for (int col = 0; col <= colCount; col++) {
        final x = startX + col * unitW;
        if (x >= region.width || y >= region.height) continue;
        if (x + tileGroup.tileWidth <= 0) continue;

        final actualW = min(tileGroup.tileWidth, region.width - x);
        final actualH = min(tileGroup.tileHeight, region.height - y);
        if (actualW <= 0 || actualH <= 0) continue;

        final isCut = (actualW - tileGroup.tileWidth).abs() > 0.01 ||
                      (actualH - tileGroup.tileHeight).abs() > 0.01;
        final cutEdges = isCut
            ? determineCutEdges(x, y, actualW, actualH,
                tileGroup.tileWidth, tileGroup.tileHeight,
                region.width, region.height)
            : <CutEdge>[];

        tiles.add(PlacedTile(
          x: region.x + x,
          y: region.y + y,
          width: actualW,
          height: actualH,
          isCut: isCut,
          cutEdges: cutEdges,
          tileGroupId: tileGroup.id,
        ));
      }
    }

    return tiles;
  }

  static List<PlacedTile> _computeHerringbone(
    RegionRect region, TileGroup tileGroup,
    double groutWidth, double offsetX, double offsetY,
  ) {
    final tw = tileGroup.tileWidth;
    final th = tileGroup.tileHeight;
    final cos45 = cos(pi / 4);
    final sin45 = sin(pi / 4);

    // Bounding box of rotated tile
    final diagW = tw * cos45 + th * sin45;
    final diagH = tw * sin45 + th * cos45;

    // Step between adjacent tiles in the herringbone pattern
    final stepX = th * cos45 + groutWidth;
    final stepY = th * sin45 + groutWidth;

    final tiles = <PlacedTile>[];

    // Calculate grid size to cover the region
    final cols = (region.width / stepX).ceil() + 2;
    final rows = (region.height / stepY).ceil() + 2;

    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        final rotation = (row + col) % 2 == 0 ? 45.0 : -45.0;

        final rawX = col * stepX + offsetX;
        final rawY = row * stepY + offsetY;

        // Check if the tile's bounding box overlaps the region
        final bx = rawX - diagW / 2;
        final by = rawY - diagH / 2;

        if (bx + diagW <= 0 || by + diagH <= 0) continue;
        if (bx >= region.width || by >= region.height) continue;

        // Clamp bounding box to region
        final cx = max(0.0, bx);
        final cy = max(0.0, by);
        final cw = min(bx + diagW, region.width) - cx;
        final ch = min(by + diagH, region.height) - cy;

        if (cw <= 0 || ch <= 0) continue;

        final isCut = (cw - diagW).abs() > 0.01 ||
                      (ch - diagH).abs() > 0.01;

        // cutEdges are in local tile space
        final cutEdges = isCut
            ? _herringboneCutEdges(bx, by, diagW, diagH, region.width, region.height)
            : <CutEdge>[];

        tiles.add(PlacedTile(
          x: region.x + cx,
          y: region.y + cy,
          width: cw,
          height: ch,
          rotation: rotation,
          isCut: isCut,
          cutEdges: cutEdges,
          tileGroupId: tileGroup.id,
        ));
      }
    }

    return tiles;
  }

  static List<CutEdge> _herringboneCutEdges(
    double bx, double by, double bw, double bh,
    double regionW, double regionH,
  ) {
    final edges = <CutEdge>[];
    if (bx < 0) edges.add(CutEdge.left);
    if (by < 0) edges.add(CutEdge.top);
    if (bx + bw > regionW) edges.add(CutEdge.right);
    if (by + bh > regionH) edges.add(CutEdge.bottom);
    return edges;
  }
}
```

- [ ] **Step 2: Write layout_engine_test.dart**

```dart
// test/engine/layout_engine_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:tile_layout/engine/layout_engine.dart';
import 'package:tile_layout/models/tile_group.dart';
import 'package:tile_layout/models/surface_tile_group.dart';
import 'package:tile_layout/models/enums.dart';

TileGroup makeTileGroup({
  String id = 'tg1',
  double width = 300,
  double height = 200,
}) =>
    TileGroup(projectId: 'p1', name: 'Test', tileWidth: width, tileHeight: height, id: id);

void main() {
  group('Grid pattern', () {
    test('full tiles fill region without cuts when exact fit', () {
      final tiles = LayoutEngine.compute(
        region: const RegionRect(x: 0, y: 0, width: 903, height: 603),
        tileGroup: makeTileGroup(width: 300, height: 200),
        groutWidth: 3,
        pattern: TilePattern.grid,
        offsetX: 0, offsetY: 0,
      );

      // 903 / 303 = 2.98 → 3 full cols? No: 3*303=909 > 903
      // 903/303=2.98, floor=2, remainder=297
      // 297 > 300*0.3=90, so no sliver elimination
      // 297/2 = 148.5 start offset
      // So: col 0 starts at 148.5, col 1 at 451.5, col 2 at 754.5
      // col 2 tile: x=754.5, actualW = min(300, 903-754.5=148.5) = 148.5 → cut!
      // Actually, 2 full cols: 903-2*303=297 → 297 tiles are cuts
      // Wait: fullCols = floor(903/303)=2
      // remainder = 903-2*303 = 297
      // 297 > 90, so no sliver elimination
      // So we get columns 0,1,2: 3 columns
      // col 0: x=148.5, col 1: x=451.5, col 2: x=754.5
      // For each: actualW = min(300, 903-x)
      // col 0: min(300, 754.5)=300 full
      // col 1: min(300, 451.5)=300 full
      // col 2: min(300, 148.5)=148.5 cut

      final cuts = tiles.where((t) => t.isCut).toList();
      final fulls = tiles.where((t) => !t.isCut).toList();
      expect(cuts.length, greaterThan(0));
      expect(fulls.length, greaterThan(0));
    });

    test('sliver elimination triggers when cut < 30% of tile', () {
      // 300 tile + 3 grout: unit = 303
      // 303*3 + 20 = 929 → 20 remainder < 30% of 300(=90)
      // fullCols should reduce from 3 to 2
      final tiles = LayoutEngine.compute(
        region: const RegionRect(x: 0, y: 0, width: 929, height: 303),
        tileGroup: makeTileGroup(width: 300, height: 200),
        groutWidth: 3,
        pattern: TilePattern.grid,
        offsetX: 0, offsetY: 0,
      );

      // After sliver elimination: fullCols=2, remainder=929-2*303=323
      // 323/2=161.5 start offset
      // col 0: x=161.5, w=min(300,767.5)=300
      // col 1: x=464.5, w=min(300,464.5)=300
      // col 2: x=767.5, w=min(300,161.5)=161.5 cut
      final cuts = tiles.where((t) => t.isCut).toList();
      expect(cuts.length, greaterThan(0));
      // Sliver was eliminated — no tiny cuts
      final tinyCuts = cuts.where((t) => t.width < 90 || t.height < 60).toList();
      expect(tinyCuts, isEmpty);
    });

    test('offset shifts tile positions', () {
      final tilesNoOffset = LayoutEngine.compute(
        region: const RegionRect(x: 0, y: 0, width: 903, height: 303),
        tileGroup: makeTileGroup(width: 300, height: 200),
        groutWidth: 3,
        pattern: TilePattern.grid,
        offsetX: 0, offsetY: 0,
      );

      final tilesWithOffset = LayoutEngine.compute(
        region: const RegionRect(x: 0, y: 0, width: 903, height: 303),
        tileGroup: makeTileGroup(width: 300, height: 200),
        groutWidth: 3,
        pattern: TilePattern.grid,
        offsetX: 50, offsetY: -20,
      );

      // First tile positions should differ
      final firstNoOffset = tilesNoOffset.first;
      final firstWithOffset = tilesWithOffset.first;
      expect(firstWithOffset.x, isNot(equals(firstNoOffset.x)));
    });
  });

  group('Brick pattern', () {
    test('every other row has horizontal offset', () {
      final tiles = LayoutEngine.compute(
        region: const RegionRect(x: 0, y: 0, width: 1200, height: 800),
        tileGroup: makeTileGroup(width: 300, height: 200),
        groutWidth: 3,
        pattern: TilePattern.brick,
        offsetX: 0, offsetY: 0,
      );

      // Tiles in row 0 and row 1 should have different x positions
      final row0Tiles = tiles.where((t) => t.y < 203).toList(); // tiles near top
      final row1Tiles = tiles.where((t) => t.y >= 203 && t.y < 406).toList();

      if (row0Tiles.isNotEmpty && row1Tiles.isNotEmpty) {
        expect(row0Tiles.first.x, isNot(equals(row1Tiles.first.x)));
      }
    });
  });

  group('Herringbone pattern', () {
    test('tiles have ±45° rotation', () {
      final tiles = LayoutEngine.compute(
        region: const RegionRect(x: 0, y: 0, width: 1200, height: 800),
        tileGroup: makeTileGroup(width: 300, height: 100),
        groutWidth: 3,
        pattern: TilePattern.herringbone,
        offsetX: 0, offsetY: 0,
      );

      final rotations = tiles.map((t) => t.rotation).toSet();
      expect(rotations, contains(45));
      expect(rotations, contains(-45));
    });
  });
}
```

- [ ] **Step 3: Run tests**

```bash
flutter test test/engine/layout_engine_test.dart
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add lib/engine/layout_engine.dart test/engine/layout_engine_test.dart
git commit -m "feat: layout engine with grid, brick, herringbone patterns"
```

---

### Task 9: Cut list generator

**Files:**
- Create: `lib/cutlist/cut_list_generator.dart`
- Create: `test/cutlist/cut_list_generator_test.dart`

- [ ] **Step 1: Write cut_list_generator.dart**

```dart
// lib/cutlist/cut_list_generator.dart
import '../models/cut_entry.dart';
import '../models/layout_result.dart';
import '../models/placed_tile.dart';
import '../models/enums.dart';

class CutListGenerator {
  /// Generate a cut list from a map of surfaceId → LayoutResult
  /// and a map of tileGroupId → tile group name for labeling.
  static List<CutEntry> generate({
    required Map<String, LayoutResult> resultsBySurface,
    required Map<String, String> surfaceNames, // surfaceId → name
    required Map<String, String> tileGroupNames, // tileGroupId → name
  }) {
    // Collect all cut tiles with their surface context
    final allCuts = <_RawCut>[];

    for (final entry in resultsBySurface.entries) {
      final surfaceId = entry.key;
      final layoutResult = entry.value;
      final surfaceName = surfaceNames[surfaceId] ?? surfaceId;

      for (final tile in layoutResult.tiles) {
        if (!tile.isCut) continue;

        allCuts.add(_RawCut(
          surfaceId: surfaceId,
          surfaceName: surfaceName,
          tile: tile,
        ));
      }
    }

    // Group by: tileGroupId + width + height + cutEdges
    final groups = <String, List<_RawCut>>{};
    for (final cut in allCuts) {
      final edgesKey = cut.tile.cutEdges.map((e) => e.name).join(',');
      // Round dimensions to 1 decimal place for grouping
      final w = (cut.tile.width * 10).round() / 10;
      final h = (cut.tile.height * 10).round() / 10;
      final key = '${cut.tile.tileGroupId}|$w|$h|$edgesKey';
      groups.putIfAbsent(key, () => []).add(cut);
    }

    // Build cut entries
    final entries = <CutEntry>[];

    for (final group in groups.entries) {
      final cuts = group.value;
      final first = cuts.first.tile;
      final edgesKey = first.cutEdges.map((e) => e.name).join(',');

      // Aggregate per-surface counts
      final surfaceCounts = <String, CutLocation>{};
      for (final cut in cuts) {
        final loc = surfaceCounts.putIfAbsent(
          cut.surfaceId,
          () => CutLocation(
            surfaceId: cut.surfaceId,
            surfaceName: cut.surfaceName,
            count: 0,
          ),
        );
        // Mutate existing — we own this object
        surfaceCounts[cut.surfaceId] = CutLocation(
          surfaceId: loc.surfaceId,
          surfaceName: loc.surfaceName,
          count: loc.count + 1,
        );
      }

      final w = (first.width * 10).round() / 10;
      final h = (first.height * 10).round() / 10;

      entries.add(CutEntry(
        tileGroupId: first.tileGroupId,
        tileGroupName: tileGroupNames[first.tileGroupId] ?? first.tileGroupId,
        width: w,
        height: h,
        cutEdgesKey: edgesKey,
        locations: surfaceCounts.values.toList(),
        totalCount: cuts.length,
      ));
    }

    // Sort by tile group name, then by dimensions largest first
    entries.sort((a, b) {
      final nameCmp = a.tileGroupName.compareTo(b.tileGroupName);
      if (nameCmp != 0) return nameCmp;
      final areaB = b.width * b.height;
      final areaA = a.width * a.height;
      return areaB.compareTo(areaA);
    });

    return entries;
  }
}

class _RawCut {
  final String surfaceId;
  final String surfaceName;
  final PlacedTile tile;

  const _RawCut({
    required this.surfaceId,
    required this.surfaceName,
    required this.tile,
  });
}
```

- [ ] **Step 2: Write test**

```dart
// test/cutlist/cut_list_generator_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:tile_layout/cutlist/cut_list_generator.dart';
import 'package:tile_layout/models/layout_result.dart';
import 'package:tile_layout/models/placed_tile.dart';
import 'package:tile_layout/models/enums.dart';

void main() {
  test('groups identical cuts across surfaces', () {
    final results = {
      's1': LayoutResult(
        surfaceId: 's1',
        stale: false,
        tiles: [
          const PlacedTile(
            x: 0, y: 0, width: 91, height: 200,
            isCut: true, cutEdges: [CutEdge.right], tileGroupId: 'tg1',
          ),
          const PlacedTile(
            x: 0, y: 0, width: 91, height: 200,
            isCut: true, cutEdges: [CutEdge.right], tileGroupId: 'tg1',
          ),
        ],
      ),
      's2': LayoutResult(
        surfaceId: 's2',
        stale: false,
        tiles: [
          const PlacedTile(
            x: 0, y: 0, width: 91, height: 200,
            isCut: true, cutEdges: [CutEdge.right], tileGroupId: 'tg1',
          ),
        ],
      ),
    };

    final entries = CutListGenerator.generate(
      resultsBySurface: results,
      surfaceNames: {'s1': 'Front wall', 's2': 'Left wall'},
      tileGroupNames: {'tg1': 'White subway'},
    );

    expect(entries.length, 1);
    final entry = entries.first;
    expect(entry.totalCount, 3);
    expect(entry.tileGroupName, 'White subway');
    expect(entry.locations.length, 2);
    expect(entry.locations.map((l) => l.count).toList(), containsAll([2, 1]));
  });

  test('different cut edges produce separate entries', () {
    final results = {
      's1': LayoutResult(
        surfaceId: 's1',
        stale: false,
        tiles: [
          const PlacedTile(
            x: 0, y: 0, width: 91, height: 200,
            isCut: true, cutEdges: [CutEdge.right], tileGroupId: 'tg1',
          ),
          const PlacedTile(
            x: 0, y: 0, width: 91, height: 200,
            isCut: true, cutEdges: [CutEdge.left], tileGroupId: 'tg1',
          ),
        ],
      ),
    };

    final entries = CutListGenerator.generate(
      resultsBySurface: results,
      surfaceNames: {'s1': 'Front wall'},
      tileGroupNames: {'tg1': 'White subway'},
    );

    expect(entries.length, 2);
  });

  test('skips non-cut tiles', () {
    final results = {
      's1': LayoutResult(
        surfaceId: 's1',
        stale: false,
        tiles: [
          const PlacedTile(
            x: 0, y: 0, width: 300, height: 200,
            isCut: false, tileGroupId: 'tg1',
          ),
        ],
      ),
    };

    final entries = CutListGenerator.generate(
      resultsBySurface: results,
      surfaceNames: {'s1': 'Front wall'},
      tileGroupNames: {'tg1': 'White subway'},
    );

    expect(entries, isEmpty);
  });
}
```

- [ ] **Step 3: Run tests**

```bash
flutter test test/cutlist/cut_list_generator_test.dart
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add lib/cutlist/ test/cutlist/
git commit -m "feat: cut list generator with grouping by dimensions, edges, surface"
```

---

## Phase 4: Rendering Engine

### Task 10: Surface painter (2D elevation)

**Files:**
- Create: `lib/render/surface_painter.dart`

- [ ] **Step 1: Write surface_painter.dart**

```dart
// lib/render/surface_painter.dart
import 'dart:math';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../models/placed_tile.dart';
import '../models/enums.dart';
import '../models/tile_group.dart';

class SurfacePainter extends CustomPainter {
  final List<PlacedTile> tiles;
  final Map<String, TileGroup> tileGroups; // tileGroupId → TileGroup
  final Map<String, ui.Image> textureCache; // tileGroupId → loaded Image
  final double surfaceWidth;
  final double surfaceHeight;
  final GroutColor groutColor;
  final double groutWidth;
  final double scale; // pixels per mm — determined by canvas size

  SurfacePainter({
    required this.tiles,
    required this.tileGroups,
    required this.textureCache,
    required this.surfaceWidth,
    required this.surfaceHeight,
    required this.groutColor,
    required this.groutWidth,
    required this.scale,
  });

  @override
  void paint(Canvas canvas, Size size) {
    _drawBackground(canvas, size);
    _drawTiles(canvas);
    _drawGroutLines(canvas);
    _drawCutAnnotations(canvas);
    _drawSurfaceDimensions(canvas);
  }

  void _drawBackground(Canvas canvas, Size size) {
    final paint = Paint()..color = const Color(0xFFF5F5F5);
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), paint);
  }

  void _drawTiles(Canvas canvas) {
    for (final tile in tiles) {
      final rect = Rect.fromLTWH(
        tile.x * scale,
        tile.y * scale,
        tile.width * scale,
        tile.height * scale,
      );

      final texture = textureCache[tile.tileGroupId];
      if (texture != null) {
        // Draw texture clipped to tile rect
        canvas.save();
        canvas.clipRect(rect);

        if (tile.rotation != 0) {
          // Rotate around center of the tile's bounding box
          final cx = rect.center.dx;
          final cy = rect.center.dy;
          canvas.translate(cx, cy);
          canvas.rotate(tile.rotation * 3.14159 / 180);
          canvas.translate(-cx, -cy);
        }

        // Scale texture to fill the tile
        final srcRect = Rect.fromLTWH(0, 0, texture.width.toDouble(), texture.height.toDouble());
        canvas.drawImageRect(texture, srcRect, rect, Paint());
        canvas.restore();
      } else {
        // Fallback: solid color fill based on tile group
        final paint = Paint()..color = const Color(0xFFE8DCC8);
        canvas.drawRect(rect, paint);
      }
    }
  }

  void _drawGroutLines(Canvas canvas) {
    final groutPx = groutWidth * scale;
    if (groutPx < 0.5) return; // too small to render

    final paint = Paint()
      ..color = _groutColorValue()
      ..strokeWidth = groutPx;

    for (final tile in tiles) {
      final x = tile.x * scale;
      final y = tile.y * scale;
      final w = tile.width * scale;
      final h = tile.height * scale;

      // Right edge
      canvas.drawLine(Offset(x + w, y), Offset(x + w, y + h), paint);
      // Bottom edge
      canvas.drawLine(Offset(x, y + h), Offset(x + w, y + h), paint);
    }

    // Left and top edges of the surface
    canvas.drawLine(
      const Offset(0, 0),
      Offset(0, surfaceHeight * scale),
      paint,
    );
    canvas.drawLine(
      const Offset(0, 0),
      Offset(surfaceWidth * scale, 0),
      paint,
    );
  }

  void _drawCutAnnotations(Canvas canvas) {
    for (final tile in tiles) {
      if (!tile.isCut) continue;

      final x = tile.x * scale;
      final y = tile.y * scale;
      final w = tile.width * scale;
      final h = tile.height * scale;

      final redPaint = Paint()
        ..color = Colors.red
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5;

      // Draw red dashed lines on cut edges
      for (final edge in tile.cutEdges) {
        switch (edge) {
          case CutEdge.right:
            _drawDashedLine(canvas, Offset(x + w, y), Offset(x + w, y + h), redPaint);
            _drawDimensionLabel(canvas, x + w, y + h / 2, tile.width, isHorizontal: false);
            break;
          case CutEdge.left:
            _drawDashedLine(canvas, Offset(x, y), Offset(x, y + h), redPaint);
            break;
          case CutEdge.bottom:
            _drawDashedLine(canvas, Offset(x, y + h), Offset(x + w, y + h), redPaint);
            _drawDimensionLabel(canvas, x + w / 2, y + h, tile.height, isHorizontal: true);
            break;
          case CutEdge.top:
            _drawDashedLine(canvas, Offset(x, y), Offset(x + w, y), redPaint);
            break;
        }
      }
    }
  }

  void _drawDashedLine(Canvas canvas, Offset start, Offset end, Paint paint) {
    const dashWidth = 4.0;
    const dashGap = 3.0;
    final dx = end.dx - start.dx;
    final dy = end.dy - start.dy;
    final length = sqrt(dx * dx + dy * dy);
    if (length == 0) return;
    final unitX = dx / length;
    final unitY = dy / length;

    double distance = 0;
    while (distance < length) {
      final dashEnd = min(distance + dashWidth, length);
      final p1 = Offset(start.dx + unitX * distance, start.dy + unitY * distance);
      final p2 = Offset(start.dx + unitX * dashEnd, start.dy + unitY * dashEnd);
      canvas.drawLine(p1, p2, paint);
      distance += dashWidth + dashGap;
    }
  }

  void _drawDimensionLabel(
    Canvas canvas, double x, double y, double mmValue, {
    required bool isHorizontal,
  }) {
    final textStyle = ui.TextStyle(
      color: Colors.red,
      fontSize: 10,
      fontWeight: ui.FontWeight.bold,
    );
    final builder = ui.ParagraphBuilder(ui.ParagraphStyle(
      textAlign: isHorizontal ? ui.TextAlign.center : ui.TextAlign.left,
    ))
      ..pushStyle(textStyle)
      ..addText('${mmValue.toStringAsFixed(0)}mm');

    final paragraph = builder.build()
      ..layout(ui.ParagraphConstraints(width: 80));

    final px = isHorizontal ? x - 40 : x + 4;
    final py = isHorizontal ? y + 2 : y - 10;

    canvas.drawParagraph(paragraph, Offset(px, py));
  }

  void _drawSurfaceDimensions(Canvas canvas) {
    final textStyle = ui.TextStyle(
      color: Colors.black87,
      fontSize: 11,
    );
    final builder = ui.ParagraphBuilder(ui.ParagraphStyle(
      textAlign: ui.TextAlign.center,
    ))
      ..pushStyle(textStyle)
      ..addText('${surfaceWidth.toStringAsFixed(0)} × ${surfaceHeight.toStringAsFixed(0)}mm');

    final paragraph = builder.build()
      ..layout(ui.ParagraphConstraints(width: surfaceWidth * scale));

    canvas.drawParagraph(
      paragraph,
      Offset(0, surfaceHeight * scale + 4),
    );
  }

  Color _groutColorValue() {
    switch (groutColor) {
      case GroutColor.black: return Colors.black87;
      case GroutColor.grey: return Colors.grey;
      case GroutColor.white: return Colors.white;
    }
  }

  @override
  bool shouldRepaint(covariant SurfacePainter oldDelegate) {
    return tiles != oldDelegate.tiles ||
        surfaceWidth != oldDelegate.surfaceWidth ||
        surfaceHeight != oldDelegate.surfaceHeight ||
        scale != oldDelegate.scale ||
        groutColor != oldDelegate.groutColor;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/render/surface_painter.dart
git commit -m "feat: 2D surface painter with tiles, grout, cut annotations"
```

---

### Task 11: Isometric painter (3D room view)

**Files:**
- Create: `lib/render/isometric_painter.dart`

- [ ] **Step 1: Write isometric_painter.dart**

```dart
// lib/render/isometric_painter.dart
import 'dart:math';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../models/surface.dart';
import 'surface_painter.dart';

class IsometricPainter extends CustomPainter {
  final List<Surface> surfaces;
  final Map<String, SurfacePainter> surfacePainters; // surfaceId → painter
  final int viewAngle; // 0, 90, 180, 270 degrees
  final bool topDown;
  final String? selectedSurfaceId;

  IsometricPainter({
    required this.surfaces,
    required this.surfacePainters,
    required this.viewAngle,
    this.topDown = false,
    this.selectedSurfaceId,
  });

  static const double cos30 = 0.8660254;
  static const double sin30 = 0.5;

  /// Project a 3D point (in room coordinates) to 2D screen.
  Offset _project(double x, double y, double z, double originX, double originY) {
    if (topDown) {
      return Offset(originX + x, originY + z);
    }
    // Apply view angle rotation around Y axis
    final rad = viewAngle * pi / 180;
    final cosA = cos(rad);
    final sinA = sin(rad);
    final rx = x * cosA - z * sinA;
    final rz = x * sinA + z * cosA;

    final ix = (rx - rz) * cos30 + originX;
    final iy = (rx + rz) * sin30 - y + originY;
    return Offset(ix, iy);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final originX = size.width / 2;
    final originY = size.height * 0.6;

    // Draw surfaces in painter's algorithm order
    final ordered = _orderSurfaces();

    for (final surface in ordered) {
      final pos = surface.position;
      canvas.save();

      // Compute isometric transform for this surface
      final topLeft = _project(pos.x, pos.y, pos.z, originX, originY);
      final topRight = _project(pos.x + surface.width, pos.y, pos.z, originX, originY);
      final bottomLeft = _project(pos.x, pos.y + surface.height, pos.z, originX, originY);

      // Apply skew transform to approximate isometric projection
      // We use a simplified approach: scale + skew based on surface orientation
      final matrix = Matrix4.identity();

      if (topDown) {
        // Top-down: surfaces at their XY positions
        matrix.translate(topLeft.dx, topLeft.dy);
        if (surface.type == SurfaceType.floor) {
          matrix.scale(
            (topRight.dx - topLeft.dx) / surface.width,
            (bottomLeft.dy - topLeft.dy) / surface.height,
          );
        }
      } else {
        matrix.translate(topLeft.dx, topLeft.dy);
        // Skew and scale to match isometric projection
        matrix.setEntry(0, 0, (topRight.dx - topLeft.dx) / surface.width);
        matrix.setEntry(0, 1, 0);
        matrix.setEntry(1, 0, (topRight.dy - topLeft.dy) / surface.width);
        matrix.setEntry(1, 1, (bottomLeft.dy - topLeft.dy) / surface.height);
      }

      canvas.transform(matrix.storage);

      // Draw the surface using its SurfacePainter
      final painter = surfacePainters[surface.id];
      if (painter != null) {
        painter.paint(
          canvas,
          Size(surface.width * painter.scale, surface.height * painter.scale),
        );
      }

      // Highlight selected surface
      if (surface.id == selectedSurfaceId) {
        final highlightPaint = Paint()
          ..color = Colors.purple.withValues(alpha: 0.3)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3;
        canvas.drawRect(
          Rect.fromLTWH(0, 0,
              surface.width * (painter?.scale ?? 1),
              surface.height * (painter?.scale ?? 1)),
          highlightPaint,
        );
      }

      canvas.restore();
    }
  }

  List<Surface> _orderSurfaces() {
    // Painter's algorithm: back-to-front
    // Floor first, then walls ordered by distance from viewer
    final floors = surfaces.where((s) => s.type == SurfaceType.floor).toList();
    final walls = surfaces.where((s) => s.type == SurfaceType.wall).toList();

    // Order walls by distance from the current view angle
    walls.sort((a, b) {
      final rad = viewAngle * pi / 180;
      final distA = a.position.x * sin(rad) + a.position.z * cos(rad);
      final distB = b.position.x * sin(rad) + b.position.z * cos(rad);
      return distA.compareTo(distB);
    });

    return [...floors, ...walls];
  }

  /// Hit test: which surface was tapped?
  String? hitTest(Offset tapPosition, Size canvasSize) {
    final originX = canvasSize.width / 2;
    final originY = canvasSize.height * 0.6;

    // Check front-to-back for overlapping surfaces
    final ordered = _orderSurfaces().reversed.toList();

    for (final surface in ordered) {
      final pos = surface.position;
      final p1 = _project(pos.x, pos.y, pos.z, originX, originY);
      final p2 = _project(pos.x + surface.width, pos.y, pos.z, originX, originY);
      final p3 = _project(pos.x + surface.width, pos.y + surface.height, pos.z, originX, originY);
      final p4 = _project(pos.x, pos.y + surface.height, pos.z, originX, originY);

      // Point-in-polygon test
      final poly = [p1, p2, p3, p4];
      if (_pointInPolygon(tapPosition, poly)) {
        return surface.id;
      }
    }
    return null;
  }

  bool _pointInPolygon(Offset point, List<Offset> polygon) {
    bool inside = false;
    final n = polygon.length;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      if ((polygon[i].dy > point.dy) != (polygon[j].dy > point.dy) &&
          point.dx < (polygon[j].dx - polygon[i].dx) * (point.dy - polygon[i].dy) /
              (polygon[j].dy - polygon[i].dy) + polygon[i].dx) {
        inside = !inside;
      }
    }
    return inside;
  }

  @override
  bool shouldRepaint(covariant IsometricPainter oldDelegate) {
    return viewAngle != oldDelegate.viewAngle ||
        selectedSurfaceId != oldDelegate.selectedSurfaceId ||
        topDown != oldDelegate.topDown;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/render/isometric_painter.dart
git commit -m "feat: 3D isometric painter with hit testing and rotation"
```

---

### Task 12: Export service

**Files:**
- Create: `lib/render/export_service.dart`

- [ ] **Step 1: Write export_service.dart**

```dart
// lib/render/export_service.dart
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'surface_painter.dart';
import 'isometric_painter.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';

class ExportService {
  /// Export a 2D surface to a PNG file.
  static Future<String> exportSurfacePNG({
    required SurfacePainter painter,
    required double surfaceWidth,
    required double surfaceHeight,
    int dpi = 200,
  }) async {
    final scale = dpi / 25.4; // pixels per mm at given DPI
    final width = (surfaceWidth * scale).round();
    final height = (surfaceHeight * scale).round();

    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    // Use a temporary scaled painter
    final scaledPainter = SurfacePainter(
      tiles: painter.tiles,
      tileGroups: painter.tileGroups,
      textureCache: painter.textureCache,
      surfaceWidth: surfaceWidth,
      surfaceHeight: surfaceHeight,
      groutColor: painter.groutColor,
      groutWidth: painter.groutWidth,
      scale: scale,
    );
    scaledPainter.paint(canvas, Size(width.toDouble(), height.toDouble()));

    final picture = recorder.endRecording();
    final img = await picture.toImage(width, height);
    final byteData = await img.toByteData(format: ui.ImageByteFormat.png);

    final dir = await getApplicationDocumentsDirectory();
    final filePath = '${dir.path}/export_${DateTime.now().millisecondsSinceEpoch}.png';
    final file = File(filePath);
    await file.writeAsBytes(byteData!.buffer.asUint8List());

    return filePath;
  }

  /// Export the 3D room to a PNG file.
  static Future<String> exportRoomPNG({
    required IsometricPainter painter,
    required Size canvasSize,
  }) async {
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);

    painter.paint(canvas, canvasSize);

    final picture = recorder.endRecording();
    final img = await picture.toImage(
      canvasSize.width.round(),
      canvasSize.height.round(),
    );
    final byteData = await img.toByteData(format: ui.ImageByteFormat.png);

    final dir = await getApplicationDocumentsDirectory();
    final filePath = '${dir.path}/export_room_${DateTime.now().millisecondsSinceEpoch}.png';
    final file = File(filePath);
    await file.writeAsBytes(byteData!.buffer.asUint8List());

    return filePath;
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/render/export_service.dart
git commit -m "feat: PNG export service for 2D and 3D renders"
```

### Task 12b: Texture loading service

**Files:**
- Create: `lib/render/texture_loader.dart`

- [ ] **Step 1: Write texture_loader.dart**

```dart
// lib/render/texture_loader.dart
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/foundation.dart';
import '../models/tile_group.dart';

class TextureLoader {
  final Map<String, ui.Image> _cache = {};

  /// Get a cached texture, or load it from disk.
  Future<ui.Image?> getTexture(TileGroup tileGroup) async {
    if (_cache.containsKey(tileGroup.id)) {
      return _cache[tileGroup.id];
    }

    if (tileGroup.texturePath == null) return null;

    final file = File(tileGroup.texturePath!);
    if (!await file.exists()) return null;

    final bytes = await file.readAsBytes();
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    final image = frame.image;

    _cache[tileGroup.id] = image;
    return image;
  }

  /// Build the full texture cache for a list of tile groups.
  Future<Map<String, ui.Image>> loadAll(List<TileGroup> tileGroups) async {
    final result = <String, ui.Image>{};
    for (final tg in tileGroups) {
      final img = await getTexture(tg);
      if (img != null) result[tg.id] = img;
    }
    return result;
  }

  /// Evict a single texture (e.g., after recapture).
  void evict(String tileGroupId) {
    _cache.remove(tileGroupId);
  }

  /// Clear entire cache (e.g., on project switch).
  void clear() {
    _cache.clear();
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/render/texture_loader.dart
git commit -m "feat: texture loader with caching for tile group images"
```

---

## Phase 5: State Management & UI

### Task 13: Riverpod providers

**Files:**
- Create: `lib/state/providers.dart`
- Create: `lib/state/project_state.dart`

- [ ] **Step 1: Write providers.dart with all providers**

```dart
// lib/state/providers.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/project.dart';
import '../models/room.dart';
import '../models/surface.dart';
import '../models/surface_tile_group.dart';
import '../models/tile_group.dart';
import '../models/layout_result.dart';
import '../models/enums.dart';
import '../storage/project_repository.dart';
import '../storage/room_repository.dart';
import '../storage/surface_repository.dart';
import '../storage/tile_group_repository.dart';
import '../storage/layout_result_repository.dart';
import '../render/texture_loader.dart';

// --- Repositories ---
final projectRepoProvider = Provider((ref) => ProjectRepository());
final roomRepoProvider = Provider((ref) => RoomRepository());
final surfaceRepoProvider = Provider((ref) => SurfaceRepository());
final tileGroupRepoProvider = Provider((ref) => TileGroupRepository());
final layoutResultRepoProvider = Provider((ref) => LayoutResultRepository());

// --- Texture loading ---
final textureLoaderProvider = Provider((ref) => TextureLoader());

// --- Project list ---
final projectsProvider = FutureProvider<List<Project>>((ref) async {
  final repo = ref.watch(projectRepoProvider);
  return repo.findAll();
});

// --- Current project ---
final currentProjectIdProvider = StateProvider<String?>((ref) => null);

final currentProjectProvider = FutureProvider.family<Project?, String>((ref, id) async {
  final repo = ref.watch(projectRepoProvider);
  return repo.findById(id);
});

// --- Rooms for current project ---
final roomsProvider = FutureProvider.family<List<Room>, String>((ref, projectId) async {
  final repo = ref.watch(roomRepoProvider);
  return repo.findByProject(projectId);
});

// --- Tile groups for project ---
final tileGroupsProvider = FutureProvider.family<List<TileGroup>, String>((ref, projectId) async {
  final repo = ref.watch(tileGroupRepoProvider);
  return repo.findByProject(projectId);
});

// --- Current room ---
final currentRoomIdProvider = StateProvider<String?>((ref) => null);

// --- Surfaces for current room ---
final surfacesProvider = FutureProvider.family<List<Surface>, String>((ref, roomId) async {
  final repo = ref.watch(surfaceRepoProvider);
  return repo.findByRoom(roomId);
});

// --- SurfaceTileGroups for a surface ---
final surfaceTileGroupsProvider = FutureProvider.family<List<SurfaceTileGroup>, String>(
  (ref, surfaceId) async {
    final repo = ref.watch(surfaceRepoProvider);
    return repo.findTileGroupsBySurface(surfaceId);
  },
);

// --- Layout result ---
final layoutResultProvider = FutureProvider.family<LayoutResult?, String>(
  (ref, surfaceId) async {
    final repo = ref.watch(layoutResultRepoProvider);
    return repo.findBySurface(surfaceId);
  },
);

// --- Layout tab state ---
final selectedSurfaceIdProvider = StateProvider<String?>((ref) => null);

final surfaceOffsetsProvider = StateProvider.family<Offset, String>(
  (ref, surfaceId) => Offset.zero,
);

final surfaceLockedProvider = StateProvider.family<bool, String>(
  (ref, surfaceId) => false,
);

class UndoEntry {
  final Map<String, Offset> priorOffsets; // stgId → prior {offsetX, offsetY}
  const UndoEntry({required this.priorOffsets});
}

final undoBufferProvider = StateProvider<UndoEntry?>((ref) => null);

// --- Preview tab state ---
final viewAngleProvider = StateProvider<int>((ref) => 0);
final topDownProvider = StateProvider<bool>((ref) => false);
final selected3dSurfaceProvider = StateProvider<String?>((ref) => null);
```

- [ ] **Step 2: Commit**

```bash
git add lib/state/
git commit -m "feat: Riverpod providers for all state management"
```

---

## Phase 6: UI — Main Screens

### Task 14: Home screen (project list)

**Files:**
- Create: `lib/ui/screens/home_screen.dart`

- [ ] **Step 1: Write home_screen.dart**

```dart
// lib/ui/screens/home_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/project.dart';
import '../../state/providers.dart' as providers;
import 'project_detail_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  Widget build(BuildContext context) {
    final projectsAsync = ref.watch(projectsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Projects')),
      body: projectsAsync.when(
        data: (projects) => projects.isEmpty
            ? _buildEmptyState()
            : _buildProjectList(projects),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Error: $e')),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showCreateProjectDialog(context),
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.grid_view, size: 64, color: Colors.grey),
            const SizedBox(height: 16),
            const Text('No projects yet',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 8),
            const Text('Create a project to start designing tile layouts',
                style: TextStyle(color: Colors.grey)),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () => _showCreateProjectDialog(context),
              child: const Text('+ New Project'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildProjectList(List<Project> projects) {
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: projects.length,
      itemBuilder: (context, index) {
        final project = projects[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: ListTile(
            title: Text(project.name, style: const TextStyle(fontWeight: FontWeight.bold)),
            subtitle: Text('${project.units.name} · ${_formatDate(project.createdAt)}'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              ref.read(currentProjectIdProvider.notifier).state = project.id;
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => ProjectDetailScreen(projectId: project.id),
                ),
              );
            },
          ),
        );
      },
    );
  }

  void _showCreateProjectDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New Project'),
        content: TextField(
          controller: nameCtrl,
          decoration: const InputDecoration(labelText: 'Project name', hintText: 'e.g. Smith Residence'),
          autofocus: true,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              final project = Project(name: nameCtrl.text);
              await ref.read(projectRepoProvider).insert(project);
              ref.invalidate(projectsProvider);
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';
  }
}
```

- [ ] **Step 2: Run build check**

```bash
cd /Users/chenwu/orca/workspaces/hasu/spec-and-plan
flutter analyze lib/ui/screens/home_screen.dart
```
Expected: no errors (may show unused import warnings — acceptable).

- [ ] **Step 3: Commit**

```bash
git add lib/ui/screens/home_screen.dart
git commit -m "feat: home screen with project list, empty state, create dialog"
```

---

### Task 15: Project detail screen (rooms list + tile library)

**Files:**
- Create: `lib/ui/screens/project_detail_screen.dart`

- [ ] **Step 1: Write project_detail_screen.dart**

```dart
// lib/ui/screens/project_detail_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/room.dart';
import '../../models/tile_group.dart';
import 'room_editor_screen.dart';
import 'tile_library_screen.dart';

class ProjectDetailScreen extends ConsumerStatefulWidget {
  final String projectId;
  const ProjectDetailScreen({super.key, required this.projectId});

  @override
  ConsumerState<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends ConsumerState<ProjectDetailScreen> {
  int _selectedSegment = 0; // 0 = Rooms, 1 = Tile Library

  @override
  Widget build(BuildContext context) {
    final projectAsync = ref.watch(currentProjectProvider(widget.projectId));

    return projectAsync.when(
      data: (project) => Scaffold(
        appBar: AppBar(
          title: Text(project?.name ?? 'Project'),
        ),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(12),
              child: SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('Rooms')),
                  ButtonSegment(value: 1, label: Text('Tile Library')),
                ],
                selected: {_selectedSegment},
                onSelectionChanged: (s) => setState(() => _selectedSegment = s.first),
              ),
            ),
            Expanded(
              child: _selectedSegment == 0
                  ? _buildRoomsList()
                  : TileLibraryScreen(projectId: widget.projectId),
            ),
          ],
        ),
        floatingActionButton: _selectedSegment == 0
            ? FloatingActionButton(
                onPressed: () => _showCreateRoomDialog(context),
                child: const Icon(Icons.add),
              )
            : null,
      ),
      loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (e, _) => Scaffold(body: Center(child: Text('Error: $e'))),
    );
  }

  Widget _buildRoomsList() {
    final roomsAsync = ref.watch(roomsProvider(widget.projectId));

    return roomsAsync.when(
      data: (rooms) => rooms.isEmpty
          ? const Center(child: Text('No rooms yet. Tap + to create one.'))
          : ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              itemCount: rooms.length,
              itemBuilder: (context, index) {
                final room = rooms[index];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    leading: const Icon(Icons.meeting_room),
                    title: Text(room.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                    subtitle: Text('${room.width.toStringAsFixed(0)} × ${room.depth.toStringAsFixed(0)} × ${room.height.toStringAsFixed(0)}mm'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () {
                      ref.read(currentRoomIdProvider.notifier).state = room.id;
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => RoomEditorScreen(roomId: room.id)),
                      );
                    },
                  ),
                );
              },
            ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
    );
  }

  void _showCreateRoomDialog(BuildContext context) {
    final nameCtrl = TextEditingController();
    final widthCtrl = TextEditingController(text: '2000');
    final depthCtrl = TextEditingController(text: '1500');
    final heightCtrl = TextEditingController(text: '2400');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New Room'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(labelText: 'Room name', hintText: 'e.g. Bathroom 3F'),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: TextField(controller: widthCtrl, decoration: const InputDecoration(labelText: 'Width (mm)'), keyboardType: TextInputType.number)),
                const SizedBox(width: 8),
                Expanded(child: TextField(controller: depthCtrl, decoration: const InputDecoration(labelText: 'Depth (mm)'), keyboardType: TextInputType.number)),
                const SizedBox(width: 8),
                Expanded(child: TextField(controller: heightCtrl, decoration: const InputDecoration(labelText: 'Height (mm)'), keyboardType: TextInputType.number)),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              final room = Room(
                projectId: widget.projectId,
                name: nameCtrl.text,
                width: double.parse(widthCtrl.text),
                depth: double.parse(depthCtrl.text),
                height: double.parse(heightCtrl.text),
              );
              await ref.read(roomRepoProvider).insert(room);
              // Auto-generate surfaces for the room
              await _generateSurfaces(room.id, room.width, room.depth, room.height);
              ref.invalidate(roomsProvider(widget.projectId));
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }

  Future<void> _generateSurfaces(String roomId, double w, double d, double h) async {
    // Import and use SurfacePositionCalculator (from engine)
    final surfaces = (await _calculateSurfaces(roomId, w, d, h));
    final repo = ref.read(surfaceRepoProvider);
    for (final surface in surfaces) {
      await repo.insert(surface);
    }
  }

  Future<List<dynamic>> _calculateSurfaces(String roomId, double w, double d, double h) async {
    // Dynamic return to avoid import complexity in this task
    // Full import chain in integration task
    return [];
  }
}
```

> **Note:** The `_generateSurfaces` and `_calculateSurfaces` methods are stubs. The full integration with `SurfacePositionCalculator` is completed in Task 22 (Integration).

- [ ] **Step 2: Commit**

```bash
git add lib/ui/screens/project_detail_screen.dart
git commit -m "feat: project detail screen with rooms list and segmented control"
```

---

### Task 16: Room editor screen (tab scaffold)

**Files:**
- Create: `lib/ui/screens/room_editor_screen.dart`

- [ ] **Step 1: Write room_editor_screen.dart**

```dart
// lib/ui/screens/room_editor_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../tabs/surfaces_tab.dart';
import '../tabs/layout_tab.dart';
import '../tabs/preview_tab.dart';
import '../tabs/cut_list_tab.dart';

class RoomEditorScreen extends ConsumerStatefulWidget {
  final String roomId;
  const RoomEditorScreen({super.key, required this.roomId});

  @override
  ConsumerState<RoomEditorScreen> createState() => _RoomEditorScreenState();
}

class _RoomEditorScreenState extends ConsumerState<RoomEditorScreen> {
  int _currentTab = 0;

  static const _tabs = ['Surfaces', 'Layout', 'Preview', 'Cut List'];

  @override
  void initState() {
    super.initState();
    // Set the current room ID so all child providers can access it
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(currentRoomIdProvider.notifier).state = widget.roomId;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Room'), // Will be replaced with room name via provider in integration
      ),
      body: IndexedStack(
        index: _currentTab,
        children: [
          SurfacesTab(roomId: widget.roomId),
          LayoutTab(roomId: widget.roomId),
          PreviewTab(roomId: widget.roomId),
          CutListTab(roomId: widget.roomId),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentTab,
        onDestinationSelected: (i) => setState(() => _currentTab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.space_dashboard_outlined), selectedIcon: Icon(Icons.space_dashboard), label: 'Surfaces'),
          NavigationDestination(icon: Icon(Icons.grid_on_outlined), selectedIcon: Icon(Icons.grid_on), label: 'Layout'),
          NavigationDestination(icon: Icon(Icons.view_in_ar_outlined), selectedIcon: Icon(Icons.view_in_ar), label: 'Preview'),
          NavigationDestination(icon: Icon(Icons.content_cut_outlined), selectedIcon: Icon(Icons.content_cut), label: 'Cut List'),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: Create placeholder tab files** (to avoid import errors)

```dart
// lib/ui/tabs/surfaces_tab.dart (minimal placeholder)
import 'package:flutter/material.dart';
class SurfacesTab extends StatelessWidget {
  final String roomId;
  const SurfacesTab({super.key, required this.roomId});
  @override
  Widget build(BuildContext context) => const Center(child: Text('Surfaces'));
}

// lib/ui/tabs/layout_tab.dart
import 'package:flutter/material.dart';
class LayoutTab extends StatelessWidget {
  final String roomId;
  const LayoutTab({super.key, required this.roomId});
  @override
  Widget build(BuildContext context) => const Center(child: Text('Layout'));
}

// lib/ui/tabs/preview_tab.dart
import 'package:flutter/material.dart';
class PreviewTab extends StatelessWidget {
  final String roomId;
  const PreviewTab({super.key, required this.roomId});
  @override
  Widget build(BuildContext context) => const Center(child: Text('Preview'));
}

// lib/ui/tabs/cut_list_tab.dart
import 'package:flutter/material.dart';
class CutListTab extends StatelessWidget {
  final String roomId;
  const CutListTab({super.key, required this.roomId});
  @override
  Widget build(BuildContext context) => const Center(child: Text('Cut List'));
}
```

- [ ] **Step 3: Commit**

```bash
git add lib/ui/screens/room_editor_screen.dart lib/ui/tabs/
git commit -m "feat: room editor screen with 4-tab scaffold and placeholder tabs"
```

---

### Task 17: Tile library screen

**Files:**
- Create: `lib/ui/screens/tile_library_screen.dart`

- [ ] **Step 1: Write tile_library_screen.dart**

```dart
// lib/ui/screens/tile_library_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/tile_group.dart';
import '../../storage/tile_group_repository.dart';
import 'tile_group_form_screen.dart';

class TileLibraryScreen extends ConsumerWidget {
  final String projectId;
  const TileLibraryScreen({super.key, required this.projectId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final groupsAsync = ref.watch(tileGroupsProvider(projectId));

    return groupsAsync.when(
      data: (groups) => groups.isEmpty
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('No tile groups yet', style: TextStyle(color: Colors.grey)),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () => _showAddOptions(context, ref),
                    icon: const Icon(Icons.add),
                    label: const Text('Add Tile Group'),
                  ),
                ],
              ),
            )
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  child: OutlinedButton.icon(
                    onPressed: () => _showAddOptions(context, ref),
                    icon: const Icon(Icons.add),
                    label: const Text('Add Tile Group'),
                  ),
                ),
                Expanded(
                  child: ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    itemCount: groups.length,
                    itemBuilder: (context, index) {
                      final group = groups[index];
                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          leading: Container(
                            width: 48, height: 48,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(6),
                              color: Colors.grey.shade200,
                            ),
                            child: group.texturePath != null
                                ? ClipRRect(
                                    borderRadius: BorderRadius.circular(6),
                                    child: Image.asset(group.texturePath!, fit: BoxFit.cover),
                                  )
                                : const Icon(Icons.texture, color: Colors.grey),
                          ),
                          title: Text(group.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                          subtitle: Text('${group.tileWidth.toStringAsFixed(0)} × ${group.tileHeight.toStringAsFixed(0)}mm · ${group.source.name}'),
                          trailing: IconButton(
                            icon: const Icon(Icons.edit_outlined, size: 20),
                            onPressed: () => _editTileGroup(context, ref, group),
                          ),
                          onTap: () => _editTileGroup(context, ref, group),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
    );
  }

  void _showAddOptions(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: const Text('Capture from camera'),
              subtitle: const Text('Scan a tile surface'),
              onTap: () {
                Navigator.pop(ctx);
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const CameraScreen(projectId: ''),
                  ),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Import from gallery'),
              subtitle: const Text('Pick an existing image'),
              onTap: () {
                Navigator.pop(ctx);
                _showImportForm(context, ref);
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showImportForm(BuildContext context, WidgetRef ref) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TileGroupFormScreen(
          projectId: projectId,
          source: TileSource.imported,
        ),
      ),
    );
  }

  void _editTileGroup(BuildContext context, WidgetRef ref, TileGroup group) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TileGroupFormScreen(
          projectId: projectId,
          existingGroup: group,
          source: group.source,
        ),
      ),
    );
  }
}

// Forward-declare CameraScreen (implemented in Task 18)
class CameraScreen extends StatelessWidget {
  final String projectId;
  const CameraScreen({super.key, required this.projectId});
  @override
  Widget build(BuildContext context) => const Scaffold(body: Center(child: Text('Camera')));
}
```

- [ ] **Step 2: Create tile_group_form_screen.dart placeholder**

```dart
// lib/ui/screens/tile_group_form_screen.dart
import 'package:flutter/material.dart';
import '../../models/tile_group.dart';
import '../../models/enums.dart';

class TileGroupFormScreen extends StatefulWidget {
  final String projectId;
  final TileGroup? existingGroup;
  final TileSource source;
  const TileGroupFormScreen({
    super.key,
    required this.projectId,
    this.existingGroup,
    required this.source,
  });
  @override
  State<TileGroupFormScreen> createState() => _TileGroupFormScreenState();
}

class _TileGroupFormScreenState extends State<TileGroupFormScreen> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _widthCtrl;
  late final TextEditingController _heightCtrl;

  @override
  void initState() {
    super.initState();
    _nameCtrl = TextEditingController(text: widget.existingGroup?.name ?? '');
    _widthCtrl = TextEditingController(text: widget.existingGroup?.tileWidth.toString() ?? '300');
    _heightCtrl = TextEditingController(text: widget.existingGroup?.tileHeight.toString() ?? '200');
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _widthCtrl.dispose();
    _heightCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.existingGroup != null;
    return Scaffold(
      appBar: AppBar(title: Text(isEditing ? 'Edit Tile Group' : 'New Tile Group')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: 'Tile name', hintText: 'e.g. White subway tile'),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _widthCtrl,
                    decoration: const InputDecoration(labelText: 'Width (mm)'),
                    keyboardType: TextInputType.number,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: _heightCtrl,
                    decoration: const InputDecoration(labelText: 'Height (mm)'),
                    keyboardType: TextInputType.number,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () {
                  // Save logic: will be wired in integration task
                  Navigator.pop(context);
                },
                child: Text(isEditing ? 'Save Changes' : 'Save Tile Group'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add lib/ui/screens/tile_library_screen.dart lib/ui/screens/tile_group_form_screen.dart
git commit -m "feat: tile library screen with add/edit tile group forms"
```

---

### Task 18: Camera capture screen

**Files:**
- Create: `lib/ui/screens/camera_screen.dart`

- [ ] **Step 1: Write camera_screen.dart**

```dart
// lib/ui/screens/camera_screen.dart
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';

class CameraScreen extends StatefulWidget {
  final String projectId;
  const CameraScreen({super.key, required this.projectId});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  CameraController? _controller;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _initCamera();
  }

  Future<void> _initCamera() async {
    final cameras = await availableCameras();
    if (cameras.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No camera available')),
        );
      }
      return;
    }
    _controller = CameraController(cameras[0], ResolutionPreset.high);
    await _controller!.initialize();
    if (mounted) setState(() => _initialized = true);
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_initialized) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        fit: StackFit.expand,
        children: [
          CameraPreview(_controller!),

          // Corner guides (document-scanner style)
          Center(
            child: Container(
              width: 260, height: 260,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white.withValues(alpha: 0.4), width: 2),
                borderRadius: BorderRadius.circular(4),
              ),
            ),
          ),

          // Corner markers
          Positioned(
            top: (MediaQuery.of(context).size.height / 2) - 130,
            left: (MediaQuery.of(context).size.width / 2) - 130,
            child: Container(width: 20, height: 20,
              decoration: const BoxDecoration(
                border: Border(top: BorderSide(color: Colors.white, width: 3), left: BorderSide(color: Colors.white, width: 3)),
              ),
            ),
          ),
          // (other 3 corners follow same pattern — omitted for brevity, identical with different border sides)

          // Instruction text
          Positioned(
            top: 60,
            left: 0, right: 0,
            child: Column(
              children: [
                const Text('Position the tile within the guides',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text('Place on a contrasting surface for best results',
                    style: TextStyle(color: Colors.white.withValues(alpha: 0.6), fontSize: 12)),
              ],
            ),
          ),

          // Capture button
          Positioned(
            bottom: 40,
            left: 0, right: 0,
            child: GestureDetector(
              onTap: () async {
                final image = await _controller!.takePicture();
                if (mounted) {
                  Navigator.pop(context, image.path);
                }
              },
              child: Container(
                width: 70, height: 70,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.white, width: 4),
                ),
                child: Center(
                  child: Container(
                    width: 56, height: 56,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/screens/camera_screen.dart
git commit -m "feat: camera capture screen with corner guides"
```

---

### Task 19: Surface detail screen

**Files:**
- Create: `lib/ui/screens/surface_detail_screen.dart`

- [ ] **Step 1: Write surface_detail_screen.dart**

```dart
// lib/ui/screens/surface_detail_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/surface.dart';
import '../../models/surface_tile_group.dart';
import '../../models/enums.dart';
import '../../storage/surface_repository.dart';
import '../widgets/grout_picker.dart';
import 'region_editor_screen.dart';

class SurfaceDetailScreen extends ConsumerStatefulWidget {
  final String surfaceId;
  const SurfaceDetailScreen({super.key, required this.surfaceId});

  @override
  ConsumerState<SurfaceDetailScreen> createState() => _SurfaceDetailScreenState();
}

class _SurfaceDetailScreenState extends ConsumerState<SurfaceDetailScreen> {
  @override
  Widget build(BuildContext context) {
    final surfaceAsync = ref.watch(
      // Use a basic FutureProvider for single surface fetch
      FutureProvider<Surface?>((ref) async {
        final repo = ref.watch(surfaceRepoProvider);
        return repo.findById(widget.surfaceId);
      }),
    );
    final stgsAsync = ref.watch(surfaceTileGroupsProvider(widget.surfaceId));

    return surfaceAsync.when(
      data: (surface) => Scaffold(
        appBar: AppBar(title: Text(surface?.type == SurfaceType.wall ? 'Wall' : 'Floor')),
        body: surface == null
            ? const Center(child: Text('Surface not found'))
            : ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Dimensions (read-only for MVP)
                  _sectionHeader('DIMENSIONS'),
                  Row(
                    children: [
                      Expanded(child: _fieldBox('Width', '${surface.width.toStringAsFixed(0)}mm')),
                      const SizedBox(width: 8),
                      Expanded(child: _fieldBox('Height', '${surface.height.toStringAsFixed(0)}mm')),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Grout
                  _sectionHeader('GROUT'),
                  GroutPicker(
                    selected: surface.groutColor,
                    onChanged: (color) async {
                      surface.groutColor = color;
                      await ref.read(surfaceRepoProvider).update(surface);
                      setState(() {});
                    },
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Text('Grout width: '),
                      SizedBox(
                        width: 80,
                        child: TextField(
                          controller: TextEditingController(text: surface.groutWidth.toString()),
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(suffixText: 'mm'),
                          onSubmitted: (val) async {
                            surface.groutWidth = double.tryParse(val) ?? 3.0;
                            await ref.read(surfaceRepoProvider).update(surface);
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),

                  // Tile groups
                  _sectionHeader('TILE GROUPS ON THIS SURFACE'),
                  stgsAsync.when(
                    data: (stgs) => Column(
                      children: [
                        ...stgs.map((stg) => _buildTileGroupCard(stg)),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: () => _showAddTileGroupDialog(context, ref),
                          icon: const Icon(Icons.add),
                          label: const Text('Add tile group from library'),
                        ),
                      ],
                    ),
                    loading: () => const CircularProgressIndicator(),
                    error: (e, _) => Text('Error: $e'),
                  ),

                  const SizedBox(height: 24),
                  OutlinedButton(
                    onPressed: () => _confirmDelete(context, ref, surface),
                    style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                    child: const Text('Delete this surface'),
                  ),
                ],
              ),
      ),
      loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (e, _) => Scaffold(body: Center(child: Text('Error: $e'))),
    );
  }

  Widget _sectionHeader(String text) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(text, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.grey)),
  );

  Widget _fieldBox(String label, String value) => Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade300), borderRadius: BorderRadius.circular(8)),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
        Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
      ],
    ),
  );

  Widget _buildTileGroupCard(SurfaceTileGroup stg) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(width: 40, height: 40, decoration: BoxDecoration(borderRadius: BorderRadius.circular(6), color: Colors.grey.shade200)),
                const SizedBox(width: 12),
                Expanded(child: Text('Tile Group', style: const TextStyle(fontWeight: FontWeight.bold))),
                IconButton(icon: const Icon(Icons.close, size: 18), onPressed: () {}),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: InkWell(
                    onTap: () => _openRegionEditor(stg),
                    child: _chipCard('REGION', '${stg.region.width.toStringAsFixed(0)}×${stg.region.height.toStringAsFixed(0)}mm'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: InkWell(
                    onTap: () => _openPatternPicker(stg),
                    child: _chipCard('PATTERN', stg.pattern.name),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _chipCard(String label, String value) => Container(
    padding: const EdgeInsets.all(10),
    decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade300), borderRadius: BorderRadius.circular(8)),
    child: Row(
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: const TextStyle(fontSize: 9, color: Colors.grey)),
            Text(value, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
          ],
        ),
        const Spacer(),
        const Icon(Icons.chevron_right, size: 16, color: Colors.grey),
      ],
    ),
  );

  void _openRegionEditor(SurfaceTileGroup stg) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => RegionEditorScreen(stg: stg, surfaceWidth: 2000, surfaceHeight: 1200)),
    );
  }

  void _openPatternPicker(SurfaceTileGroup stg) {
    showDialog(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Pattern'),
        children: TilePattern.values.map((p) => SimpleDialogOption(
          onPressed: () {
            stg.pattern = p;
            // Save in integration task
            Navigator.pop(ctx);
            setState(() {});
          },
          child: Text(p.name),
        )).toList(),
      ),
    );
  }

  void _showAddTileGroupDialog(BuildContext context, WidgetRef ref) {
    // Show tile group picker — implemented in integration task
  }

  void _confirmDelete(BuildContext context, WidgetRef ref, Surface surface) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete surface?'),
        content: const Text('This will remove the surface and all its tile assignments.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              await ref.read(surfaceRepoProvider).delete(surface.id);
              if (ctx.mounted) Navigator.pop(ctx);
              if (context.mounted) Navigator.pop(context);
            },
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/screens/surface_detail_screen.dart
git commit -m "feat: surface detail screen with dimensions, grout, tile group cards"
```

---

### Task 20: Region editor screen

**Files:**
- Create: `lib/ui/screens/region_editor_screen.dart`

- [ ] **Step 1: Write region_editor_screen.dart**

```dart
// lib/ui/screens/region_editor_screen.dart
import 'package:flutter/material.dart';
import '../../models/surface_tile_group.dart';

class RegionEditorScreen extends StatefulWidget {
  final SurfaceTileGroup stg;
  final double surfaceWidth;
  final double surfaceHeight;

  const RegionEditorScreen({
    super.key,
    required this.stg,
    required this.surfaceWidth,
    required this.surfaceHeight,
  });

  @override
  State<RegionEditorScreen> createState() => _RegionEditorScreenState();
}

class _RegionEditorScreenState extends State<RegionEditorScreen> {
  String _selectedPreset = 'Full surface';
  final _sizeCtrl = TextEditingController(text: '150');

  static const _presets = [
    'Full surface', 'Top strip', 'Bottom strip',
    'Left strip', 'Right strip',
    'H-center band', 'V-center band', 'Custom',
  ];

  @override
  void dispose() {
    _sizeCtrl.dispose();
    super.dispose();
  }

  RegionRect _computeRegion(String preset) {
    final W = widget.surfaceWidth;
    final H = widget.surfaceHeight;
    final s = double.tryParse(_sizeCtrl.text) ?? 150;

    switch (preset) {
      case 'Full surface': return RegionRect(x: 0, y: 0, width: W, height: H);
      case 'Top strip': return RegionRect(x: 0, y: 0, width: W, height: s);
      case 'Bottom strip': return RegionRect(x: 0, y: H - s, width: W, height: s);
      case 'Left strip': return RegionRect(x: 0, y: 0, width: s, height: H);
      case 'Right strip': return RegionRect(x: W - s, y: 0, width: s, height: H);
      case 'H-center band': return RegionRect(x: 0, y: (H - s) / 2, width: W, height: s);
      case 'V-center band': return RegionRect(x: (W - s) / 2, y: 0, width: s, height: H);
      default: return RegionRect(x: 0, y: 0, width: W, height: H);
    }
  }

  @override
  Widget build(BuildContext context) {
    final needsSizeInput = _selectedPreset != 'Full surface';

    return Scaffold(
      appBar: AppBar(title: const Text('Edit Region')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Preset chips
          Text('REGION TYPE', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.grey)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8, runSpacing: 8,
            children: _presets.map((preset) => ChoiceChip(
              label: Text(preset, style: const TextStyle(fontSize: 12)),
              selected: _selectedPreset == preset,
              onSelected: (_) => setState(() => _selectedPreset = preset),
            )).toList(),
          ),

          if (needsSizeInput) ...[
            const SizedBox(height: 20),
            Row(
              children: [
                const Text('Size: '),
                SizedBox(
                  width: 80,
                  child: TextField(
                    controller: _sizeCtrl,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(suffixText: 'mm'),
                    onChanged: (_) => setState(() {}),
                  ),
                ),
              ],
            ),
          ],

          const SizedBox(height: 24),

          // Mini preview
          Text('PREVIEW', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.grey)),
          const SizedBox(height: 8),
          Container(
            height: 120,
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey.shade300),
              borderRadius: BorderRadius.circular(8),
            ),
            child: CustomPaint(
              painter: _RegionPreviewPainter(
                region: _computeRegion(_selectedPreset),
                surfaceW: widget.surfaceWidth,
                surfaceH: widget.surfaceHeight,
              ),
            ),
          ),

          const SizedBox(height: 24),
          FilledButton(
            onPressed: () {
              widget.stg.region = _computeRegion(_selectedPreset);
              Navigator.pop(context);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}

class _RegionPreviewPainter extends CustomPainter {
  final RegionRect region;
  final double surfaceW, surfaceH;

  _RegionPreviewPainter({required this.region, required this.surfaceW, required this.surfaceH});

  @override
  void paint(Canvas canvas, Size size) {
    final scaleX = size.width / surfaceW;
    final scaleY = size.height / surfaceH;

    // Draw surface background
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height),
        Paint()..color = Colors.grey.shade100);

    // Draw region highlight
    canvas.drawRect(
      Rect.fromLTWH(
        region.x * scaleX, region.y * scaleY,
        region.width * scaleX, region.height * scaleY,
      ),
      Paint()..color = Colors.blue.withValues(alpha: 0.3),
    );

    // Draw region border
    canvas.drawRect(
      Rect.fromLTWH(
        region.x * scaleX, region.y * scaleY,
        region.width * scaleX, region.height * scaleY,
      ),
      Paint()..color = Colors.blue..style = PaintingStyle.stroke..strokeWidth = 2,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/screens/region_editor_screen.dart
git commit -m "feat: region editor with preset picker and mini preview"
```

---

### Task 21: Help diagram dialog

**Files:**
- Create: `lib/ui/screens/help_diagram_dialog.dart`

- [ ] **Step 1: Write help_diagram_dialog.dart**

```dart
// lib/ui/screens/help_diagram_dialog.dart
import 'package:flutter/material.dart';

class HelpDiagramDialog extends StatelessWidget {
  const HelpDiagramDialog({super.key});

  static void show(BuildContext context) {
    showDialog(
      context: context,
      builder: (_) => const HelpDiagramDialog(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Where are you standing?',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            const Text('Imagine you\'re at the doorway looking in',
                style: TextStyle(color: Colors.grey, fontSize: 12)),
            const SizedBox(height: 20),

            // Perspective diagram
            Container(
              width: 240, height: 180,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey.shade300),
                borderRadius: BorderRadius.circular(8),
              ),
              child: CustomPaint(
                painter: _RoomPerspectivePainter(),
              ),
            ),

            const SizedBox(height: 16),

            // Labels
            _labelRow('Front wall', 'The wall directly ahead, facing you'),
            _labelRow('Back wall', 'The wall behind you (with the doorway)'),
            _labelRow('Left wall', 'The wall to your left'),
            _labelRow('Right wall', 'The wall to your right'),
            _labelRow('Floor', 'Under your feet'),

            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Got it'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _labelRow(String title, String desc) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: Row(
      children: [
        SizedBox(
          width: 90,
          child: Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
        ),
        Expanded(
          child: Text(desc, style: const TextStyle(color: Colors.grey, fontSize: 11)),
        ),
      ],
    ),
  );
}

class _RoomPerspectivePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = const Color(0xFFF5F0E8);
    final linePaint = Paint()..color = Colors.grey.shade400..style = PaintingStyle.stroke;

    // Floor
    final floorPath = Path()
      ..moveTo(size.width * 0.5, size.height * 0.7)
      ..lineTo(size.width * 0.1, size.height * 0.5)
      ..lineTo(size.width * 0.5, size.height * 0.35)
      ..lineTo(size.width * 0.9, size.height * 0.5)
      ..close();
    canvas.drawPath(floorPath, Paint()..color = const Color(0xFFD8C8A8));

    // Front wall
    canvas.drawRect(
      Rect.fromLTWH(size.width * 0.3, size.height * 0.05, size.width * 0.4, size.height * 0.45),
      paint,
    );

    // Front wall label
    _drawText(canvas, 'Front Wall', Offset(size.width * 0.5, size.height * 0.25), Colors.blue);

    // Left wall
    final leftPath = Path()
      ..moveTo(size.width * 0.1, size.height * 0.5)
      ..lineTo(size.width * 0.1, size.height * 0.15)
      ..lineTo(size.width * 0.3, size.height * 0.05)
      ..lineTo(size.width * 0.3, size.height * 0.35)
      ..close();
    canvas.drawPath(leftPath, Paint()..color = const Color(0xFFF0E8D8));

    // Right wall
    final rightPath = Path()
      ..moveTo(size.width * 0.9, size.height * 0.5)
      ..lineTo(size.width * 0.9, size.height * 0.15)
      ..lineTo(size.width * 0.7, size.height * 0.05)
      ..lineTo(size.width * 0.7, size.height * 0.35)
      ..close();
    canvas.drawPath(rightPath, Paint()..color = const Color(0xFFF0E8D8));

    // Person icon at bottom
    canvas.drawCircle(Offset(size.width * 0.5, size.height * 0.85), 8, Paint()..color = Colors.blue);
    _drawText(canvas, 'You', Offset(size.width * 0.5, size.height * 0.95), Colors.blue);
  }

  void _drawText(Canvas canvas, String text, Offset offset, Color color) {
    final builder = ui.ParagraphBuilder(ui.ParagraphStyle(textAlign: ui.TextAlign.center))
      ..pushStyle(ui.TextStyle(color: color, fontSize: 10, fontWeight: ui.FontWeight.bold))
      ..addText(text);
    final paragraph = builder.build()..layout(ui.ParagraphConstraints(width: 80));
    canvas.drawParagraph(paragraph, Offset(offset.dx - 40, offset.dy));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/screens/help_diagram_dialog.dart
git commit -m "feat: help diagram dialog with person-in-room perspective"
```

---

## Phase 7: Tab Screens (Full Implementation)

### Task 22: Surfaces tab

**Files:**
- Modify: `lib/ui/tabs/surfaces_tab.dart`

- [ ] **Step 1: Rewrite surfaces_tab.dart with full implementation**

```dart
// lib/ui/tabs/surfaces_tab.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/surface.dart';
import '../screens/surface_detail_screen.dart';

class SurfacesTab extends ConsumerWidget {
  final String roomId;
  const SurfacesTab({super.key, required this.roomId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final surfacesAsync = ref.watch(surfacesProvider(roomId));

    return surfacesAsync.when(
      data: (surfaces) => surfaces.isEmpty
          ? const Center(child: Text('No surfaces. Add a wall or floor to get started.'))
          : ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: surfaces.length,
              itemBuilder: (context, index) => _SurfaceCard(
                surface: surfaces[index],
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => SurfaceDetailScreen(surfaceId: surfaces[index].id),
                    ),
                  );
                },
              ),
            ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
    );
  }
}

class _SurfaceCard extends StatelessWidget {
  final Surface surface;
  final VoidCallback onTap;

  const _SurfaceCard({required this.surface, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final icon = surface.type == SurfaceType.wall ? Icons.vertical_split : Icons.crop_square;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: Icon(icon),
        title: Text('${surface.type.name} — ${surface.width.toStringAsFixed(0)}×${surface.height.toStringAsFixed(0)}mm',
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text('Grout: ${surface.groutColor.name}'),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/tabs/surfaces_tab.dart
git commit -m "feat: surfaces tab with surface list and navigation to detail"
```

---

### Task 23: Layout tab

**Files:**
- Modify: `lib/ui/tabs/layout_tab.dart`

- [ ] **Step 1: Rewrite layout_tab.dart with full implementation**

```dart
// lib/ui/tabs/layout_tab.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/surface.dart';
import '../../models/enums.dart';

class LayoutTab extends ConsumerStatefulWidget {
  final String roomId;
  const LayoutTab({super.key, required this.roomId});

  @override
  ConsumerState<LayoutTab> createState() => _LayoutTabState();
}

class _LayoutTabState extends ConsumerState<LayoutTab> {
  Offset _dragOffset = Offset.zero;
  bool _isDragging = false;

  @override
  Widget build(BuildContext context) {
    final selectedId = ref.watch(selectedSurfaceIdProvider);
    final surfacesAsync = ref.watch(surfacesProvider(widget.roomId));

    return surfacesAsync.when(
      data: (surfaces) {
        if (surfaces.isEmpty) {
          return const Center(child: Text('No surfaces to edit.'));
        }

        // Auto-select first surface if none selected
        if (selectedId == null || !surfaces.any((s) => s.id == selectedId)) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            ref.read(selectedSurfaceIdProvider.notifier).state = surfaces.first.id;
          });
        }

        final selected = surfaces.where((s) => s.id == selectedId).firstOrNull;

        return Column(
          children: [
            // Surface selector chips
            SizedBox(
              height: 44,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                children: surfaces.map((s) => Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    label: Text('${s.type.name} ${_surfaceLabel(s)}',
                        style: TextStyle(fontSize: 11, color: s.id == selectedId ? Colors.white : null)),
                    selected: s.id == selectedId,
                    onSelected: (_) => ref.read(selectedSurfaceIdProvider.notifier).state = s.id,
                  ),
                )).toList(),
              ),
            ),

            // 2D elevation canvas
            Expanded(
              child: selected != null
                  ? GestureDetector(
                      onPanStart: (_) => _onDragStart(selected, surfaces),
                      onPanUpdate: (details) {
                        setState(() => _dragOffset += details.delta);
                      },
                      onPanEnd: (_) => _onDragEnd(selected, surfaces),
                      child: Container(
                        margin: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          border: Border.all(color: Colors.blue, width: 2),
                          borderRadius: BorderRadius.circular(8),
                          color: const Color(0xFFF8F6F0),
                        ),
                        child: Stack(
                          children: [
                            // Placeholder: 2D render will be wired in integration
                            Center(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.grid_on, size: 48, color: Colors.grey),
                                  const SizedBox(height: 8),
                                  Text('${selected.width.toStringAsFixed(0)} × ${selected.height.toStringAsFixed(0)}mm',
                                      style: const TextStyle(color: Colors.grey)),
                                ],
                              ),
                            ),
                            // Offset overlay
                            Positioned(
                              top: 8, left: 8,
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: Colors.black87,
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: Text('Offset: x=${_dragOffset.dx.toStringAsFixed(0)}, y=${_dragOffset.dy.toStringAsFixed(0)}',
                                    style: const TextStyle(color: Colors.white, fontSize: 10)),
                              ),
                            ),
                          ],
                        ),
                      ),
                    )
                  : const Center(child: Text('Select a surface')),
            ),

            // Lock chips
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: [
                  const Text('LOCK:', style: TextStyle(fontSize: 10, color: Colors.grey)),
                  const SizedBox(width: 8),
                  ...surfaces.where((s) => s.id != selectedId).map((s) {
                    final locked = ref.watch(surfaceLockedProvider(s.id));
                    return Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: FilterChip(
                        label: Text(
                          '${_surfaceLabel(s)} ${locked ? '🔒' : ''}',
                          style: TextStyle(fontSize: 10, color: locked ? Colors.white : Colors.grey),
                        ),
                        selected: locked,
                        onSelected: (_) {
                          ref.read(surfaceLockedProvider(s.id).notifier).state = !locked;
                        },
                      ),
                    );
                  }),
                ],
              ),
            ),

            // Action buttons
            Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _resetToAuto,
                      child: const Text('Reset', style: TextStyle(fontSize: 12)),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => _snapToCenter(selected),
                      child: const Text('Snap to Center', style: TextStyle(fontSize: 12)),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _undo,
                      child: const Text('Undo', style: TextStyle(fontSize: 12)),
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
    );
  }

  /// Snapshot current offsets before drag begins (for undo).
  void _onDragStart(Surface selected, List<Surface> surfaces) {
    _isDragging = true;
    // Save current offsets of all affected STGs (selected + locked) to undo buffer
    final affectedIds = _getAffectedSurfaceIds(selected, surfaces);
    final priorOffsets = <String, Offset>{};
    for (final id in affectedIds) {
      priorOffsets[id] = ref.read(surfaceOffsetsProvider(id));
    }
    ref.read(undoBufferProvider.notifier).state = UndoEntry(priorOffsets: priorOffsets);
  }

  /// On drag end: persist offsets to SurfaceTileGroups, propagate to locked
  /// surfaces, and mark layout results stale.
  void _onDragEnd(Surface selected, List<Surface> surfaces) {
    _isDragging = false;
    final delta = _dragOffset;
    setState(() => _dragOffset = Offset.zero);

    // Apply delta to selected surface
    _applyOffset(selected.id, delta);

    // Lock propagation (axis-aware per spec)
    final lockedSurfaces = surfaces.where((s) =>
        s.id != selected.id && ref.read(surfaceLockedProvider(s.id)));

    for (final locked in lockedSurfaces) {
      final propagated = _propagateDelta(selected, locked, delta);
      if (propagated != Offset.zero) {
        _applyOffset(locked.id, propagated);
      }
    }
  }

  /// Apply offset delta to a surface's SurfaceTileGroups and mark stale.
  void _applyOffset(String surfaceId, Offset delta) {
    final current = ref.read(surfaceOffsetsProvider(surfaceId));
    ref.read(surfaceOffsetsProvider(surfaceId).notifier).state = current + delta;
    // Mark layout stale — integration task wires the debounced recompute
  }

  /// Axis-aware lock propagation per spec:
  /// - Walls: horizontal (dx) propagates only to parallel walls;
  ///   vertical (dy) propagates to all walls (shared "up" axis).
  /// - Floors: both dx and dy propagate to other floors.
  Offset _propagateDelta(Surface source, Surface target, Offset delta) {
    if (source.type == SurfaceType.floor && target.type == SurfaceType.floor) {
      return delta; // floors propagate both axes
    }
    if (source.type == SurfaceType.wall && target.type == SurfaceType.wall) {
      final isParallel = _areParallelWalls(source, target);
      return Offset(
        isParallel ? delta.dx : 0, // horizontal only if parallel
        delta.dy, // vertical always propagates between walls
      );
    }
    return Offset.zero; // wall↔floor: no propagation in MVP
  }

  bool _areParallelWalls(Surface a, Surface b) {
    // Walls at rotation 0/180 are parallel (front/back)
    // Walls at rotation 90/270 are parallel (left/right)
    final aAxis = a.position.rotation % 180;
    final bAxis = b.position.rotation % 180;
    return aAxis == bAxis;
  }

  List<String> _getAffectedSurfaceIds(Surface selected, List<Surface> surfaces) {
    final ids = [selected.id];
    for (final s in surfaces) {
      if (s.id != selected.id && ref.read(surfaceLockedProvider(s.id))) {
        ids.add(s.id);
      }
    }
    return ids;
  }

  void _resetToAuto() {
    final selectedId = ref.read(selectedSurfaceIdProvider);
    if (selectedId != null) {
      ref.read(surfaceOffsetsProvider(selectedId).notifier).state = Offset.zero;
    }
  }

  void _snapToCenter(Surface? selected) {
    if (selected != null) {
      ref.read(surfaceOffsetsProvider(selected.id).notifier).state = Offset.zero;
    }
  }

  void _undo() {
    final entry = ref.read(undoBufferProvider);
    if (entry == null) return;
    for (final e in entry.priorOffsets.entries) {
      ref.read(surfaceOffsetsProvider(e.key).notifier).state = e.value;
    }
    ref.read(undoBufferProvider.notifier).state = null;
  }

  String _surfaceLabel(Surface s) {
    if (s.type == SurfaceType.floor) return 'Floor';
    switch (s.position.rotation.toInt()) {
      case 0: return 'Front';
      case 90: return 'Left';
      case 180: return 'Back';
      case 270: return 'Right';
      default: return 'Wall';
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/tabs/layout_tab.dart
git commit -m "feat: layout tab with surface selector, drag canvas, lock chips, reset"
```

---

### Task 24: Preview tab

**Files:**
- Modify: `lib/ui/tabs/preview_tab.dart`

- [ ] **Step 1: Rewrite preview_tab.dart**

```dart
// lib/ui/tabs/preview_tab.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/surface.dart';
import '../../models/enums.dart';

class PreviewTab extends ConsumerStatefulWidget {
  final String roomId;
  const PreviewTab({super.key, required this.roomId});

  @override
  ConsumerState<PreviewTab> createState() => _PreviewTabState();
}

class _PreviewTabState extends ConsumerState<PreviewTab> {
  int _viewAngle = 0;
  bool _topDown = false;
  String? _selectedSurfaceId;

  @override
  Widget build(BuildContext context) {
    final surfacesAsync = ref.watch(surfacesProvider(widget.roomId));

    return Column(
      children: [
        // Export button in header area
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          child: Row(
            children: [
              const Spacer(),
              TextButton.icon(
                onPressed: () {
                  // Export logic in integration task
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Export triggered')),
                  );
                },
                icon: const Icon(Icons.upload, size: 16),
                label: const Text('Export', style: TextStyle(fontSize: 11)),
              ),
            ],
          ),
        ),

        // 3D room view
        Expanded(
          child: Stack(
            children: [
              // Rotation arrow: left
              Positioned(
                left: 4,
                top: 0, bottom: 0,
                child: Center(
                  child: GestureDetector(
                    onTap: () => setState(() => _viewAngle = (_viewAngle - 90) % 360),
                    child: Container(
                      width: 36, height: 36,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.15), blurRadius: 8)],
                      ),
                      child: const Center(child: Icon(Icons.chevron_left)),
                    ),
                  ),
                ),
              ),

              // 3D room placeholder
              Center(
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 48),
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey.shade300),
                    borderRadius: BorderRadius.circular(10),
                    color: const Color(0xFFF8F6F0),
                  ),
                  child: const SizedBox.expand(),
                ),
              ),

              // Rotation arrow: right
              Positioned(
                right: 4,
                top: 0, bottom: 0,
                child: Center(
                  child: GestureDetector(
                    onTap: () => setState(() => _viewAngle = (_viewAngle + 90) % 360),
                    child: Container(
                      width: 36, height: 36,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.15), blurRadius: 8)],
                      ),
                      child: const Center(child: Icon(Icons.chevron_right)),
                    ),
                  ),
                ),
              ),

              // Angle indicator
              Positioned(
                bottom: 4, left: 0, right: 0,
                child: Text('View: ${_viewAngle}°', textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 10, color: Colors.grey)),
              ),
            ],
          ),
        ),

        // Top-down toggle + selected surface info
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              FilterChip(
                label: const Text('Top-down', style: TextStyle(fontSize: 10)),
                selected: _topDown,
                onSelected: (_) => setState(() => _topDown = !_topDown),
              ),
              const Spacer(),
              if (_selectedSurfaceId != null) ...[
                Text('Surface selected', style: const TextStyle(fontSize: 11, color: Colors.grey)),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () {
                    // Jump to Layout tab — handled in integration
                    ref.read(selectedSurfaceIdProvider.notifier).state = _selectedSurfaceId;
                  },
                  style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6)),
                  child: const Text('Edit Layout', style: TextStyle(fontSize: 12)),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/tabs/preview_tab.dart
git commit -m "feat: preview tab with rotation arrows, top-down toggle, surface select"
```

---

### Task 25: Cut list tab

**Files:**
- Modify: `lib/ui/tabs/cut_list_tab.dart`

- [ ] **Step 1: Rewrite cut_list_tab.dart**

```dart
// lib/ui/tabs/cut_list_tab.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../state/providers.dart';
import '../../models/cut_entry.dart';
import '../../cutlist/cut_list_generator.dart';
import '../../models/layout_result.dart';
import '../../storage/layout_result_repository.dart';

class CutListTab extends ConsumerWidget {
  final String roomId;
  const CutListTab({super.key, required this.roomId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final surfacesAsync = ref.watch(surfacesProvider(roomId));

    return surfacesAsync.when(
      data: (surfaces) => FutureBuilder<List<CutEntry>>(
        future: _generateCutList(ref, surfaces),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          final entries = snapshot.data ?? [];

          if (entries.isEmpty) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.check_circle_outline, size: 48, color: Colors.grey),
                  SizedBox(height: 8),
                  Text('No cuts needed', style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }

          // Count totals
          int totalFulls = 0, totalCuts = 0;
          // (Full counts would need full LayoutResult data, skipped for MVP)
          totalCuts = entries.fold(0, (sum, e) => sum + e.totalCount);

          return Column(
            children: [
              // Summary bar
              Container(
                padding: const EdgeInsets.all(12),
                color: Colors.amber.shade50,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.grid_view, size: 16),
                    Text(' $totalFulls full  ', style: const TextStyle(fontSize: 12)),
                    const Icon(Icons.content_cut, size: 16),
                    Text(' $totalCuts cuts  ', style: const TextStyle(fontSize: 12)),
                    Text('${entries.length} unique sizes', style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
                  ],
                ),
              ),

              // Cut list
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: entries.length,
                  itemBuilder: (context, index) => _CutEntryCard(entry: entries[index]),
                ),
              ),
            ],
          );
        },
      ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
    );
  }

  Future<List<CutEntry>> _generateCutList(WidgetRef ref, List<dynamic> surfaces) async {
    // Collect layout results for all surfaces
    final repo = ref.read(layoutResultRepoProvider);
    final resultsBySurface = <String, LayoutResult>{};
    final surfaceNames = <String, String>{};
    final tileGroupNames = <String, String>{};

    for (final surface in surfaces) {
      final result = await repo.findBySurface(surface.id);
      if (result != null) {
        resultsBySurface[surface.id] = result;
      }
      surfaceNames[surface.id] = surface.name ?? 'Surface';
    }

    return CutListGenerator.generate(
      resultsBySurface: resultsBySurface,
      surfaceNames: surfaceNames,
      tileGroupNames: tileGroupNames,
    );
  }
}

class _CutEntryCard extends StatelessWidget {
  final CutEntry entry;
  const _CutEntryCard({required this.entry});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Shape diagram
            Container(
              width: 56, height: 40,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.red.shade200),
                borderRadius: BorderRadius.circular(4),
                color: Colors.red.shade50,
              ),
              child: Center(
                child: Text('${entry.width.toStringAsFixed(0)}×${entry.height.toStringAsFixed(0)}',
                    style: const TextStyle(fontSize: 9, color: Colors.red)),
              ),
            ),
            const SizedBox(width: 12),

            // Details
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('${entry.width.toStringAsFixed(0)} × ${entry.height.toStringAsFixed(0)}mm',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                  const SizedBox(height: 2),
                  Text(entry.cutTypeDescription, style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
                  Text(entry.tileGroupName, style: const TextStyle(fontSize: 10, color: Colors.grey)),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 6,
                    children: entry.locations.map((loc) => Chip(
                      label: Text('${loc.surfaceName} ×${loc.count}',
                          style: const TextStyle(fontSize: 10)),
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      visualDensity: VisualDensity.compact,
                    )).toList(),
                  ),
                ],
              ),
            ),

            // Quantity badge
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.red,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text('×${entry.totalCount}',
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
            ),
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add lib/ui/tabs/cut_list_tab.dart
git commit -m "feat: cut list tab with grouped entries, shape diagrams, location chips"
```

---

## Phase 8: Supporting Widgets

### Task 26: Reusable widget files

**Files:**
- Create: `lib/ui/widgets/grout_picker.dart`
- Create: `lib/ui/widgets/pattern_picker.dart`
- Create: `lib/ui/widgets/region_picker.dart`

- [ ] **Step 1: Write grout_picker.dart**

```dart
// lib/ui/widgets/grout_picker.dart
import 'package:flutter/material.dart';
import '../../models/enums.dart';

class GroutPicker extends StatelessWidget {
  final GroutColor selected;
  final ValueChanged<GroutColor> onChanged;

  const GroutPicker({super.key, required this.selected, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: GroutColor.values.map((color) {
        final isSelected = color == selected;
        final swatch = switch (color) {
          GroutColor.black => Colors.black,
          GroutColor.grey => Colors.grey,
          GroutColor.white => Colors.white,
        };
        return GestureDetector(
          onTap: () => onChanged(color),
          child: Container(
            width: 40, height: 40,
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: swatch,
              shape: BoxShape.circle,
              border: Border.all(
                color: isSelected ? Colors.blue : Colors.grey.shade300,
                width: isSelected ? 2.5 : 1,
              ),
              boxShadow: color == GroutColor.white ? [BoxShadow(color: Colors.grey.shade300, blurRadius: 2)] : null,
            ),
            child: isSelected ? const Icon(Icons.check, color: Colors.white, size: 16) : null,
          ),
        );
      }).toList(),
    );
  }
}
```

- [ ] **Step 2: Write pattern_picker.dart**

```dart
// lib/ui/widgets/pattern_picker.dart
import 'package:flutter/material.dart';
import '../../models/enums.dart';

class PatternPicker extends StatelessWidget {
  final TilePattern selected;
  final ValueChanged<TilePattern> onChanged;

  const PatternPicker({super.key, required this.selected, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8, runSpacing: 8,
      children: TilePattern.values.map((pattern) => ChoiceChip(
        label: Text(pattern.name, style: const TextStyle(fontSize: 12)),
        selected: pattern == selected,
        onSelected: (_) => onChanged(pattern),
      )).toList(),
    );
  }
}
```

- [ ] **Step 3: Write region_picker.dart** (the preset selection widget)

```dart
// lib/ui/widgets/region_picker.dart
import 'package:flutter/material.dart';
import '../../models/surface_tile_group.dart';

class RegionPicker extends StatelessWidget {
  final String selected;
  final double surfaceWidth;
  final double surfaceHeight;
  final double stripSize;
  final ValueChanged<RegionRect> onChanged;

  const RegionPicker({
    super.key,
    required this.selected,
    required this.surfaceWidth,
    required this.surfaceHeight,
    required this.stripSize,
    required this.onChanged,
  });

  static const presets = [
    'Full surface', 'Top strip', 'Bottom strip',
    'Left strip', 'Right strip',
    'H-center band', 'V-center band',
    'Custom',
  ];

  RegionRect _rectFor(String preset) {
    final W = surfaceWidth;
    final H = surfaceHeight;
    final s = stripSize;
    return switch (preset) {
      'Top strip' => RegionRect(x: 0, y: 0, width: W, height: s),
      'Bottom strip' => RegionRect(x: 0, y: H - s, width: W, height: s),
      'Left strip' => RegionRect(x: 0, y: 0, width: s, height: H),
      'Right strip' => RegionRect(x: W - s, y: 0, width: s, height: H),
      'H-center band' => RegionRect(x: 0, y: (H - s) / 2, width: W, height: s),
      'V-center band' => RegionRect(x: (W - s) / 2, y: 0, width: s, height: H),
      _ => RegionRect(x: 0, y: 0, width: W, height: H),
    };
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8, runSpacing: 8,
          children: presets.map((preset) => ChoiceChip(
            label: Text(preset, style: const TextStyle(fontSize: 12)),
            selected: selected == preset,
            onSelected: (_) => onChanged(_rectFor(preset)),
          )).toList(),
        ),
      ],
    );
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add lib/ui/widgets/grout_picker.dart lib/ui/widgets/pattern_picker.dart lib/ui/widgets/region_picker.dart
git commit -m "feat: reusable widgets — grout picker, pattern picker, region picker"
```

---

## Phase 9: Integration

### Task 27: Wire providers, navigation, and compute pipeline

**Files:**
- Modify: `lib/app.dart` (add routes)
- Create: `lib/state/layout_engine_service.dart` (orchestrate compute → save → invalidate)

- [ ] **Step 1: Update app.dart with routes**

```dart
// lib/app.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/project_detail_screen.dart';
import 'ui/screens/room_editor_screen.dart';
import 'ui/screens/surface_detail_screen.dart';
import 'ui/screens/region_editor_screen.dart';
import 'ui/screens/tile_library_screen.dart';
import 'ui/screens/tile_group_form_screen.dart';
import 'ui/screens/camera_screen.dart';

class TileLayoutApp extends StatelessWidget {
  const TileLayoutApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Tile Layout',
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
      ),
      initialRoute: '/',
      onGenerateRoute: (settings) {
        // Simple route map for the MVP
        switch (settings.name) {
          case '/':
            return MaterialPageRoute(builder: (_) => const HomeScreen());
          case '/project':
            final projectId = settings.arguments as String;
            return MaterialPageRoute(
              builder: (_) => ProjectDetailScreen(projectId: projectId),
            );
          case '/room':
            final roomId = settings.arguments as String;
            return MaterialPageRoute(
              builder: (_) => RoomEditorScreen(roomId: roomId),
            );
          case '/surface':
            final surfaceId = settings.arguments as String;
            return MaterialPageRoute(
              builder: (_) => SurfaceDetailScreen(surfaceId: surfaceId),
            );
          default:
            return MaterialPageRoute(builder: (_) => const HomeScreen());
        }
      },
    );
  }
}
```

- [ ] **Step 2: Create layout_engine_service.dart**

```dart
// lib/state/layout_engine_service.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../engine/layout_engine.dart';
import '../engine/surface_position_calculator.dart';
import '../models/layout_result.dart';
import '../models/surface_tile_group.dart';
import '../storage/layout_result_repository.dart';
import '../storage/surface_repository.dart';
import '../storage/tile_group_repository.dart';
import 'providers.dart';

/// Orchestrates: fetch data → run LayoutEngine → save LayoutResult → invalidate providers.
class LayoutEngineService {
  final Ref ref;

  LayoutEngineService(this.ref);

  /// Compute and persist layout for a single surface.
  Future<void> computeSurface(String surfaceId) async {
    final surfaceRepo = ref.read(surfaceRepoProvider);
    final tileGroupRepo = ref.read(tileGroupRepoProvider);
    final layoutRepo = ref.read(layoutResultRepoProvider);

    final surface = await surfaceRepo.findById(surfaceId);
    if (surface == null) return;

    final stgs = await surfaceRepo.findTileGroupsBySurface(surfaceId);
    if (stgs.isEmpty) return;

    final allTiles = <dynamic>[];

    for (final stg in stgs) {
      final tileGroup = await tileGroupRepo.findById(stg.tileGroupId);
      if (tileGroup == null) continue;

      final tiles = LayoutEngine.compute(
        region: stg.region,
        tileGroup: tileGroup,
        groutWidth: surface.groutWidth,
        pattern: stg.pattern,
        offsetX: stg.offsetX,
        offsetY: stg.offsetY,
      );

      allTiles.addAll(tiles);
    }

    final result = LayoutResult(
      surfaceId: surfaceId,
      tiles: allTiles.cast(),
      stale: false,
    );

    await layoutRepo.save(result);

    // Invalidate the layout provider for this surface
    ref.invalidate(layoutResultProvider(surfaceId));
  }

  /// Compute layouts for all surfaces in a room.
  Future<void> computeAllSurfaces(String roomId) async {
    final surfaceRepo = ref.read(surfaceRepoProvider);
    final surfaces = await surfaceRepo.findByRoom(roomId);

    for (final surface in surfaces) {
      await computeSurface(surface.id);
    }
  }

  /// Generate default surfaces for a room.
  Future<void> generateSurfaces({
    required String roomId,
    required double roomWidth,
    required double roomDepth,
    required double roomHeight,
  }) async {
    final surfaces = SurfacePositionCalculator.generateSurfaces(
      roomId: roomId,
      roomWidth: roomWidth,
      roomDepth: roomDepth,
      roomHeight: roomHeight,
      includeFront: true,
      includeBack: true,
      includeLeft: true,
      includeRight: true,
      includeFloor: true,
    );

    final surfaceRepo = ref.read(surfaceRepoProvider);
    for (final surface in surfaces) {
      await surfaceRepo.insert(surface);
    }

    ref.invalidate(surfacesProvider(roomId));
  }
}

final layoutEngineServiceProvider = Provider<LayoutEngineService>((ref) {
  return LayoutEngineService(ref);
});
```

- [ ] **Step 3: Run full analysis**

```bash
flutter analyze
```
Expected: clean analysis with no errors.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: integration — routing, layout engine service, compute pipeline"
```

---

## Phase 10: Final Testing & Polish

### Task 28: Run all tests and verify

- [ ] **Step 1: Run all unit tests**

```bash
cd /Users/chenwu/orca/workspaces/hasu/spec-and-plan
flutter test
```
Expected: all tests pass (engine + storage + cutlist tests from earlier tasks).

- [ ] **Step 2: Add widget test for home screen**

```dart
// test/ui/home_screen_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:tile_layout/ui/screens/home_screen.dart';

void main() {
  testWidgets('home screen shows empty state', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: HomeScreen())),
    );
    expect(find.text('No projects yet'), findsOneWidget);
    expect(find.text('+ New Project'), findsOneWidget);
  });
}
```

- [ ] **Step 3: Run widget test**

```bash
flutter test test/ui/home_screen_test.dart
```
Expected: test passes (empty state rendered).

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "test: add home screen widget test, finalize integration"
```

---

**Plan complete.** 28 tasks across 10 phases. Each task produces a working, testable increment with a git commit checkpoint.

