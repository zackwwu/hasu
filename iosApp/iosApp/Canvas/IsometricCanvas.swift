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
        zoom: Double = 1.0,
        doorWorldRects: [String: [SIMD3<Double>]] = [:]
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

        // Drop shadow: floor silhouette offset down-right, drawn first
        if let floor = surfaces.first(where: { $0.type == SurfaceType.floor }) {
            let shadowCorners = projectCorners(
                projection: projection,
                surface: floor,
                viewAngle: viewAngle,
                originX: fit.originX,
                originY: fit.originY,
                scale: fit.scale * zoom,
                count: 4
            )
            var shadowPath = Path()
            let offset: CGFloat = max(4, 10 * CGFloat(zoom))
            shadowPath.move(to: CGPoint(
                x: shadowCorners[0].x + offset,
                y: shadowCorners[0].y + offset * 1.4
            ))
            for i in 1..<shadowCorners.count {
                shadowPath.addLine(to: CGPoint(
                    x: shadowCorners[i].x + offset,
                    y: shadowCorners[i].y + offset * 1.4
                ))
            }
            shadowPath.closeSubpath()
            context.fill(shadowPath, with: .color(.black.opacity(0.15)))
        }

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
            let fillColor = surfaceFillColor(surface: surface, isSelected: isSelected, light: lightFactor(for: surface, viewAngle: viewAngle))
            context.fill(path, with: .color(fillColor))

            let strokeWidth: CGFloat = isSelected ? 3.0 : 1.2
            let strokeColor: Color = isSelected ? .blue : Color(red: 0.62, green: 0.58, blue: 0.52)
            context.stroke(path, with: .color(strokeColor), lineWidth: strokeWidth)

            // Door opening: dark cutout drawn after the wall fill, before the label
            if let doorCorners = doorWorldRects[surface.id] {
                var doorPath = Path()
                let scale = fit.scale * zoom
                for (i, corner) in doorCorners.enumerated() {
                    let sp = projection.project(
                        sx: corner.x * scale,
                        sy: corner.y * scale,
                        sz: corner.z * scale,
                        viewAngle: Int32(viewAngle),
                        originX: fit.originX,
                        originY: fit.originY
                    )
                    let point = CGPoint(x: sp.x, y: sp.y)
                    if i == 0 {
                        doorPath.move(to: point)
                    } else {
                        doorPath.addLine(to: point)
                    }
                }
                doorPath.closeSubpath()
                context.fill(doorPath, with: .color(Color(red: 0x3A / 255.0, green: 0x3A / 255.0, blue: 0x3A / 255.0)))
                context.stroke(doorPath, with: .color(Color(red: 0x6E / 255.0, green: 0x66 / 255.0, blue: 0x58 / 255.0)), lineWidth: 1.2)
            }

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

    private static func surfaceFillColor(surface: Surface, isSelected: Bool, light: Double) -> Color {
        if isSelected {
            return Color(red: 0.0, green: 0.74, blue: 0.83).opacity(0.33)
        }
        let factor = CGFloat(light)
        switch surface.type {
        case SurfaceType.wall:
            // warm beige, shaded per face orientation
            return Color(
                red: 0xE8 / 255.0 * factor,
                green: 0xE0 / 255.0 * factor,
                blue: 0xD8 / 255.0 * factor
            )
        case SurfaceType.floor:
            return Color(
                red: 0xD4 / 255.0 * (0.9 + 0.1 * factor),
                green: 0xC8 / 255.0 * (0.9 + 0.1 * factor),
                blue: 0xB8 / 255.0 * (0.9 + 0.1 * factor)
            )
        default:
            return .gray.opacity(0.15)
        }
    }

    private static func lightFactor(for surface: Surface, viewAngle: Int) -> Double {
        let projection = IsometricProjection()
        return projection.faceLightFactor(
            surface: surface,
            viewAngle: Int32(viewAngle)
        )
    }

    private static func surfaceLabel(surface: Surface) -> String {
        surface.displayName()
    }
}
