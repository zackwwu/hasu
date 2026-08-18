import SwiftUI
import SharedLogic

/// Cut list tab showing grouped cut entries for the room's surfaces,
/// ordered by tile group and then cut dimension (largest first).
///
/// The entries are computed in the shared `RoomEditorViewModel` after every
/// layout computation and bridged through `IOSRoomEditorViewModel.cutEntries`,
/// so this view stays fresh reactively — no per-appear regeneration needed.
struct CutListTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel

    var body: some View {
        Group {
            if vm.cutEntries.isEmpty {
                ContentUnavailableView(
                    "No Cuts Required",
                    systemImage: "scissors",
                    description: Text("All tiles fit within their regions without needing cuts.")
                )
            } else {
                List {
                    ForEach(Array(vm.cutEntries.enumerated()), id: \.offset) { _, entry in
                        CutEntrySection(entry: entry)
                    }
                }
            }
        }
    }
}

// MARK: - Cut Entry Section

private struct CutEntrySection: View {
    let entry: CutEntry

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.tileGroupName)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        Text("Size: \(Int(entry.width)) × \(Int(entry.height)) mm")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(entry.totalCount)×")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(.orange)
                }

                Label(entry.cutTypeDescription, systemImage: "scissors")
                    .font(.caption)
                    .foregroundStyle(.orange)

                ForEach(entry.locations as? [CutLocation] ?? [], id: \.surfaceId) { location in
                    HStack {
                        Text(location.surfaceName)
                            .font(.caption)
                        Spacer()
                        Text("\(location.count) cuts")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                cutDiagram
            }
            .padding(.vertical, 4)
        }
    }

    private var cutDiagram: some View {
        Canvas { context, size in
            let rect = CGRect(x: 4, y: 4, width: size.width - 8, height: size.height - 8)
            context.fill(Path(rect), with: .color(.orange.opacity(0.15)))
            context.stroke(Path(rect), with: .color(.orange.opacity(0.5)), lineWidth: 1)

            let edges = entry.cutEdgesKey.split(separator: ",").map(String.init)
            for edge in edges {
                let markerRect: CGRect
                switch edge.trimmingCharacters(in: .whitespaces) {
                case "LEFT":
                    markerRect = CGRect(x: rect.minX, y: rect.minY, width: 3, height: rect.height)
                case "RIGHT":
                    markerRect = CGRect(x: rect.maxX - 3, y: rect.minY, width: 3, height: rect.height)
                case "TOP":
                    markerRect = CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: 3)
                case "BOTTOM":
                    markerRect = CGRect(x: rect.minX, y: rect.maxY - 3, width: rect.width, height: 3)
                default:
                    continue
                }
                context.fill(Path(markerRect), with: .color(.red))
            }

            let label = "\(Int(entry.width))×\(Int(entry.height))"
            let text = Text(label).font(.system(size: 8)).foregroundColor(.orange)
            context.draw(text, at: CGPoint(x: rect.midX, y: rect.midY))
        }
        .frame(height: 60)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

#Preview {
    CutListTabView(vm: IOSRoomEditorViewModel())
}
