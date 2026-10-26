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
    @Published var previewCamera = OrbitCamera(yawDegrees: OrbitCamera.companion.DEFAULT_YAW, pitchDegrees: OrbitCamera.companion.DEFAULT_PITCH, zoom: 1.0)
    @Published var hiddenSurfaceIds: Set<String> = []
    @Published var previewScene: RoomScene = RoomScene.companion.EMPTY
    @Published var lockedSurfaceIds: Set<String> = []
    @Published var hasUndoBuffer = false
    @Published var currentTiles: [PlacedTile] = []
    @Published var cutEntries: [CutEntry] = []
    @Published var isDragging = false
    /// surfaceId → door surface-local rect, for the 2D layout tab.
    @Published var doorLocalRects: [String: RegionRect] = [:]

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

    // MARK: - 3D preview

    func orbitPreview(deltaYaw: Double, deltaPitch: Double) {
        sharedVM.orbitPreview(deltaYawDegrees: deltaYaw, deltaPitchDegrees: deltaPitch)
        refreshPreviewCamera()
    }

    func topPreviewCamera() {
        sharedVM.topPreviewCamera()
        refreshPreviewCamera()
    }

    func zoomPreviewBy(_ factor: Double) {
        sharedVM.zoomPreviewBy(factor: factor)
        refreshPreviewCamera()
    }

    func resetPreviewCamera() {
        sharedVM.resetPreviewCamera()
        refreshPreviewCamera()
    }

    func toggleSurfaceHidden(_ id: String) {
        sharedVM.toggleSurfaceHidden(surfaceId: id)
        refreshHiddenSurfaces()
    }

    func showAllSurfaces() {
        sharedVM.showAllSurfaces()
        refreshHiddenSurfaces()
    }

    /// Surface under a point of the 3D view (any unit, as long as the viewport size uses the same one).
    func pickSurface(x: Double, y: Double, viewportWidth: Double, viewportHeight: Double) -> String? {
        sharedVM.pickSurface(x: x, y: y, viewportWidth: viewportWidth, viewportHeight: viewportHeight)
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
        "\(surface.displayName()) \(Int(surface.width))×\(Int(surface.height))"
    }

    // MARK: - Private

    private func refresh() {
        surfaces = sharedVM.surfaces.value as? [Surface] ?? []
        selectedSurfaceId = sharedVM.selectedSurfaceId.value as? String
        refreshPreviewCamera()
        refreshHiddenSurfaces()
        if let scene = sharedVM.previewScene.value as? RoomScene, scene !== previewScene {
            previewScene = scene
        }
        lockedSurfaceIds = Set((sharedVM.lockedSurfaceIds.value as? Set<AnyHashable>)?.compactMap { $0 as? String } ?? [])
        currentTiles = sharedVM.currentTiles.value as? [PlacedTile] ?? []
        cutEntries = sharedVM.cutEntries.value as? [CutEntry] ?? []
        hasUndoBuffer = sharedVM.undoBuffer.value != nil
        doorLocalRects = sharedVM.doorLocalRects.value as? [String: RegionRect] ?? [:]
    }

    private func refreshPreviewCamera() {
        if let camera = sharedVM.previewCamera.value as? OrbitCamera {
            previewCamera = camera
        }
    }

    private func refreshHiddenSurfaces() {
        hiddenSurfaceIds = Set((sharedVM.hiddenSurfaceIds.value as? Set<AnyHashable>)?.compactMap { $0 as? String } ?? [])
    }
}
