import SwiftUI
import SharedLogic

/// Cut list tab showing grouped cut entries for the selected surface,
/// ordered by tile group and then cut dimension (largest first).
struct CutListTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel

    @State private var cutEntries: [CutEntry] = []

    lazy var db = DatabaseProvider.shared.createTileLayoutDb()
    lazy var surfaceRepo: SurfaceRepository = SqlDelightSurfaceRepository(queries: db.tileLayoutDbQueries)
    lazy var layoutRepo: LayoutResultRepository = SqlDelightLayoutResultRepository(queries: db.tileLayoutDbQueries)

    var body: some View {
        Group {
            if cutEntries.isEmpty {
                ContentUnavailableView(
                    "No Cuts Required",
                    systemImage: "scissors",
                    description: Text("All tiles fit within their regions without needing cuts.")
                )
            } else {
                List {
                    ForEach(Array(cutEntries.enumerated()), id: \.offset) { _, entry in
                        CutEntrySection(entry: entry)
                    }
                }
            }
        }
        .onChange(of: vm.selectedSurfaceId) { _, _ in
            Task { await generateCutList() }
        }
        .onChange(of: vm.currentTiles.count) { _, _ in
            Task { await generateCutList() }
        }
        .task { await generateCutList() }
    }

    private func generateCutList() async {
        guard let surfaceId = vm.selectedSurfaceId else {
            cutEntries = []
            return
        }
        do {
            guard let result = try await layoutRepo.getBySurface(surfaceId: surfaceId) else {
                cutEntries = []
                return
            }

            // Build lookup maps
            var surfaceNames: [String: String] = [:]
            for surface in vm.surfaces {
                surfaceNames[surface.id] = vm.displayName(for: surface)
            }

            var tileGroupNames: [String: String] = [:]
            let cutTiles = result.tiles.filter { ($0 as? PlacedTile)?.isCut ?? false }
            for tile in cutTiles {
                if let tile = tile as? PlacedTile, tileGroupNames[tile.tileGroupId] == nil {
                    tileGroupNames[tile.tileGroupId] = tile.tileGroupId // Will be resolved below
                }
            }

            let generator = CutListGenerator()
            let entries = generator.generate(
                resultsBySurface: [surfaceId: result],
                surfaceNames: surfaceNames,
                tileGroupNames: tileGroupNames
            ) as? [CutEntry] ?? []

            cutEntries = entries
        } catch {
            print("Generate cut list failed: \(error)")
            cutEntries = []
        }
    }
}

// MARK: - Cut Entry Section

private struct CutEntrySection: View {
    let entry: CutEntry

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.tileGroupName)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text("Size: \(Int(entry.width)) × \(Int(entry.height)) mm")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(entry.totalCount)×")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(.orange)
                }

                // Cut type
                Label(entry.cutTypeDescription, systemImage: "scissors")
                    .font(.caption)
                    .foregroundStyle(.orange)

                // Per-surface breakdown
                ForEach(entry.locations as? [CutLocation] ?? [], id: \.surfaceId) { location in
                    HStack {
                        Text(location.surfaceName)
                            .font(.caption)
                        Spacer()
                        Text("\(location.count) cuts")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                // Mini diagram (simple rectangle with marked cut edges)
                cutDiagram
            }
            .padding(.vertical, 4)
        }
    }

    private var cutDiagram: some View {
        Canvas { context, size in
            let rect = CGRect(x: 4, y: 4, width: size.width - 8, height: size.height - 8)
            context.fill(Path(rect), with: .color(.orange.opacity(0.15)))
            context.stroke(Path(rect), with: .color(.orange.opacity(0.5)), lineWidth: 1)

            // Mark cut edges
            let edges = entry.cutEdgesKey.split(separator: ",").map(String.init)
            for edge in edges {
                let markerRect: CGRect
                switch edge.trimmingCharacters(in: .whitespaces) {
                case "LEFT":
                    markerRect = CGRect(x: rect.minX, y: rect.minY, width: 3, height: rect.height)
                case "RIGHT":
                    markerRect = CGRect(x: rect.maxX - 3, y: rect.minY, width: 3, height: rect.height)
                case "TOP":
                    markerRect = CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: 3)
                case "BOTTOM":
                    markerRect = CGRect(x: rect.minX, y: rect.maxY - 3, width: rect.width, height: 3)
                default:
                    continue
                }
                context.fill(Path(markerRect), with: .color(.red))
            }

            // Draw tile dimensions text
            let label = "\(Int(entry.width))×\(Int(entry.height))"
            let text = Text(label).font(.system(size: 8)).foregroundColor(.orange)
            context.draw(text, at: CGPoint(x: rect.midX, y: rect.midY))
        }
        .frame(height: 60)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

#Preview {
    CutListTabView(vm: IOSRoomEditorViewModel())
}
