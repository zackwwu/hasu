import SwiftUI
import SharedLogic

/// 2D elevation canvas drawing helpers for surface tile layouts.
struct SurfaceCanvas {
    /// Draw all tiles for a surface onto the given Canvas context.
    static func drawTiles(
        context: inout GraphicsContext,
        size: CGSize,
        tiles: [PlacedTile],
        groutColor: Color,
        groutWidth: Double
    ) {
        guard !tiles.isEmpty else { return }

        // Compute the bounding box of all tiles to determine scale
        let maxX = tiles.map { $0.x + $0.width }.max() ?? 1
        let maxY = tiles.map { $0.y + $0.height }.max() ?? 1
        let scaleX = size.width / maxX
        let scaleY = size.height / maxY
        let scale = min(scaleX, scaleY) * 0.95
        let offsetX = (size.width - maxX * scale) / 2
        let offsetY = (size.height - maxY * scale) / 2

        // Draw grout background
        let bgRect = CGRect(x: offsetX, y: offsetY, width: maxX * scale, height: maxY * scale)
        context.fill(Path(bgRect), with: .color(groutColor))

        // Draw each tile
        for tile in tiles {
            let tileRect = CGRect(
                x: offsetX + tile.x * scale,
                y: offsetY + tile.y * scale,
                width: tile.width * scale,
                height: tile.height * scale
            )

            // Fill tile with texture or fallback color
            let tileColor = tileFillColor(tile: tile)
            context.fill(Path(tileRect), with: .color(tileColor))

            // Draw tile border
            context.stroke(Path(tileRect), with: .color(.black.opacity(0.3)), lineWidth: 0.5)

            // Mark cut edges
            if tile.isCut {
                drawCutMarkers(context: &context, rect: tileRect, cutEdges: tile.cutEdges as? [CutEdge] ?? [])
            }

            // Label rotation for herringbone
            if abs(tile.rotation) > 0.01 {
                let center = CGPoint(x: tileRect.midX, y: tileRect.midY)
                let text = Text("\(Int(tile.rotation))°")
                    .font(.system(size: 8))
                    .foregroundColor(.white)
                context.draw(text, at: center)
            }
        }
    }

    private static func tileFillColor(tile: PlacedTile) -> Color {
        if tile.isCut {
            return Color.orange.opacity(0.5)
        }
        // Color by tileGroupId hash for visual distinction
        let hash = tile.tileGroupId.hashValue
        let hue = Double(abs(hash) % 360) / 360.0
        return Color(hue: hue, saturation: 0.3, brightness: 0.85)
    }

    private static func drawCutMarkers(
        context: inout GraphicsContext,
        rect: CGRect,
        cutEdges: [CutEdge]
    ) {
        let markerWidth: CGFloat = 3.0
        for edge in cutEdges {
            let markerRect: CGRect
            switch edge {
            case CutEdge.left:
                markerRect = CGRect(x: rect.minX, y: rect.minY, width: markerWidth, height: rect.height)
            case CutEdge.right:
                markerRect = CGRect(x: rect.maxX - markerWidth, y: rect.minY, width: markerWidth, height: rect.height)
            case CutEdge.top:
                markerRect = CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: markerWidth)
            case CutEdge.bottom:
                markerRect = CGRect(x: rect.minX, y: rect.maxY - markerWidth, width: rect.width, height: markerWidth)
            default:
                continue
            }
            context.fill(Path(markerRect), with: .color(.red))
        }
    }
}
