import SharedLogic
import SwiftUI

/// 3D preview tab: SceneKit view of the room with orbit / pinch / scroll
/// camera control, tap-to-select, and per-wall hide/show.
struct PreviewTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    @State private var showExportSheet = false
    @State private var showHint = true
    @State private var rendererHandle = RoomRendererHandle()

    private var walls: [Surface] {
        vm.surfaces.filter { $0.type == SurfaceType.wall }
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                RoomSceneView(
                    scene: vm.previewScene,
                    camera: vm.previewCamera,
                    hiddenSurfaceIds: vm.hiddenSurfaceIds,
                    selectedSurfaceId: vm.selectedSurfaceId,
                    handle: rendererHandle,
                    onOrbit: { dYaw, dPitch in
                        showHint = false
                        vm.orbitPreview(deltaYaw: dYaw, deltaPitch: dPitch)
                    },
                    onZoom: { factor in
                        showHint = false
                        vm.zoomPreviewBy(factor)
                    },
                    onTap: { location, viewport in
                        if let id = vm.pickSurface(
                            x: location.x,
                            y: location.y,
                            viewportWidth: viewport.width,
                            viewportHeight: viewport.height
                        ) {
                            vm.selectSurface(id)
                        }
                    }
                )

                if showHint && !vm.surfaces.isEmpty {
                    VStack {
                        Text("Drag to rotate · Pinch to zoom · Tap a wall to select")
                            .font(.caption)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(.black.opacity(0.55), in: Capsule())
                            .padding(.top, 12)
                        Spacer()
                    }
                    .allowsHitTesting(false)
                }

                VStack {
                    Spacer()
                    HStack(spacing: 8) {
                        Spacer()
                        Button("Top") { vm.orbitPreview(deltaYaw: 0, deltaPitch: 90) }
                            .accessibilityIdentifier("preview-top")
                        Button("Reset view") { vm.resetPreviewCamera() }
                            .accessibilityIdentifier("preview-reset")
                    }
                    .font(.caption)
                    .buttonStyle(.bordered)
                    .padding(8)
                }
            }

            if let selected = vm.selectedSurface {
                HStack {
                    Text("\(selected.displayName()) · \(Int(selected.width))×\(Int(selected.height)) mm")
                        .font(.caption)
                    Spacer()
                    if selected.type == SurfaceType.wall {
                        let hidden = vm.hiddenSurfaceIds.contains(selected.id)
                        Button {
                            vm.toggleSurfaceHidden(selected.id)
                        } label: {
                            Label(hidden ? "Show wall" : "Hide wall", systemImage: hidden ? "eye" : "eye.slash")
                                .font(.caption)
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 6)
                .background(Color(.secondarySystemBackground))
            }

            if !walls.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Walls")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(walls, id: \.id) { wall in
                                wallChip(wall)
                            }
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top, 8)
            }

            HStack {
                Spacer()
                if !vm.hiddenSurfaceIds.isEmpty {
                    Button("Show all walls") { vm.showAllSurfaces() }
                        .font(.caption)
                }
                Button {
                    showExportSheet = true
                } label: {
                    Label("Export", systemImage: "square.and.arrow.up")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
        }
        .sheet(isPresented: $showExportSheet) {
            ExportSheetView(title: "Preview") { dpi in
                let scale = dpi / 72
                let longest = 1200 * scale
                let clamp = min(1, Self.maxExportPixels / longest)
                let size = CGSize(width: 1200 * scale * clamp, height: 900 * scale * clamp)
                return rendererHandle.snapshot(size: size)
            }
        }
    }

    private func wallChip(_ wall: Surface) -> some View {
        let visible = !vm.hiddenSurfaceIds.contains(wall.id)
        return Button {
            vm.toggleSurfaceHidden(wall.id)
        } label: {
            Label(wall.displayName(), systemImage: visible ? "eye" : "eye.slash")
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    visible ? Color.accentColor.opacity(0.15) : Color(.tertiarySystemFill),
                    in: Capsule()
                )
                .foregroundStyle(visible ? Color.accentColor : Color.secondary)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("wall-chip-\(wall.id)")
        .accessibilityValue(visible ? "Visible" : "Hidden")
    }

    private static let maxExportPixels: CGFloat = 4096
}

#Preview {
    PreviewTabView(vm: IOSRoomEditorViewModel())
}
