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
        selectedSurfaceId: String?,
        zoom: Double = 1.0
    ) {
        let projection = IsometricProjection()
        let fit = projection.fitViewport(
            surfaces: surfaces,
            viewAngle: Int32(viewAngle),
            viewportWidth: Double(size.width),
            viewportHeight: Double(size.height),
            paddingFraction: 0.9
        )

        // Order surfaces back-to-front (painter's algorithm)
        let ordered = projection.orderSurfaces(surfaces: surfaces, viewAngle: Int32(viewAngle)) as? [Surface] ?? surfaces

        for surface in ordered {
            let corners = projectCorners(
                projection: projection,
                surface: surface,
                viewAngle: viewAngle,
                originX: fit.originX,
                originY: fit.originY,
                scale: fit.scale * zoom,
                count: 4
            )

            // Draw surface polygon
            var path = Path()
            path.move(to: corners[0])
            for i in 1..<corners.count {
                path.addLine(to: corners[i])
            }
            path.closeSubpath()

            let isSelected = surface.id == selectedSurfaceId
            let fillColor = surfaceFillColor(surface: surface, isSelected: isSelected)
            context.fill(path, with: .color(fillColor))

            let strokeWidth: CGFloat = isSelected ? 3.0 : 1.0
            let strokeColor: Color = isSelected ? .blue : .gray.opacity(0.5)
            context.stroke(path, with: .color(strokeColor), lineWidth: strokeWidth)

            // Draw surface label at centroid
            let cx = corners.map(\.x).reduce(0, +) / CGFloat(corners.count)
            let cy = corners.map(\.y).reduce(0, +) / CGFloat(corners.count)
            let label = surfaceLabel(surface: surface)
            let text = Text(label).font(.system(size: 9)).foregroundColor(.primary)
            context.draw(text, at: CGPoint(x: cx, y: cy))
        }
    }

    /// Project a surface's corners to screen points using the shared projection.
    private static func projectCorners(
        projection: IsometricProjection,
        surface: Surface,
        viewAngle: Int,
        originX: Double,
        originY: Double,
        scale: Double,
        count: Int
    ) -> [CGPoint] {
        let corners = projection.projectSurfaceCorners(
            surface: surface,
            viewAngle: Int32(viewAngle),
            originX: originX,
            originY: originY,
            scale: scale
        ) as? [Any] ?? []

        var points: [CGPoint] = []
        for corner in corners {
            if let pt = corner as? IsometricProjection.ScreenPoint {
                points.append(CGPoint(x: pt.x, y: pt.y))
            }
        }
        return points
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
