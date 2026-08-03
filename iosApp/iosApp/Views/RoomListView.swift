import SwiftUI

/// Placeholder list of rooms for a project. Replaced in Task 11 (Room Editor).
struct RoomListView: View {
    let projectId: String

    var body: some View {
        ContentUnavailableView(
            "Rooms for project",
            systemImage: "square.split.bottomrightquarter",
            description: Text("Project ID: \(projectId)")
        )
    }
}

#Preview {
    RoomListView(projectId: "prj-preview")
}
