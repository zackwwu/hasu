import SwiftUI

/// 3D preview tab with isometric Canvas, hit testing for surface selection,
/// rotation controls, and top-down toggle.
struct PreviewTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    @State private var topDown = false

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
                        selectedSurfaceId: vm.selectedSurfaceId
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

                Text("\(vm.viewAngle)°")
                    .font(.headline)
                    .frame(minWidth: 50)

                Button {
                    vm.rotateView(90)
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.bordered)

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
    }
}

#Preview {
    PreviewTabView(vm: IOSRoomEditorViewModel())
}
