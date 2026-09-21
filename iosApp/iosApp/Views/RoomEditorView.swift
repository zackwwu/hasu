import SwiftUI
import SharedLogic

/// Main room editor with tab scaffold: Surfaces | Layout | Preview | Cut List
struct RoomEditorView: View {
    let roomId: String
    @StateObject private var vm = IOSRoomEditorViewModel()
    @State private var selectedTab = 0
    @State private var showHelp = false
    @State private var showEditRoom = false
    @State private var roomName = "Room"

    private let db = DatabaseProvider.shared.createTileLayoutDb()
    private let roomRepo: RoomRepository

    init(roomId: String) {
        self.roomId = roomId
        self.roomRepo = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $selectedTab) {
                Text("Surfaces").tag(0)
                Text("Layout").tag(1)
                Text("Preview").tag(2)
                Text("Cut List").tag(3)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.top, 4)

            Divider()

            switch selectedTab {
            case 0:
                SurfacesListView(vm: vm, roomId: roomId) {
                    showEditRoom = true
                }
            case 1:
                LayoutTabView(vm: vm) {
                    selectedTab = 0
                }
            case 2:
                PreviewTabView(vm: vm)
            case 3:
                CutListTabView(vm: vm)
            default:
                EmptyView()
            }
        }
        .navigationTitle(roomName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showEditRoom = true
                } label: {
                    Image(systemName: "pencil")
                }
                .accessibilityLabel("Edit room")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showHelp = true
                } label: {
                    Image(systemName: "questionmark.circle")
                }
                .accessibilityLabel("Coordinate system help")
            }
        }
        .sheet(isPresented: $showEditRoom) {
            EditRoomSheet(roomId: roomId) {
                await vm.load(roomId: roomId)
            }
        }
        .sheet(isPresented: $showHelp) {
            NavigationStack {
                HelpDiagramView()
            }
        }
        .task {
            if let room = try? await roomRepo.getById(id: roomId) {
                roomName = room.name
            }
            await vm.load(roomId: roomId)
        }
    }
}

/// Sheet for editing the room after creation: name, dimensions, and door.
/// Confirming regenerates the wall/floor surfaces automatically whenever the
/// dimensions changed (or the room has no surfaces yet).
private struct EditRoomSheet: View {
    let roomId: String
    let onSaved: () async -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var room: Room?
    @State private var name = ""
    @State private var width: Double = 3000
    @State private var depth: Double = 4000
    @State private var height: Double = 2400
    @State private var doorWall: Double?
    @State private var doorWidth: Double = 900
    @State private var doorHeight: Double = 2100
    @State private var doorOffsetText: String = ""
    @State private var doorCentered = true

    private let minDoorWidth = 400.0
    private let minDoorHeight = 1500.0

    private let roomRepo: RoomRepository
    private let surfaceRepo: SurfaceRepository
    private let layoutRepo: LayoutResultRepository

    init(roomId: String, onSaved: @escaping () async -> Void) {
        self.roomId = roomId
        self.onSaved = onSaved
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.roomRepo = SqlDelightRoomRepository(queries: database.tileLayoutDbQueries)
        self.surfaceRepo = SqlDelightSurfaceRepository(queries: database.tileLayoutDbQueries)
        self.layoutRepo = SqlDelightLayoutResultRepository(queries: database.tileLayoutDbQueries)
    }

    // MARK: Door validation (mirrors AddRoomSheet)

    private var wallSpan: Double {
        switch doorWall {
        case 90, 270: return depth
        default: return width
        }
    }

    private var doorWidthText: Binding<String> {
        Binding(
            get: { String(Int(doorWidth)) },
            set: { doorWidth = Double($0) ?? doorWidth }
        )
    }

    private var doorHeightText: Binding<String> {
        Binding(
            get: { String(Int(doorHeight)) },
            set: { doorHeight = Double($0) ?? doorHeight }
        )
    }

    private var widthText: Binding<String> {
        Binding(get: { String(Int(width)) }, set: { width = Double($0) ?? width })
    }

    private var depthText: Binding<String> {
        Binding(get: { String(Int(depth)) }, set: { depth = Double($0) ?? depth })
    }

    private var heightText: Binding<String> {
        Binding(get: { String(Int(height)) }, set: { height = Double($0) ?? height })
    }

    private var doorWidthInvalid: Bool { doorWidth < minDoorWidth || doorWidth > wallSpan }
    private var doorHeightInvalid: Bool { doorHeight < minDoorHeight || doorHeight > height }
    /// Centered = offset nil (saved as null → "(centered)" everywhere).
    private var doorOffsetValue: Double? {
        doorCentered ? nil : (doorOffsetText.isEmpty ? nil : Double(doorOffsetText))
    }
    private var doorOffsetInvalid: Bool {
        guard let offset = doorOffsetValue else { return false }
        return offset < 0 || offset > wallSpan - doorWidth
    }
    private var doorValid: Bool { !doorWidthInvalid && !doorHeightInvalid && !doorOffsetInvalid }

    /// The centered offset for the current wall/door width — shown in the field
    /// and recomputed live as the room or door dimensions change.
    private var centeredOffsetString: String {
        String(Int(max((wallSpan - doorWidth) / 2, 0)))
    }

    /// Field binding: displays the live centered value until the user types
    /// their own number (which switches off centering).
    private var offsetFieldText: Binding<String> {
        Binding(
            get: { doorCentered ? centeredOffsetString : doorOffsetText },
            set: { newValue in
                doorCentered = false
                doorOffsetText = newValue
            }
        )
    }

    private var dimsChanged: Bool {
        guard let room else { return true }
        return abs(width - room.width) > 0.01 ||
               abs(depth - room.depth) > 0.01 ||
               abs(height - room.height) > 0.01
    }

    var body: some View {
        NavigationStack {
            Form {
                if let room {
                    Section("Room") {
                        TextField("Room name", text: $name)
                            .accessibilityIdentifier("edit-room-name")
                        TappableDimensionRow(label: "Width", text: widthText)
                        TappableDimensionRow(label: "Depth", text: depthText)
                        TappableDimensionRow(label: "Height", text: heightText)
                    }

                    Section {
                        DoorDiagramView(
                            roomWidth: width,
                            roomDepth: depth,
                            selectedWall: doorWall,
                            onWallSelected: { wall in
                                doorWall = wall
                                doorCentered = true
                            },
                            doorWidth: doorWidth,
                            doorOffset: doorOffsetValue
                        )
                        .frame(height: 180)

                        HStack(spacing: 12) {
                            TappableDimensionRow(label: "Width", text: doorWidthText)
                            TappableDimensionRow(label: "Height", text: doorHeightText)
                        }
                        TappableDimensionRow(label: "Offset from wall corner (mm)", text: offsetFieldText,
                                             caption: doorCentered ? "(centered)" : nil)

                        Button {
                            doorCentered = true
                        } label: {
                            Label("Center Door", systemImage: "arrow.left.and.right.circle")
                                .font(.caption)
                        }
                        .disabled(doorWall == nil)
                    } header: {
                        Text("Door")
                    }

                    if dimsChanged {
                        Section {
                            Label("Saving with new dimensions regenerates the wall and floor surfaces and clears their tile assignments.",
                                  systemImage: "exclamationmark.triangle")
                                .font(.caption)
                                .foregroundStyle(.orange)
                        }
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Edit Room")
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task { await save() }
                    }
                    .disabled(width <= 0 || depth <= 0 || height <= 0 || doorWall == nil || !doorValid)
                }
            }
        }
        .task {
            if let loaded = try? await roomRepo.getById(id: roomId) {
                room = loaded
                name = loaded.name
                width = loaded.width
                depth = loaded.depth
                height = loaded.height
                doorWall = loaded.doorWall?.doubleValue
                doorWidth = loaded.doorWidth
                doorHeight = loaded.doorHeight
                doorOffsetText = loaded.doorOffset != nil ? String(Int(loaded.doorOffset!.doubleValue)) : ""
                doorCentered = loaded.doorOffset == nil
            }
        }
    }

    private func save() async {
        guard let room else { return }
        let updated = Room(
            id: room.id,
            projectId: room.projectId,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "Room" : name.trimmingCharacters(in: .whitespacesAndNewlines),
            width: width,
            depth: depth,
            height: height,
            doorWall: doorWall.map { KotlinDouble(double: $0) },
            doorWidth: doorWidth,
            doorHeight: doorHeight,
            doorOffset: doorOffsetValue.map { KotlinDouble(double: $0) }
        )
        try? await roomRepo.insert(room: updated)

        // Regenerate surfaces when dimensions changed (or the room never got
        // surfaces). Clears old tile assignments — flagged in the UI above.
        let existing = (try? await surfaceRepo.getByRoom(roomId: room.id)) ?? []
        if dimsChanged || existing.isEmpty {
            for surface in existing {
                let stgs = (try? await surfaceRepo.getSTGsBySurface(surfaceId: surface.id)) ?? []
                for stg in stgs {
                    try? await surfaceRepo.deleteSTG(id: stg.id)
                }
                if let layout = try? await layoutRepo.getBySurface(surfaceId: surface.id) {
                    try? await layoutRepo.delete(id: layout.id)
                }
                try? await surfaceRepo.delete(id: surface.id)
            }
            let calc = SurfacePositionCalculator()
            let generated = calc.generate(
                roomId: room.id,
                roomWidth: width,
                roomDepth: depth,
                roomHeight: height,
                includeFront: true,
                includeBack: true,
                includeLeft: true,
                includeRight: true,
                includeFloor: true
            ) as? [Surface] ?? []
            for surface in generated {
                try? await surfaceRepo.insert(surface: surface)
            }
        }

        await onSaved()
        dismiss()
    }
}

#Preview {
    NavigationStack {
        RoomEditorView(roomId: "preview-room-id")
    }
}
