import SwiftUI
import SharedLogic

/// 3D isometric canvas drawing helpers using shared IsometricProjection math.
struct IsometricCanvas {

    /// Draw the isometric room view: all surfaces projected in 3D.
    static func drawIsometricRoom(
        context: inout GraphicsContext,
        size: CGSize,
        surfaces: [Surface],
        viewAngle: Int,
        selectedSurfaceId: String?
    ) {
        let originX = size.width / 2.0
        let originY = size.height * 0.6
        let projection = IsometricProjection()

        // Order surfaces back-to-front (painter's algorithm)
        let ordered = projection.orderSurfaces(surfaces: surfaces, viewAngle: Int32(viewAngle)) as? [Surface] ?? surfaces

        for surface in ordered {
            let corners = projection.projectSurfaceCorners(
                surface: surface,
                viewAngle: Int32(viewAngle),
                originX: originX,
                originY: originY
            ) as? [IsometricProjectionScreenPoint] ?? []

            guard corners.count >= 4 else { continue }

            let cgPoints = corners.map { CGPoint(x: $0.x, y: $0.y) }

            // Draw surface polygon
            var path = Path()
            path.move(to: cgPoints[0])
            for i in 1..<cgPoints.count {
                path.addLine(to: cgPoints[i])
            }
            path.closeSubpath()

            let isSelected = surface.id == selectedSurfaceId
            let fillColor = surfaceFillColor(surface: surface, isSelected: isSelected)
            context.fill(path, with: .color(fillColor))

            // Selected surface gets a thicker border
            let strokeWidth: CGFloat = isSelected ? 3.0 : 1.0
            let strokeColor: Color = isSelected ? .blue : .gray.opacity(0.5)
            context.stroke(path, with: .color(strokeColor), lineWidth: strokeWidth)

            // Draw surface label at centroid
            let centroid = CGPoint(
                x: cgPoints.map(\.x).reduce(0, +) / CGFloat(cgPoints.count),
                y: cgPoints.map(\.y).reduce(0, +) / CGFloat(cgPoints.count)
            )
            let label = surfaceLabel(surface: surface)
            let text = Text(label)
                .font(.system(size: 9))
                .foregroundColor(.primary)
            context.draw(text, at: centroid)
        }
    }

    private static func surfaceFillColor(surface: Surface, isSelected: Bool) -> Color {
        if isSelected {
            return .blue.opacity(0.25)
        }
        switch surface.type {
        case SurfaceType.wall:
            return .gray.opacity(0.15)
        case SurfaceType.floor:
            return .brown.opacity(0.1)
        default:
            return .gray.opacity(0.1)
        }
    }

    private static func surfaceLabel(surface: Surface) -> String {
        let typeName = surface.type == SurfaceType.wall ? "Wall" : "Floor"
        return "\(typeName) \(Int(surface.width))×\(Int(surface.height))"
    }
}
