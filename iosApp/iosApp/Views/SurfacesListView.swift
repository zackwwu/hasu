import SwiftUI
import SharedLogic

/// Lists all surfaces for a room and provides a button to auto-generate them.
struct SurfacesListView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    let roomId: String

    @State private var room: Room? = nil

    lazy var db = DatabaseProvider.shared.createTileLayoutDb()
    lazy var roomRepo: RoomRepository = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)

    var body: some View {
        Group {
            if vm.surfaces.isEmpty {
                emptyState
            } else {
                surfaceList
            }
        }
        .task {
            await loadRoomInfo()
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 20) {
            if let room {
                VStack(spacing: 4) {
                    Text(room.name)
                        .font(.headline)
                    Text("\(Int(room.width)) × \(Int(room.depth)) × \(Int(room.height)) mm")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            ContentUnavailableView(
                "No Surfaces",
                systemImage: "square.split.bottomrightquarter",
                description: Text("Generate wall and floor surfaces from room dimensions.")
            )

            Button {
                Task { await generateDefaultSurfaces() }
            } label: {
                Label("Generate Surfaces", systemImage: "sparkles")
            }
            .buttonStyle(.borderedProminent)
        }
    }

    // MARK: - Surface List

    private var surfaceList: some View {
        List {
            if let room {
                Section("Room") {
                    HStack {
                        Text(room.name)
                            .font(.headline)
                        Spacer()
                        Text("\(Int(room.width)) × \(Int(room.depth)) × \(Int(room.height)) mm")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section("Surfaces (\(vm.surfaces.count))") {
                ForEach(vm.surfaces, id: \.id) { surface in
                    NavigationLink {
                        SurfaceDetailView(surface: surface, vm: vm)
                    } label: {
                        SurfaceRow(surface: surface)
                    }
                }
            }
        }
    }

    // MARK: - Surface Generation

    private func generateDefaultSurfaces() async {
        guard let room else { return }
        let calc = SurfacePositionCalculator()
        let generated = calc.generate(
            roomId: room.id,
            roomWidth: room.width,
            roomDepth: room.depth,
            roomHeight: room.height,
            includeFront: true,
            includeBack: true,
            includeLeft: true,
            includeRight: true,
            includeFloor: true
        ) as? [Surface] ?? []

        let surfaceRepo: SurfaceRepository = SqlDelightSurfaceRepository(queries: db.tileLayoutDbQueries)

        do {
            for surface in generated {
                try await surfaceRepo.insert(surface: surface)
            }
            // Reload surfaces into the shared VM
            try await vm.load(roomId: roomId)
            vm.selectSurface(generated.first?.id)
        } catch {
            print("Failed to insert surfaces: \(error)")
        }
    }

    private func loadRoomInfo() async {
        do {
            room = try await roomRepo.getById(id: roomId)
        } catch {
            print("Failed to load room: \(error)")
        }
    }
}

// MARK: - Surface Row

private struct SurfaceRow: View {
    let surface: Surface

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: surface.type == SurfaceType.wall ? "square.fill" : "square.fill.text.grid.1x2")
                .foregroundStyle(surface.type == SurfaceType.wall ? .blue : .brown)
                .font(.title3)

            VStack(alignment: .leading, spacing: 2) {
                Text(displayName)
                    .font(.body)
                    .fontWeight(.medium)

                HStack(spacing: 8) {
                    Text("\(Int(surface.width)) × \(Int(surface.height)) mm")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Circle()
                        .fill(groutColor)
                        .frame(width: 10, height: 10)
                }
            }

            Spacer()

            Text(surface.type == SurfaceType.wall ? "Wall" : "Floor")
                .font(.caption2)
                .padding(.horizontal, 8)
                .padding(.vertical, 2)
                .background(surface.type == SurfaceType.wall ? Color.blue.opacity(0.15) : Color.brown.opacity(0.15))
                .clipShape(Capsule())
        }
        .padding(.vertical, 2)
    }

    private var displayName: String {
        let typeName = surface.type == SurfaceType.wall ? "Wall" : "Floor"
        return "\(typeName) \(Int(surface.width))×\(Int(surface.height))"
    }

    private var groutColor: Color {
        switch surface.groutColor {
        case GroutColor.black: return .black
        case GroutColor.white: return .white
        default: return .gray
        }
    }
}

#Preview {
    NavigationStack {
        SurfacesListView(vm: IOSRoomEditorViewModel(), roomId: "preview")
    }
}
