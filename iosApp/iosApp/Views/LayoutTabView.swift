import SwiftUI
import SharedLogic

/// 2D layout tab with Canvas rendering, drag gesture for tile offset adjustments,
/// lock propagation chips, and undo support.
struct LayoutTabView: View {
    @ObservedObject var vm: IOSRoomEditorViewModel
    @GestureState private var dragOffset = CGSize.zero
    @State private var showExportSheet = false

    private let scaleFactor: Double = 1.0  // Points per mm — adjust for zoom

    var body: some View {
        VStack(spacing: 0) {
            // Surface selector chips
            surfaceSelector

            Divider()

            // 2D Canvas with drag gesture
            canvasView

            Divider()

            // Lock chips
            lockChips

            // Action buttons
            actionButtons
        }
        .sheet(isPresented: $showExportSheet) {
            ExportSheetView(title: "Layout") { dpi in
                ExportService.renderToImage(
                    content: Canvas { context, size in
                        SurfaceCanvas.drawTiles(
                            context: &context,
                            size: size,
                            tiles: vm.currentTiles,
                            groutColor: .gray,
                            groutWidth: 3
                        )
                    }
                    .background(Color.white),
                    dpi: dpi
                )
            }
        }
    }

    // MARK: - Surface Selector

    private var surfaceSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(vm.surfaces, id: \.id) { surface in
                    Button {
                        vm.selectSurface(surface.id)
                    } label: {
                        Text(vm.displayName(for: surface))
                            .font(.caption)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.bordered)
                    .tint(surface.id == vm.selectedSurfaceId ? .blue : .gray)
                }

                if vm.surfaces.isEmpty {
                    Text("No surfaces — generate them in the Surfaces tab")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Canvas

    private var canvasView: some View {
        GeometryReader { geometry in
            if vm.currentTiles.isEmpty {
                ContentUnavailableView(
                    "No Layout",
                    systemImage: "square.grid.3x3",
                    description: Text("Select a surface to view its tile layout.")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Canvas { context, size in
                    SurfaceCanvas.drawTiles(
                        context: &context,
                        size: size,
                        tiles: vm.currentTiles,
                        groutColor: .gray,
                        groutWidth: 3
                    )
                }
                .gesture(
                    DragGesture(minimumDistance: 1)
                        .updating($dragOffset) { value, state, _ in
                            state = value.translation
                        }
                        .onChanged { _ in
                            if !vm.isDragging {
                                vm.isDragging = true
                                Task { await vm.onDragStart() }
                            }
                        }
                        .onEnded { value in
                            vm.isDragging = false
                            let dx = Double(value.translation.width) / scaleFactor
                            let dy = Double(value.translation.height) / scaleFactor
                            Task { await vm.onDragEnd(dx: dx, dy: dy) }
                        }
                )
            }
        }
    }

    // MARK: - Lock Chips

    private var lockChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Text("Lock:").font(.caption).foregroundStyle(.secondary)

                ForEach(vm.otherSurfaces, id: \.id) { surface in
                    Button {
                        vm.toggleLock(surface.id)
                    } label: {
                        Label(vm.displayName(for: surface),
                              systemImage: vm.isLocked(surface.id) ? "lock.fill" : "lock.open")
                            .font(.caption2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                    }
                    .buttonStyle(.bordered)
                    .tint(vm.isLocked(surface.id) ? .purple : .gray)
                }

                if vm.otherSurfaces.isEmpty {
                    Text("No other surfaces").font(.caption2).foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 6)
        }
    }

    // MARK: - Action Buttons

    private var actionButtons: some View {
        HStack(spacing: 12) {
            Button {
                Task {
                    if let surfaceId = vm.selectedSurfaceId {
                        await vm.resetToAuto(surfaceId: surfaceId)
                    }
                }
            } label: {
                Label("Reset", systemImage: "arrow.counterclockwise")
                    .font(.caption)
            }
            .buttonStyle(.bordered)

            Button {
                Task {
                    if let surfaceId = vm.selectedSurfaceId {
                        await vm.snapToCenter(surfaceId: surfaceId)
                    }
                }
            } label: {
                Label("Snap Center", systemImage: "align.horizontal.center")
                    .font(.caption)
            }
            .buttonStyle(.bordered)

            Button {
                Task { await vm.undo() }
            } label: {
                Label("Undo", systemImage: "arrow.uturn.backward")
                    .font(.caption)
            }
            .buttonStyle(.bordered)
            .disabled(!vm.hasUndoBuffer)

            Button {
                showExportSheet = true
            } label: {
                Label("Export", systemImage: "square.and.arrow.up")
                    .font(.caption)
            }
            .buttonStyle(.bordered)
            .disabled(vm.currentTiles.isEmpty)

            Spacer()

            NavigationLink {
                HelpDiagramView()
            } label: {
                Image(systemName: "questionmark.circle")
                    .font(.title3)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
}

#Preview {
    LayoutTabView(vm: IOSRoomEditorViewModel())
}
