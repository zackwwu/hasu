import SwiftUI
import SharedLogic

/// Lists rooms for a project, navigates to RoomEditorView.
struct RoomListView: View {
    let projectId: String

    @State private var rooms: [Room] = []
    @State private var showAddRoom = false

    lazy var db = DatabaseProvider.shared.createTileLayoutDb()
    lazy var roomRepo: RoomRepository = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)

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
                        Task {
                            for idx in indexSet {
                                try? await roomRepo.delete(id: rooms[idx].id)
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
        }
        .sheet(isPresented: $showAddRoom) {
            AddRoomSheet(projectId: projectId) {
                Task { await load() }
            }
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

private struct AddRoomSheet: View {
    let projectId: String
    let onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var width: Double = 3000
    @State private var depth: Double = 4000
    @State private var height: Double = 2400

    lazy var db = DatabaseProvider.shared.createTileLayoutDb()
    lazy var roomRepo: RoomRepository = SqlDelightRoomRepository(queries: db.tileLayoutDbQueries)

    var body: some View {
        NavigationStack {
            Form {
                TextField("Room name (e.g. Bathroom)", text: $name)

                Section("Dimensions (mm)") {
                    HStack {
                        Text("Width")
                        Spacer()
                        TextField("Width", value: $width, format: .number)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("Depth")
                        Spacer()
                        TextField("Depth", value: $depth, format: .number)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("Height")
                        Spacer()
                        TextField("Height", value: $height, format: .number)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            .navigationTitle("New Room")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        Task {
                            let room = Room(
                                id: TypeId.generate(prefix: "rm"),
                                projectId: projectId,
                                name: name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                    ? "Room" : name.trimmingCharacters(in: .whitespacesAndNewlines),
                                width: width,
                                depth: depth,
                                height: height
                            )
                            try? await roomRepo.insert(room: room)
                            onDismiss()
                            dismiss()
                        }
                    }
                    .disabled(width <= 0 || depth <= 0 || height <= 0)
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        RoomListView(projectId: "preview-project")
    }
}
