import SwiftUI
import SharedLogic

/// Kotlin models don't bridge Identifiable — add it for sheet(item:).
extension SurfaceTileGroup: Identifiable {}

/// Detailed view for a single surface: grout settings, tile group assignments,
/// and region editor access.
struct SurfaceDetailView: View {
    let surface: Surface
    @ObservedObject var vm: IOSRoomEditorViewModel

    @State private var selectedGroutColor: GroutColor
    @State private var groutWidth: Double

    @State private var selectedSTG: SurfaceTileGroup? = nil

    @State private var stgs: [SurfaceTileGroup] = []
    @State private var tileGroups: [TileGroup] = []

    @State private var showTileGroupPicker = false
    @State private var availableTileGroups: [TileGroup] = []

    private let surfaceRepo: SurfaceRepository
    private let tileGroupRepo: TileGroupRepository
    private let roomRepo: RoomRepository
    private let typeId = TypeId()

    init(surface: Surface, vm: IOSRoomEditorViewModel) {
        self.surface = surface
        self.vm = vm
        _selectedGroutColor = State(initialValue: surface.groutColor)
        _groutWidth = State(initialValue: surface.groutWidth)
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.surfaceRepo = SqlDelightSurfaceRepository(queries: database.tileLayoutDbQueries)
        self.tileGroupRepo = SqlDelightTileGroupRepository(queries: database.tileLayoutDbQueries)
        self.roomRepo = SqlDelightRoomRepository(queries: database.tileLayoutDbQueries)
    }

    var body: some View {
        Form {
            surfaceInfoSection
            groutSection
            tileGroupSection
        }
        .navigationTitle(vm.displayName(for: surface))
        .task {
            await loadTileGroups()
        }
        .onChange(of: selectedGroutColor) { _, _ in
            Task { await updateGrout() }
        }
        .onChange(of: groutWidth) { _, _ in
            Task { await updateGrout() }
        }
        .sheet(item: $selectedSTG) { stg in
            RegionEditorView(stg: stg, surface: surface)
        }
        .sheet(isPresented: $showTileGroupPicker) {
            tileGroupPicker
        }
    }

    // MARK: - Surface Info

    private var surfaceInfoSection: some View {
        Section("Surface Info") {
            LabeledContent("Type") {
                Text(surface.type == SurfaceType.wall ? "Wall" : "Floor")
            }
            LabeledContent("Dimensions") {
                Text("\(Int(surface.width)) × \(Int(surface.height)) mm")
            }
        }
    }

    // MARK: - Grout Settings

    private var groutSection: some View {
        Section("Grout") {
            HStack(spacing: 16) {
                ForEach([GroutColor.black, GroutColor.grey, GroutColor.white], id: \.self) { color in
                    Button {
                        selectedGroutColor = color
                    } label: {
                        Circle()
                            .fill(groutSwiftUIColor(color))
                            .frame(width: 36, height: 36)
                            .overlay {
                                if selectedGroutColor == color {
                                    Circle()
                                        .stroke(.blue, lineWidth: 3)
                                        .frame(width: 42, height: 42)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 4)

            Stepper("Width: \(Int(groutWidth)) mm", value: $groutWidth, in: 1...10, step: 1)
        }
    }

    // MARK: - Tile Groups

    private var tileGroupSection: some View {
        Section {
            if stgs.isEmpty {
                HStack {
                    Text("No tile groups assigned")
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button("Add") {
                        Task { await openTileGroupPicker() }
                    }
                }
            } else {
                ForEach(stgs, id: \.id) { stg in
                    STGRow(stg: stg, tileGroups: tileGroups)
                        .onTapGesture {
                            selectedSTG = stg
                        }
                }
                .onDelete { indexSet in
                    let repo = surfaceRepo
                    Task {
                        for idx in indexSet {
                            do {
                                try await repo.deleteSTG(id: stgs[idx].id)
                            } catch {
                                print("Delete STG failed: \(error)")
                            }
                        }
                        await loadSTGs()
                    }
                }

                Button {
                    Task { await openTileGroupPicker() }
                } label: {
                    Label("Add Tile Group", systemImage: "plus")
                }
            }
        } header: {
            Text("Tile Groups")
        } footer: {
            Text("Each tile group controls pattern, offset, and region for one type of tile on this surface.")
        }
    }

    /// Picker listing every tile group in the project — the user chooses which
    /// tile this surface region uses instead of silently getting the first one.
    private var tileGroupPicker: some View {
        NavigationStack {
            List {
                if availableTileGroups.isEmpty {
                    ContentUnavailableView(
                        "No Tiles",
                        systemImage: "square.grid.3x3",
                        description: Text("Add a tile in the Tile Library first.")
                    )
                } else {
                    ForEach(availableTileGroups, id: \.id) { tg in
                        Button {
                            Task { await addSTG(tileGroupId: tg.id) }
                            showTileGroupPicker = false
                        } label: {
                            HStack(spacing: 12) {
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(Color.blue.opacity(0.2))
                                    .frame(width: 36, height: 36)
                                    .overlay {
                                        Image(systemName: "square.grid.3x3")
                                            .font(.system(size: 12))
                                            .foregroundStyle(.blue)
                                    }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tg.name)
                                        .font(.body)
                                        .fontWeight(.medium)
                                    Text("\(Int(tg.tileWidth)) × \(Int(tg.tileHeight)) mm")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("Choose Tile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showTileGroupPicker = false }
                }
            }
        }
    }

    // MARK: - Data

    private func loadTileGroups() async {
        await loadSTGs()
    }

    private func loadSTGs() async {
        do {
            stgs = try await surfaceRepo.getSTGsBySurface(surfaceId: surface.id)
            var tgs: [TileGroup] = []
            for stg in stgs {
                if let tg = try await tileGroupRepo.getById(id: stg.tileGroupId) {
                    tgs.append(tg)
                }
            }
            tileGroups = tgs
        } catch {
            print("Load STGs failed: \(error)")
        }
    }

    private func openTileGroupPicker() async {
        guard let room = try? await roomRepo.getById(id: surface.roomId) else { return }
        availableTileGroups = (try? await tileGroupRepo.getByProject(projectId: room.projectId)) ?? []
        showTileGroupPicker = true
    }

    private func addSTG(tileGroupId: String) async {
        let region = RegionRect(
            x: 0, y: 0,
            width: surface.width,
            height: surface.height
        )
        let tid = typeId
        let stg = SurfaceTileGroup(
            id: tid.generate(prefix: "stg"),
            surfaceId: surface.id,
            tileGroupId: tileGroupId,
            region: region,
            pattern: TilePattern.grid,
            offsetX: 0,
            offsetY: 0,
            locked: false
        )
        do {
            try await surfaceRepo.insertSTG(stg: stg)
            await loadSTGs()
        } catch {
            print("Insert STG failed: \(error)")
        }
    }

    private func updateGrout() async {
        do {
            try await surfaceRepo.updateGrout(
                id: surface.id,
                groutColor: selectedGroutColor,
                groutWidth: groutWidth
            )
            try await vm.load(roomId: surface.roomId)
            await vm.computeLayoutForSurface(surfaceId: surface.id)
        } catch {
            print("Update grout failed: \(error)")
        }
    }

    private func groutSwiftUIColor(_ color: GroutColor) -> Color {
        switch color {
        case GroutColor.black: return .black
        case GroutColor.white: return .white
        default: return .gray
        }
    }
}

// MARK: - STG Row

private struct STGRow: View {
    let stg: SurfaceTileGroup
    let tileGroups: [TileGroup]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(tileGroupName)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Spacer()
                PatternBadge(pattern: stg.pattern)
            }

            HStack {
                Text("Region: (\(Int(stg.region.x)), \(Int(stg.region.y))) \(Int(stg.region.width))×\(Int(stg.region.height))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("Offset: (\(String(format: "%.1f", stg.offsetX)), \(String(format: "%.1f", stg.offsetY)))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var tileGroupName: String {
        tileGroups.first { $0.id == stg.tileGroupId }?.name ?? stg.tileGroupId
    }
}

private struct PatternBadge: View {
    let pattern: TilePattern

    var body: some View {
        Text(patternName)
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Color.teal.opacity(0.15))
            .clipShape(Capsule())
    }

    private var patternName: String {
        switch pattern {
        case TilePattern.grid: return "Grid"
        case TilePattern.brick: return "Brick"
        case TilePattern.stacked: return "Stacked"
        case TilePattern.herringbone: return "Herringbone"
        default: return "Grid"
        }
    }
}

#Preview {
    NavigationStack {
        SurfaceDetailView(
            surface: Surface(
                id: "srf-1",
                roomId: "rm-1",
                type: SurfaceType.wall,
                width: 2000,
                height: 1200,
                position: SurfacePosition(x: 0, y: 0, z: 0, rotation: 0),
                groutColor: GroutColor.grey,
                groutWidth: 3,
                doorRotation: nil
            ),
            vm: IOSRoomEditorViewModel()
        )
    }
}
