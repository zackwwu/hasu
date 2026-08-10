import Foundation

/// Generates prefixed UUID strings matching the shared Kotlin `uuid4()` pattern.
struct TypeId {
    func generate(prefix: String) -> String {
        "\(prefix)-\(UUID().uuidString.lowercased())"
    }
}
