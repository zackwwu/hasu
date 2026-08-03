import SwiftUI

struct HomeView: View {
    @StateObject private var vm = IOSProjectListViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if vm.projects.isEmpty {
                    ContentUnavailableView(
                        "No Projects",
                        systemImage: "square.grid.2x2",
                        description: Text("Tap + to create your first project")
                    )
                } else {
                    List {
                        ForEach(vm.projects, id: \.id) { project in
                            NavigationLink(project.name, destination: ProjectDetailView(project: project))
                        }
                        .onDelete { vm.delete(at: $0) }
                    }
                }
            }
            .navigationTitle("Projects")
            .toolbar {
                Button {
                    vm.showCreate = true
                } label: {
                    Image(systemName: "plus")
                }
            }
            .sheet(isPresented: $vm.showCreate) {
                CreateProjectSheet(vm: vm)
            }
        }
        .task { await vm.load() }
    }
}

#Preview {
    HomeView()
}
