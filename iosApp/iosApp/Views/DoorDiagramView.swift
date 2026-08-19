import SwiftUI
import SharedLogic

/// Mini top-down room diagram for the door wall picker. The room rectangle is
/// drawn centered and aspect-preserved; taps hit-test through the shared
/// `DoorPickerGeometry.wallAtTap` (inside the rectangle, near an edge).
struct DoorDiagramView: View {
    let roomWidth: Double
    let roomDepth: Double
    let selectedWall: Double?
    let onWallSelected: (Double) -> Void

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let safeWidth = max(roomWidth, 1)
            let safeDepth = max(roomDepth, 1)
            let scale = min(size.width / safeWidth, size.height / safeDepth)
            let rectW = roomWidth * scale
            let rectH = roomDepth * scale
            let rect = CGRect(
                x: (size.width - rectW) / 2,
                y: (size.height - rectH) / 2,
                width: rectW,
                height: rectH
            )

            ZStack {
                Rectangle()
                    .stroke(diagramEdgeColor, lineWidth: 2)
                    .frame(width: rectW, height: rectH)

                selectedEdgeIndicator(rect: rect, rectW: rectW, rectH: rectH)

                VStack {
                    Text("Back").font(.caption2).foregroundStyle(diagramEdgeColor).padding(.top, 2)
                    Spacer()
                    Text("Front").font(.caption2).foregroundStyle(diagramEdgeColor).padding(.bottom, 2)
                }
                HStack {
                    Text("Left").font(.caption2).foregroundStyle(diagramEdgeColor).padding(.leading, 2)
                    Spacer()
                    Text("Right").font(.caption2).foregroundStyle(diagramEdgeColor).padding(.trailing, 2)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .accessibilityIdentifier("door-diagram")
            .onTapGesture { location in
                let wall = DoorPickerGeometry().wallAtTap(
                    x: Double(location.x),
                    y: Double(location.y),
                    diagramW: Double(size.width),
                    diagramH: Double(size.height),
                    roomWidth: roomWidth,
                    roomDepth: roomDepth,
                    edgeMarginFraction: 0.15
                )
                if let wall = wall {
                    onWallSelected(wall.doubleValue)
                }
            }
        }
    }

    @ViewBuilder
    private func selectedEdgeIndicator(rect: CGRect, rectW: CGFloat, rectH: CGFloat) -> some View {
        let notchW = rectW * 0.2
        let notchH = rectH * 0.2
        switch selectedWall {
        case 0: // Front = bottom edge
            edgeLine(from: CGPoint(x: rect.minX, y: rect.maxY),
                     to: CGPoint(x: rect.maxX, y: rect.maxY))
            notch(width: notchW, height: 8)
                .position(x: rect.midX, y: rect.maxY - 4)
        case 90: // Left edge
            edgeLine(from: CGPoint(x: rect.minX, y: rect.minY),
                     to: CGPoint(x: rect.minX, y: rect.maxY))
            notch(width: 8, height: notchH)
                .position(x: rect.minX + 4, y: rect.midY)
        case 180: // Back = top edge
            edgeLine(from: CGPoint(x: rect.minX, y: rect.minY),
                     to: CGPoint(x: rect.maxX, y: rect.minY))
            notch(width: notchW, height: 8)
                .position(x: rect.midX, y: rect.minY + 4)
        case 270: // Right edge
            edgeLine(from: CGPoint(x: rect.maxX, y: rect.minY),
                     to: CGPoint(x: rect.maxX, y: rect.maxY))
            notch(width: 8, height: notchH)
                .position(x: rect.maxX - 4, y: rect.midY)
        default:
            EmptyView()
        }
    }

    private func edgeLine(from: CGPoint, to: CGPoint) -> some View {
        Path { path in
            path.move(to: from)
            path.addLine(to: to)
        }
        .stroke(Color.blue, lineWidth: 3)
    }

    private func notch(width: CGFloat, height: CGFloat) -> some View {
        Rectangle()
            .fill(doorCutoutColor)
            .frame(width: width, height: height)
    }

    private var diagramEdgeColor: Color { Color(red: 0.62, green: 0.58, blue: 0.52) }
    private var doorCutoutColor: Color { Color(red: 0x3A / 255.0, green: 0x3A / 255.0, blue: 0x3A / 255.0) }
}

/// Door configuration section: diagram wall picker, width/height/offset fields,
/// inline validation, and a Save button gated on dirty & valid state.
struct DoorSectionView: View {
    let room: Room
    let roomRepo: RoomRepository
    let onSaved: () async -> Void

    @State private var selectedWall: Double?
    @State private var widthText: String
    @State private var heightText: String
    @State private var offsetText: String
    @State private var offsetTouched: Bool

    init(room: Room, roomRepo: RoomRepository, onSaved: @escaping () async -> Void) {
        self.room = room
        self.roomRepo = roomRepo
        self.onSaved = onSaved
        _selectedWall = State(initialValue: room.doorWall?.doubleValue)
        _widthText = State(initialValue: String(Int(room.doorWidth)))
        _heightText = State(initialValue: String(Int(room.doorHeight)))
        _offsetText = State(initialValue: room.doorOffset != nil ? String(Int(room.doorOffset!.doubleValue)) : "")
        _offsetTouched = State(initialValue: room.doorOffset != nil)
    }

    private let minDoorWidth = 400.0
    private let minDoorHeight = 1500.0

    private var wallSpan: Double {
        switch selectedWall {
        case 90, 270: return room.depth
        default: return room.width
        }
    }

    private var width: Double { Double(widthText) ?? 0 }
    private var height: Double { Double(heightText) ?? 0 }
    private var offset: Double? { offsetText.isEmpty ? nil : Double(offsetText) }

    private var widthError: Bool { width < minDoorWidth || width > wallSpan }
    private var heightError: Bool { height < minDoorHeight || height > room.height }
    private var offsetError: Bool {
        guard offsetTouched, let offset, !offsetText.isEmpty else { return false }
        return offset < 0 || offset > wallSpan - width
    }

    private var valid: Bool { !widthError && !heightError && !offsetError }

    private var dirty: Bool {
        selectedWall != room.doorWall?.doubleValue ||
        abs(width - room.doorWidth) > 0.01 ||
        abs(height - room.doorHeight) > 0.01 ||
        offset != room.doorOffset?.doubleValue
    }

    private var canSave: Bool {
        if selectedWall == nil { return dirty }
        return dirty && valid
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            DoorDiagramView(
                roomWidth: room.width,
                roomDepth: room.depth,
                selectedWall: selectedWall,
                onWallSelected: { wall in
                    selectedWall = wall
                    let span = wallSpanAfterSelecting(wall)
                    offsetText = String(Int(max((span - width) / 2, 0)))
                    offsetTouched = false
                }
            )
            .frame(height: 180)
            .frame(maxWidth: .infinity)

            Button("None") { selectedWall = nil }
                .font(.caption)
                .frame(maxWidth: .infinity)

            HStack(spacing: 12) {
                field("Width", text: $widthText, caption: widthErrorText)
                field("Height", text: $heightText, caption: heightErrorText)
            }

            field("Offset from wall corner (mm)", text: $offsetText, caption: offsetCaption)

            Button("Save Door") {
                Task {
                    try? await roomRepo.updateDoor(
                        roomId: room.id,
                        doorWall: selectedWall.map { KotlinDouble(double: $0) },
                        doorWidth: width,
                        doorHeight: height,
                        doorOffset: offset.map { KotlinDouble(double: $0) }
                    )
                    await onSaved()
                }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity)
            .disabled(!canSave)
            .accessibilityIdentifier("save-door")
        }
        .padding(.vertical, 4)
    }

    private func wallSpanAfterSelecting(_ wall: Double) -> Double {
        switch wall {
        case 90, 270: return room.depth
        default: return room.width
        }
    }

    private func field(_ label: String, text: Binding<String>, caption: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Spacer()
                TextField(label, text: text)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .disabled(selectedWall == nil)
                    .frame(maxWidth: 110)
            }
            if let caption {
                Text(caption).font(.caption2).foregroundStyle(.red)
            }
        }
    }

    private var widthErrorText: String? {
        guard selectedWall != nil, widthError else { return nil }
        return "Door width must be \(Int(minDoorWidth))–\(Int(wallSpan)) mm"
    }

    private var heightErrorText: String? {
        guard selectedWall != nil, heightError else { return nil }
        return "Door height must be \(Int(minDoorHeight))–\(Int(room.height)) mm"
    }

    private var offsetCaption: String? {
        if offsetError {
            return "Offset must be 0–\(Int(wallSpan - width)) mm"
        }
        if selectedWall != nil && !offsetTouched {
            return "Auto-centered on wall selection (\(Int(max((wallSpan - width) / 2, 0))) mm)"
        }
        return nil
    }
}
