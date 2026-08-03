import SwiftUI
import SharedLogic

/// Lists tile groups for a project. Supports add/edit/delete.
struct TileLibraryView: View {
    let projectId: String

    @State private var tileGroups: [TileGroup] = []
    @State private var showAdd = false

    private let tileGroupRepo: TileGroupRepository

    init(projectId: String) {
        self.projectId = projectId
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.tileGroupRepo = SqlDelightTileGroupRepository(queries: database.tileLayoutDbQueries)
    }

    var body: some View {
        Group {
            if tileGroups.isEmpty {
                ContentUnavailableView(
                    "No Tiles",
                    systemImage: "square.grid.3x3.topleft.filled",
                    description: Text("Add tile groups to use in your layouts.")
                )
            } else {
                List {
                    ForEach(tileGroups, id: \.id) { tg in
                        TileGroupRow(tileGroup: tg)
                    }
                    .onDelete { indexSet in
                        let repo = tileGroupRepo
                        Task {
                            for idx in indexSet {
                                try? await repo.delete(id: tileGroups[idx].id)
                            }
                            await load()
                        }
                    }
                }
            }
        }
        .toolbar {
            Button {
                showAdd = true
            } label: {
                Image(systemName: "plus")
            }
        }
        .sheet(isPresented: $showAdd) {
            AddTileGroupSheet(projectId: projectId) {
                Task { await load() }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            tileGroups = try await tileGroupRepo.getByProject(projectId: projectId)
        } catch {
            print("Load tile groups failed: \(error)")
        }
    }
}

// MARK: - Tile Group Row

private struct TileGroupRow: View {
    let tileGroup: TileGroup

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.blue.opacity(0.2))
                .frame(width: 44, height: 44)
                .overlay {
                    Image(systemName: "square.grid.3x3")
                        .foregroundStyle(.blue)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(tileGroup.name)
                    .font(.body)
                    .fontWeight(.medium)
                Text("\(Int(tileGroup.tileWidth)) × \(Int(tileGroup.tileHeight)) mm")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            SourceBadge(source: tileGroup.source)
        }
        .padding(.vertical, 2)
    }
}

private struct SourceBadge: View {
    let source: TileSource

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: source == TileSource.captured ? "camera.fill" : "square.and.arrow.down")
                .font(.system(size: 8))
            Text(source == TileSource.captured ? "Photo" : "Import")
                .font(.caption2)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 2)
        .background(Color.teal.opacity(0.15))
        .clipShape(Capsule())
    }
}

// MARK: - Add Tile Group Sheet

private struct AddTileGroupSheet: View {
    let projectId: String
    let onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var tileWidth: Double = 300
    @State private var tileHeight: Double = 200

    private let tileGroupRepo: TileGroupRepository
    private let typeId = TypeId()

    init(projectId: String, onDismiss: @escaping () -> Void) {
        self.projectId = projectId
        self.onDismiss = onDismiss
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.tileGroupRepo = SqlDelightTileGroupRepository(queries: database.tileLayoutDbQueries)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Tile name (e.g. White Ceramic)", text: $name)

                Section("Tile Size (mm)") {
                    HStack {
                        Text("Width")
                        Spacer()
                        TextField("Width", value: $tileWidth, format: .number)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("Height")
                        Spacer()
                        TextField("Height", value: $tileHeight, format: .number)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            .navigationTitle("New Tile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        let repo = tileGroupRepo
                        let tid = typeId
                        Task {
                            let tg = TileGroup(
                                id: tid.generate(prefix: "tg"),
                                projectId: projectId,
                                name: name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                    ? "Tile" : name.trimmingCharacters(in: .whitespacesAndNewlines),
                                tileWidth: tileWidth,
                                tileHeight: tileHeight,
                                texturePath: nil,
                                source: TileSource.imported
                            )
                            try? await repo.insert(tileGroup: tg)
                            onDismiss()
                            dismiss()
                        }
                    }
                    .disabled(tileWidth <= 0 || tileHeight <= 0)
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        TileLibraryView(projectId: "preview-project")
    }
}
