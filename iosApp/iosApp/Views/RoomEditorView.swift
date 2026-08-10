import SwiftUI
import SharedLogic

/// Main room editor with tab scaffold: Surfaces | Layout | Preview | Cut List
struct RoomEditorView: View {
    let roomId: String
    @StateObject private var vm = IOSRoomEditorViewModel()
    @State private var selectedTab = 0
    @State private var showHelp = false

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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showHelp = true
                } label: {
                    Image(systemName: "questionmark.circle")
                }
                .accessibilityLabel("Coordinate system help")
            }
        }
        .sheet(isPresented: $showHelp) {
            NavigationStack {
                HelpDiagramView()
            }
        }
        .task {
            await vm.load(roomId: roomId)
        }
    }
}

#Preview {
    NavigationStack {
        RoomEditorView(roomId: "preview-room-id")
    }
}
