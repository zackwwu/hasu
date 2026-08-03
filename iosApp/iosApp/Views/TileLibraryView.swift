import SwiftUI

/// Placeholder tile library for a project. Replaced in Task 14.
struct TileLibraryView: View {
    let projectId: String

    var body: some View {
        ContentUnavailableView(
            "Tile Library for project",
            systemImage: "square.grid.3x3.topleft.filled",
            description: Text("Project ID: \(projectId)")
        )
    }
}

#Preview {
    TileLibraryView(projectId: "prj-preview")
}
