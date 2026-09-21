import SwiftUI
import SharedLogic

/// Lists rooms for a project, navigates to RoomEditorView.
struct RoomListView: View {
    let projectId: String

    @State private var rooms: [Room] = []
    @State private var showAddRoom = false
    @State private var roomNavigation: RoomNavigation?

    private let roomRepo: RoomRepository

    init(projectId: String) {
        self.projectId = projectId
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.roomRepo = SqlDelightRoomRepository(queries: database.tileLayoutDbQueries)
    }

    var body: some View {
        Group {
            if rooms.isEmpty {
                ContentUnavailableView(
                    "No Rooms",
                    systemImage: "square.split.bottomrightquarter",
                    description: Text("Add a room to start designing tile layouts.")
                )
            } else {
                List {
                    ForEach(rooms, id: \.id) { room in
                        NavigationLink {
                            RoomEditorView(roomId: room.id)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(room.name)
                                    .font(.body)
                                    .fontWeight(.medium)
                                Text("\(Int(room.width)) × \(Int(room.depth)) × \(Int(room.height)) mm")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .onDelete { indexSet in
                        let repo = roomRepo
                        Task {
                            for idx in indexSet {
                                try? await repo.delete(id: rooms[idx].id)
                            }
                            await load()
                        }
                    }
                }
            }
        }
        .toolbar {
            Button {
                showAddRoom = true
            } label: {
                Image(systemName: "plus")
            }
            .accessibilityIdentifier("add-room")
        }
        .sheet(isPresented: $showAddRoom) {
            AddRoomSheet(projectId: projectId) { roomId in
                Task { await load() }
                // Adding a room goes straight into the room editor.
                roomNavigation = RoomNavigation(id: roomId)
            }
        }
        .navigationDestination(item: $roomNavigation) { nav in
            RoomEditorView(roomId: nav.id)
        }
        .task { await load() }
    }

    private func load() async {
        do {
            rooms = try await roomRepo.getByProject(projectId: projectId)
        } catch {
            print("Load rooms failed: \(error)")
        }
    }
}

// MARK: - Add Room Sheet

/// Identifiable wrapper for programmatic navigation to a created room.
private struct RoomNavigation: Identifiable, Hashable {
    let id: String
}

private struct AddRoomSheet: View {
    let projectId: String
    let onCreated: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var width: Double = 3000
    @State private var depth: Double = 4000
    @State private var height: Double = 2400
    // No door selected until the user taps a wall — Add stays disabled.
    @State private var doorWall: Double? = nil
    @State private var doorWidth: Double = 900
    @State private var doorHeight: Double = 2100
    @State private var doorOffsetText: String = ""
    @State private var doorCentered = true
    @State private var step = 0

    private let minDoorWidth = 400.0
    private let minDoorHeight = 1500.0

    private let roomRepo: RoomRepository
    private let surfaceRepo: SurfaceRepository
    private let typeId = TypeId()

    init(projectId: String, onCreated: @escaping (String) -> Void) {
        self.projectId = projectId
        self.onCreated = onCreated
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.roomRepo = SqlDelightRoomRepository(queries: database.tileLayoutDbQueries)
        self.surfaceRepo = SqlDelightSurfaceRepository(queries: database.tileLayoutDbQueries)
    }

    // MARK: Door dimension helpers (mirror DoorSectionView's validation)

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

    var body: some View {
        NavigationStack {
            Form {
                if step == 0 {
                    // Step 1: room identity + dimensions. The door diagram on
                    // step 2 is drawn from these, so its proportions match.
                    Section {
                        TextField("Room name (e.g. Bathroom)", text: $name)
                            .accessibilityIdentifier("room-name-field")
                    }

                    Section("Dimensions (mm)") {
                        TappableDimensionRow(label: "Width", text: widthText)
                        TappableDimensionRow(label: "Depth", text: depthText)
                        TappableDimensionRow(label: "Height", text: heightText)
                    }
                } else {
                    // Step 2: door. Wall names are door-relative, so they only
                    // appear once a wall has actually been tapped.
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
                        Text("Tap the wall with the door")
                    } footer: {
                        Text("Wall names (Door Wall, Left Wall, Front Wall, Right Wall) are assigned from the door once you add the room.")
                    }
                }
            }
            .navigationTitle(step == 0 ? "New Room" : "Door")
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if step == 0 {
                        Button("Cancel") { dismiss() }
                    } else {
                        Button("Back") { step = 0 }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if step == 0 {
                        Button("Next") { step = 1 }
                            .disabled(width <= 0 || depth <= 0 || height <= 0)
                            .accessibilityIdentifier("add-room-next")
                    } else {
                        Button("Add") {
                            addRoom()
                        }
                        .disabled(doorWall == nil || !doorValid)
                        .accessibilityIdentifier("add-room-confirm")
                    }
                }
            }
        }
    }

    private func addRoom() {
        let repo = roomRepo
        let tid = typeId
        Task {
            let room = Room(
                id: tid.generate(prefix: "rm"),
                projectId: projectId,
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
            try? await repo.insert(room: room)
            // Surfaces generate automatically from the room dimensions — no
            // separate "Generate Surfaces" step for new rooms.
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
            onCreated(room.id)
            dismiss()
        }
    }
}

#Preview {
    NavigationStack {
        RoomListView(projectId: "preview-project")
    }
}

/// Dismisses the software keyboard when the user taps outside a text field.
extension View {
    func dismissKeyboardOnTap() -> some View {
        self.onTapGesture {
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder),
                to: nil, from: nil, for: nil
            )
        }
    }
}

/// Number input row where tapping ANYWHERE in the row focuses the text field —
/// not just the digits themselves. Used by every numeric entry form.
struct TappableDimensionRow: View {
    let label: String
    @Binding var text: String
    var caption: String? = nil

    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Spacer()
                TextField(label, text: $text)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .focused($focused)
                    .frame(maxWidth: 110)
            }
            .contentShape(Rectangle())
            .onTapGesture { focused = true }

            if let caption {
                Text(caption).font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}
