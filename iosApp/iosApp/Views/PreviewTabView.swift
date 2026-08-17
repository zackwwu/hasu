import SwiftUI

/// 3D preview tab with isometric Canvas, hit testing for surface selection,
/// rotation controls, and top-down toggle.
struct PreviewTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    @State private var topDown = false
    @State private var showExportSheet = false
    @State private var zoomAtGestureStart: Double = 1.0

    var body: some View {
        VStack(spacing: 0) {
            // 3D Isometric Canvas
            GeometryReader { geometry in
                let canvasSize = geometry.size

                Canvas { context, size in
                    IsometricCanvas.drawIsometricRoom(
                        context: &context,
                        size: size,
                        surfaces: vm.surfaces,
                        viewAngle: vm.viewAngle,
                        selectedSurfaceId: vm.selectedSurfaceId,
                        zoom: vm.previewZoom
                    )
                }
                .contentShape(Rectangle())
                .onTapGesture { location in
                    if let id = vm.hitTest(
                        tapX: location.x,
                        tapY: location.y,
                        canvasWidth: canvasSize.width,
                        canvasHeight: canvasSize.height
                    ) {
                        vm.selectSurface(id)
                    }
                }
                .simultaneousGesture(
                    MagnifyGesture()
                        .onChanged { value in
                            // magnification is cumulative per gesture, starting at 1.0
                            if value.magnification == 1.0 {
                                zoomAtGestureStart = vm.previewZoom
                            }
                            vm.setPreviewZoom(zoomAtGestureStart * value.magnification)
                        }
                )
            }

            Divider()

            // Rotation controls
            HStack(spacing: 16) {
                Button {
                    vm.rotateView(-90)
                } label: {
                    Image(systemName: "arrow.counterclockwise")
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("rotate-left")

                Text("\(vm.viewAngle)°")
                    .font(.headline)
                    .frame(minWidth: 50)

                Button {
                    vm.rotateView(90)
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("rotate-right")

                Divider()
                    .frame(height: 24)

                Toggle(isOn: $topDown) {
                    Text("Top-Down")
                        .font(.caption)
                }
                .toggleStyle(.switch)
                .onChange(of: topDown) { _, newValue in
                    if newValue {
                        // Set view angle to ~90 degrees for top-down look
                        vm.rotateView(90 - (vm.viewAngle % 360))
                    }
                }

                Button {
                    vm.resetPreviewZoom()
                } label: {
                    Text("\(Int(vm.previewZoom * 100))%")
                        .font(.caption)
                        .foregroundStyle(vm.previewZoom != 1.0 ? Color.blue : Color.secondary)
                }
                .buttonStyle(.plain)

                Spacer()

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

            // Surface info for selected
            if let selected = vm.selectedSurface {
                HStack {
                    Text("Selected: \(vm.displayName(for: selected))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("Angle: \(Int(selected.position.rotation))°")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
            }
        }
        .sheet(isPresented: $showExportSheet) {
            ExportSheetView(title: "Preview") { dpi in
                ExportService.renderToImage(
                    content: Canvas { context, size in
                        IsometricCanvas.drawIsometricRoom(
                            context: &context,
                            size: size,
                            surfaces: vm.surfaces,
                            viewAngle: vm.viewAngle,
                            selectedSurfaceId: vm.selectedSurfaceId
                        )
                    }
                    .background(Color.white),
                    dpi: dpi
                )
            }
        }
    }
}

#Preview {
    PreviewTabView(vm: IOSRoomEditorViewModel())
}
