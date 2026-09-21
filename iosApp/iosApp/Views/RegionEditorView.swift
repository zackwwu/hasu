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

    private let surfaceRepo: SurfaceRepository

    private var regionXText: Binding<String> {
        Binding(get: { String(Int(regionX)) }, set: { regionX = Double($0) ?? regionX })
    }

    private var regionYText: Binding<String> {
        Binding(get: { String(Int(regionY)) }, set: { regionY = Double($0) ?? regionY })
    }

    private var regionWText: Binding<String> {
        Binding(get: { String(Int(regionW)) }, set: { regionW = Double($0) ?? regionW })
    }

    private var regionHText: Binding<String> {
        Binding(get: { String(Int(regionH)) }, set: { regionH = Double($0) ?? regionH })
    }

    init(stg: SurfaceTileGroup, surface: Surface) {
        self.stg = stg
        self.surface = surface
        _regionX = State(initialValue: stg.region.x)
        _regionY = State(initialValue: stg.region.y)
        _regionW = State(initialValue: stg.region.width)
        _regionH = State(initialValue: stg.region.height)
        let database = DatabaseProvider.shared.createTileLayoutDb()
        self.surfaceRepo = SqlDelightSurfaceRepository(queries: database.tileLayoutDbQueries)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        TappableDimensionRow(label: "X", text: regionXText)
                            .frame(maxWidth: .infinity)
                        TappableDimensionRow(label: "Y", text: regionYText)
                            .frame(maxWidth: .infinity)
                    }
                    HStack(spacing: 12) {
                        TappableDimensionRow(label: "Width", text: regionWText)
                            .frame(maxWidth: .infinity)
                        TappableDimensionRow(label: "Height", text: regionHText)
                            .frame(maxWidth: .infinity)
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
                    Button {
                        Task { await validateAndSave() }
                    } label: {
                        Text("Validate & Save")
                            .frame(maxWidth: .infinity)
                            .fontWeight(.semibold)
                    }
                }
            }
            .navigationTitle("Edit Region")
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func validateAndSave() async {
        let newRegion = RegionRect(x: regionX, y: regionY, width: regionW, height: regionH)

        if regionX < 0 || regionY < 0 || regionX + regionW > surface.width || regionY + regionH > surface.height {
            validationError = "Region extends outside surface bounds (\(Int(surface.width)) × \(Int(surface.height)))."
            return
        }

        if regionW <= 0 || regionH <= 0 {
            validationError = "Width and height must be positive."
            return
        }

        let validator = RegionValidator()
        let repo = surfaceRepo
        let existingSTGs = (try? await repo.getSTGsBySurface(surfaceId: surface.id)) ?? []
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
            do {
                try await repo.updateSTG(stg: updated)
                dismiss()
            } catch {
                validationError = "Save failed: \(error.localizedDescription)"
            }
        } else {
            let messages = result.errors as? [RegionValidationError] ?? []
            validationError = messages.map { $0.message }.joined(separator: "\n")
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
                groutWidth: 3,
                doorRotation: nil
            )
        )
    }
}

