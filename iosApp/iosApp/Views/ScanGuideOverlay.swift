import SwiftUI
import SharedLogic

/// Dashed frame at the tile's own aspect ratio, so the user can square the tile up
/// before shooting. Geometry comes from the shared ScanGuideGeometry, so iOS and
/// Android frame identically.
struct ScanGuideOverlay: View {
    let tileWidth: Double
    let tileHeight: Double

    /// Space reserved for the scanner's own chrome, so the frame never sits under
    /// the close button or the shutter. Padding shrinks the measured region.
    var chromeTop: CGFloat = 60
    var chromeBottom: CGFloat = 140

    var body: some View {
        GeometryReader { geo in
            let frame = ScanGuideGeometry.shared.frame(
                viewportWidth: Double(geo.size.width),
                viewportHeight: Double(geo.size.height),
                tileWidth: tileWidth,
                tileHeight: tileHeight,
                insetFraction: 0.85
            )
            ZStack {
                Rectangle()
                    .stroke(.white.opacity(0.9),
                            style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
                    .frame(width: CGFloat(frame.width), height: CGFloat(frame.height))
                    .position(x: CGFloat(frame.x + frame.width / 2),
                              y: CGFloat(frame.y + frame.height / 2))
                    .shadow(radius: 2)

                // Confirms which tile group is being scanned.
                Text("\(Int(tileWidth)) × \(Int(tileHeight)) mm")
                    .font(.caption2)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(.black.opacity(0.5))
                    .clipShape(Capsule())
                    .position(x: CGFloat(frame.x + frame.width / 2),
                              y: CGFloat(frame.y + frame.height + 16))
            }
        }
        .padding(.top, chromeTop)
        .padding(.bottom, chromeBottom)
    }
}
