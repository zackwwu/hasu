import SwiftUI
import SharedLogic

/// Editor for a surface tile group's region rectangle.
/// Validates region bounds and overlap with existing regions.
struct RegionEditorView: View {
    let stg: SurfaceTileGroup
    let surface: Surface

    @Environment(\.dismiss) private var dismiss

    @State private var regionX: Double
    @State private var regionY: Double
    @State private var regionW: Double
    @State private var regionH: Double
    @State private var validationError: String? = nil

    private lazy var db = DatabaseProvider.shared.createTileLayoutDb()
    private lazy var surfaceRepo: SurfaceRepository = SqlDelightSurfaceRepository(queries: db.tileLayoutDbQueries)

    init(stg: SurfaceTileGroup, surface: Surface) {
        self.stg = stg
        self.surface = surface
        _regionX = State(initialValue: stg.region.x)
        _regionY = State(initialValue: stg.region.y)
        _regionW = State(initialValue: stg.region.width)
        _regionH = State(initialValue: stg.region.height)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Region") {
                    HStack {
                        LabeledContent("X") {
                            TextField("X", value: $regionX, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                        LabeledContent("Y") {
                            TextField("Y", value: $regionY, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                    HStack {
                        LabeledContent("Width") {
                            TextField("W", value: $regionW, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                        LabeledContent("Height") {
                            TextField("H", value: $regionH, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                } header: {
                    Text("Region Bounds (mm)")
                } footer: {
                    Text("Surface size: \(Int(surface.width)) × \(Int(surface.height)) mm")
                }

                if let error = validationError {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                    }
                }

                Section {
                    Button("Validate & Save") {
                        validateAndSave()
                    }
                    .frame(maxWidth: .infinity)
                    .fontWeight(.semibold)
                }
            }
            .navigationTitle("Edit Region")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func validateAndSave() {
        let newRegion = RegionRect(x: regionX, y: regionY, width: regionW, height: regionH)

        // Basic bounds check
        if regionX < 0 || regionY < 0 || regionX + regionW > surface.width || regionY + regionH > surface.height {
            validationError = "Region extends outside surface bounds (\(Int(surface.width)) × \(Int(surface.height)))."
            return
        }

        if regionW <= 0 || regionH <= 0 {
            validationError = "Width and height must be positive."
            return
        }

        // Use RegionValidator from shared module
        let validator = RegionValidator()
        let existingSTGs = (try? surfaceRepo.getSTGsBySurface(surfaceId: surface.id)) ?? []
        let result = validator.validate(
            newRegion: newRegion,
            existingRegions: existingSTGs,
            surfaceWidth: surface.width,
            surfaceHeight: surface.height,
            excludeId: stg.id
        )

        if result.isValid {
            let updated = SurfaceTileGroup(
                id: stg.id,
                surfaceId: stg.surfaceId,
                tileGroupId: stg.tileGroupId,
                region: newRegion,
                pattern: stg.pattern,
                offsetX: stg.offsetX,
                offsetY: stg.offsetY,
                locked: stg.locked
            )
            Task {
                do {
                    try await surfaceRepo.updateSTG(stg: updated)
                    dismiss()
                } catch {
                    validationError = "Save failed: \(error.localizedDescription)"
                }
            }
        } else {
            let messages = result.errors as? [RegionValidationError] ?? []
            validationError = messages.map { $0.message }.joined(separator: "\n")
        }
    }
}

// MARK: - Help Diagram

struct HelpDiagramView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Coordinate System")
                    .font(.title2)
                    .fontWeight(.bold)

                // 3D coordinate system diagram
                coordinateDiagram

                Divider()

                VStack(alignment: .leading, spacing: 12) {
                    diagramItem(
                        icon: "arrow.right",
                        color: .red,
                        title: "X Axis",
                        description: "Width direction. Front and back walls span X."
                    )
                    diagramItem(
                        icon: "arrow.up",
                        color: .green,
                        title: "Y Axis",
                        description: "Height direction. Walls extend upward in Y."
                    )
                    diagramItem(
                        icon: "arrow.forward",
                        color: .blue,
                        title: "Z Axis",
                        description: "Depth direction. Left and right walls span Z."
                    )
                    diagramItem(
                        icon: "arrow.triangle.2.circlepath",
                        color: .orange,
                        title: "Rotation",
                        description: "Surface facing angle in degrees. 0 = front, 90 = left, 180 = back, 270 = right."
                    )
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Surfaces")
                        .font(.headline)

                    Text("• **Front Wall:** rotation 0°, positioned at z=0")
                    Text("• **Back Wall:** rotation 180°, positioned at z=roomDepth")
                    Text("• **Left Wall:** rotation 90°, positioned at x=0 (spans Z)")
                    Text("• **Right Wall:** rotation 270°, positioned at x=roomWidth (spans Z)")
                    Text("• **Floor:** width=X, height=Z, positioned at y=0")
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Lock Propagation")
                        .font(.headline)

                    Text("When dragging tiles on one surface while others are locked:")
                    Text("• **Floor → Floor:** Both axes propagate")
                    Text("• **Wall → Wall (parallel):** Both axes propagate")
                    Text("• **Wall → Wall (perpendicular):** Only Y axis propagates")
                    Text("• **Wall ↔ Floor:** No propagation")
                        .padding(.top, 4)
                }
            }
            .padding()
        }
        .navigationTitle("Help")
    }

    private var coordinateDiagram: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemGray6))
                .frame(height: 200)

            VStack(spacing: 16) {
                HStack(spacing: 40) {
                    Text("Front Wall\n(0°)")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .padding(8)
                        .background(Color.blue.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 6))

                    Text("Back Wall\n(180°)")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .padding(8)
                        .background(Color.blue.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                }

                HStack(spacing: 40) {
                    Text("Left Wall\n(90°)")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .padding(8)
                        .background(Color.green.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 6))

                    Text("Floor")
                        .font(.caption)
                        .padding(8)
                        .background(Color.brown.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 6))

                    Text("Right Wall\n(270°)")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                        .padding(8)
                        .background(Color.green.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                }
            }
        }
    }

    private func diagramItem(icon: String, color: Color, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(color)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                Text(description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

#Preview {
    NavigationStack {
        RegionEditorView(
            stg: SurfaceTileGroup(
                id: "stg-1",
                surfaceId: "srf-1",
                tileGroupId: "tg-1",
                region: RegionRect(x: 0, y: 0, width: 500, height: 400),
                pattern: TilePattern.grid,
                offsetX: 0,
                offsetY: 0,
                locked: false
            ),
            surface: Surface(
                id: "srf-1",
                roomId: "rm-1",
                type: SurfaceType.wall,
                width: 2000,
                height: 1200,
                position: SurfacePosition(x: 0, y: 0, z: 0, rotation: 0),
                groutColor: GroutColor.grey,
                groutWidth: 3
            )
        )
    }
}

#Preview("Help Diagram") {
    NavigationStack {
        HelpDiagramView()
    }
}
