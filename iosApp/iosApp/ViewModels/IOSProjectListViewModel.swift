import Foundation
import SharedLogic

/// ObservableObject wrapper around the shared `ProjectListViewModel`.
///
/// The shared VM exposes projects as a `StateFlow`, which does not interoperate
/// with SwiftUI directly, so we read `projects.value` after each mutation.
@MainActor
final class IOSProjectListViewModel: ObservableObject {
    @Published var projects: [Project] = []
    @Published var showCreate = false

    private lazy var db: TileLayoutDb = DatabaseProvider.shared.createTileLayoutDb()

    private lazy var sharedVM: ProjectListViewModel = ProjectListViewModel(
        repo: SqlDelightProjectRepository(queries: db.tileLayoutDbQueries)
    )

    func load() async {
        _ = try? await sharedVM.load()
        refresh()
    }

    func create(name: String) async {
        _ = try? await sharedVM.create(name: name)
        refresh()
    }

    func delete(at offsets: IndexSet) {
        let doomed = offsets.map { projects[$0] }
        Task {
            for project in doomed {
                _ = try? await sharedVM.delete(id: project.id)
            }
            refresh()
        }
    }

    private func refresh() {
        // `StateFlow.value` is exported to ObjC as `Any?` — cast back to [Project].
        projects = (sharedVM.projects.value as? [Project]) ?? []
    }
}
