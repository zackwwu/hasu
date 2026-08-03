import SwiftUI
import SharedLogic

struct ProjectDetailView: View {
    let project: Project

    @State private var tab = 0

    var body: some View {
        VStack {
            Picker("", selection: $tab) {
                Text("Rooms").tag(0)
                Text("Tile Library").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                RoomListView(projectId: project.id)
            } else {
                TileLibraryView(projectId: project.id)
            }
        }
        .navigationTitle(project.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        ProjectDetailView(project: Project(id: "prj-preview", name: "Preview", units: UnitSystem.mm, createdAt: 1))
    }
}
