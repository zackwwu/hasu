import SwiftUI
import PhotosUI
import SharedLogic

/// Kotlin models don't bridge Identifiable — add it for fullScreenCover(item:).
extension TileGroup: Identifiable {}

/// Shared texture storage: PNGs live in Documents/textures/{tileGroupId}.png.
/// Used by the tile library, the add/edit sheet, and the texture viewer.
enum TileTextureStore {
    static var directory: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("textures", isDirectory: true)
    }

    static func textureURL(for tileGroupId: String) -> URL {
        directory.appendingPathComponent("\(tileGroupId).png")
    }

    static func image(for tileGroup: TileGroup) -> UIImage? {
        guard let path = tileGroup.texturePath, !path.isEmpty else { return nil }
        return UIImage(contentsOfFile: path)
    }

    /// Persist a texture (PNG) and return the saved file URL.
    static func save(image: UIImage, tileGroupId: String) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let fileURL = textureURL(for: tileGroupId)
        guard let data = image.pngData() else {
            throw NSError(domain: "TileTextureStore", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "PNG encoding failed"])
        }
        try data.write(to: fileURL)
        return fileURL
    }

    /// Re-insert the group with the new texture + source, preserving all other fields.
    static func applyTexture(
        image: UIImage,
        source: TileSource,
        tileGroup: TileGroup,
        repo: TileGroupRepository
    ) async throws {
        let fileURL = try save(image: image, tileGroupId: tileGroup.id)
        guard let tg = try await repo.getById(id: tileGroup.id) else { return }
        let updated = TileGroup(
            id: tg.id, projectId: tg.projectId, name: tg.name,
            tileWidth: tg.tileWidth, tileHeight: tg.tileHeight,
            texturePath: fileURL.path, source: source
        )
        try await repo.insert(tileGroup: updated)
    }
}

/// Lists tile groups for a project. Supports add/edit/delete, texture capture
/// and import, texture thumbnails, tap-to-view, and swipe-to-edit.
struct TileLibraryView: View {
    let projectId: String

    @State private var tileGroups: [TileGroup] = []
    @State private var showAdd = false
    @State private var editingTileGroup: TileGroup?
    @State private var viewingTexture: TileGroup?
    @State private var capturingTileGroup: TileGroup?

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
                        TileGroupRow(tileGroup: tg) {
                            capturingTileGroup = tg
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            viewingTexture = tg
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                let repo = tileGroupRepo
                                Task {
                                    try? await repo.delete(id: tg.id)
                                    await load()
                                }
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }

                            Button {
                                editingTileGroup = tg
                            } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            .tint(.blue)
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
            .accessibilityIdentifier("add-tile-group")
        }
        .sheet(isPresented: $showAdd) {
            AddTileGroupSheet(projectId: projectId) {
                Task { await load() }
            }
        }
        .sheet(item: $editingTileGroup) { tg in
            AddTileGroupSheet(projectId: projectId, editing: tg) {
                Task { await load() }
            }
        }
        .fullScreenCover(item: $viewingTexture) { tg in
            TextureViewer(tileGroup: tg)
        }
        .fullScreenCover(item: $capturingTileGroup) { tg in
            EdgeDetectionCameraView(tileGroup: tg) { image in
                Task {
                    try? await TileTextureStore.applyTexture(
                        image: image,
                        source: TileSource.captured,
                        tileGroup: tg,
                        repo: tileGroupRepo
                    )
                    capturingTileGroup = nil
                    await load()
                }
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
    let onCapture: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            if let image = TileTextureStore.image(for: tileGroup) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            } else {
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.blue.opacity(0.2))
                    .frame(width: 44, height: 44)
                    .overlay {
                        Image(systemName: "square.grid.3x3")
                            .foregroundStyle(.blue)
                    }
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

            // Quick capture entry point: opens the edge-detection scanner fullscreen.
            Button(action: onCapture) {
                Image(systemName: "camera")
                    .font(.system(size: 14))
                    .foregroundStyle(.teal)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("capture-tile-group")

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

// MARK: - Texture Viewer

/// Fullscreen viewer for a tile's texture image, with the tile's name + size.
private struct TextureViewer: View {
    let tileGroup: TileGroup

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Spacer()
                if let image = TileTextureStore.image(for: tileGroup) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                } else {
                    ContentUnavailableView(
                        "No Texture",
                        systemImage: "photo",
                        description: Text("Capture or import a texture for this tile.")
                    )
                }
                Spacer()
                VStack(spacing: 4) {
                    Text(tileGroup.name)
                        .font(.headline)
                    Text("\(Int(tileGroup.tileWidth)) × \(Int(tileGroup.tileHeight)) mm")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.bottom, 16)
            }
            .navigationTitle(tileGroup.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Add / Edit Tile Group Sheet

/// Two-step sheet: name + size, then optional texture (capture / import / skip).
/// In edit mode the fields are pre-filled and "Add" becomes "Save".
private struct AddTileGroupSheet: View {
    let projectId: String
    let editing: TileGroup?
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var tileWidth: Double = 300
    @State private var tileHeight: Double = 200

    /// nil = new tile; set once the group exists (after save or in edit mode).
    @State private var createdTileGroup: TileGroup?

    @State private var showTextureOptions = false

    private var isEdit: Bool { editing != nil }

    private let tileGroupRepo: TileGroupRepository
    private let typeId = TypeId()

    private var tileWidthText: Binding<String> {
        Binding(
            get: { String(Int(tileWidth)) },
            set: { tileWidth = Double($0) ?? tileWidth }
        )
    }

    private var tileHeightText: Binding<String> {
        Binding(
            get: { String(Int(tileHeight)) },
            set: { tileHeight = Double($0) ?? tileHeight }
        )
    }

    init(projectId: String, editing: TileGroup? = nil, onSaved: @escaping () -> Void) {
        self.projectId = projectId
        self.editing = editing
        self.onSaved = onSaved
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.tileGroupRepo = SqlDelightTileGroupRepository(queries: database.tileLayoutDbQueries)
        if let editing {
            _name = State(initialValue: editing.name)
            _tileWidth = State(initialValue: editing.tileWidth)
            _tileHeight = State(initialValue: editing.tileHeight)
            _createdTileGroup = State(initialValue: editing)
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Tile name (e.g. White Ceramic)", text: $name)
                    .accessibilityIdentifier("tile-name-field")

                Section("Tile Size (mm)") {
                    TappableDimensionRow(label: "Width", text: tileWidthText)
                    TappableDimensionRow(label: "Height", text: tileHeightText)
                }

                // The texture entry point is visible from the start — no hidden
                // step after Add. Tapping it saves the tile if needed, then
                // offers Import / Capture from a bottom sheet.
                textureSection
            }
            .navigationTitle(editing == nil ? "New Tile" : "Edit Tile")
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isEdit {
                        Button("Save") {
                            Task {
                                await save()
                                onSaved()
                                dismiss()
                            }
                        }
                        .disabled(tileWidth <= 0 || tileHeight <= 0)
                    } else if createdTileGroup == nil {
                        Button("Add") {
                            Task {
                                await save()
                                onSaved()
                                dismiss()
                            }
                        }
                        .disabled(tileWidth <= 0 || tileHeight <= 0)
                    } else {
                        Button("Done") {
                            onSaved()
                            dismiss()
                        }
                    }
                }
            }
            .sheet(isPresented: $showTextureOptions) {
                TextureOptionsSheet(
                    tileGroupRepo: tileGroupRepo,
                    ensureSaved: { await ensureGroup() },
                    onTextureApplied: { tg in
                        Task {
                            createdTileGroup = try? await tileGroupRepo.getById(id: tg.id)
                            onSaved()
                        }
                    }
                )
            }
        }
    }

    /// Creates the tile group from the current fields if it doesn't exist yet,
    /// then returns it (needed before a texture can be attached).
    private func ensureGroup() async -> TileGroup? {
        if createdTileGroup == nil {
            await save()
        }
        return createdTileGroup
    }

    // MARK: Texture section

    private var textureSection: some View {
        Section {
            if let tg = createdTileGroup,
               let image = TileTextureStore.image(for: tg) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxHeight: 180)
                    .frame(maxWidth: .infinity)
            }

            Button {
                // Present the choice sheet immediately; the tile is created
                // lazily right before a capture/import actually needs it.
                showTextureOptions = true
            } label: {
                Label("Add Texture", systemImage: "plus.circle")
            }
            .disabled(tileWidth <= 0 || tileHeight <= 0)

            if createdTileGroup != nil,
               let tg = createdTileGroup,
               TileTextureStore.image(for: tg) != nil {
                Button("Remove Texture", role: .destructive) {
                    Task { await removeTexture() }
                }
            }
        } header: {
            Text("Texture")
        } footer: {
            Text("Import a photo or capture the tile with the camera. The icon in the tile list will show this texture.")
        }
    }

    // MARK: Actions

    private func save() async {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let tg = TileGroup(
            id: editing?.id ?? typeId.generate(prefix: "tg"),
            projectId: editing?.projectId ?? projectId,
            name: trimmedName.isEmpty ? "Tile" : trimmedName,
            tileWidth: tileWidth,
            tileHeight: tileHeight,
            texturePath: editing?.texturePath,
            source: editing?.source ?? TileSource.imported
        )
        try? await tileGroupRepo.insert(tileGroup: tg)
        createdTileGroup = tg
        onSaved()
    }

    private func removeTexture() async {
        guard let tg = createdTileGroup else { return }
        let updated = TileGroup(
            id: tg.id, projectId: tg.projectId, name: tg.name,
            tileWidth: tg.tileWidth, tileHeight: tg.tileHeight,
            texturePath: nil, source: TileSource.imported
        )
        try? await tileGroupRepo.insert(tileGroup: updated)
        createdTileGroup = try? await tileGroupRepo.getById(id: tg.id)
        onSaved()
    }
}

/// Small bottom sheet with the texture choices: capture with the camera or
/// import from the photo library. Presented from the Add/Edit Tile sheet.
private struct TextureOptionsSheet: View {
    let tileGroupRepo: TileGroupRepository
    var ensureSaved: () async -> TileGroup?
    var onTextureApplied: (TileGroup) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var capturing: TileGroup?
    @State private var photoItem: PhotosPickerItem?
    @State private var importFailed = false

    var body: some View {
        NavigationStack {
            List {
                Button {
                    Task {
                        if let tg = await ensureSaved() {
                            capturing = tg
                        }
                    }
                } label: {
                    Label("Capture with Camera", systemImage: "camera")
                }

                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Import from Photo Library", systemImage: "photo.on.rectangle")
                }
            }
            .navigationTitle("Add Texture")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.height(200)])
        .fullScreenCover(item: $capturing) { tg in
            EdgeDetectionCameraView(tileGroup: tg) { image in
                Task {
                    try? await TileTextureStore.applyTexture(
                        image: image,
                        source: TileSource.captured,
                        tileGroup: tg,
                        repo: tileGroupRepo
                    )
                    capturing = nil
                    onTextureApplied(tg)
                }
            }
        }
        .onChange(of: photoItem) { _, newItem in
            guard let newItem else { return }
            Task {
                if let tg = await ensureSaved() {
                    do {
                        guard let data = try await newItem.loadTransferable(type: Data.self),
                              let image = UIImage(data: data) else {
                            importFailed = true
                            photoItem = nil
                            return
                        }
                        try await TileTextureStore.applyTexture(
                            image: image,
                            source: TileSource.imported,
                            tileGroup: tg,
                            repo: tileGroupRepo
                        )
                        onTextureApplied(tg)
                    } catch {
                        importFailed = true
                    }
                }
                photoItem = nil
            }
        }
        .alert("Import Failed", isPresented: $importFailed) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("The selected photo could not be loaded.")
        }
    }
}

#Preview {
    NavigationStack {
        TileLibraryView(projectId: "preview-project")
    }
}
