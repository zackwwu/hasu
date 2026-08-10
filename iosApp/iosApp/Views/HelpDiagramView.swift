import SwiftUI

/// Help diagram explaining the room coordinate system.
/// Shows the "Where are you standing?" orientation guide per spec.
struct HelpDiagramView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 24) {
            Text("Room Coordinate System")
                .font(.title2)
                .fontWeight(.bold)

            Image(systemName: "cube.transparent")
                .font(.system(size: 80))
                .foregroundStyle(.blue.opacity(0.6))

            VStack(alignment: .leading, spacing: 12) {
                Label("X axis = room width (left → right)", systemImage: "arrow.right")
                Label("Y axis = room height (floor → ceiling)", systemImage: "arrow.up")
                Label("Z axis = room depth (front → back)", systemImage: "arrow.up.right")
            }
            .font(.body)

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                Text("Where are you standing?")
                    .font(.headline)
                Text("Imagine standing in the doorway looking into the room. The wall directly ahead is the Back wall. Left/Right walls are relative to your position.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Help")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
            }
        }
    }
}

#Preview {
    NavigationStack {
        HelpDiagramView()
    }
}
