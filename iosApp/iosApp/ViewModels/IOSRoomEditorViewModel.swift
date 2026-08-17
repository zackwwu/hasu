import Foundation
import SharedLogic

/// ObservableObject wrapper around the shared `RoomEditorViewModel`.
///
/// Bridges KMP StateFlow values to SwiftUI @Published properties.
/// The shared VM handles all business logic; this class only reads
/// StateFlow values and forwards user actions.
@MainActor
final class IOSRoomEditorViewModel: ObservableObject {
    @Published var surfaces: [Surface] = []
    @Published var selectedSurfaceId: String? = nil
    @Published var viewAngle: Int = 0
    @Published var previewZoom: Double = 1.0
    @Published var lockedSurfaceIds: Set<String> = []
    @Published var hasUndoBuffer = false
    @Published var currentTiles: [PlacedTile] = []
    @Published var cutEntries: [CutEntry] = []
    @Published var isDragging = false

    private let db: TileLayoutDb = DatabaseProvider.shared.createTileLayoutDb()

    private lazy var sharedVM: RoomEditorViewModel = {
        let roomRepo = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)
        let surfaceRepo = SqlDelightSurfaceRepository(queries: db.tileLayoutDbQueries)
        let tileGroupRepo = SqlDelightTileGroupRepository(queries: db.tileLayoutDbQueries)
        let layoutRepo = SqlDelightLayoutResultRepository(queries: db.tileLayoutDbQueries)
        return DatabaseProvider.shared.createRoomEditorViewModel(
            roomRepo: roomRepo,
            surfaceRepo: surfaceRepo,
            tileGroupRepo: tileGroupRepo,
            layoutRepo: layoutRepo
        )
    }()

    private lazy var roomRepo: RoomRepository = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)
    private lazy var surfaceRepo: SurfaceRepository = SqlDelightSurfaceRepository(queries: db.tileLayoutDbQueries)
    private lazy var tileGroupRepo: TileGroupRepository = SqlDelightTileGroupRepository(queries: db.tileLayoutDbQueries)
    private lazy var layoutRepo: LayoutResultRepository = SqlDelightLayoutResultRepository(queries: db.tileLayoutDbQueries)

    // MARK: - Public API

    func load(roomId: String) async {
        do {
            try await sharedVM.loadSurfaces(roomId: roomId)
            refresh()
        } catch {
            print("IOSRoomEditorViewModel.load failed: \(error)")
        }
    }

    func selectSurface(_ id: String?) {
        Task {
            do {
                try await sharedVM.selectSurface(id: id)
                refresh()
            } catch {
                print("selectSurface failed: \(error)")
            }
        }
    }

    func rotateView(_ delta: Int) {
        sharedVM.rotateView(delta: Int32(delta))
        refresh()
    }

    func zoomPreviewBy(_ factor: Double) {
        sharedVM.zoomPreviewBy(factor: factor)
        previewZoom = (sharedVM.previewZoom.value as? NSNumber)?.doubleValue ?? 1.0
    }

    func setPreviewZoom(_ zoom: Double) {
        sharedVM.setPreviewZoom(zoom: zoom)
        previewZoom = (sharedVM.previewZoom.value as? NSNumber)?.doubleValue ?? 1.0
    }

    func resetPreviewZoom() {
        sharedVM.resetPreviewZoom()
        previewZoom = 1.0
    }

    func toggleLock(_ id: String) {
        sharedVM.toggleLock(surfaceId: id)
        refresh()
    }

    func onDragStart() async {
        do {
            try await sharedVM.onDragStart()
            refresh()
        } catch {
            print("onDragStart failed: \(error)")
        }
    }

    func onDragEnd(dx: Double, dy: Double) async {
        do {
            // The shared VM computes the layout synchronously inside onDragEnd,
            // so a plain refresh reads the fresh tiles — no sleep hack needed.
            try await sharedVM.onDragEnd(dx: dx, dy: dy)
            refresh()
        } catch {
            print("onDragEnd failed: \(error)")
        }
    }

    /// Synchronously recompute the layout for a surface, then refresh.
    /// Used as the reactive path after mutations that bypass the debounce.
    func computeAndRefresh(surfaceId: String) async {
        do {
            try await sharedVM.computeLayout(surfaceId: surfaceId)
            refresh()
        } catch {
            print("computeAndRefresh failed: \(error)")
        }
    }

    func undo() async {
        do {
            try await sharedVM.undo()
            refresh()
        } catch {
            print("undo failed: \(error)")
        }
    }

    func hitTest(tapX: Double, tapY: Double, canvasWidth: Double, canvasHeight: Double) -> String? {
        return sharedVM.hitTest(
            tapX: tapX,
            tapY: tapY,
            canvasWidth: canvasWidth,
            canvasHeight: canvasHeight
        )
    }

    func loadRoom(projectId: String) async -> [Room] {
        do {
            return try await roomRepo.getByProject(projectId: projectId)
        } catch {
            return []
        }
    }

    /// Recompute layout for an arbitrary surface (used by SurfaceDetailView for grout changes).
    func computeLayoutForSurface(surfaceId: String) async {
        do {
            try await sharedVM.computeLayout(surfaceId: surfaceId)
            refresh()
        } catch {
            print("computeLayout failed: \(error)")
        }
    }

    /// Reset offsets to zero and recompute layout.
    func resetToAuto(surfaceId: String) async {
        do {
            try await sharedVM.resetToAuto(surfaceId: surfaceId)
            refresh()
        } catch {
            print("resetToAuto failed: \(error)")
        }
    }

    /// Snap offsets to center pattern on surface and recompute layout.
    func snapToCenter(surfaceId: String) async {
        do {
            try await sharedVM.snapToCenter(surfaceId: surfaceId)
            refresh()
        } catch {
            print("snapToCenter failed: \(error)")
        }
    }

    // MARK: - Helpers

    var otherSurfaces: [Surface] {
        surfaces.filter { $0.id != selectedSurfaceId }
    }

    var selectedSurface: Surface? {
        surfaces.first { $0.id == selectedSurfaceId }
    }

    func isLocked(_ id: String) -> Bool {
        lockedSurfaceIds.contains(id)
    }

    func displayName(for surface: Surface) -> String {
        let typeName = surface.type == SurfaceType.wall ? "Wall" : "Floor"
        return "\(typeName) \(Int(surface.width))×\(Int(surface.height))"
    }

    // MARK: - Private

    private func refresh() {
        surfaces = sharedVM.surfaces.value as? [Surface] ?? []
        selectedSurfaceId = sharedVM.selectedSurfaceId.value as? String
        if let kotlinInt = sharedVM.viewAngle.value as? NSNumber {
            viewAngle = kotlinInt.intValue
        }
        previewZoom = (sharedVM.previewZoom.value as? NSNumber)?.doubleValue ?? 1.0
        lockedSurfaceIds = Set((sharedVM.lockedSurfaceIds.value as? Set<AnyHashable>)?.compactMap { $0 as? String } ?? [])
        currentTiles = sharedVM.currentTiles.value as? [PlacedTile] ?? []
        cutEntries = sharedVM.cutEntries.value as? [CutEntry] ?? []
        hasUndoBuffer = sharedVM.undoBuffer.value != nil
    }
}
