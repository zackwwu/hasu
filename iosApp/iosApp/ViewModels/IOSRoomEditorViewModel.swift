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
    @Published var lockedSurfaceIds: Set<String> = []
    @Published var undoBuffer: [String: KotlinPair<Swift.Double, Swift.Double>]? = nil
    @Published var currentTiles: [PlacedTile] = []
    @Published var isDragging = false

    private let db: TileLayoutDb = DatabaseProvider.shared.createTileLayoutDb()

    private lazy var sharedVM: RoomEditorViewModel = {
        let roomRepo = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)
        let surfaceRepo = SqlDelightSurfaceRepository(queries: db.tileLayoutDbQueries)
        let tileGroupRepo = SqlDelightTileGroupRepository(queries: db.tileLayoutDbQueries)
        let layoutRepo = SqlDelightLayoutResultRepository(queries: db.tileLayoutDbQueries)
        return RoomEditorViewModel(
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
        sharedVM.selectSurface(id: id)
        refresh()
        // Trigger layout computation for newly selected surface
        if let id {
            Task {
                do {
                    try await sharedVM.computeLayout(surfaceId: id)
                    refresh()
                } catch {
                    print("computeLayout failed: \(error)")
                }
            }
        }
    }

    func rotateView(_ delta: Int) {
        sharedVM.rotateView(delta: Int32(delta))
        refresh()
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
            try await sharedVM.onDragEnd(dx: dx, dy: dy)
            refresh()
        } catch {
            print("onDragEnd failed: \(error)")
        }
    }

    func undo() async {
        let buffer = sharedVM.undoBuffer.value as? [String: KotlinPair<Swift.Double, Swift.Double>]
        sharedVM.undo()
        if let priors = buffer {
            do {
                try await sharedVM.undoRestore(priors: priors)
                refresh()
            } catch {
                print("undoRestore failed: \(error)")
            }
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
        viewAngle = Int(sharedVM.viewAngle.value as? Int32 ?? 0)
        lockedSurfaceIds = Set(sharedVM.lockedSurfaceIds.value as? [String] ?? [])
        currentTiles = sharedVM.currentTiles.value as? [PlacedTile] ?? []
        // undoBuffer stays as the shared value for undo flow
    }
}
