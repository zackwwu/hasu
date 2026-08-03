import SwiftUI
import SharedLogic

/// Main room editor with tab scaffold: Surfaces | Layout | Preview | Cut List
struct RoomEditorView: View {
    let roomId: String
    @StateObject private var vm = IOSRoomEditorViewModel()
    @State private var selectedTab = 0

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
                SurfacesListView(vm: vm, roomId: roomId)
            case 1:
                LayoutTabView(vm: vm)
            case 2:
                PreviewTabView(vm: vm)
            case 3:
                CutListTabView(vm: vm)
            default:
                EmptyView()
            }
        }
        .navigationTitle("Room Editor")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await vm.load(roomId: roomId)
        }
    }
}

// MARK: - Stubs (implemented in Tasks 12-14)

private struct LayoutTabStub: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    var body: some View {
        ContentUnavailableView(
            "Layout Tab",
            systemImage: "square.grid.3x3",
            description: Text("Coming in Task 12")
        )
    }
}

private struct PreviewTabStub: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    var body: some View {
        ContentUnavailableView(
            "3D Preview",
            systemImage: "cube.transparent",
            description: Text("Coming in Task 13")
        )
    }
}

private struct CutListTabStub: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    var body: some View {
        ContentUnavailableView(
            "Cut List",
            systemImage: "scissors",
            description: Text("Coming in Task 14")
        )
    }
}

#Preview {
    NavigationStack {
        RoomEditorView(roomId: "preview-room-id")
    }
}
